"""Fast capture/runner contract tests; real Oracle parity uses run_golden.sh."""
import csv
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from validate_csv import validate


ROOT = Path(__file__).resolve().parent
BASELINE = 'employee_id,value\n1,1\n'


class CaptureValidationTests(unittest.TestCase):
    def check_capture(self, rows):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'capture.csv'
            with path.open('w', newline='') as target:
                csv.writer(target).writerows(rows)
            return validate(path)

    def test_preserves_duplicate_rows_and_explicit_empty_result_sets(self):
        self.assertEqual(self.check_capture([
            ['scenario', 'result'], ['rows', '{"name":"Fixture, Employee"}'],
            ['rows', '{"name":"Fixture, Employee"}'], ['rows.row_count', '2'],
            ['empty.row_count', '0'],
        ]), 4)

    def test_rejects_empty_malformed_and_incomplete_captures(self):
        for rows in (
            [], [['scenario', 'result']], [['wrong', 'header'], ['s', '1']],
            [['scenario', 'result'], ['s']],
            [['scenario', 'result'], ['s', '{bad json}']],
            [['scenario', 'result'], ['s', '1'], ['s.row_count', '2']],
            [['scenario', 'result'], ['s.row_count', '-1']],
            [['scenario', 'result'], ['s.row_count', '0'], ['s.row_count', '0']],
        ):
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                self.check_capture(rows)


class RunnerContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.harness = root / 'harness'
        self.harness.mkdir()
        (self.harness / 'golden').mkdir()
        (self.harness / 'work').mkdir()
        for filename in ('run_golden.sh', 'validate_csv.py'):
            shutil.copyfile(ROOT / filename, self.harness / filename)
        (self.harness / 'load.py').write_text('')
        for name in ('payroll', 'leave'):
            (self.harness / 'golden' / f'{name}.csv').write_text(BASELINE)
        bin_dir = root / 'bin'
        bin_dir.mkdir()
        docker = bin_dir / 'docker'
        docker.write_text('''#!/usr/bin/env python3
import os
from pathlib import Path
import sys
if len(sys.argv) > 2 and sys.argv[-2].startswith('oracle:/tmp/'):
    name = Path(sys.argv[-2]).stem
    value = '2' if os.environ.get('MUTATE_CAPTURE') == name else '1'
    header = 'employee_id,value' if name in ('payroll', 'leave') else 'scenario,result'
    Path(sys.argv[-1]).write_text(header + '\\nfixture,' + value + '\\n'
        if name not in ('payroll', 'leave') else header + '\\n1,' + value + '\\n')
''')
        docker.chmod(0o755)
        self.env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ['PATH'])

    def run_harness(self, *args, mutate=''):
        return subprocess.run(['bash', str(self.harness / 'run_golden.sh'), *args],
                              env=dict(self.env, MUTATE_CAPTURE=mutate),
                              capture_output=True, text=True)

    def test_record_protects_originals_and_comparison_rejects_drift(self):
        result = self.run_harness('--record')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('common: RECORDED', result.stdout)
        for name in ('payroll', 'leave'):
            self.assertEqual((self.harness / 'golden' / f'{name}.csv').read_text(), BASELINE)
        self.assertEqual(self.run_harness().returncode, 0)
        self.assertNotEqual(self.run_harness(mutate='common').returncode, 0)
        for name in ('payroll', 'leave'):
            self.assertNotEqual(self.run_harness('--record', mutate=name).returncode, 0)
            self.assertEqual((self.harness / 'golden' / f'{name}.csv').read_text(), BASELINE)

    def test_rejects_unknown_argument(self):
        self.assertEqual(self.run_harness('--overwrite').returncode, 2)


if __name__ == '__main__':
    unittest.main()
