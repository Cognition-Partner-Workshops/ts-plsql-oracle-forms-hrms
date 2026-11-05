import copy
import os
from pathlib import Path
import re
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
CATALOGUE = Path(os.environ.get('HRMS_CATALOGUE_DIR', ROOT / 'modernization'))
ID = re.compile(r'[A-Z][A-Z0-9_]*')
CITATION = re.compile(r'([^:\s]+):([1-9][0-9]*)(?:-([1-9][0-9]*))?')


def validate_catalogue(data):
    errors = []
    items = data.get('items', [])
    missing = data.get('missing', [])
    ids = set()
    for item in items + missing:
        ident = item.get('id', '')
        if not ID.fullmatch(ident):
            errors.append(f'invalid id: {ident!r}')
        if ident in ids:
            errors.append(f'duplicate id: {ident}')
        ids.add(ident)
        sources = item.get('sources', [])
        if not isinstance(sources, list) or not sources:
            errors.append(f'{ident}: no citations')
            continue
        for source in sources:
            match = CITATION.fullmatch(source) if isinstance(source, str) else None
            if not match:
                errors.append(f'{ident}: malformed citation: {source!r}')
                continue
            path, start, end = match.groups()
            file = (ROOT / path).resolve()
            if Path(path).is_absolute() or not file.is_relative_to(ROOT):
                errors.append(f'{ident}: citation must be repo-relative: {source}')
            elif not file.is_file():
                errors.append(f'{ident}: missing file: {source}')
            else:
                start, end = int(start), int(end or start)
                if not start <= end <= len(file.read_text().splitlines()):
                    errors.append(f'{ident}: citation out of bounds: {source}')
    for item in items:
        ident = item['id']
        for field in ('callers', 'callees'):
            for edge in item.get(field, []):
                if ID.fullmatch(edge) and edge not in ids:
                    errors.append(f'{ident}: unknown {field} id: {edge}')
        if item.get('kind') not in {'form', 'menu', 'pll', 'package', 'trigger', 'schema', 'seed'}:
            errors.append(f'{ident}: invalid kind')
        if item.get('golden') not in {'G', 'L', 'N', 'E', 'O'}:
            errors.append(f'{ident}: invalid golden key')
        if item.get('scope') not in {'in', 'out'} or not item.get('reason'):
            errors.append(f'{ident}: missing scope/reason')
        if type(item.get('port_order')) is not int or item['port_order'] < 0:
            errors.append(f'{ident}: invalid port_order')
    return errors


class CatalogueTests(unittest.TestCase):
    def test_source_coverage(self):
        data = yaml.safe_load((CATALOGUE / 'inventory.yaml').read_text())
        sources = {path.relative_to(ROOT).as_posix()
                   for directory in ('forms', 'plsql', 'schema', 'data/seed')
                   for path in (ROOT / directory).rglob('*') if path.is_file()}
        cited = {source.split(':')[0] for item in data['items'] for source in item['sources']}
        covered = sources & cited
        missing = sorted(sources - cited)
        outside = sorted(cited - sources)
        print(f'Source coverage: {len(covered)}/{len(sources)} files; '
              f'uncited: {missing}; cited outside source directories: {outside}', flush=True)
        self.assertEqual([], missing, f'Uncited repository source files: {missing}')
        self.assertEqual(41, len(covered), f'Expected 41 covered source files, got {len(covered)}')

    def test_catalogue(self):
        data = yaml.safe_load((CATALOGUE / 'inventory.yaml').read_text())
        self.assertEqual([], validate_catalogue(data))
        self.assertEqual(34, len(data['items']))
        self.assertTrue(all(item['scope'] == 'in' for item in data['items']))
        files = {source.split(':')[0] for item in data['items'] for source in item['sources']}
        self.assertEqual(41, len(files))
        self.assertEqual({'REPORTS', 'ADMIN'}, {item['id'] for item in data['missing']})
        rules = CATALOGUE / 'rules.yaml'
        if rules.exists():
            self.assertIsInstance(yaml.safe_load(rules.read_text()), dict)

    def test_validator_valid(self):
        item = self.example()
        item['callees'] = ['EXAMPLE', 'note: external SQL']
        self.assertEqual([], validate_catalogue({'items': [item]}))

    def test_validator_rejects_bad_data(self):
        cases = [
            ('no citations', {'sources': []}),
            ('malformed citation', {'sources': ['harness/load.py:0']}),
            ('malformed citation', {'sources': ['harness/load.py']}),
            ('out of bounds', {'sources': ['harness/load.py:999999']}),
            ('out of bounds', {'sources': ['harness/load.py:3-1']}),
            ('missing file', {'sources': ['nonexistent.sql:1']}),
            ('repo-relative', {'sources': ['../outside.sql:1']}),
            ('unknown callees id', {'callees': ['PKG_UNKNOWN']}),
            ('unknown callers id', {'callers': ['HRMS_UNKNOWN']}),
        ]
        for message, changes in cases:
            with self.subTest(changes=changes):
                item = self.example()
                item.update(changes)
                errors = validate_catalogue({'items': [item]})
                self.assertTrue(any(message in error for error in errors), errors)
        item = self.example()
        self.assertIn('duplicate id: EXAMPLE', validate_catalogue({'items': [item, copy.deepcopy(item)]}))

    def test_validator_empty(self):
        self.assertEqual([], validate_catalogue({'items': [], 'missing': []}))

    @staticmethod
    def example():
        return dict(id='EXAMPLE', kind='schema', sources=['harness/load.py:1-2'],
                    callers=[], callees=[], golden='L', port_order=0, scope='in', reason='fixture')
