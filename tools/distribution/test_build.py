# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

import json
import hashlib
import io
import importlib.util
import os
from pathlib import Path
import tempfile
import tarfile
import zipfile
import unittest
from unittest.mock import patch

import build


class DistributionBoundaryTest(unittest.TestCase):
    def test_user_supplied_jdbc_drivers_cannot_enter_runtime_bundles(self):
        for name in ['mysql-connector-j-8.3.0.jar', 'ojdbc8-19.30.0.0.jar',
                     'ojdbc11-23.jar', 'orai18n-19.30.0.0.jar']:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                bundle = Path(directory)
                with zipfile.ZipFile(bundle / 'app.jar', 'w') as archive:
                    archive.writestr('BOOT-INF/lib/' + name, b'not redistributed')
                with self.assertRaisesRegex(ValueError, 'User-supplied JDBC'):
                    build.write_third_party_notices(bundle)

    def test_private_website_cannot_be_reintroduced_through_the_allowlist(self):
        spec = importlib.util.spec_from_file_location('repository_policy', build.ROOT / 'tools/repository/prepare.py')
        policy = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(policy)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / '.repository').mkdir()
            for name in ['tools/public-site/build.py', 'website/site.css', 'website-private/site.js',
                         'TOOLS/PUBLIC-SITE/catalog.json']:
                with self.subTest(name=name):
                    path = root / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text('private website content')
                    (root / '.repository/allowlist.json').write_text(json.dumps({'files': [name]}))
                    self.assertTrue(policy.forbidden(name))
                    with self.assertRaisesRegex(ValueError, 'Forbidden'):
                        policy.load_files(root)
                    with patch.object(build, 'ROOT', root):
                        with self.assertRaisesRegex(ValueError, 'Private website'):
                            build.source_files()

    def test_public_product_snapshot_keeps_manuals_but_excludes_website(self):
        names = build.source_files()
        self.assertIn('document/zh-CN/installation.md', names)
        self.assertIn('document/en/installation.md', names)
        self.assertIn('develop/web/ijob/package.json', names)
        self.assertFalse(any(name.startswith(('tools/public-site/', 'website/', 'website-private/'))
                             for name in names))

    def test_both_targets_publish_external_source_and_checksummed_runtime_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'source'
            (root / '.repository').mkdir(parents=True)
            (root / 'program.java').write_bytes(b'// source only\n')
            (root / 'run.sh').write_bytes(b'#!/bin/sh\n')
            names = ['.repository/allowlist.json', 'program.java', 'run.sh']
            (root / names[0]).write_text(json.dumps({'files': names}))
            hashes = []

            def runtime(work, bundle, reports):
                self.assertTrue((work / 'program.java').is_file())
                (bundle / 'app.jar').write_bytes(b'compiled artifact')
                return {'tests': 'fixture, not a real compilation'}

            for target in ['native', 'docker']:
                output = root.parent / target
                with patch.object(build, 'ROOT', root), patch.object(build, 'git_source', return_value=(None, {})), \
                        patch.object(build, 'copy_deployment'), patch.object(build, 'native', side_effect=runtime), \
                        patch.object(build, 'docker', side_effect=runtime), \
                        patch.object(build, 'write_third_party_notices', return_value={}), \
                        patch.dict(os.environ, {}, clear=True), \
                        patch('sys.argv', ['build.py', '--target', target, '--output', str(output)]):
                    build.main()
                bundle = output / 'ijob-1.0.0'
                metadata = json.loads((bundle / 'MANIFEST.json').read_text())
                source = output / 'ijob-1.0.0-source.zip'
                hashes.append(build.digest(source))
                self.assertEqual({'filename': source.name, 'sha256': hashes[-1], 'bundled': False},
                                 metadata['source_archive'])
                self.assertFalse((bundle / 'source.zip').exists())
                with zipfile.ZipFile(source) as archive:
                    self.assertEqual({'ijob-1.0.0-source/' + name for name in names}, set(archive.namelist()))
                    self.assertEqual(b'// source only\n', archive.read('ijob-1.0.0-source/program.java'))
                    self.assertEqual(0o755, (archive.getinfo('ijob-1.0.0-source/run.sh').external_attr >> 16) & 0o777)
                runtime_files = {p.relative_to(output).as_posix() for p in bundle.rglob('*') if p.is_file()}
                with zipfile.ZipFile(output / f'ijob-1.0.0-{target}.zip') as archive:
                    self.assertEqual(runtime_files, set(archive.namelist()))
                with tarfile.open(output / f'ijob-1.0.0-{target}.tar.gz') as archive:
                    self.assertEqual(runtime_files, {p.name for p in archive.getmembers() if p.isfile()})
                for checksum_root in [output, bundle]:
                    rows = (checksum_root / 'SHA256SUMS.txt').read_text().splitlines()
                    self.assertEqual(3, len(rows))
                    for row in rows:
                        expected, name = row.split('  ', 1)
                        self.assertEqual(expected, build.digest(checksum_root / name))
                # A second target must not depend on source mtimes or filesystem modes.
                os.utime(root / 'program.java', (1700000000, 1700000000))
            self.assertEqual(hashes[0], hashes[1])

    def test_finalization_rejects_embedded_source_and_mismatched_external_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bundle = root / 'ijob-1.0.0'
            bundle.mkdir()
            (bundle / 'source-manifest.json').write_text(json.dumps({'file': hashlib.sha256(b'expected').hexdigest()}))
            with zipfile.ZipFile(root / build.SOURCE_ARCHIVE, 'w') as archive:
                archive.writestr('ijob-1.0.0-source/file', b'tampered')
            for name in ['source.zip', build.SOURCE_ARCHIVE, 'source', 'build-source', 'develop']:
                embedded = bundle / name
                embedded.write_bytes(b'not allowed')
                with self.assertRaisesRegex(ValueError, 'must not contain'):
                    build.finalize_distribution(root, bundle, 'native', {})
                embedded.unlink()
            with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                build.finalize_distribution(root, bundle, 'native', {})
            self.assertFalse((root / 'ijob-1.0.0-native.zip').exists())

    def test_supplement_is_bound_to_binary_hash_and_verified_before_copy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'tools/distribution/third-party'
            source.mkdir(parents=True)
            nested = io.BytesIO()
            with zipfile.ZipFile(nested, 'w') as library:
                library.writestr('code.class', b'code')
                library.writestr('META-INF/NOTICE', b'Attribution without full license terms')
            binary_hash = hashlib.sha256(nested.getvalue()).hexdigest()
            text = b'Exact upstream terms\n'
            notice_hash = hashlib.sha256(text).hexdigest()
            notice = source / (notice_hash + '.txt')
            notice.write_bytes(b'corrupted')
            (source / 'supplemental-notices.json').write_text(json.dumps({binary_hash: {
                'filename': 'upstream.jar', 'notices': [{'file': notice.name,
                    'sha256': notice_hash, 'source': 'https://example.invalid/exact-release/LICENSE'}]}}))
            for label, valid in [('corrupt', False), ('valid', True)]:
                bundle = root / label
                (bundle / 'web-dist/.vite').mkdir(parents=True)
                (bundle / 'web-dist/.vite/third-party-components.json').write_text('{"components": []}')
                with zipfile.ZipFile(bundle / 'app.jar', 'w') as application:
                    application.writestr('BOOT-INF/lib/upstream.jar', nested.getvalue())
                with patch.object(build, 'ROOT', root):
                    if not valid:
                        with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                            build.write_third_party_notices(bundle)
                    else:
                        notice.write_bytes(text)
                        build.write_third_party_notices(bundle)
                        self.assertEqual(text, (bundle / 'third-party' / notice.name).read_bytes())
                        manifest = json.loads((bundle / 'third-party/manifest.json').read_text())
                        self.assertEqual([], manifest['missing_license_text'])
                        self.assertEqual(2, len(manifest['components'][0]['notices']))

    def test_notices_preserve_actual_bytes_and_report_missing_text(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory)
            frontend = bundle / 'web-dist/.vite'
            frontend.mkdir(parents=True)
            (frontend / 'third-party-components.json').write_text(json.dumps({'components': [
                {'name': 'missing', 'version': '1', 'licenseTextStatus': 'missing-upstream-text'}]}))
            nested = io.BytesIO()
            with zipfile.ZipFile(nested, 'w') as library:
                library.writestr('META-INF/license.txt', b'Upstream terms\r\n')
                library.writestr('../../NOTICE', b'Original copyright\n')
            missing = io.BytesIO()
            with zipfile.ZipFile(missing, 'w') as library:
                library.writestr('code.class', b'code')
            notice_only = io.BytesIO()
            with zipfile.ZipFile(notice_only, 'w') as library:
                library.writestr('NOTICE', b'Attribution without license text')
            with zipfile.ZipFile(bundle / 'app.jar', 'w') as application:
                application.writestr('BOOT-INF/lib/upstream.jar', nested.getvalue())
                application.writestr('BOOT-INF/lib/no-notice.jar', missing.getvalue())
                application.writestr('BOOT-INF/lib/notice-only.jar', notice_only.getvalue())
            before = (bundle / 'app.jar').read_bytes()
            result = build.write_third_party_notices(bundle)
            manifest = json.loads((bundle / result['manifest']).read_text())
            self.assertEqual(before, (bundle / 'app.jar').read_bytes())
            self.assertEqual(['no-notice.jar'], manifest['missing_embedded_notices'])
            self.assertEqual(['no-notice.jar', 'notice-only.jar'], manifest['missing_license_text'])
            self.assertEqual(['missing@1'], manifest['frontend_missing_text'])
            upstream = next(c for c in manifest['components'] if c['filename'] == 'upstream.jar')
            self.assertEqual(hashlib.sha256(nested.getvalue()).hexdigest(), upstream['sha256'])
            self.assertEqual({b'Upstream terms\r\n', b'Original copyright\n'},
                             {(bundle / 'third-party' / n['file']).read_bytes() for n in upstream['notices']})
            self.assertFalse((bundle.parent / 'NOTICE').exists())

    def test_cross_platform_archives_use_safe_program_modes_and_preserve_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            bundle = output / 'ijob-1.0.0'
            (bundle / 'support').mkdir(parents=True)
            (bundle / 'ijob.sh').write_bytes(b'#!/bin/sh\nexit 0\n')
            (bundle / 'support/readme.txt').write_bytes(b'content unchanged\n')
            build.write_archives(output, bundle, 'native')
            with tarfile.open(output / 'ijob-1.0.0-native.tar.gz') as archive:
                for entry in archive.getmembers():
                    self.assertEqual(0, entry.mode & 0o022)
                    self.assertEqual((0, 0, 'root', 'root'), (entry.uid, entry.gid, entry.uname, entry.gname))
                    if entry.isdir() or entry.name.endswith('.sh'):
                        self.assertEqual(0o755, entry.mode)
                self.assertEqual(b'content unchanged\n', archive.extractfile('ijob-1.0.0/support/readme.txt').read())
            with zipfile.ZipFile(output / 'ijob-1.0.0-native.zip') as archive:
                for entry in archive.infolist():
                    self.assertEqual(0, (entry.external_attr >> 16) & 0o022)
                self.assertEqual(0o755, (archive.getinfo('ijob-1.0.0/ijob.sh').external_attr >> 16) & 0o777)

    def test_rejects_source_tree_and_existing_output(self):
        with self.assertRaises(ValueError):
            build.external_destination(build.ROOT / 'package')
        with self.assertRaises(ValueError):
            build.external_destination(build.ROOT.parent)
        with tempfile.TemporaryDirectory() as directory:
            existing = Path(directory)
            with self.assertRaises(ValueError):
                build.external_destination(existing)
            self.assertEqual(existing / 'new', build.external_destination(existing / 'new'))

    def test_missing_and_escaping_source_entries_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'source'
            (root / '.repository').mkdir(parents=True)
            policy = root / '.repository/allowlist.json'
            outside = root.parent / 'outside.txt'
            outside.write_text('unrelated')
            with patch.object(build, 'ROOT', root):
                for name in ['missing.txt', '../outside.txt']:
                    policy.write_text(json.dumps({'files': [name]}))
                    with self.assertRaises(ValueError):
                        build.source_files()

    def test_allowlist_excludes_unregistered_local_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / '.repository').mkdir()
            (root / '.repository/allowlist.json').write_text(json.dumps({'files': ['source.txt']}))
            (root / 'source.txt').write_text('reviewed')
            (root / 'private.txt').write_text('not included')
            with patch.object(build, 'ROOT', root):
                self.assertEqual(['source.txt'], build.source_files())


if __name__ == '__main__':
    unittest.main()
