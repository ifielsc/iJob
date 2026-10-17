package com.ifelio.ijob.plugin.etl.extractor.database;

import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.extractor.AbstractExtractor;
import com.ifelio.ijob.plugin.etl.model.EtlContext;
import com.ifelio.ijob.plugin.etl.reader.DataReader;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

public class DatabaseExtractor extends AbstractExtractor {
    private final ResourceResolver resourceResolver;

    public DatabaseExtractor(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Override
    public DataReader extract(EtlContext context) {
        EtlTaskConfig.SourceConfig config = context.getConfig().getSource();
        DataSource dataSource = resourceResolver.getDataSource(config.getResourceId());
        Map<String, Object> parameters = new LinkedHashMap<>();
        config.getParameters()
                .forEach(
                        (name, value) ->
                                parameters.put(
                                        name, context.getTaskContext().resolveObject(value)));
        context.getIncrementalSession().addParameters(parameters);
        return JdbcDataReader.open(
                dataSource,
                config,
                context,
                parameters,
                !context.getIncrementalSession().enabled());
    }
}
