"""Review/export an exact source allowlist. Never invokes Git or contacts a network."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]
POLICY = ".repository/allowlist.json"
REVIEWS = ".repository/scan-reviews.json"
RULES = {
    "private-key": r"-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----",
    "provider-token": r"(?<![A-Za-z0-9_])(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|AKIA[A-Z0-9]{16}|xox[baprs]-[A-Za-z0-9-]{20,}|sk-(?:proj-)?[A-Za-z0-9_-]{40,})",
    "local-address": r"\b(?:192\.168\.\d+\.\d+|10\.\d+\.\d+\.\d+|172\.(?:1[6-9]|2\d|3[01])\.\d+\.\d+)\b",
    "personal-path": r"(?i)(?:[A-Z]:[/\\](?:Users|iFeel|iFielsc|Java|Program Files|Software)[/\\]|账户\.txt)",
    "credential-literal": r'''(?i)(?:password|passwd|pwd|secret|api[_-]?key|access[_-]?key|token|aes[_-]?key)[\w-]*["']?\s*[:=]\s*["'][^"'\r\n]{4,}["']''',
    "credential-setter": r'''(?i)\bset(?:Password|Secret|Token|ApiKey|AccessKey|PasswordHash)\(\s*["'][^"'\r\n]{4,}["']''',
    "credential-scalar": r'''(?i)^\s*(?:[\w.-]*password(?:-hash)?|[\w.-]*secret|[\w.-]*token|[\w.-]*aes\.key)\s*[:=]\s*[^\s#$"'{][^\r\n]{3,}''',
    "url-credential": r"(?i)\b\w+://[^\s/:]+:[^\s/@]+@",
    "bcrypt-literal": r"\$2[aby]\$\d\d\$[./A-Za-z0-9]{53}",
    "email-address": r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}",
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def safe_path(root, relative):
    parts = PurePosixPath(relative).parts
    if not parts or relative != PurePosixPath(relative).as_posix() or "\\" in relative:
        raise ValueError("Non-canonical allowlist path")
    if PurePosixPath(relative).is_absolute() or any(x in ("..", ".") or ":" in x for x in parts):
        raise ValueError("Unsafe allowlist path")
    current = root
    for part in parts:
        current = current / part
        if current.is_symlink() or getattr(current, "is_junction", lambda: False)():
            raise ValueError("Links/junctions cannot enter the candidate: " + relative)
        if current.exists() and getattr(current.stat(), "st_file_attributes", 0) & 0x400:
            raise ValueError("Reparse point cannot enter the candidate: " + relative)
    if not current.resolve().is_relative_to(root.resolve()):
        raise ValueError("Path escaped source root")
    return current


def forbidden(relative):
    p = PurePosixPath(relative)
    segments = set(p.parts)
    if p.parts[0] in {"backup", "outputs", "output", "local-secrets", ".m2", "UI风格参考"}:
        return True
    if segments & {".git", ".idea", "node_modules", "dist", "__pycache__", "_secrets"}:
        return True
    if relative.startswith("test/") and relative != "test/tools/verify-frontend.ps1":
        return True
    if any(x.startswith(("test-results", "playwright-report")) for x in p.parts):
        return True
    if "target" in p.parts and "src" not in p.parts[:p.parts.index("target")]:
        return True
    if p.name.startswith("application-local.") or p.name == "账户.txt":
        return True
    if p.name.startswith(".env") and not p.name.endswith(".example"):
        return True
    return p.suffix.lower() in {".db", ".sqlite", ".sqlite3", ".log", ".zip", ".jar", ".class", ".pem", ".p12", ".pfx", ".jks", ".key", ".iml", ".exe"}


def load_files(root, generating_ignore=False):
    policy = json.loads((root / POLICY).read_text(encoding="utf-8"))
    files = policy["files"]
    if len(files) != len(set(x.casefold() for x in files)):
        raise ValueError("Duplicate allowlist entries")
    for item in files:
        path = safe_path(root, item)
        if forbidden(item) or (not path.is_file() and not (generating_ignore and item == ".gitignore")):
            raise ValueError("Forbidden or missing allowlist file: " + item)
    return sorted(files)


def ignore_text(files):
    directories = set()
    for item in files:
        directories.update(str(p) for p in PurePosixPath(item).parents if str(p) != ".")
    # Escape Git wildcards; each new file is denied until explicitly reviewed.
    def escape(s):
        return re.sub(r"([*?\[\]\\])", r"\\\1", s)
    lines = ["# Generated from .repository/allowlist.json by tools/repository/prepare.py",
             "# Deny by default. Do not use git add -f to bypass review.", "/*"]
    for directory in sorted(directories, key=lambda x: (x.count("/"), x)):
        lines.extend(["!/" + escape(directory) + "/", "/" + escape(directory) + "/*"])
    lines.extend("!/" + escape(item) for item in sorted(files))
    return "\n".join(lines) + "\n"


def local_values(paths):
    """Read optional local credential sources in memory; never record their values."""
    values = set()
    for p in paths:
        text = p.read_text(encoding="utf-8-sig")
        for line in text.splitlines():
            m = re.search(r"(?i)(?:密码|口令|密钥|password(?:-hash)?|passwd|secret|token|\bkey)\s*[：:=]\s*(.+)", line)
            if not m:
                continue
            value = m[1].strip().strip('"\'')
            env = re.fullmatch(r"\$\{[^:}]+:([^}]+)\}", value)
            if env:
                value = env[1]
            if len(value) >= 8 and not any(x in value for x in ("${", "<", " ", "待", "示例")):
                values.add(value)
    return values


def scan(root, files, known):
    review_data = json.loads((root / REVIEWS).read_text(encoding="utf-8"))
    reviews = review_data["reviews"]
    binary_reviews = {(x["path"], x["sha256"]): x["reason"]
                      for x in review_data.get("binary_reviews", [])}
    reviewed = {(x["path"], x["line"], x["rule"], x["line_sha256"]): x["reason"] for x in reviews}
    findings, binaries = [], []
    patterns = {k: re.compile(v) for k, v in RULES.items()}
    for name in files:
        raw = (root / name).read_bytes()
        try:
            content = raw.decode("utf-8-sig")
        except UnicodeError:
            binaries.append({"path": name, "sha256": digest(raw), "bytes": len(raw)})
            if (name, digest(raw)) not in binary_reviews:
                findings.append({"path": name, "line": 0, "rule": "unreviewed-binary",
                                 "line_sha256": digest(raw), "reviewed": False, "reason": None})
            content = raw.decode("utf-8", errors="replace")
        for number, line in enumerate(content.splitlines(), 1):
            rules = [rule for rule, pattern in patterns.items() if pattern.search(line)
                     and (rule != "credential-scalar" or Path(name).suffix in
                          {".yml", ".yaml", ".properties", ".example", ".env", ".conf"})]
            if any(value in line for value in known):
                rules.append("known-local-value")
            for rule in rules:
                fingerprint = digest(line.encode("utf-8"))
                reason = reviewed.get((name, number, rule, fingerprint))
                if rule in {"known-local-value", "private-key", "provider-token"}:
                    reason = None
                findings.append({"path": name, "line": number, "rule": rule,
                                 "line_sha256": fingerprint, "reviewed": bool(reason), "reason": reason})
    return {"files_scanned": len(files), "rules": list(RULES) + ["known-local-value"],
            "local_comparison_values": len(known), "binary_files": binaries, "findings": findings,
            "unreviewed": sum(not x["reviewed"] for x in findings),
            "limitations": "Heuristic scan of allowlisted files only; Git history is not scanned by this tool. Binary assets require visual/provenance review. New or changed matching lines require review. Not a proof of absence of secrets."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["ignore", "scan", "export"])
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--destination", type=Path)
    parser.add_argument("--local-input", type=Path, action="append", default=[])
    args = parser.parse_args()
    root = args.root.resolve()
    files = load_files(root, generating_ignore=args.action == "ignore")
    expected = ignore_text(files)
    if args.action == "ignore":
        (root / ".gitignore").write_bytes(expected.encode("utf-8"))
        print(json.dumps({"ignore_rules_generated": True, "allowlisted_files": len(files)}))
        return
    if (root / ".gitignore").read_bytes() != expected.encode("utf-8"):
        raise ValueError("Root .gitignore differs from the reviewed allowlist")
    if args.report is None:
        raise ValueError("Provide an excluded local --report path")
    report = args.report.resolve()
    if report.exists():
        raise ValueError("Report already exists; choose a fresh path")
    if report.is_relative_to(root) and report.relative_to(root).as_posix() in files:
        raise ValueError("Report cannot overwrite an allowlisted source")
    result = scan(root, files, local_values(args.local_input))
    if args.action == "export" and result["unreviewed"] == 0:
        if args.destination is None:
            raise ValueError("Provide a new --destination directory")
        destination = args.destination.resolve()
        if destination.exists() or root.is_relative_to(destination):
            raise ValueError("Destination must be new and cannot contain the source")
        if destination.is_relative_to(root):
            if not destination.is_relative_to(root / "test/repository-candidates"):
                raise ValueError("Within the workspace, use test/repository-candidates")
            safe_path(root, destination.relative_to(root).as_posix())
        records = []
        for name in files:
            source, target = root / name, destination / name
            data = source.read_bytes()
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            if target.read_bytes() != data:
                raise ValueError("Candidate copy changed: " + name)
            records.append({"path": name, "bytes": len(data), "sha256": digest(data)})
        # Re-scan the copy, then check every source and copy against the recorded bytes.
        candidate_scan = scan(destination, files, local_values(args.local_input))
        for item in records:
            for base in (root, destination):
                if digest((base / item["path"]).read_bytes()) != item["sha256"]:
                    raise ValueError("Source changed during export: " + item["path"])
        if candidate_scan["unreviewed"]:
            raise ValueError("Candidate scan differs; do not use this candidate")
        result.update({"candidate": str(destination), "candidate_rescanned": True,
                       "candidate_files": records, "git_initialized": False})
    write_json(report, result)
    print(json.dumps({"files": len(files), "findings": len(result["findings"]),
                      "unreviewed": result["unreviewed"], "candidate": result.get("candidate")}, ensure_ascii=False))
    if result["unreviewed"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
