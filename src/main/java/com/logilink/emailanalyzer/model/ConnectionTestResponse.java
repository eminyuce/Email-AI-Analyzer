package com.logilink.emailanalyzer.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConnectionTestResponse {

    private boolean success;
    private String message;
    private String smtpHost;
    private Integer smtpPort;
    private Integer statusCode;
    private String model;
}
