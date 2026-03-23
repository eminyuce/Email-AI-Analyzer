package com.logilink.emailanalyzer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
          PasswordEncoder passwordEncoder
  ) {
    UserDetails actuatorAdmin = User.builder()
            .username(username)
            .password(passwordEncoder.encode(password))
            .roles("ADMIN")
            .build();

    return new InMemoryUserDetailsManager(actuatorAdmin);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
