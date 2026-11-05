#!/usr/bin/env python3
"""Reject empty, malformed or incomplete captures before recording/comparing."""
from collections import Counter
import csv
import json
from pathlib import Path
import sys


def validate(path: Path) -> int:
    with path.open(newline='') as source:
        reader = csv.reader(source, strict=True)
        if next(reader, None) != ['scenario', 'result']:
            raise ValueError(f'{path}: expected scenario,result header')
        counts = Counter()
        expected = {}
        total = 0
        for row in reader:
            if len(row) != 2 or not all(row):
                raise ValueError(f'{path}: incomplete capture at line {reader.line_num}')
            scenario, result = row
            if scenario.endswith('.row_count'):
                name = scenario.removesuffix('.row_count')
                if name in expected or not result.isdecimal():
                    raise ValueError(f'{path}: invalid row count for {name}')
                expected[name] = int(result)
            else:
                counts[scenario] += 1
                if result.startswith('{'):
                    json.loads(result)
            total += 1
        if total == 0:
            raise ValueError(f'{path}: empty capture')
        for scenario, count in expected.items():
            if counts[scenario] != count:
                raise ValueError(f'{path}: {scenario}: expected {count} rows, got {counts[scenario]}')
        return total


if __name__ == '__main__':
    for filename in sys.argv[1:]:
        validate(Path(filename))
