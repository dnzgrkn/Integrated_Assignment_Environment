package com.iae.service.compare;

import com.iae.model.ComparisonResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OutputComparator {

    public ComparisonResult compare(String actualOutput, String expectedOutput) {
        List<String> actual = normalize(actualOutput);
        List<String> expected = normalize(expectedOutput);

        int max = Math.max(actual.size(), expected.size());
        for (int i = 0; i < max; i++) {
            String a = (i < actual.size()) ? actual.get(i) : null;
            String e = (i < expected.size()) ? expected.get(i) : null;
            if (!Objects.equals(a, e)) {
                return ComparisonResult.mismatch(i + 1, e, a);
            }
        }
        return ComparisonResult.success();
    }

    private static List<String> normalize(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        for (String raw : text.split("\\r?\\n", -1)) {
            lines.add(stripTrailing(raw));
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }
}
