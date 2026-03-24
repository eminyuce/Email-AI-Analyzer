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
          PasswordEncoder passwordEncoder) {
    if (logCredentialsOnStartup) {
      log.warn(
          "SECURITY_LOG_CREDENTIALS_ON_STARTUP is true: emitting SECURITY_USER and SECURITY_PASS as "
              + "configured in the process environment (for example from GitHub Actions secrets passed into "
              + "the container). Disable this immediately after debugging; plaintext credentials in logs are a "
              + "security risk. SECURITY_USER=[{}], SECURITY_PASS=[{}]",
          username,
          password);
    } else {
      log.info(
          "Security user configured for form login and actuator. configuredUsername={}. "
              + "SECURITY_PASS is not logged. To print both for debugging, set "
              + "SECURITY_LOG_CREDENTIALS_ON_STARTUP=true in the environment.",
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
