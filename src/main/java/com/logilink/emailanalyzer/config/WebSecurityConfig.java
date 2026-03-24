package com.logilink.emailanalyzer.config;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http, LoginAuthenticationFailureHandler loginAuthenticationFailureHandler)
            throws Exception {
        http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(PathRequest.toStaticResources().atCommonLocations())
                                        .permitAll()
                                        .requestMatchers("/login", "/error")
                                        .permitAll()
                                        .anyRequest()
                                        .hasRole("ADMIN"))
                .formLogin(
                        form ->
                                form.loginPage("/login")
                                        .defaultSuccessUrl("/emails", true)
                                        .failureHandler(loginAuthenticationFailureHandler)
                                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public LoginAuthenticationFailureHandler loginAuthenticationFailureHandler() {
        return new LoginAuthenticationFailureHandler();
    }
}
