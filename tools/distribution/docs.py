# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

"""Generate/check paired user manuals and their single-source configuration tables."""
import argparse
import csv
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / 'develop/api/ijob/ijob-setup-tool/src/main/resources/user-settings.tsv'
START = '<!-- SETTINGS_TABLE -->'
END = '<!-- /SETTINGS_TABLE -->'


def table(language):
    with CATALOG.open(encoding='utf-8', newline='') as source:
        entries = list(csv.DictReader(source, delimiter='\t'))
    assert len({entry['key'] for entry in entries}) == len(entries)
    heading = ('配置项', '类型', '新安装默认值', '说明') if language == 'zh-CN' else ('Setting', 'Type', 'Fresh-install default', 'Description')
    rows = ['| ' + ' | '.join(heading) + ' |', '| --- | --- | --- | --- |']
    for entry in entries:
        assert entry['en'] and entry['zh'], entry['key']
        values = ['`' + entry['key'] + '`', entry['type'], entry['default'], entry['zh' if language == 'zh-CN' else 'en']]
        rows.append('| ' + ' | '.join(value.replace('|', '\\|') for value in values) + ' |')
    return START + '\n\n' + '\n'.join(rows) + '\n\n' + END


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--write', action='store_true')
    mode.add_argument('--check', action='store_true')
    args = parser.parse_args()
    paths = {lang: {p.name for p in (ROOT / 'document' / lang).glob('*.md')} for lang in ['zh-CN', 'en']}
    assert paths['zh-CN'] == paths['en'], 'Unpaired manual pages'
    for lang in paths:
        path = ROOT / 'document' / lang / 'configuration.md'
        content = path.read_text(encoding='utf-8')
        assert content.count(START) == 1 and content.count(END) == 1, 'Missing or duplicate configuration table markers: ' + lang
        pattern = re.escape(START) + (r'.*?' + re.escape(END) if END in content else '')
        updated = re.sub(pattern, lambda match: table(lang), content, flags=re.S)
        if args.write:
            path.write_text(updated, encoding='utf-8', newline='\n')
        else:
            assert content == updated, 'Stale configuration table: ' + lang
        for name in paths[lang]:
            page = ROOT / 'document' / lang / name
            content = page.read_text(encoding='utf-8')
            opposite = '../en/' if lang == 'zh-CN' else '../zh-CN/'
            assert opposite + name in content, 'Missing language switch: ' + str(page)
            for target in re.findall(r'\]\(([^)]+)\)', content):
                if '://' in target or target.startswith('#'):
                    continue
                destination = (page.parent / target.split('#')[0]).resolve()
                assert destination.is_relative_to(ROOT) and destination.is_file(), 'Broken link: ' + str(page) + ' -> ' + target
    print('Paired manuals, local links and configuration tables passed.')


if __name__ == '__main__':
    main()
