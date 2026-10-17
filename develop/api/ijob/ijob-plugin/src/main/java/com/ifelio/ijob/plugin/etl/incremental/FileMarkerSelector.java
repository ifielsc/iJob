package com.ifelio.ijob.plugin.etl.incremental;

import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;

import java.util.regex.Pattern;

public final class FileMarkerSelector {
    private final EtlTaskConfig.FileIncrementalConfig config;
    private final String type;
    private final Pattern pattern;

    public FileMarkerSelector(EtlTaskConfig.IncrementalConfig incremental) {
        this.config = incremental.getFile();
        this.type = incremental.getWatermarkType();
        String expression = config.getExpression();
        if (expression == null || expression.isBlank() || expression.length() > 512)
            throw new IllegalArgumentException(
                    "ETL file expression is required and limited to 512 characters");
        if (config.getMatchMode() == EtlTaskConfig.MatchMode.TEMPLATE) {
            int mark = expression.indexOf("{mark}");
            if (mark < 0 || expression.indexOf("{mark}", mark + 1) >= 0)
                throw new IllegalArgumentException("ETL file template requires exactly one {mark}");
            expression =
                    glob(expression.substring(0, mark))
                            + "(?<mark>.+?)"
                            + glob(expression.substring(mark + 6));
        } else if (!expression.contains("(?<mark>")) {
            throw new IllegalArgumentException("ETL file regex requires named group mark");
        }
        pattern = Pattern.compile(expression);
    }

    public String extract(String relativePath) {
        String value =
                config.getMarkerLocation() == EtlTaskConfig.MarkerLocation.NAME
                        ? relativePath.substring(relativePath.lastIndexOf('/') + 1)
                        : relativePath;
        if (value.length() > 2048) throw new IllegalArgumentException("ETL file path is too long");
        try {
            var matcher = pattern.matcher(new BoundedText(value, new int[] {100000}));
            if (!matcher.matches()) return null;
            return EtlWatermark.normalize(
                    matcher.group("mark"), type, config.getMarkerFormat(), config.getZoneId());
        } catch (StackOverflowError error) {
            throw new IllegalArgumentException("ETL file expression exceeds matching limits");
        }
    }

    private static String glob(String text) {
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray())
            result.append(
                    c == '*' ? "[^/]*" : c == '?' ? "[^/]" : Pattern.quote(String.valueOf(c)));
        return result.toString();
    }

    private record BoundedText(String text, int[] remaining) implements CharSequence {
        public int length() {
            return text.length();
        }

        public char charAt(int index) {
            if (--remaining[0] < 0)
                throw new IllegalArgumentException("ETL file expression exceeds matching limits");
            return text.charAt(index);
        }

        public CharSequence subSequence(int start, int end) {
            return new BoundedText(text.substring(start, end), remaining);
        }

        public String toString() {
            return text;
        }
    }
}
