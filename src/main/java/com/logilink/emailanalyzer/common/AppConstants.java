package com.logilink.emailanalyzer.common;

/**
 * Shared application constants used by multiple layers. Map lookup keys (e.g. JSON object keys in
 * {@code Map.get("key")}) remain inline at call sites per project convention.
 */
public final class AppConstants {

    private AppConstants() {
    }

    /**
     * Default classpath resources and file names.
     */
    public static final class Defaults {

        public static final String DEFAULT_SYSTEM_PROMPT_PATH = "system-prompt.txt";

        private Defaults() {
        }
    }

    /**
     * Micrometer metric names and common tag values.
     */
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

    /**
     * User-facing or log messages.
     */
    public static final class Messages {

        public static final String SETTINGS_NOT_FOUND =
                "Settings not found. Please configure /settings first.";
        public static final String EMAIL_SETTINGS_MISSING =
                "Email server settings are not configured. Please update them on /settings.";
        public static final String SYSTEM_PROMPT_MISSING =
                "System prompt is not configured. Please update it on /settings.";

        private Messages() {
        }
    }

    /**
     * Extra instructions appended to the system prompt for Groq JSON-only responses.
     */
    public static final class AiPrompts {

        public static final String GROQ_JSON_DISCIPLINE =
                """
                        Output rule: respond with one JSON object only (no markdown fences, no commentary), using the field names expected by EmailAnalysisResult (e.g. email_id, email_date, subject, sender, criticality_score, criticality_level, breakdown, summary, key_risks, affected_stakeholders, action_needed, recommended_action, estimated_response_time, confidence).
                        """;

        private AiPrompts() {
        }
    }

    /**
     * Analysis pipeline tuning (IMAP fetch is separate).
     */
    public static final class Analysis {

        /**
         * Upper bound on concurrent LLM calls during batch processing.
         */
        public static final int MAX_CONCURRENT_AI_REQUESTS = 10;

        /**
         * Micrometer tag names for {@link io.micrometer.core.instrument.Timer}.
         */
        public static final String METER_TAG_OPERATION = "operation";
        public static final String METER_TAG_OUTCOME = "outcome";
        public static final String METER_OUTCOME_SUCCESS = "success";
        public static final String METER_OUTCOME_ERROR = "error";

        /**
         * Timer operation label values.
         */
        public static final String TIMER_OP_PROCESS_EMAILS = "processEmails";
        public static final String TIMER_OP_PROCESS_EMAILS_BY_RANGE = "processEmailsByRange";

        private Analysis() {
        }
    }
}
