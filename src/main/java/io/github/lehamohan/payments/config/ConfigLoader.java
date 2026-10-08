package io.github.lehamohan.payments.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Resolves {@link FrameworkConfig} from layered sources. Later layers win:
 *
 * <ol>
 *   <li>Classpath defaults: {@code config/default.properties}</li>
 *   <li>Optional local file: {@code config.properties} in the working directory (git-ignored),
 *       or the path given by {@code -Dconfig.file=...}</li>
 *   <li>Environment variables: key upper-cased, dots replaced by underscores
 *       ({@code payments.api.token} -&gt; {@code PAYMENTS_API_TOKEN})</li>
 *   <li>JVM system properties: {@code -Dpayments.api.token=...}</li>
 * </ol>
 *
 * <p>Secrets are expected to come from layer 3 (CI secret store) and are never committed.
 */
public final class ConfigLoader {

    static final String DEFAULTS_RESOURCE = "config/default.properties";
    static final String LOCAL_FILE = "config.properties";

    public static final String BASE_URL = "payments.base.url";
    public static final String API_TOKEN = "payments.api.token";
    public static final String CONNECT_TIMEOUT = "payments.http.connect.timeout.ms";
    public static final String READ_TIMEOUT = "payments.http.read.timeout.ms";
    public static final String MAX_ATTEMPTS = "payments.retry.max.attempts";
    public static final String RETRY_BACKOFF = "payments.retry.backoff.ms";
    public static final String LOG_BODIES = "payments.log.bodies";

    private final Map<String, String> env;
    private final Properties systemProperties;

    public ConfigLoader() {
        this(System.getenv(), System.getProperties());
    }

    /** Visible for tests: inject environment and system properties. */
    public ConfigLoader(Map<String, String> env, Properties systemProperties) {
        this.env = env;
        this.systemProperties = systemProperties;
    }

    public FrameworkConfig load() {
        Properties merged = new Properties();
        merged.putAll(readClasspath(DEFAULTS_RESOURCE));
        merged.putAll(readOptionalFile(Path.of(systemProperties.getProperty("config.file", LOCAL_FILE))));

        return new FrameworkConfig(
                resolve(merged, BASE_URL, FrameworkConfig.EMBEDDED),
                resolve(merged, API_TOKEN, ""),
                Integer.parseInt(resolve(merged, CONNECT_TIMEOUT, "2000")),
                Integer.parseInt(resolve(merged, READ_TIMEOUT, "5000")),
                Integer.parseInt(resolve(merged, MAX_ATTEMPTS, "3")),
                Long.parseLong(resolve(merged, RETRY_BACKOFF, "200")),
                Boolean.parseBoolean(resolve(merged, LOG_BODIES, "true")));
    }

    String resolve(Properties fileLayers, String key, String fallback) {
        String sys = systemProperties.getProperty(key);
        if (notBlank(sys)) {
            return sys.trim();
        }
        String fromEnv = env.get(toEnvKey(key));
        if (notBlank(fromEnv)) {
            return fromEnv.trim();
        }
        String fromFile = fileLayers.getProperty(key);
        if (notBlank(fromFile)) {
            return fromFile.trim();
        }
        return fallback;
    }

    static String toEnvKey(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static Properties readClasspath(String resource) {
        Properties props = new Properties();
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read " + resource, e);
        }
        return props;
    }

    private static Properties readOptionalFile(Path path) {
        Properties props = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to read " + path, e);
            }
        }
        return props;
    }
}
