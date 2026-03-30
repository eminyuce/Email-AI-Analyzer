package com.logilink.emailanalyzer;

import com.logilink.emailanalyzer.config.AppSecretsDebugProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

@SpringBootApplication
public class EmailAnalyzerApplication {

    private static final Logger log = LoggerFactory.getLogger(EmailAnalyzerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(EmailAnalyzerApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(AppSecretsDebugProperties secretsDebug) {
        return args -> {
            log.info("✅ E-posta analizi başlatıldı – Ollama + 1080 Ti aktif");

            YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
            yamlFactory.setResources(new ClassPathResource("application.yml"));
            Properties properties = yamlFactory.getObject();

            if (properties == null || properties.isEmpty()) {
                log.warn("application.yml dosyasından ayar okunamadı.");
                System.out.println("application.yml dosyasından ayar okunamadı.");
                return;
            }

            List<String> keys = new ArrayList<>(properties.stringPropertyNames());
            keys.sort(String::compareTo);

            log.info("---- application.yml değerleri ----");
            System.out.println("---- application.yml değerleri ----");

            boolean revealSecrets = secretsDebug.isDebugLogSecrets();
            if (revealSecrets) {
                log.warn(
                        "app.debug-log-secrets is enabled: printing resolved application.yml entries including "
                                + "sensitive keys. Disable DEBUG_LOG_SECRETS as soon as you finish troubleshooting."
                );
            }

            for (String key : keys) {
                String value = properties.getProperty(key);
                boolean mask = !revealSecrets && isSensitiveKey(key);
                String safeValue = mask ? "******" : Objects.toString(value, "");
                String message = key + " = " + safeValue;
                log.info(message);
                System.out.println(message);
            }

            log.info("---- application.yml değerleri sonu ----");
            System.out.println("---- application.yml değerleri sonu ----");
        };
    }

    private static boolean isSensitiveKey(String key) {
        String lowerCase = key.toLowerCase();
        return lowerCase.contains("password")
                || lowerCase.contains("secret")
                || lowerCase.contains("token")
                || lowerCase.contains("api-key")
                || lowerCase.contains("apikey")
                || lowerCase.contains("groq.api");
    }
}
