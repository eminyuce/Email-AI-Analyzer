package com.logilink.emailanalyzer.common;

/**
 * Shared application constants used by multiple layers.
 */
public final class AppConstants {

  private AppConstants() {
  }

  public static final class Defaults {
    public static final String DEFAULT_SYSTEM_PROMPT_PATH = "system-prompt.txt";

    private Defaults() {
    }
  }

  public static final class Metrics {
    public static final String PROCESSING_LATENCY_METRIC = "analysis.processing.latency";
    public static final String ACTIVE_SESSIONS_METRIC = "ai.agent.sessions.active";
    public static final String TRANSACTIONS_FAILED_METRIC = "transactions.failed";
    public static final String ORDERS_ACTIVE_METRIC = "orders.active";
    public static final String ENV_TAG_KEY = "env";
    public static final String ENV_TAG_VALUE = "production";

    private Metrics() {
    }
  }

  public static final class Messages {
    public static final String SETTINGS_NOT_FOUND =
        "Settings not found. Please configure /settings first.";
    public static final String EMAIL_SETTINGS_MISSING =
        "Email server settings are not configured. Please update them on /settings.";
    public static final String SYSTEM_PROMPT_MISSING =
        "System prompt is not configured. Please update it on /settings.";
    public static final String CRON_REQUIRED = "Cron expression cannot be empty.";
    public static final String CRON_INVALID =
        "Invalid cron expression. Use 6-field Spring cron format.";

    private Messages() {
    }
  }
}
