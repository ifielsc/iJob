// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.setup;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.SecureRandom;
import java.util.*;

public final class SetupTool {
    private static final Map<String, String> DRIVERS =
            Map.of(
                    "sqlite",
                    "org.sqlite.JDBC",
                    "mysql",
                    "com.mysql.cj.jdbc.Driver",
                    "postgresql",
                    "org.postgresql.Driver",
                    "sqlserver",
                    "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                    "oracle",
                    "oracle.jdbc.OracleDriver");
    private static final LinkOption[] NOFOLLOW = {LinkOption.NOFOLLOW_LINKS};
    private static final Map<String, String> CONFIG_NOTES =
            Map.ofEntries(
                    Map.entry(
                            "app.auth.mode",
                            "STATIC: one configured account; DATABASE: managed accounts. See README"
                                    + " before switching."),
                    Map.entry(
                            "app.auth.bootstrap-enabled",
                            "DATABASE only: create the first administrator in an empty user table."
                                    + " Disable after setup."),
                    Map.entry(
                            "app.auth.username",
                            "Required for STATIC or first DATABASE bootstrap; unused for DATABASE"
                                    + " login after bootstrap."),
                    Map.entry(
                            "app.auth.password-hash",
                            "BCrypt hash, not plaintext. Required for STATIC or first DATABASE"
                                    + " bootstrap."),
                    Map.entry(
                            "app.runtime.mode",
                            "STANDALONE: one instance; CLUSTER: shared server database and matching"
                                    + " security configuration. SQLite cannot cluster."),
                    Map.entry(
                            "app.runtime.instance-name",
                            "Use a unique name for each cluster node."),
                    Map.entry(
                            "app.schedule.enabled",
                            "Global schedule scanning. Also enable each schedule in the UI; a"
                                    + " SCHEDULER node is required."),
                    Map.entry(
                            "app.notification.outbox.enabled",
                            "Persistent notification delivery. Required in CLUSTER mode."),
                    Map.entry(
                            "app.event.recovery.enabled",
                            "Recover persisted events; separate from accepting new events through"
                                    + " the ingress API."),
                    Map.entry(
                            "app.runtime.maintenance.enabled",
                            "Runtime recovery and coordination. Required in CLUSTER mode; not"
                                    + " business history cleanup."),
                    Map.entry(
                            "app.plugin.etl.enabled",
                            "Enable the ETL task plugin; configure resources and tasks in the UI."),
                    Map.entry(
                            "app.plugin.batch-etl.enabled",
                            "Enable the batch ETL task plugin; configure resources and tasks in the"
                                    + " UI."),
                    Map.entry(
                            "app.plugin.local-jar.enabled",
                            "Enable execution of local JAR plugins; disabled by default."),
                    Map.entry(
                            "app.event.ingress.enabled",
                            "Enable the event ingress API; login and EVENT_PUBLISH permission are"
                                    + " still required."),
                    Map.entry(
                            "app.retention.enabled",
                            "Enable business history cleanup; review retention rules and dry-run"
                                    + " before use."),
                    Map.entry(
                            "app.retention.dry-run",
                            "When true, history cleanup does not delete data. Independent of"
                                    + " application log rotation."));
    private boolean chinese = "zh".equals(System.getenv("IJOB_LANG"));
    private final Console console = System.console();
    private final BufferedReader input =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private final Map<String, String> options = new HashMap<>();

    public static void main(String[] args) {
        try {
            if (args.length > 0 && Set.of("settings", "check-settings").contains(args[0])) {
                UserSettings.run(args, System.out);
            } else new SetupTool().run(args);
        } catch (Exception e) {
            // Do not echo connection strings, passwords or configuration contents in errors.
            System.err.println(
                    "Setup stopped: "
                            + (e instanceof SetupException
                                    ? e.getMessage()
                                    : e.getClass().getSimpleName())
                            + ". Existing files were retained.");
            System.exit(1);
        }
    }

