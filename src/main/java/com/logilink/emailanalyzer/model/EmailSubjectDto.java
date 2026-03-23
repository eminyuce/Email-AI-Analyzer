package com.logilink.emailanalyzer.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailSubjectDto {

    private String subject;
    private String receivedAt;
    private String from;
}
