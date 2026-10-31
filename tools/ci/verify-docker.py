# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

"""Validate Compose and build local images only; never start or push containers."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
DEPLOYMENT = ROOT / "develop/deployment/docker"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9-]+", args.run_id):
        parser.error("run-id must contain lowercase letters, digits or hyphens")
    evidence = ROOT / "test/reports" / args.run_id
    evidence.mkdir(parents=True, exist_ok=False)
    images = json.loads((DEPLOYMENT / "images.lock.json").read_text())
    env = os.environ.copy()
    for line in (DEPLOYMENT / "images.env").read_text().splitlines():
        if line and not line.startswith("#"):
            key, value = line.split("=", 1)
            env[key] = value
    for name, image in images.items():
        if not re.fullmatch(r".+@sha256:[0-9a-f]{64}", image["image"]):
            raise ValueError("Unpinned image: " + name)
        if env["IJOB_" + name.upper() + "_IMAGE"] != image["image"]:
            raise ValueError("images.env and images.lock.json disagree: " + name)
    env["IJOB_IMAGE_TAG"] = "ci-" + args.run_id
    env["DOCKER_DEFAULT_PLATFORM"] = "linux/amd64"
    env["COMPOSE_PARALLEL_LIMIT"] = "1"
    env["COMPOSE_ANSI"] = "never"
    results = []

    def run(label, command, capture=False):
        with (evidence / (label + ".log")).open("w", encoding="utf-8") as log:
            result = subprocess.run(command, cwd=DEPLOYMENT, env=env,
                                    stdout=subprocess.PIPE if capture else log,
                                    stderr=log, text=True, encoding="utf-8")
            if capture:
                log.write(result.stdout)
        results.append({"step": label, "exit_code": result.returncode})
        (evidence / "steps.json").write_text(json.dumps(results, indent=2) + "\n")
        result.check_returncode()
        return result.stdout

    run("docker-version", ["docker", "version"])
    # These files are deliberately empty: Compose validates paths, not application
    # credentials. No application is started, and no actual secret is needed.
    with tempfile.TemporaryDirectory(prefix="ijob-ci-compose-") as temporary:
        config = Path(temporary) / "validation-only.yaml"
        token = Path(temporary) / "validation-only-token"
        config.write_text("{}\n")
        token.touch()
        env["IJOB_CONFIG_PATH"] = str(config)
        drivers = Path(temporary) / "drivers"
        drivers.mkdir()
        env["IJOB_JDBC_DRIVERS_DIR"] = str(drivers)
        env["IJOB_AGENT_TOKEN_PATH"] = str(token)
        base = ["docker", "compose", "--project-name", "ijob-" + args.run_id,
                "--env-file", "images.env", "-f", "compose.yaml",
                "-f", "compose.defaults.yaml", "-f", "compose.agent.yaml"]
        for vendor in ("sqlite", "mysql", "postgresql", "sqlserver", "oracle"):
            env["IJOB_PLATFORM_DB"] = vendor
            run("config-" + vendor, base + ["config", "--quiet"])
            if vendor != "sqlite":
                run("config-cluster-" + vendor, base + ["-f", "compose.cluster.yaml",
                    "-f", "compose.cluster.defaults.yaml", "config", "--quiet"])
        env["IJOB_PLATFORM_DB"] = "sqlite"
        run("build", base + ["build", "--pull", "backend", "web", "agent"])
        image_ids = {}
        for name in ("ijob-backend", "ijob-web", "ijob-monitor-agent"):
            image_ids[name] = run("inspect-" + name, ["docker", "image", "inspect",
                "--format", "{{.Id}}", name + ":" + env["IJOB_IMAGE_TAG"]], True).strip()
        (evidence / "images.json").write_text(json.dumps({"platform": "linux/amd64",
            "tag": env["IJOB_IMAGE_TAG"], "images": image_ids,
            "pushed": False, "containers_started": False}, indent=2) + "\n")


if __name__ == "__main__":
    main()
