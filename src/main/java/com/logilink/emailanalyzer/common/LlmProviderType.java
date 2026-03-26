package com.logilink.emailanalyzer.common;

import org.apache.commons.lang3.StringUtils;

public enum LlmProviderType {
    OLLAMA,
    GROQ;

    public static LlmProviderType fromSettingsValue(String value) {
        if (StringUtils.isBlank(value)) {
            return OLLAMA;
        }
        return "groq".equalsIgnoreCase(value.trim()) ? GROQ : OLLAMA;
    }

    public String toSettingsValue() {
        return this == GROQ ? "groq" : "ollama";
    }
}
