package com.ifelio.ijob.plugin.etl.extractor.file;

import com.ifelio.ijob.core.resource.ResourceClientLease;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.core.resource.factory.file.client.FileClient;
import com.ifelio.ijob.plugin.etl.TempPathResource;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.extractor.AbstractExtractor;
import com.ifelio.ijob.plugin.etl.extractor.csv.DelimitedDataReader;
import com.ifelio.ijob.plugin.etl.extractor.excel.ExcelDataReader;
import com.ifelio.ijob.plugin.etl.extractor.excel.ExcelExtractConfig;
import com.ifelio.ijob.plugin.etl.extractor.excel.ExcelReadQuota;
import com.ifelio.ijob.plugin.etl.extractor.excel.ExcelStreamReader;
import com.ifelio.ijob.plugin.etl.extractor.excel.ExcelStreamReaderFactory;
import com.ifelio.ijob.plugin.etl.extractor.regex.RegexStreamDataReader;
import com.ifelio.ijob.plugin.etl.model.EtlContext;
import com.ifelio.ijob.plugin.etl.reader.DataReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileExtractor extends AbstractExtractor {
    private final ResourceResolver resourceResolver;

    public FileExtractor(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Override
    public DataReader extract(EtlContext context) {
        EtlTaskConfig.SourceConfig config = context.getConfig().getSource();
        if (config.getReadMode() == EtlTaskConfig.FileReadMode.SQL) {
            throw new IllegalArgumentException("ETL file SQL mode is not available in this batch");
        }
        return extractDirect(context);
    }

    public DataReader extractDirect(EtlContext context) {
        EtlTaskConfig.SourceConfig config = context.getConfig().getSource();
        if (context.getStagedSource() != null) return extractStaged(context);
        String path = context.getTaskContext().resolve(config.getPath());
        ResourceClientLease<FileClient> lease =
                resourceResolver.leaseFileClient(config.getResourceId());
        context.getTaskContext().registerResource(lease);
        try {
            return switch (config.getFormat()) {
                case CSV, TXT ->
                        new DelimitedDataReader(
                                limited(lease.getClient().download(path), context),
                                config,
                                context.getConfig().getExecution().getMaxTextFieldChars());
                case REGEX ->
                        new RegexStreamDataReader(
                                limited(lease.getClient().download(path), context), config);
                case EXCEL -> openExcel(lease.getClient(), path, config, context);
                case SQLITE, MDB ->
                        throw new IllegalArgumentException(
                                "ETL database file format requires SQL mode");
            };
        } catch (IOException exception) {
            lease.close();
            throw new IllegalStateException("ETL file source could not be opened", exception);
        }
    }

    private DataReader extractStaged(EtlContext context) {
        var source = context.getConfig().getSource();
        try {
            return switch (source.getFormat()) {
                case CSV, TXT ->
                        new DelimitedDataReader(
                                stagedInput(context),
                                source,
                                context.getConfig().getExecution().getMaxTextFieldChars());
                case REGEX -> new RegexStreamDataReader(stagedInput(context), source);
                case EXCEL -> {
                    var excel = excelConfig(source, context.getConfig().getExecution());
                    yield openExcel(
                            context.getStagedSource(), excel, new ExcelReadQuota(excel), context);
                }
                default ->
                        throw new IllegalArgumentException(
                                "ETL database file format requires SQL mode");
            };
        } catch (IOException exception) {
            throw new IllegalStateException("ETL staged source is unavailable", exception);
        }
    }

    private InputStream stagedInput(EtlContext context) throws IOException {
        InputStream input = Files.newInputStream(context.getStagedSource());
        context.getTaskContext().registerResource(input);
        return input;
    }

    private InputStream limited(InputStream input, EtlContext context) {
        return new LimitedInputStream(input, context.getConfig().getExecution().getMaxFileBytes());
    }

    private DataReader openExcel(
            FileClient client,
            String sourcePath,
            EtlTaskConfig.SourceConfig source,
            EtlContext context)
            throws IOException {
        Path temp = downloadExcel(client, sourcePath, context);
        ExcelExtractConfig excel = excelConfig(source, context.getConfig().getExecution());
        return openExcel(temp, excel, new ExcelReadQuota(excel), context);
    }

    Path stageExcel(EtlContext context) {
        if (context.getStagedSource() != null) return context.getStagedSource();
        EtlTaskConfig.SourceConfig source = context.getConfig().getSource();
        ResourceClientLease<FileClient> lease =
                resourceResolver.leaseFileClient(source.getResourceId());
        context.getTaskContext().registerResource(lease);
        try {
            return downloadExcel(
                    lease.getClient(), context.getTaskContext().resolve(source.getPath()), context);
        } catch (IOException exception) {
            throw new IllegalStateException("ETL Excel source could not be staged", exception);
        } finally {
            lease.close();
        }
    }

    private Path downloadExcel(FileClient client, String sourcePath, EtlContext context)
            throws IOException {
        context.checkControl();
        String suffix = sourcePath.toLowerCase().endsWith(".xls") ? ".xls" : ".xlsx";
        Path temp = Files.createTempFile("ijob-etl-source-", suffix);
        TempPathResource tempResource = new TempPathResource(temp);
        context.getTaskContext().registerResource(tempResource);
        try (InputStream input = client.download(sourcePath);
                OutputStream output = Files.newOutputStream(temp)) {
            copyWithLimit(input, output, context);
        } catch (Exception exception) {
            tempResource.close();
            throw exception;
        }
        return temp;
    }

    static ExcelExtractConfig excelConfig(
            EtlTaskConfig.SourceConfig source, EtlTaskConfig.ExecutionConfig execution) {
        ExcelExtractConfig excel = new ExcelExtractConfig();
        excel.setSheetName(source.getSheetName());
        excel.setSheetIndex(source.getSheetIndex());
        excel.setHeaderRow(source.getHeaderRow());
        excel.setHeader(source.isHeader());
        excel.setStartRow(source.getStartRow());
        excel.setMaxRows(execution.getMaxRecords());
        excel.setMaxFields(execution.getMaxRecordFields());
        excel.setMaxTextFieldChars(execution.getMaxTextFieldChars());
        excel.setMaxExpandedTextChars(execution.getMaxExpandedTextChars());
        excel.setMaxCells(execution.getMaxExcelCells());
        excel.setMaxSheets(execution.getMaxExcelSheets());
        return excel;
    }

    static ExcelDataReader openExcel(
            Path file, ExcelExtractConfig excel, ExcelReadQuota quota, EtlContext context) {
        context.checkControl();
        excel.setFilePath(file.toString());
        ExcelStreamReader stream = ExcelStreamReaderFactory.create(file, excel);
        ExcelDataReader reader = new ExcelDataReader(stream, excel, quota);
        context.getTaskContext().registerResource(reader);
        return reader;
    }

    private void copyWithLimit(InputStream input, OutputStream output, EtlContext context)
            throws IOException {
        long limit = context.getConfig().getExecution().getMaxFileBytes();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            context.checkControl();
            total += read;
            if (total > limit) {
                throw new IllegalArgumentException("ETL source file limit exceeded");
            }
            output.write(buffer, 0, read);
        }
    }
}
