package com.logilink.emailanalyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EmailAnalyzerApplication {

    private static final Logger log = LoggerFactory.getLogger(EmailAnalyzerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(EmailAnalyzerApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            log.info("✅ E-posta analizi başlatıldı – Ollama + 1080 Ti aktif");
        };
    }
}
