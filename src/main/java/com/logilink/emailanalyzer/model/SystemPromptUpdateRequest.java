package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body for test API that updates only {@code app_settings.system_prompt} on the singleton row.
 */
public record SystemPromptUpdateRequest(
        @JsonProperty("system_prompt") @JsonAlias("systemPrompt") String systemPrompt) {
}
