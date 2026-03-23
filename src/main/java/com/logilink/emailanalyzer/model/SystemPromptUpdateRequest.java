package com.logilink.emailanalyzer.model;

/**
 * Body for test API that updates only {@code app_settings.system_prompt} on the singleton row.
 */
public record SystemPromptUpdateRequest(String systemPrompt) {
}
