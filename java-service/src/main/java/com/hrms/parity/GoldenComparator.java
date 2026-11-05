package com.hrms.parity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GoldenComparator {
    private GoldenComparator() { }

    public record Comparison(int expected, int matched, int mismatches, List<String> diffs) {
        public boolean passed() { return mismatches == 0; }
    }

    public static Comparison compare(List<String> expected, List<String> actual, boolean ordered) {
        List<String> diffs = new ArrayList<>();
        int mismatches = 0;
        if (expected.isEmpty() || actual.isEmpty() || !expected.get(0).equals(actual.get(0))) {
            mismatches++;
            diff(diffs, expected.isEmpty() ? "<missing header>" : expected.get(0),
                    actual.isEmpty() ? "<missing header>" : actual.get(0));
        }
        var left = new ArrayList<>(expected.subList(Math.min(1, expected.size()), expected.size()));
        var right = new ArrayList<>(actual.subList(Math.min(1, actual.size()), actual.size()));
        int count = left.size();
        int matched = 0;
        if (!ordered) {
            var binary = (java.util.Comparator<String>) (a, b) -> Arrays.compareUnsigned(
                    a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
            left.sort(binary);
            right.sort(binary);
            for (int i = 0; i < left.size();) {
                int index = java.util.Collections.binarySearch(right, left.get(i), binary);
                if (index >= 0) {
                    left.remove(i);
                    right.remove(index);
                    matched++;
                } else {
                    i++;
                }
            }
        }
        for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
            String e = i < left.size() ? left.get(i) : null;
            String a = i < right.size() ? right.get(i) : null;
            if (e != null && e.equals(a)) {
                matched++;
            } else {
                mismatches++;
                diff(diffs, e == null ? "<missing>" : e, a == null ? "<missing>" : a);
            }
        }
        return new Comparison(count, matched, mismatches, List.copyOf(diffs));
    }

    private static void diff(List<String> diffs, String expected, String actual) {
        if (diffs.size() < 3) {
            diffs.add("-" + expected + " / +" + actual);
        }
    }
}
