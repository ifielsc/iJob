# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

"""Build an auditable 1.0.0 distribution from a clone or source ZIP."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]
VERSION = '1.0.0'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def external_destination(path):
    destination = path.expanduser().resolve()
    if destination.is_relative_to(ROOT) or ROOT.is_relative_to(destination):
        raise ValueError('Output must be outside the source tree and cannot contain it')
    if destination.exists():
        raise ValueError('Output already exists; choose a new directory')
    if not destination.parent.is_dir():
        raise ValueError('Create the output parent first')
    return destination


def source_files():
    names = json.loads((ROOT / '.repository/allowlist.json').read_text(encoding='utf-8'))['files']
    for name in names:
        path = ROOT / name
        if not path.resolve().is_relative_to(ROOT) or path.is_symlink() or not path.is_file():
            raise ValueError('Missing or unsafe source file: ' + name)
    return sorted(names)


def git_source():
    if not (ROOT / '.git').exists() or not shutil.which('git'):
        return None, {}
    revision = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=ROOT, capture_output=True, text=True)
    if revision.returncode:
        return None, {}
    commit = revision.stdout.strip()
    tree = subprocess.run(['git', 'ls-tree', '-r', '-z', commit], cwd=ROOT, capture_output=True)
    if tree.returncode:
        return None, {}
    files = {}
    for record in tree.stdout.decode('utf-8').split('\0'):
        if record:
            metadata, name = record.split('\t', 1)
            files[name] = metadata.split()[2]
    return commit, files


def run(command, cwd, log, capture=False):
    # Resolve .cmd tools on Windows without building a shell command from user input.
    command = [str(item) for item in command]
    command[0] = shutil.which(command[0]) or command[0]
    with log.open('xb') as stream:
        result = subprocess.run(command, cwd=cwd, stdout=subprocess.PIPE if capture else stream,
                                stderr=stream, check=False)
        if capture:
            stream.write(result.stdout)
    if result.returncode:
        raise RuntimeError('Command failed; inspect ' + str(log))
    return result.stdout.decode('utf-8', errors='replace').strip() if capture else None


def toolchains(reports):
    expected = [('java', ['-version'], 'version "21.'),
                ('mvn', ['--version'], 'Apache Maven 3.9.11'),
                ('node', ['--version'], 'v24.14.1'), ('pnpm', ['--version'], '11.19.0')]
    versions = {}
    for name, args, required in expected:
        executable = shutil.which(name)
        if not executable:
            raise ValueError('Required tool not on PATH: ' + name)
        result = subprocess.run([executable, *args], cwd=ROOT, capture_output=True, text=True)
        value = result.stdout + result.stderr
        (reports / (name + '-version.log')).write_text(value, encoding='utf-8')
        if result.returncode or required not in value:
            raise ValueError('Wrong tool version: ' + name + '; inspect version log')
        versions[name] = required
    return versions


def copy_deployment(source, bundle):
    for path in (source / 'develop/deployment/first-run').iterdir():
        if path.is_file() and path.name != 'README.md':
            content = path.read_bytes()
            if path.suffix == '.sh':
                content = content.replace(b'\r\n', b'\n')
            destination = bundle / path.name
            destination.write_bytes(content)
            if path.suffix == '.sh':
                destination.chmod(0o755)
    for language in ['zh-CN', 'en']:
        shutil.copytree(source / 'document' / language, bundle / 'docs' / language)
    shutil.copytree(source / 'document/reference', bundle / 'docs/reference')
    shutil.copy2(source / 'LICENSE', bundle / 'LICENSE')
    (bundle / 'README.md').write_text(
        '# iJob 1.0.0\n\n[中文](docs/zh-CN/README.md) | [English](docs/en/README.md)\n\n'
        'Start with the installation guide for your platform. Keep the data home outside this '
        'directory. This package contains no credentials or database.\n', encoding='utf-8')


def native(work, bundle, reports):
    versions = toolchains(reports)
    main = work / 'develop/api/ijob'
    agent = work / 'develop/api/ijob-monitor-agent'
    web = work / 'develop/web/ijob'
    run(['mvn', '-B', '-ntp', '-Pquality', '-DskipTests', 'install'], main, reports / 'backend.log')
    run(['mvn', '-B', '-ntp', '-Pquality', '-DskipTests', 'package'], agent, reports / 'agent.log')
    run(['pnpm', 'install', '--frozen-lockfile'], web, reports / 'frontend-install.log')
    run(['pnpm', 'check'], web, reports / 'frontend-check.log')
    for module in ['starter', 'setup-tool']:
        shutil.copy2(main / f'ijob-{module}/target/ijob-{module}-{VERSION}.jar', bundle)
    (bundle / 'optional').mkdir()
    shutil.copy2(agent / f'target/ijob-monitor-agent-{VERSION}.jar', bundle / 'optional')
    shutil.copy2(main / f'ijob-plugin-spi/target/ijob-plugin-spi-{VERSION}.jar', bundle / 'optional')
    shutil.copytree(web / 'dist', bundle / 'web-dist')
    return {'toolchains': versions, 'frontend_check': 'passed',
            'backend_tests': 'not-run: packaging uses -DskipTests',
            'browser_tests': 'not-run: use the full CI gates separately'}


def docker(work, bundle, reports):
    run(['docker', 'version'], work, reports / 'docker-version.log')
    locks = {}
    for line in (work / 'develop/deployment/docker/images.env').read_text().splitlines():
        if line and not line.startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            locks[key] = value
    images = []
    specs = [('backend', 'develop/api/ijob', 'develop/api/ijob/Dockerfile', ['MAVEN', 'JRE']),
             ('web', 'develop/web/ijob', 'develop/web/ijob/Dockerfile', ['NODE', 'NGINX']),
             ('agent', 'develop/api', 'develop/api/ijob-monitor-agent/Dockerfile', ['MAVEN', 'JRE'])]
    for name, context, dockerfile, args in specs:
        tag = f'ijob-{name}:{VERSION}'
        command = ['docker', 'build', '--platform', 'linux/amd64', '-t', tag, '-f', work / dockerfile]
        for key in args:
            command.extend(['--build-arg', key + '_IMAGE=' + locks['IJOB_' + key + '_IMAGE']])
        command.append(work / context)
        run(command, work, reports / (name + '-image-build.log'))
        identity = run(['docker', 'image', 'inspect', '--format', '{{.Id}}', tag], work,
                       reports / (name + '-image-id.log'), capture=True)
        images.append({'tag': tag, 'id': identity})
    image_ids = {image['tag'].split(':')[0].removeprefix('ijob-'): image['id'] for image in images}
    for identity in image_ids.values():
        if not identity.startswith('sha256:') or len(identity) != 71 or any(c not in '0123456789abcdef' for c in identity[7:]):
            raise ValueError('Docker returned an invalid image ID')
    (bundle / 'docker-images.lock').write_text(''.join(
        'IJOB_' + name.upper() + '_IMAGE=' + identity + '\n' for name, identity in image_ids.items()), newline='\n')
    (bundle / 'docker-images.compose.yaml').write_text('services:\n' + ''.join(
        '  ' + name + ':\n    image: ' + image_ids[name] + '\n' for name in ['backend', 'web']), newline='\n')
    # Created containers are never started; only artifacts are copied, then exact owned IDs removed.
    for name, paths in [('backend', [('/opt/ijob/app.jar', f'ijob-starter-{VERSION}.jar'),
                                     ('/opt/ijob/setup.jar', f'ijob-setup-tool-{VERSION}.jar')]),
                        ('web', [('/usr/share/nginx/html/.', 'web-dist')]),
                        ('agent', [('/opt/ijob/agent.jar', f'optional/ijob-monitor-agent-{VERSION}.jar')])]:
        container_name = 'ijob-package-' + uuid.uuid4().hex
        container = run(['docker', 'create', '--name', container_name,
                         '--label', 'com.ifielsc.ijob.purpose=package-extraction', f'ijob-{name}:{VERSION}'],
                        work, reports / (name + '-extract-create.log'), capture=True)
        try:
            for index, (source, destination) in enumerate(paths):
                target = bundle / destination
                target.parent.mkdir(parents=True, exist_ok=True)
                if source.endswith('/.'):
                    target.mkdir()
                run(['docker', 'cp', container + ':' + source, target], work,
                    reports / f'{name}-extract-{index}.log')
        finally:
            run(['docker', 'rm', container], work, reports / (name + '-extract-remove.log'))
    run(['docker', 'save', '-o', bundle / 'ijob-docker-images.tar',
         *[image['tag'] for image in images]], work, reports / 'image-export.log')
    return {'images': images, 'tests': 'not-run: image builds are not quality or runtime acceptance'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True, help='New directory outside source')
    parser.add_argument('--target', choices=['native', 'docker'], default='native')
    args = parser.parse_args()
    output = external_destination(args.output)
    names = source_files()
    commit, git_files = git_source()
    output.mkdir()
    reports = output / 'reports'; reports.mkdir()
    work = output / 'build-source'; work.mkdir()
    bundle = output / f'ijob-{VERSION}'; bundle.mkdir()
    # Do not inherit application credentials or runtime flags into build/test processes.
    unsafe = [key for key in os.environ if key.startswith(('IJOB_', 'SPRING_')) or
              key in {'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'MAVEN_ARGS', 'MAVEN_OPTS'}]
    if unsafe:
        raise ValueError('Remove inherited application/JVM configuration before building: ' + ', '.join(unsafe))
    manifest = {name: digest(ROOT / name) for name in names}
    with zipfile.ZipFile(bundle / 'source.zip', 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name in names:
            source = ROOT / name
            destination = work / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
            archive.write(source, name)
            if digest(destination) != manifest[name] or digest(source) != manifest[name]:
                raise RuntimeError('Source changed while copying: ' + name)
    (bundle / 'source-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    provenance = {}
    if commit:
        matches = set(git_files) == set(names)
        for name in names:
            data = (work / name).read_bytes()
            blob = hashlib.sha1(b'blob ' + str(len(data)).encode('ascii') + b'\0' + data).hexdigest()
            matches = matches and git_files.get(name) == blob
        provenance = {'source_base_commit': commit, 'source_has_local_changes': not matches}
    copy_deployment(work, bundle)
    result = native(work, bundle, reports) if args.target == 'native' else docker(work, bundle, reports)
    result.update(version=VERSION, target=args.target, public_release_approved=False,
                  source_manifest_sha256=digest(bundle / 'source-manifest.json'))
    result.update(provenance)
    (bundle / 'MANIFEST.json').write_text(json.dumps(result, indent=2) + '\n')
    entries = sorted(p for p in bundle.rglob('*') if p.is_file())
    (bundle / 'SHA256SUMS.txt').write_text(''.join(digest(p) + '  ' + p.relative_to(bundle).as_posix() + '\n' for p in entries))
    with zipfile.ZipFile(output / f'ijob-{VERSION}-{args.target}.zip', 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(p for p in bundle.rglob('*') if p.is_file()):
            archive.write(path, path.relative_to(output))
    with tarfile.open(output / f'ijob-{VERSION}-{args.target}.tar.gz', 'w:gz') as archive:
        archive.add(bundle, arcname=bundle.name)
    print(json.dumps({'bundle': str(bundle), 'target': args.target, 'validation': result}, ensure_ascii=False))


if __name__ == '__main__':
    main()
