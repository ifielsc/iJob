package com.ifelio.ijob.plugin.datacheck.executor;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResultSetExtractor
        implements org.springframework.jdbc.core.ResultSetExtractor<List<Map<String, Object>>> {
    @Override
    public List<Map<String, Object>> extractData(ResultSet rs) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();

        ResultSetMetaData meta = rs.getMetaData();

        int columnCount = meta.getColumnCount();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();

            for (int i = 1; i <= columnCount; i++) {
                String name = meta.getColumnLabel(i);

                row.put(name, rs.getObject(i));
            }

            result.add(row);
        }

        return result;
    }
}
