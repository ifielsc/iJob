# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

from pathlib import Path
import tempfile
import unittest

import docs
import build


class ManualBoundaryTest(unittest.TestCase):
    def test_complete_bundle_retains_nested_manuals_and_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory)
            build.copy_deployment(build.ROOT, bundle)
            self.assertTrue((bundle / 'docs/en/installation/windows.md').is_file())
            self.assertTrue((bundle / 'docs/zh-CN/images/manual/script-run.png').is_file())
            self.assertTrue((bundle / 'docs/reference/screenshots.json').is_file())
            self.assertEqual({'ijob.cmd', 'ijob.sh'},
                             {p.name for p in bundle.iterdir() if p.suffix in {'.cmd', '.sh', '.ps1'}})
            self.assertTrue((bundle / 'legacy/start.cmd').is_file())
            self.assertTrue((bundle / 'legacy/stop-docker.sh').is_file())
            self.assertTrue((bundle / 'support/instance.ps1').is_file())
            self.assertTrue((bundle / 'support/logging-native.xml').is_file())
            self.assertNotIn(b'\r\n', (bundle / 'legacy/start.sh').read_bytes())
            self.assertEqual(docs.validate_pages(build.ROOT),
                             docs.validate_pages(bundle, bundle / 'docs'))

    def create_pair(self, root):
        for language, other in [('en', 'zh-CN'), ('zh-CN', 'en')]:
            page = root / 'document' / language / 'nested/page.md'
            page.parent.mkdir(parents=True)
            page.write_text(f'# Page\n\n[Language](../../{other}/nested/page.md)\n', encoding='utf-8')

    def test_nested_pages_require_language_counterpart(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_pair(root)
            self.assertEqual((1, 0), docs.validate_pages(root))
            (root / 'document/en/nested/extra.md').write_text('# Unpaired', encoding='utf-8')
            with self.assertRaisesRegex(AssertionError, 'Unpaired manual pages'):
                docs.validate_pages(root)

    def test_nested_image_links_must_exist(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.create_pair(root)
            page = root / 'document/en/nested/page.md'
            page.write_text(page.read_text() + '\n![Actual page](../images/missing.png)\n', encoding='utf-8')
            with self.assertRaisesRegex(AssertionError, 'Broken link'):
                docs.validate_pages(root)

    def test_links_cannot_escape_distribution(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'bundle'
            self.create_pair(root)
            outside = root.parent / 'private.txt'
            outside.write_text('not part of the bundle', encoding='utf-8')
            page = root / 'document/en/nested/page.md'
            page.write_text(page.read_text() + '\n[Bad](../../../../private.txt)\n', encoding='utf-8')
            with self.assertRaisesRegex(AssertionError, 'Broken link'):
                docs.validate_pages(root)


if __name__ == '__main__':
    unittest.main()
