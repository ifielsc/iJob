# Copyright (c) 2026 ifielsc
# SPDX-License-Identifier: MIT

"""Generate/check paired user manuals and their single-source configuration tables."""
import argparse
import csv
import os
from pathlib import Path
import re
from urllib.parse import unquote

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
        default = entry['default']
        if language == 'zh-CN':
            default = {'generated': '安装时生成', 'empty': '空'}.get(default, default)
        values = ['`' + entry['key'] + '`', entry['type'], default, entry['zh' if language == 'zh-CN' else 'en']]
        rows.append('| ' + ' | '.join(value.replace('|', '\\|') for value in values) + ' |')
    return START + '\n\n' + '\n'.join(rows) + '\n\n' + END


def validate_pages(root, manual_root=None):
    root = root.resolve()
    manual_root = manual_root or root / 'document'
    paths = {lang: {p.relative_to(manual_root / lang).as_posix()
                    for p in (manual_root / lang).rglob('*.md')} for lang in ['zh-CN', 'en']}
    assert paths['zh-CN'] == paths['en'], 'Unpaired manual pages: ' + str(paths['zh-CN'] ^ paths['en'])
    assert paths['en'], 'No manual pages found'
    for lang, names in paths.items():
        other = 'en' if lang == 'zh-CN' else 'zh-CN'
        for name in names:
            page = manual_root / lang / name
            content = page.read_text(encoding='utf-8')
            opposite = os.path.relpath(manual_root / other / name, page.parent).replace('\\', '/')
            assert '](' + opposite + ')' in content, 'Missing language switch: ' + str(page)
            # Ignore fenced code samples, which may contain illustrative Markdown syntax.
            prose = re.sub(r'^```[^\n]*\n.*?^```\s*$', '', content, flags=re.M | re.S)
            for target in re.findall(r'\]\(([^)]+)\)', prose):
                target = target.strip().strip('<>')
                if '://' in target or target.startswith(('#', 'mailto:')):
                    continue
                destination = (page.parent / unquote(target.split('#')[0])).resolve()
                assert destination.is_relative_to(root) and destination.is_file(), 'Broken link: ' + str(page) + ' -> ' + target
    images = {lang: {p.relative_to(manual_root / lang).as_posix()
                     for p in (manual_root / lang / 'images').rglob('*') if p.is_file()}
              for lang in paths}
    assert images['zh-CN'] == images['en'], 'Unpaired manual images'
    return len(paths['en']), len(images['en'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--write', action='store_true')
    mode.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for lang in ['zh-CN', 'en']:
        path = ROOT / 'document' / lang / 'configuration.md'
        content = path.read_text(encoding='utf-8')
        assert content.count(START) == 1 and content.count(END) == 1, 'Missing or duplicate configuration table markers: ' + lang
        pattern = re.escape(START) + (r'.*?' + re.escape(END) if END in content else '')
        updated = re.sub(pattern, lambda match: table(lang), content, flags=re.S)
        if args.write:
            path.write_text(updated, encoding='utf-8', newline='\n')
        else:
            assert content == updated, 'Stale configuration table: ' + lang
    pages, images = validate_pages(ROOT)
    print(f'Paired manuals ({pages} per language), images ({images} per language), local links and configuration tables passed.')


if __name__ == '__main__':
    main()
