package io.github.lehamohan.payments.unit;

import io.github.lehamohan.payments.config.ConfigLoader;
import io.github.lehamohan.payments.config.FrameworkConfig;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigLoaderTest {

    @Test(groups = {"unit", "regression"})
    public void defaultsComeFromClasspathFile() {
        FrameworkConfig config = new ConfigLoader(Map.of(), new Properties()).load();

        assertThat(config.useEmbeddedSimulator()).isTrue();
        assertThat(config.maxAttempts()).isEqualTo(3);
    }

    @Test(groups = {"unit", "regression"})
    public void environmentVariableOverridesFile() {
        FrameworkConfig config = new ConfigLoader(
                Map.of("PAYMENTS_HTTP_READ_TIMEOUT_MS", "9000"), new Properties()).load();

        assertThat(config.readTimeoutMs()).isEqualTo(9000);
    }

    @Test(groups = {"unit", "regression"})
    public void systemPropertyOverridesEnvironment() {
        Properties sys = new Properties();
        sys.setProperty(ConfigLoader.MAX_ATTEMPTS, "5");

        FrameworkConfig config = new ConfigLoader(Map.of("PAYMENTS_RETRY_MAX_ATTEMPTS", "2"), sys).load();

        assertThat(config.maxAttempts()).isEqualTo(5);
    }

    @Test(groups = {"unit", "regression"})
    public void toStringNeverRevealsToken() {
        Properties sys = new Properties();
        sys.setProperty(ConfigLoader.API_TOKEN, "super-sensitive-value");

        FrameworkConfig config = new ConfigLoader(Map.of(), sys).load();

        assertThat(config.toString()).doesNotContain("super-sensitive-value").contains("apiToken=****");
    }

    @Test(groups = {"unit", "regression"})
    public void invalidValuesFailFast() {
        Properties sys = new Properties();
        sys.setProperty(ConfigLoader.MAX_ATTEMPTS, "0");

        assertThatThrownBy(() -> new ConfigLoader(Map.of(), sys).load())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }
}
