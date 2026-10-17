package com.ifelio.ijob.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class IJobMigrationTool {

    private IJobMigrationTool() {}

    public static void main(String[] args) {
        int exit = run(args);
        if (exit != 0) {
            System.exit(exit);
        }
    }

    static int run(String[] args) {
        try {
            Arguments arguments = Arguments.parse(args);
            ConnectionSettings settings = ConnectionSettings.load(arguments.config());
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            JdbcMigrationService service =
                    new JdbcMigrationService(mapper, arguments.release(), arguments.replica());
            switch (arguments.command()) {
                case PREFLIGHT -> service.preflight(settings);
                case EXPORT -> service.exportBundle(settings, arguments.bundle());
                case IMPORT -> service.importBundle(settings, arguments.bundle());
                case VERIFY -> service.verifyBundle(settings, arguments.bundle());
                case INIT -> ReleaseSchemaPreparation.initialize(settings);
                case PREPARE -> ReleaseSchemaPreparation.prepare(settings);
            }
            return 0;
        } catch (Exception exception) {
            System.err.println(
                    "FAILED: "
                            + (args.length > 0 && args[0].startsWith("release-")
                                    ? exception.getClass().getSimpleName()
                                            + "; check schema lineage, offline source, empty target"
                                            + " and bundle integrity"
                                    : safeMessage(exception)));
            return 2;
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message.replaceAll("(?i)(password|pwd)=([^;&\\s]+)", "$1=<redacted>");
    }
}
