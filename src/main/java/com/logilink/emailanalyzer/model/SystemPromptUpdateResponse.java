package com.logilink.emailanalyzer.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SystemPromptUpdateResponse {

    private boolean success;
    private String message;
    private Long settingsId;
    private int systemPromptLength;
}
