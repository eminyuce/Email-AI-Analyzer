package com.logilink.emailanalyzer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ActuatorSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(ActuatorSecurityConfig.class);

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to("health")).permitAll()
                        .anyRequest().hasRole("ADMIN")
                )
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${SECURITY_USER:admin}") String username,
            @Value("${SECURITY_PASS:changeme}") String password,
            @Value("${SECURITY_LOG_CREDENTIALS_ON_STARTUP:false}") boolean logCredentialsOnStartup,
            AppSecretsDebugProperties secretsDebug,
            PasswordEncoder passwordEncoder) {
        boolean emitPlaintextCredentials = logCredentialsOnStartup || secretsDebug.isDebugLogSecrets();
        if (emitPlaintextCredentials) {
            log.warn(
                    "Plaintext security credentials logging is enabled (SECURITY_LOG_CREDENTIALS_ON_STARTUP and/or "
                            + "DEBUG_LOG_SECRETS / app.debug-log-secrets): SECURITY_USER=[{}], SECURITY_PASS=[{}]. "
                            + "Disable as soon as you finish debugging.",
                    username,
                    password);
        } else {
            log.info(
                    "Security user configured for form login and actuator. configuredUsername={}. "
                            + "SECURITY_PASS is not logged. For debugging only, set DEBUG_LOG_SECRETS=true or "
                            + "SECURITY_LOG_CREDENTIALS_ON_STARTUP=true.",
                    username);
        }

        UserDetails actuatorAdmin = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(actuatorAdmin);
    }

    /**
     * Single provider that does not hide {@link
     * org.springframework.security.core.userdetails.UsernameNotFoundException}, so form login can
     * tell "unknown user" apart from "wrong password" in logs and on the login page.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder);
        provider.setUserDetailsService(userDetailsService);
        provider.setHideUserNotFoundExceptions(false);
        return new ProviderManager(provider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
