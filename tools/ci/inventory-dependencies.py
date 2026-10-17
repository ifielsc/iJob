"""Inventory generated BOMs and installed pnpm license metadata; no network or legal verdict."""
import argparse
from collections import Counter
import csv
import hashlib
import io
import json
from pathlib import Path
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    inputs = args.input.resolve()
    output = args.output.resolve()
    if output.exists():
        raise ValueError("Use a new output directory; evidence is never overwritten")
    sources = {}
    records = []
    bom_filenames = {}
    for label in ("ijob", "ijob-monitor-agent"):
        path = inputs / f"{label}-bom.json"
        bom = read_json(path)
        sources[path.name] = digest(path.read_bytes())
        for component in bom["components"]:
            filename = f"{component['name']}-{component['version']}.jar"
            bom_filenames.setdefault(filename, set()).add(component.get("purl", component["name"]))
            if component.get("group") == "com.ifelio":
                continue
            records.append({
                "scope": label,
                "coordinate": component.get("purl"),
                "version": component["version"],
                "licenses": component.get("licenses", []),
                "hashes": component.get("hashes", []),
                "evidence": path.name,
            })
    node_modules = (root / "develop/web/ijob/node_modules").resolve()
    for label, filename in (("frontend-all-installed", "frontend-licenses.json"),
                            ("frontend-production-installed", "frontend-production-licenses.json")):
        path = inputs / filename
        sources[path.name] = digest(path.read_bytes())
        for license_name, packages in read_json(path).items():
            for package in packages:
                texts = {version: [] for version in package["versions"]}
                for directory in package["paths"]:
                    package_root = Path(directory).resolve()
                    if not package_root.is_relative_to(node_modules):
                        raise ValueError("pnpm package path escaped the installed dependency tree")
                    metadata = read_json(package_root / "package.json")
                    version = metadata["version"]
                    if metadata["name"] != package["name"] or version not in texts:
                        raise ValueError("pnpm metadata does not match the installed package")
                    for candidate in package_root.iterdir():
                        if candidate.is_file() and candidate.name.lower().startswith(("license", "licence", "copying", "notice")):
                            texts[version].append({"file": candidate.relative_to(node_modules).as_posix(),
                                                   "sha256": digest(candidate.read_bytes())})
                for version in package["versions"]:
                    records.append({"scope": label, "coordinate": f"pkg:npm/{package['name']}@{version}",
                                    "version": version, "licenses": [license_name],
                                    "licenseFiles": texts[version], "evidence": filename})
    archives = []
    for label, relative in (
        ("backend", "develop/api/ijob/ijob-starter/target/ijob-starter-1.0.0.jar"),
        ("agent", "develop/api/ijob-monitor-agent/target/ijob-monitor-agent-1.0.0.jar"),
        ("setup", "develop/api/ijob/ijob-setup-tool/target/ijob-setup-tool-1.0.0.jar"),
        ("migration", "develop/api/ijob/ijob-migration-tool/target/ijob-migration-tool-1.0.0.jar"),
    ):
        path = root / relative
        with zipfile.ZipFile(path) as archive:
            libraries = []
            for name in archive.namelist():
                if not name.startswith("BOOT-INF/lib/") or not name.endswith(".jar"):
                    continue
                data = archive.read(name)
                notices = []
                with zipfile.ZipFile(io.BytesIO(data)) as library:
                    for entry in library.namelist():
                        if not entry.endswith("/") and Path(entry).name.lower().startswith(("license", "licence", "notice", "copying")):
                            notices.append({"file": entry, "sha256": digest(library.read(entry))})
                libraries.append({"file": name, "sha256": digest(data), "noticeFiles": notices,
                                  "bomCoordinatesByFilename": sorted(bom_filenames.get(Path(name).name, set()))})
        archives.append({"artifact": label, "path": relative, "sha256": digest(path.read_bytes()), "libraries": libraries})
    summary = {
        "purpose": "Declared-license inventory, not redistribution approval or vulnerability clearance",
        "sourceSha256": sources,
        "componentRecordsByScope": dict(Counter(r["scope"] for r in records)),
        "uniqueMavenComponents": len({r["coordinate"] for r in records if r["scope"] in ("ijob", "ijob-monitor-agent")}),
        "missingDeclaredLicense": [r["coordinate"] for r in records if not r["licenses"]],
        "packagedLibrariesAbsentFromBom": sorted({Path(lib["file"]).name for archive in archives
                                                 for lib in archive["libraries"]
                                                 if not lib["bomCoordinatesByFilename"]}),
        "limitations": [
            "CycloneDX aggregate covers compile/runtime/provided dependencies; test scope is excluded by the plugin default.",
            "pnpm inventory covers packages installed on this Windows host, not every optional platform variant in the lockfile.",
            "A production dependency graph does not prove that every package is included in the final browser bundle.",
            "Packaged library filename matches are inventory hints; unmatched injected libraries must be added to the final artifact SBOM.",
            "License alternatives and obligations require review of the exact upstream terms and distributed artifact.",
            "Container OS layers, test toolchains, vulnerability assessment and final release notices remain separate gates.",
        ],
    }
    output.mkdir(parents=True)
    for filename, value in (("summary.json", summary), ("components.json", records), ("packaged-libraries.json", archives)):
        (output / filename).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with (output / "components.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["scope", "coordinate", "version", "declaredLicenses", "evidence"])
        writer.writeheader()
        for record in records:
            writer.writerow({key: record[key] for key in ("scope", "coordinate", "version", "evidence")} |
                            {"declaredLicenses": json.dumps(record["licenses"], ensure_ascii=False)})
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