    private void run(String[] args) throws Exception {
        String command = args.length == 0 ? "init" : args[0];
        if (!Set.of("init", "start").contains(command))
            throw new SetupException("Use init or start");
        for (int i = 1; i < args.length; i++) {
            String key = args[i];
            if (key.equals("--stdin")) options.put(key, "true");
            else if (Set.of("--home", "--bundle", "--mode", "--host-uid", "--host-gid", "--lang")
                            .contains(key)
                    && i + 1 < args.length) options.put(key, args[++i]);
            else throw new SetupException("Unknown or incomplete option");
        }
        if (options.containsKey("--lang")) {
            if (!Set.of("zh", "en").contains(options.get("--lang")))
                throw new SetupException("Use --lang zh or en");
            chinese = "zh".equals(options.get("--lang"));
        }
        Path bundle = Path.of(options.getOrDefault("--bundle", ".")).toAbsolutePath().normalize();
        String homeOption =
                options.getOrDefault(
                        "--home",
                        System.getenv()
                                .getOrDefault(
                                        "IJOB_HOME",
                                        bundle.resolveSibling("ijob-data").toString()));
        Path home = Path.of(homeOption).toAbsolutePath().normalize();
        String mode = options.getOrDefault("--mode", "windows");
        if (!Set.of("windows", "linux", "docker").contains(mode))
            throw new SetupException("Invalid mode");
        if (command.equals("start") && mode.equals("docker"))
            throw new SetupException("Use start-docker.sh for Docker");
        if (home.startsWith(bundle))
            throw new SetupException("Data home must be outside the release folder");
        rejectLinks(home);
        if (!Files.isDirectory(home.getParent(), NOFOLLOW))
            throw new SetupException("Data parent directory must exist");
        Properties installation;
        boolean emptyHome = false;
        if (Files.isDirectory(home, NOFOLLOW)) {
            try (var entries = Files.list(home)) {
                emptyHome = entries.findAny().isEmpty();
            }
        }
        if (Files.exists(home, NOFOLLOW) && !emptyHome) {
            installation = existing(home, mode);
            System.out.println((SetupMessages.text(chinese, "reused")) + home);
        } else {
            if (console == null && !options.containsKey("--stdin"))
                throw new SetupException(
                        "Interactive terminal required (automation may use --stdin)");
            String username = read("Administrator username: ", false).trim();
            if (!username.matches("[A-Za-z0-9_.@-]{1,64}"))
                throw new SetupException("Username must be 1-64 letters, digits or _.@-");
            String password = read("Administrator password (12+ characters): ", true);
            if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
                throw new SetupException(
                        "Password must have at least 12 characters and at most 72 UTF-8 bytes");
            if (!password.equals(read("Confirm password: ", true)))
                throw new SetupException("Passwords do not match");
            String vendor =
                    read("Database [sqlite/mysql/postgresql/sqlserver/oracle] (sqlite): ", false)
                            .trim()
                            .toLowerCase(Locale.ROOT);
            if (vendor.isEmpty()) vendor = "sqlite";
            if (!DRIVERS.containsKey(vendor)) throw new SetupException("Unsupported database");
            String url =
                    "jdbc:sqlite:"
                            + (mode.equals("docker")
                                    ? "/var/lib/ijob/platform.db"
                                    : home.resolve("runtime/platform.db")
                                            .toString()
                                            .replace('\\', '/'));
            String dbUser = "", dbPassword = "";
            if (!vendor.equals("sqlite")) {
                System.out.println("JDBC format example: " + jdbcExample(vendor));
                System.out.println(
                        "Replace host, port and database/service name; configure TLS for your"
                                + " environment. Enter credentials separately. Setup does not test"
                                + " connectivity.");
                url =
                        read(
                                        "JDBC URL (existing empty database; schema migration occurs"
                                                + " only at startup): ",
                                        false)
                                .trim();
                if (!url.startsWith("jdbc:" + vendor + ":"))
                    throw new SetupException("JDBC URL does not match database");
                dbUser = read("Database username: ", false);
                dbPassword = read("Database password: ", true);
            }
            byte[] aes = new byte[32];
            new SecureRandom().nextBytes(aes);
            Properties config = new Properties();
            config.setProperty("spring.profiles.active", "release");
            config.setProperty("server.address", mode.equals("docker") ? "0.0.0.0" : "127.0.0.1");
            config.setProperty("server.port", mode.equals("docker") ? "8080" : "18080");
            config.setProperty("spring.datasource.url", url);
            config.setProperty("spring.datasource.driver-class-name", DRIVERS.get(vendor));
            config.setProperty("spring.datasource.username", dbUser);
            config.setProperty("spring.datasource.password", dbPassword);
            config.setProperty("spring.datasource.hikari.maximum-pool-size", "4");
            config.setProperty("app.platform-database.vendor", vendor);
            config.setProperty("spring.flyway.enabled", "true");
            config.setProperty("spring.flyway.locations", "classpath:db/release/" + vendor);
            config.setProperty("app.runtime.mode", "STANDALONE");
            config.setProperty("app.runtime.instance-name", "setup-" + UUID.randomUUID());
            config.setProperty("app.runtime.groups", "default");
            config.setProperty("app.auth.mode", "STATIC");
            config.setProperty("app.auth.bootstrap-enabled", "false");
            config.setProperty("app.auth.username", username);
            config.setProperty(
                    "app.auth.password-hash", new BCryptPasswordEncoder(12).encode(password));
            config.setProperty("app.security.aes.key", Base64.getEncoder().encodeToString(aes));
            config.setProperty("app.security.aes.key-id", "setup-key-1");
            for (String key :
                    List.of(
                            "app.schedule.enabled",
                            "app.notification.outbox.enabled",
                            "app.event.recovery.enabled",
                            "app.runtime.maintenance.enabled",
                            "app.plugin.etl.enabled",
                            "app.plugin.batch-etl.enabled")) config.setProperty(key, "true");
            for (String key :
                    List.of(
                            "app.retention.enabled",
                            "app.plugin.local-jar.enabled",
                            "app.event.ingress.enabled")) config.setProperty(key, "false");
            config.setProperty("app.retention.dry-run", "true");
            if (!mode.equals("docker")) {
                config.setProperty("app.web.enabled", "true");
                config.setProperty("app.web.directory", bundle.resolve("web-dist").toString());
            }
            installation = new Properties();
            installation.setProperty("format", "1");
            installation.setProperty("vendor", vendor);
            installation.setProperty("mode", mode);
            // CREATE_NEW prevents concurrent setup runs from replacing any existing installation.
            if (emptyHome) {
                if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
                    Files.setPosixFilePermissions(
                            home, PosixFilePermissions.fromString("rwx------"));
                else restrictWindows(home);
            } else privateDirectory(home);
            writePrivate(
                    home.resolve(".setup-lock"),
                    "Initialization started; do not delete after an interrupted setup.\n");
            Path runtime = home.resolve("runtime");
            privateDirectory(runtime);
            writeProperties(home.resolve("application.properties"), config);
            if (mode.equals("docker")) {
                writePrivate(home.resolve("compose.yaml"), compose(home, vendor));
                owner(runtime, 10001, 10001);
                owner(home.resolve("application.properties"), 10001, 10001);
            }
            writeProperties(home.resolve("installation.properties"), installation);
            if (mode.equals("docker")) {
                int uid = Integer.parseInt(options.getOrDefault("--host-uid", "0"));
                int gid = Integer.parseInt(options.getOrDefault("--host-gid", "0"));
                if (uid < 0 || gid < 0) throw new SetupException("Invalid host owner");
                owner(home.resolve("compose.yaml"), uid, gid);
                owner(home.resolve("installation.properties"), uid, gid);
                owner(home, uid, gid);
            }
            writePrivate(home.resolve("setup.complete"), "1\n");
            System.out.println(
                    (SetupMessages.text(chinese, "initialized"))
                            + home
                            + (SetupMessages.text(chinese, "configuration-only")));
        }
        if (command.equals("start")) {
            Path jar = bundle.resolve("ijob-starter-1.0.0.jar");
            if (!Files.isRegularFile(jar)) throw new SetupException("Backend JAR missing");
            Path logging = bundle.resolve("logging-native.xml");
            if (!Files.isRegularFile(logging))
                throw new SetupException(
                        "Native logging configuration missing; copy the complete release folder");
            Path logs = home.resolve("logs");
            rejectLinks(logs);
            rejectLinks(logs.resolve("ijob.log"));
            rejectLinks(logs.resolve("archive"));
            if (!Files.exists(logs, NOFOLLOW)) privateDirectory(logs);
            ProcessBuilder builder =
                    new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                            "-jar",
                            jar.toString(),
                            "--spring.profiles.active=release",
                            "--spring.config.additional-location="
                                    + home.resolve("application.properties").toUri(),
                            "--app.web.directory=" + bundle.resolve("web-dist"));
            builder.command().add("--logging.config=" + logging.toUri());
            builder.command().add("--ijob.native.log-directory=" + logs);
            builder.command().add("--ijob.native.log-session=" + UUID.randomUUID());
            System.out.println("Application log: " + logs.resolve("ijob.log"));
            builder.environment().put("IJOB_PLATFORM_DB", installation.getProperty("vendor"));
            builder.directory(home.resolve("runtime").toFile()).inheritIO();
            Process child = builder.start();
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        if (child.isAlive()) child.destroy();
                                    }));
            System.exit(child.waitFor());
        }
    }

    private Properties existing(Path home, String mode) throws Exception {
        for (String file :
                List.of(
                        "installation.properties",
                        "application.properties",
                        "runtime",
                        "setup.complete")) {
            Path path = home.resolve(file);
            rejectLinks(path);
            if (!Files.exists(path, NOFOLLOW))
                throw new SetupException(
                        "Unmanaged or incomplete data home; choose a new directory or restore its"
                                + " configuration");
        }
        Properties p = new Properties();
        try (InputStream stream = Files.newInputStream(home.resolve("installation.properties"))) {
            p.load(stream);
        }
        if (!"1".equals(p.getProperty("format"))
                || !mode.equals(p.getProperty("mode"))
                || !DRIVERS.containsKey(p.getProperty("vendor", "")))
            throw new SetupException("Installation metadata mismatch");
        if (mode.equals("docker")) {
            rejectLinks(home.resolve("compose.yaml"));
            if (!Files.isRegularFile(home.resolve("compose.yaml")))
                throw new SetupException("Compose file missing");
        }
        return p;
    }

    private String read(String prompt, boolean secret) throws IOException {
        if (chinese)
            prompt =
                    switch (prompt) {
                        case "Administrator username: " ->
                                SetupMessages.text(chinese, "prompt.username");
                        case "Administrator password (12+ characters): " ->
                                SetupMessages.text(chinese, "prompt.password");
                        case "Confirm password: " -> SetupMessages.text(chinese, "prompt.confirm");
                        case "Database [sqlite/mysql/postgresql/sqlserver/oracle] (sqlite): " ->
                                SetupMessages.text(chinese, "prompt.database");
                        case "Database username: " ->
                                SetupMessages.text(chinese, "prompt.database-user");
                        case "Database password: " ->
                                SetupMessages.text(chinese, "prompt.database-password");
                        default ->
                                prompt.startsWith("JDBC URL")
                                        ? SetupMessages.text(chinese, "prompt.jdbc")
                                        : prompt;
                    };
        if (console != null) {
            if (secret) {
                char[] chars = console.readPassword("%s", prompt);
                if (chars == null) throw new EOFException();
                String value = new String(chars);
                Arrays.fill(chars, '\0');
                return value;
            }
            String value = console.readLine("%s", prompt);
            if (value == null) throw new EOFException();
            return value;
        }
        System.out.print(prompt);
        System.out.flush();
        String value = input.readLine();
        if (value == null) throw new EOFException();
        return value;
    }

    private static void rejectLinks(Path path) throws IOException {
        for (Path p = path; p != null; p = p.getParent()) {
            if (Files.exists(p, NOFOLLOW)) {
                BasicFileAttributes attributes =
                        Files.readAttributes(p, BasicFileAttributes.class, NOFOLLOW);
                if (attributes.isSymbolicLink() || attributes.isOther())
                    throw new SetupException(
                            "Links and reparse points are not supported for the data home");
            }
        }
    }

    private static void privateDirectory(Path path) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
            Files.createDirectory(
                    path,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
        else {
            Files.createDirectory(path);
            restrictWindows(path);
        }
    }

    private static void writePrivate(Path path, String value) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
            Files.createFile(
                    path,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
        else {
            Files.createFile(path);
            restrictWindows(path);
        }
        Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.WRITE);
    }

    private static void writeProperties(Path path, Properties properties) throws IOException {
        StringWriter writer = new StringWriter();
        properties.store(writer, "iJob private configuration - preserve across upgrades");
        StringBuilder annotated = new StringBuilder();
        if (properties.containsKey("app.auth.mode"))
            annotated.append(
                    "# Edit existing keys instead of adding duplicates; restart to apply"
                            + " changes.\n");
        for (String line : writer.toString().split("\\r?\\n")) {
            int separator = line.indexOf('=');
            String note = separator < 0 ? null : CONFIG_NOTES.get(line.substring(0, separator));
            if (note != null) annotated.append("# ").append(note).append('\n');
            annotated.append(line).append('\n');
        }
        StringBuilder escaped = new StringBuilder();
        for (char c : annotated.toString().toCharArray()) {
            if (c > 127) escaped.append(String.format("\\u%04x", (int) c));
            else escaped.append(c);
        }
        writePrivate(path, escaped.toString());
    }

    private static void restrictWindows(Path path) throws IOException {
        AclFileAttributeView view =
                Files.getFileAttributeView(path, AclFileAttributeView.class, NOFOLLOW);
        if (view == null)
            throw new SetupException("Filesystem does not support private permissions");
        AclEntry.Builder entry =
                AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(Files.getOwner(path, NOFOLLOW))
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (Files.isDirectory(path, NOFOLLOW))
            entry.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
        view.setAcl(List.of(entry.build()));
        String grant =
                Files.getOwner(path, NOFOLLOW).getName()
                        + (Files.isDirectory(path, NOFOLLOW) ? ":(OI)(CI)F" : ":F");
        Process process =
                new ProcessBuilder(
                                Path.of(System.getenv("SystemRoot"), "System32", "icacls.exe")
                                        .toString(),
                                path.toString(),
                                "/inheritance:r",
                                "/grant:r",
                                grant)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
        try {
            if (process.waitFor() != 0)
                throw new SetupException("Could not protect Windows permissions");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SetupException("Permission setup interrupted");
        }
    }

    private static void owner(Path path, int uid, int gid) throws IOException {
        Files.setAttribute(path, "unix:uid", uid, NOFOLLOW);
        Files.setAttribute(path, "unix:gid", gid, NOFOLLOW);
    }

    private static String yaml(String value) {
        return "'" + value.replace("'", "''").replace("$", "$$") + "'";
    }

    private static String jdbcExample(String vendor) {
        return switch (vendor) {
            case "mysql" -> "jdbc:mysql://db-host:3306/ijob?serverTimezone=UTC";
            case "postgresql" -> "jdbc:postgresql://db-host:5432/ijob";
            case "sqlserver" -> "jdbc:sqlserver://db-host:1433;databaseName=ijob;encrypt=true";
            case "oracle" -> "jdbc:oracle:thin:@//db-host:1521/service_name";
            default -> throw new IllegalArgumentException("Unsupported server database");
        };
    }

    private static String compose(Path home, String vendor) {
        String config = yaml(home.resolve("application.properties").toString());
        String runtime = yaml(home.resolve("runtime").toString());
        return """
               name: ijob-setup-%s
               services:
                 backend:
                   image: ijob-backend:1.0.0
                   environment:
                     IJOB_PLATFORM_DB: %s
                     SPRING_PROFILES_ACTIVE: release
                     SPRING_CONFIG_ADDITIONAL_LOCATION: file:/run/secrets/application.properties
                   volumes:
                     - type: bind
                       source: %s
                       target: /run/secrets/application.properties
                       read_only: true
                     - type: bind
                       source: %s
                       target: /var/lib/ijob
                   read_only: true
                   tmpfs: ['/tmp:size=256m,mode=0700,uid=10001,gid=10001,exec']
                   cap_drop: [ALL]
                   security_opt: ['no-new-privileges:true']
                   init: true
                   restart: unless-stopped
                   stop_grace_period: 120s
                   mem_limit: 1g
                   cpus: 2.0
                   logging:
                     driver: json-file
                     options: {max-size: 10m, max-file: '5'}
                 web:
                   image: ijob-web:1.0.0
                   ports: ['127.0.0.1:${IJOB_HTTP_PORT:-18080}:8080']
                   depends_on:
                     backend: {condition: service_healthy}
                   read_only: true
                   tmpfs: ['/tmp:size=128m,mode=1777']
                   cap_drop: [ALL]
                   security_opt: ['no-new-privileges:true']
                   restart: unless-stopped
                   mem_limit: 128m
                   logging:
                     driver: json-file
                     options: {max-size: 10m, max-file: '5'}
               """
                .formatted(
                        Integer.toUnsignedString(home.toString().hashCode(), 16),
                        vendor,
                        config,
                        runtime);
    }

    private static final class SetupException extends IOException {
        SetupException(String message) {
            super(message);
        }
    }
}
