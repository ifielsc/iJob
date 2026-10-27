# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

import json
import hashlib
import io
from pathlib import Path
import tempfile
import tarfile
import zipfile
import unittest
from unittest.mock import patch

import build


class DistributionBoundaryTest(unittest.TestCase):
    def test_supplement_is_bound_to_binary_hash_and_verified_before_copy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'tools/distribution/third-party'
            source.mkdir(parents=True)
            nested = io.BytesIO()
            with zipfile.ZipFile(nested, 'w') as library:
                library.writestr('code.class', b'code')
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
            with zipfile.ZipFile(bundle / 'app.jar', 'w') as application:
                application.writestr('BOOT-INF/lib/upstream.jar', nested.getvalue())
                application.writestr('BOOT-INF/lib/no-notice.jar', missing.getvalue())
            before = (bundle / 'app.jar').read_bytes()
            result = build.write_third_party_notices(bundle)
            manifest = json.loads((bundle / result['manifest']).read_text())
            self.assertEqual(before, (bundle / 'app.jar').read_bytes())
            self.assertEqual(['no-notice.jar'], manifest['missing_embedded_notices'])
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
