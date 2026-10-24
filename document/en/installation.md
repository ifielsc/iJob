# Installation, builds and lifecycle

[简体中文](../zh-CN/installation.md) · [Manual home](README.md)

## Build a GitHub clone or source ZIP

Clone with an account authorized to access this private repository, or download and extract the source ZIP for the selected commit. Enter the directory containing the root README.md and tools/. Keep deployment settings, databases and output packages outside the source directory.

Native builds require Python 3.11+, JDK 21, Maven 3.9.11, Node 24.14.1 and pnpm 11.19.0 on PATH, with JAVA_HOME pointing to JDK 21. Initial builds need access to Maven/npm dependency sources. Preserve lockfiles.

```sh
python tools/distribution/build.py --target native --output ../ijob-build-001
```

The output directory must be new and its parent must exist. The command copies the exact source allowlist into an isolated build snapshot, then produces ijob-1.0.0/, ZIP and tar.gz packages. reports/ retains logs and build-source/ retains the build workspace. No manual target/dist assembly or full Git history is required. New installations do not need historical database migration resources.

Native packaging runs the complete frontend pnpm check. Backend packaging uses -DskipTests: successful packaging does not imply backend/browser acceptance. See [verification](development.md). Build logs can contain machine paths; distribute the package, not the entire build directory.

## Native Windows

Running the package requires Java 21 and Windows PowerShell, without Node, Maven, Python or Nginx. Extract the complete package and open PowerShell in it. The data parent directory must exist; use a new data directory:

```powershell
$env:JAVA_HOME = 'C:\Tools\jdk-21'
$env:IJOB_HOME = 'C:\iJobData\installation-a'
$env:IJOB_LANG = 'en'
.\initialize.cmd
.\start.cmd
.\status.cmd
.\logs.cmd
.\stop.cmd
# After changing settings:
.\restart.cmd
```

Replace the example paths. Always use the same IJOB_HOME when managing an installation; save these non-secret environment choices in your own external launcher if convenient. Enter an administrator name and password; accept SQLite for a first installation. Password input is hidden, requires at least 12 characters and allows at most 72 UTF-8 bytes. No common default password exists.

start.cmd launches in the background and waits for readiness. The default URL is http://127.0.0.1:18080. stop.cmd verifies process ownership, sends Ctrl+C and waits for graceful shutdown without force-killing. status.cmd returns 0 for an existing process and 3 when stopped; inspect logs for readiness. Ctrl+C in logs.cmd only closes the log viewer. If downloaded-script marking blocks PowerShell, review and verify SHA256SUMS before unblocking those specific scripts according to local policy; do not disable global security policy.

## Native Linux

Install Java 21, Bash and flock from util-linux. Run as a dedicated ordinary user. Extracting tar.gz preserves executable permissions:

```sh
export IJOB_HOME=/srv/ijob-data/installation-a
export IJOB_LANG=en
./start.sh
./status.sh
./logs.sh
./stop.sh
# After changing settings:
./stop.sh && ./start.sh
```

An administrator prepares the parent directory and grants the runtime user access. The first start initializes settings; subsequent starts retain them. Background process records, logs and data belong to this IJOB_HOME. The default listener is loopback only. For remote access, configure a restricted listener and HTTPS reverse proxy and validate the actual target environment.

## Docker: Linux amd64

Source image builds require Python 3.11+ and Docker capable of Linux amd64 builds. Java, Maven and Node execute inside build images. Network access is required for locked base images and dependencies:

```sh
python tools/distribution/build.py --target docker --output ../ijob-docker-build-001
```

This builds backend/web/agent images, exports an offline image archive and assembles a complete package. Image builds skip some tests; independent CI and runtime acceptance remain required. Tags remain 1.0.0; exact image IDs are recorded in MANIFEST.json. No images are pushed to a registry.

Extract the Docker package on a Linux Docker host; ensure the data parent exists:

```sh
export IJOB_HOME=/srv/ijob-data/container-a
export IJOB_HTTP_PORT=18080
export IJOB_LANG=en
./start-docker.sh
docker compose -f "$IJOB_HOME/compose.yaml" ps
docker compose -f "$IJOB_HOME/compose.yaml" logs --tail=100 -f
./stop-docker.sh
```

Where sudo is required, pass choices explicitly: sudo env IJOB_HOME=... IJOB_HTTP_PORT=... IJOB_LANG=en ./start-docker.sh. Missing images load automatically and first start initializes settings. Existing installations are retained. SQLite data lives in this installation's runtime directory; no external database server is created. Browse http://127.0.0.1:18080; backend keeps port 8080 inside the container. After changing the published port, use the same IJOB_HTTP_PORT with docker compose ... up -d; restart alone does not apply that change.

Docker Desktop Linux containers can provide the build engine; runtime scripts target Linux hosts. ARM64, Windows containers and Docker Desktop filesystem permissions are outside Linux amd64 acceptance. Agent is optional and is not started by the basic launcher.

## Acceptance and contents

Verify SHA256SUMS.txt, initialize, confirm READY in logs and successful login, then follow the [quick start](quickstart.md). Keep the complete data home and original AES key across upgrades; do not overwrite a fresh installation with an unrelated old directory.

Packages contain JARs, web-dist, lifecycle scripts, bilingual docs, LICENSE, source.zip, source-manifest.json, MANIFEST.json and SHA256SUMS.txt. Docker packages add the image archive. MANIFEST records performed/unperformed checks and the fact that public release is not yet approved.
