package com.logilink.emailanalyzer.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;

/**
 * Logs why form login failed (without logging the password) and redirects with a {@code reason}
 * query parameter so the login page can show whether the username or password was wrong.
 */
public class LoginAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginAuthenticationFailureHandler.class);

    public LoginAuthenticationFailureHandler() {
        super("/login?error");
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {

        String attemptedUsername = request.getParameter("username");
        if (attemptedUsername != null) {
            attemptedUsername = attemptedUsername.trim();
        }
        if (attemptedUsername == null || attemptedUsername.isEmpty()) {
            attemptedUsername = "(empty or missing)";
        }

        String redirectBase = request.getContextPath() + "/login?error";

        if (exception instanceof UsernameNotFoundException) {
            log.warn(
                    "Login failed: username not found (no such user). attemptedUsername={}",
                    attemptedUsername);
            getRedirectStrategy().sendRedirect(request, response, redirectBase + "&reason=user_unknown");
            return;
        }

        if (exception instanceof BadCredentialsException) {
            log.warn(
                    "Login failed: bad password (username exists but password did not match). "
                            + "attemptedUsername={}",
                    attemptedUsername);
            getRedirectStrategy().sendRedirect(request, response, redirectBase + "&reason=bad_password");
            return;
        }

        log.warn(
                "Login failed: {}. attemptedUsername={}",
                exception.getClass().getSimpleName(),
                attemptedUsername,
                exception);
        getRedirectStrategy().sendRedirect(request, response, redirectBase + "&reason=other");
    }
}
