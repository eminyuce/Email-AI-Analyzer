package com.logilink.emailanalyzer.client;

import com.logilink.emailanalyzer.model.GroqRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(contentType = "application/json")
public interface GroqHttpClient {

    @PostExchange("/chat/completions")
    String chat(@RequestBody GroqRequest request);
}
