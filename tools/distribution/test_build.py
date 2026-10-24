# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import build


class DistributionBoundaryTest(unittest.TestCase):
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
