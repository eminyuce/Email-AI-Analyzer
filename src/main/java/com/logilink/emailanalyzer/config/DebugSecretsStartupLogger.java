package com.logilink.emailanalyzer.config;

import com.logilink.emailanalyzer.domain.AppSettings;
import com.logilink.emailanalyzer.service.AppSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logs persisted application settings that contain credentials when {@link AppSecretsDebugProperties} is enabled.
 */
@Component
@Order(200)
public class DebugSecretsStartupLogger implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DebugSecretsStartupLogger.class);

    private final AppSecretsDebugProperties secretsDebug;
    private final AppSettingsService appSettingsService;

    public DebugSecretsStartupLogger(AppSecretsDebugProperties secretsDebug, AppSettingsService appSettingsService) {
        this.secretsDebug = secretsDebug;
        this.appSettingsService = appSettingsService;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(String... args) {
        if (!secretsDebug.isDebugLogSecrets()) {
            return;
        }
        AppSettings s = appSettingsService.getOrCreate();
        log.warn(
                "DEBUG_LOG_SECRETS: active settings profile id={}: mailUsername=[{}], mailPassword=[{}], "
                        + "dbHost=[{}], dbPort=[{}], dbName=[{}], dbUsername=[{}], dbPassword=[{}]",
                s.getId(),
                s.getMailUsername(),
                s.getMailPassword(),
                s.getDbHost(),
                s.getDbPort(),
                s.getDbName(),
                s.getDbUsername(),
                s.getDbPassword()
        );
    }
}
