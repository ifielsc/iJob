# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

"""Build per-JAR CycloneDX inventories and retain the actual embedded license texts."""
import argparse
import copy
import datetime
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import uuid
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True, help='Directory containing both aggregate BOM files')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    if args.output.exists():
        raise ValueError('Use a fresh evidence directory')
    lookup = {}
    for filename in ('ijob-bom.json', 'ijob-monitor-agent-bom.json'):
        bom = json.loads((args.input / filename).read_text(encoding='utf-8-sig'))
        for component in bom.get('components', []):
            name = f"{component['name']}-{component['version']}.jar"
            candidates = lookup.setdefault(name, {})
            candidates[component.get('purl', component['name'])] = component
    artifacts = {
        'backend': 'develop/api/ijob/ijob-starter/target/ijob-starter-1.0.0.jar',
        'agent': 'develop/api/ijob-monitor-agent/target/ijob-monitor-agent-1.0.0.jar',
        'setup': 'develop/api/ijob/ijob-setup-tool/target/ijob-setup-tool-1.0.0.jar',
        'migration': 'develop/api/ijob/ijob-migration-tool/target/ijob-migration-tool-1.0.0.jar',
    }
    args.output.mkdir(parents=True)
    summary = []
    injected_checksums = {}
    for label, relative in artifacts.items():
        data = (root / relative).read_bytes()
        components = []
        notices = []
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            own_license = jar.read('META-INF/LICENSE')
            if own_license != (root / 'LICENSE').read_bytes():
                raise ValueError(f'First-party license differs or is absent: {label}')
            for entry in sorted(jar.namelist()):
                if not entry.startswith('BOOT-INF/lib/') or not entry.endswith('.jar'):
                    continue
                nested = jar.read(entry)
                filename = Path(entry).name
                candidates = list(lookup.get(filename, {}).values())
                if len(candidates) == 1:
                    source = candidates[0]
                    component = {k: copy.deepcopy(source[k]) for k in ('type', 'group', 'name', 'version', 'purl', 'licenses') if k in source}
                elif not candidates and filename.startswith('spring-boot-jarmode-tools-'):
                    version = filename[len('spring-boot-jarmode-tools-'):-4]
                    if filename not in injected_checksums:
                        url = f'https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-jarmode-tools/{version}/{filename}'
                        with urllib.request.urlopen(url, timeout=45) as response:
                            injected_checksums[filename] = {'url': url, 'sha256': sha(response.read())}
                    if sha(nested) != injected_checksums[filename]['sha256']:
                        raise ValueError('Injected Spring Boot library differs from Maven Central')
                    component = {'type': 'library', 'group': 'org.springframework.boot', 'name': 'spring-boot-jarmode-tools', 'version': version,
                                 'purl': f'pkg:maven/org.springframework.boot/spring-boot-jarmode-tools@{version}', 'licenses': [{'license': {'id': 'Apache-2.0'}}]}
                    with zipfile.ZipFile(io.BytesIO(nested)) as lib:
                        text = lib.read('META-INF/LICENSE.txt').decode()
                        if 'Apache License' not in text or 'Version 2.0' not in text:
                            raise ValueError('Review injected library license')
                else:
                    raise ValueError(f'Unresolved or ambiguous embedded component: {filename}')
                component['bom-ref'] = component['purl']
                component['hashes'] = [{'alg': 'SHA-256', 'content': sha(nested)}]
                component['properties'] = [{'name': 'ijob:archive-path', 'value': entry}]
                components.append(component)
                with zipfile.ZipFile(io.BytesIO(nested)) as lib:
                    for item in sorted(lib.namelist()):
                        if item.endswith('/') or not Path(item).name.lower().startswith(('license', 'licence', 'notice', 'copying')):
                            continue
                        content = lib.read(item)
                        target = args.output / 'license-texts' / sha(content)
                        target.parent.mkdir(exist_ok=True)
                        target.write_bytes(content)
                        notices.append({'component': component['purl'], 'archiveEntry': item, 'sha256': sha(content), 'textFile': target.relative_to(args.output).as_posix()})
        reference = f'urn:sha256:{sha(data)}'
        document = {'bomFormat': 'CycloneDX', 'specVersion': '1.6', 'serialNumber': 'urn:uuid:' + str(uuid.uuid4()), 'version': 1,
                    'metadata': {'timestamp': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                                 'component': {'type': 'application', 'bom-ref': reference, 'name': label, 'version': '1.0.0',
                                               'hashes': [{'alg': 'SHA-256', 'content': sha(data)}], 'licenses': [{'license': {'id': 'MIT'}}]}},
                    'components': components, 'dependencies': [{'ref': reference, 'dependsOn': [c['bom-ref'] for c in components]}]}
        (args.output / f'{label}.cdx.json').write_text(json.dumps(document, indent=2) + '\n', encoding='utf-8')
        (args.output / f'{label}.notices.json').write_text(json.dumps(notices, indent=2) + '\n', encoding='utf-8')
        summary.append({'artifact': relative, 'sha256': sha(data), 'embeddedLibraries': len(components), 'noticeEntries': len(notices),
                        'missingDeclaredLicense': [c['purl'] for c in components if not c.get('licenses')]})
    result = {'artifacts': summary, 'injectedUpstreamChecksums': injected_checksums,
              'scope': 'Exact nested JARs and their embedded notice texts; not a complete redistribution or vulnerability verdict. Native payloads, browser bundles and image OS layers require separate inventories.'}
    (args.output / 'summary.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
