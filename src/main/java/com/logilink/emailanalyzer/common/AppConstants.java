package com.logilink.emailanalyzer.common;

/**
 * Shared application constants used by multiple layers.
 */
public final class AppConstants {

  private AppConstants() {
  }

  public static final class Defaults {
    public static final String DEFAULT_MAIL_HOST = "imap.gmail.com";
    public static final int DEFAULT_MAIL_PORT = 993;
    public static final String DEFAULT_MAIL_USERNAME = "logilinklojistik@gmail.com";
    public static final boolean DEFAULT_MAIL_SSL_ENABLED = true;
    public static final String DEFAULT_LLM_URL = "http://localhost:11434";
    public static final double DEFAULT_LLM_TEMPERATURE = 0.3;
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
    public static final String AI_ANALYSIS_FAILED = "AI Analysis failed";
    public static final String AI_MODEL_TEST_FAILED = "AI model test failed";
    public static final String SETTINGS_NOT_FOUND =
        "Settings not found. Please configure /settings first.";
    public static final String EMAIL_SETTINGS_MISSING =
        "Email server settings are not configured. Please update them on /settings.";
    public static final String SYSTEM_PROMPT_MISSING =
        "System prompt is not configured. Please update it on /settings.";
    public static final String INVALID_DATE_RANGE = "Invalid date range for email search.";
    public static final String EMAIL_FETCH_FAILED = "Failed to fetch emails within range";
    public static final String CRON_REQUIRED = "Cron expression cannot be empty.";
    public static final String CRON_INVALID =
        "Invalid cron expression. Use 6-field Spring cron format.";

    private Messages() {
    }
  }
}
