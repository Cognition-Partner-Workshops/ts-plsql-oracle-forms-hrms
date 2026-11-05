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


def validate_sources(ident, sources):
    errors = []
    if not isinstance(sources, list) or not sources:
        return [f'{ident}: no citations']
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
    return errors


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
        errors.extend(validate_sources(ident, item.get('sources', [])))
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


def validate_rules(data, inventory_ids):
    errors = []
    rules, drift = data.get('rules', []), data.get('drift', [])
    catalogue_ids = set()
    rule_items = {rule.get('item') for rule in rules}
    for entry in data.get('items', []):
        item = entry.get('item')
        if item in catalogue_ids:
            errors.append(f'duplicate catalogue item: {item}')
        catalogue_ids.add(item)
        if item not in inventory_ids:
            errors.append(f'unknown catalogue item: {item}')
        if item not in rule_items and not entry.get('reason', '').strip():
            errors.append(f'{item}: zero-rule item needs a reason')
    for item in sorted(inventory_ids - catalogue_ids):
        errors.append(f'missing catalogue item: {item}')
    rule_ids = {rule['id'] for rule in rules}
    drift_ids = {entry['id'] for entry in drift}
    for label, entries in [('rule', rules), ('drift', drift)]:
        seen = set()
        for entry in entries:
            ident = entry['id']
            if ident in seen:
                errors.append(f'duplicate {label} id: {ident}')
            seen.add(ident)
            errors.extend(validate_sources(ident, entry.get('sources', [])))
    for rule in rules:
        ident = rule['id']
        if rule.get('item') not in inventory_ids:
            errors.append(f'{ident}: unknown inventory item: {rule.get("item")}')
        for ref in rule.get('drift', []):
            if ref not in drift_ids:
                errors.append(f'{ident}: unknown drift id: {ref}')
        if rule.get('location') not in {'form_trigger', 'pll', 'package', 'db_trigger', 'menu', 'config'}:
            errors.append(f'{ident}: invalid location')
        if rule.get('kind') not in {'hard_coded', 'data_driven', 'mixed'}:
            errors.append(f'{ident}: invalid kind')
        if not all(rule.get(field) for field in ('statement', 'change_likelihood', 'hypothesis')):
            errors.append(f'{ident}: missing rule metadata')
    for entry in drift:
        for ref in entry.get('rules', []):
            if ref not in rule_ids:
                errors.append(f'{entry["id"]}: unknown rule id: {ref}')
    if data.get('context_sources'):
        errors.extend(validate_sources('context_sources', data['context_sources']))
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
            rules = yaml.safe_load(rules.read_text())
            self.assertEqual([], validate_rules(rules, {item['id'] for item in data['items']}))
            self.assertEqual(152, len(rules['rules']))
            self.assertEqual(8, len(rules['drift']))
            total = sum(len(entry['sources']) for entry in rules['rules'] + rules['drift'])
            self.assertEqual(247, total + len(rules.get('context_sources', [])))

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
        self.assertEqual([], validate_rules({'rules': [], 'drift': []}, set()))

    def test_rules_validator_valid(self):
        self.assertEqual([], validate_rules(self.rules_example(), {'EXAMPLE'}))

    def test_rules_item_coverage(self):
        data = self.rules_example()
        data['items'].append(dict(item='ZERO', reason='Allocation mechanics'))
        self.assertEqual([], validate_rules(data, {'EXAMPLE', 'ZERO'}))
        data['items'][-1].pop('reason')
        self.assertIn('ZERO: zero-rule item needs a reason', validate_rules(data, {'EXAMPLE', 'ZERO'}))
        data = self.rules_example()
        data['items'] = []
        self.assertIn('missing catalogue item: EXAMPLE', validate_rules(data, {'EXAMPLE'}))
        data['items'] = [dict(item='UNKNOWN', reason='Fixture')]
        self.assertIn('unknown catalogue item: UNKNOWN', validate_rules(data, {'EXAMPLE'}))
        data = self.rules_example()
        data['items'].append(dict(item='EXAMPLE'))
        self.assertIn('duplicate catalogue item: EXAMPLE', validate_rules(data, {'EXAMPLE'}))

    def test_rules_validator_rejects_bad_data(self):
        cases = [
            ('no citations', 'sources', []),
            ('missing file', 'sources', ['nonexistent.sql:1']),
            ('malformed citation', 'sources', ['harness/load.py:0']),
            ('out of bounds', 'sources', ['harness/load.py:999999']),
            ('unknown inventory item', 'item', 'UNKNOWN'),
            ('unknown drift id', 'drift', ['DRIFT-999']),
        ]
        for message, field, value in cases:
            with self.subTest(field=field, value=value):
                data = self.rules_example()
                data['rules'][0][field] = value
                errors = validate_rules(data, {'EXAMPLE'})
                self.assertTrue(any(message in error for error in errors), errors)
        for label in ('rules', 'drift'):
            with self.subTest(duplicate=label):
                data = self.rules_example()
                data[label].append(copy.deepcopy(data[label][0]))
                self.assertTrue(any('duplicate' in error for error in validate_rules(data, {'EXAMPLE'})))
        data = self.rules_example()
        data['drift'][0]['rules'] = ['UNKNOWN-001']
        self.assertIn('DRIFT-001: unknown rule id: UNKNOWN-001', validate_rules(data, {'EXAMPLE'}))

    @staticmethod
    def rules_example():
        rule = dict(id='TEST-001', item='EXAMPLE', statement='Fixture statement',
                    sources=['harness/load.py:1'], location='config', kind='hard_coded',
                    change_likelihood='yes', drift=['DRIFT-001'], hypothesis='Fixture hypothesis')
        drift = dict(id='DRIFT-001', description='Fixture difference', rules=['TEST-001'],
                     sources=['harness/load.py:1-2'])
        return dict(items=[dict(item='EXAMPLE')], rules=[rule], drift=[drift], context_sources=[])

    @staticmethod
    def example():
        return dict(id='EXAMPLE', kind='schema', sources=['harness/load.py:1-2'],
                    callers=[], callees=[], golden='L', port_order=0, scope='in', reason='fixture')
