package com.logilink.emailanalyzer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * When true, the application may log plaintext secrets (API keys, passwords, etc.) for troubleshooting.
 * Enable only temporarily. Set environment variable {@code DEBUG_LOG_SECRETS} or {@code app.debug-log-secrets}.
 */
@Component
public class AppSecretsDebugProperties {

    private final boolean debugLogSecrets;

    public AppSecretsDebugProperties(@Value("${app.debug-log-secrets:false}") String raw) {
        this.debugLogSecrets = parseTruthy(raw);
    }

    private static boolean parseTruthy(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return "true".equalsIgnoreCase(raw.trim())
                || "1".equals(raw.trim())
                || "yes".equalsIgnoreCase(raw.trim());
    }

    public boolean isDebugLogSecrets() {
        return debugLogSecrets;
    }
}
