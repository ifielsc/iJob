# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

"""Check ownership headers and exact first-party license copies in the reviewed source list."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
files = json.loads((ROOT / '.repository/allowlist.json').read_text(encoding='utf-8'))['files']
license_text = (ROOT / 'LICENSE').read_bytes()
assert b'Copyright (c) 2026 ifielsc' in license_text
checked = 0
copies = 0
for name in files:
    path = ROOT / name
    if name.endswith('/META-INF/LICENSE') or name == 'develop/web/ijob/public/LICENSE.txt':
        assert path.read_bytes() == license_text, f'License copy differs: {name}'
        copies += 1
    if '/.mvn/' in name or '/patches/' in name:
        continue
    if path.suffix not in {'.java', '.ts', '.vue', '.css', '.mjs', '.py', '.ps1', '.sh'} and path.name != 'Dockerfile':
        continue
    text = path.read_text(encoding='utf-8-sig')
    head = '\n'.join(text.splitlines()[:7])
    assert 'Copyright (c) 2026 ifielsc' in head, f'Missing copyright: {name}'
    assert 'SPDX-License-Identifier: MIT' in head, f'Missing SPDX: {name}'
    checked += 1
assert copies > 0, 'Missing distribution license copies'
print(json.dumps({'source_headers_checked': checked, 'license_copies_checked': copies}))
