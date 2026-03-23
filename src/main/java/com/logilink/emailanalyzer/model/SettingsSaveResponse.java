package com.logilink.emailanalyzer.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SettingsSaveResponse {

    private boolean success;
    private String message;
    private boolean schedulerRunning;
    private List<ApiValidationError> errors = new ArrayList<>();
}
