package com.ifielsc.ijob.plugin.etl.transformer.impl;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.transformer.AbstractTransformer;
import com.ifielsc.ijob.plugin.etl.transformer.TransformConfig;
import com.ifielsc.ijob.plugin.etl.transformer.TransformType;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class DateFormatTransformer extends AbstractTransformer {
    @Override
    public String type() {
        return TransformType.DATE_FORMAT.name();
    }

    @Override
    public List<DataRecord> transform(List<DataRecord> records, TransformConfig config) {
        String field = getString(config, "field");

        String source = getString(config, "source");

        String target = getString(config, "target");

        DateTimeFormatter sf = DateTimeFormatter.ofPattern(source);

        DateTimeFormatter tf = DateTimeFormatter.ofPattern(target);

        for (DataRecord record : records) {
            Object value = record.get(field);

            if (value != null) {
                LocalDate date = LocalDate.parse(value.toString(), sf);

                record.put(field, tf.format(date));
            }
        }

        return records;
    }
}
