"""Recover frozen historical SQL outside the checkout; never fetch or access a database."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def restore(destination):
    destination = destination.resolve()
    if destination.is_relative_to(ROOT) or ROOT.is_relative_to(destination):
        raise ValueError("Historical SQL must be restored outside the source checkout")
    if destination.exists():
        raise ValueError("Use a new historical-resource directory")
    manifest = json.loads((ROOT / ".repository/legacy-db.json").read_text(encoding="utf-8"))
    commit = manifest["commit"]
    subprocess.run(["git", "cat-file", "-e", commit + "^{commit}"], cwd=ROOT, check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    # Validate every blob before creating the destination. No checkout, filters or line conversion.
    blobs = []
    seen = set()
    for entry in manifest["files"]:
        name = entry["path"]
        relative = PurePosixPath(name)
        if (relative.is_absolute() or ".." in relative.parts or "\\" in name
                or ":" in name or not name.startswith("db/migration/")
                or not name.endswith((".sql", ".sql.conf")) or name in seen):
            raise ValueError("Invalid historical resource path")
        seen.add(name)
        blob = subprocess.check_output(
            ["git", "show", commit + ":" + manifest["sourcePrefix"] + name], cwd=ROOT)
        if len(blob) != entry["bytes"] or hashlib.sha256(blob).hexdigest() != entry["sha256"]:
            raise ValueError("Historical SQL hash mismatch: " + name)
        blobs.append((relative, blob))
    if (len(blobs) != 118 or sum(p.suffix == ".sql" for p, _ in blobs) != 117
            or sum(str(p).endswith(".sql.conf") for p, _ in blobs) != 1):
        raise ValueError("The frozen resources must contain 117 SQL files and one Flyway sidecar")
    destination.mkdir(parents=True, exist_ok=False)
    for relative, blob in blobs:
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("xb") as output:
            output.write(blob)
    print(json.dumps({"commit": commit, "files": len(blobs), "destination": str(destination)}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", required=True, type=Path)
    restore(parser.parse_args().destination)
