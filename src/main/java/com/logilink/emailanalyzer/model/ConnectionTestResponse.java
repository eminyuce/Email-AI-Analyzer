package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConnectionTestResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("smtp_host")
    @JsonAlias("smtpHost")
    private String smtpHost;

    @JsonProperty("smtp_port")
    @JsonAlias("smtpPort")
    private Integer smtpPort;

    @JsonProperty("status_code")
    @JsonAlias("statusCode")
    private Integer statusCode;

    @JsonProperty("model")
    private String model;
}
