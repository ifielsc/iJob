package com.ifielsc.ijob.plugin.etl.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record NamedSql(String sql, List<Object> values) {
    public static NamedSql parse(String source, Map<String, ?> parameters) {
        StringBuilder sql = new StringBuilder(source.length());
        List<Object> values = new ArrayList<>();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int index = 0; index < source.length(); ) {
            char current = source.charAt(index);
            if (current == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
                sql.append(current);
                index++;
                continue;
            }
            if (current == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
                sql.append(current);
                index++;
                continue;
            }
            if (!singleQuoted
                    && !doubleQuoted
                    && current == ':'
                    && (index == 0 || source.charAt(index - 1) != ':')
                    && index + 1 < source.length()
                    && Character.isJavaIdentifierStart(source.charAt(index + 1))) {
                int end = index + 2;
                while (end < source.length()
                        && Character.isJavaIdentifierPart(source.charAt(end))) {
                    end++;
                }
                String name = source.substring(index + 1, end);
                if (!parameters.containsKey(name)) {
                    throw new IllegalArgumentException("SQL parameter is missing: " + name);
                }
                sql.append('?');
                values.add(parameters.get(name));
                index = end;
                continue;
            }
            sql.append(current);
            index++;
        }
        return new NamedSql(sql.toString(), Collections.unmodifiableList(values));
    }
}
