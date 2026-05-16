package com.example.polyusigwebsite.security;

import com.example.polyusigwebsite.repository.UserAccountRepository;
import com.example.polyusigwebsite.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

/**
 * Ensures persisted authorities match DB roles ({@link com.example.polyusigwebsite.entity.RoleType}) —
 * required for {@code /api/admin/**}.
 * Plain OAuth logins retain IdP-backed {@link OAuth2User} principals that usually carry generic scopes
 * (e.g. {@code ROLE_USER}) only, while {@link com.example.polyusigwebsite.service.AuthService#currentUser}
 * resolves admin from DB, masking the mismatch until an admin-only API rejects the request with 403.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final UserAccountRepository userAccountRepository;

    public OAuth2LoginSuccessHandler(AuthService authService, UserAccountRepository userAccountRepository) {
        this.authService = authService;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (authentication instanceof OAuth2AuthenticationToken token) {
            OAuth2User user = token.getPrincipal();
            String email = user.getAttribute("email") instanceof String es ? es : null;
            String name = user.getAttribute("name") instanceof String ns ? ns : null;
            String id = user.getName();
            authService.processOAuthLogin(email, name, token.getAuthorizedClientRegistrationId(), id);

            if (email != null && !email.isBlank()) {
                userAccountRepository
                        .findByEmail(email.trim().toLowerCase(Locale.ROOT))
                        .map(CustomUserPrincipal::new)
                        .ifPresent(principal -> {
                            UsernamePasswordAuthenticationToken sessionAuth = new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    principal.getAuthorities());
                            SecurityContextHolder.getContext().setAuthentication(sessionAuth);
                            HttpSession session = request.getSession(false);
                            if (session != null) {
                                session.setAttribute(
                                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                                        SecurityContextHolder.getContext());
                            }
                        });
            }
        }
        response.sendRedirect("http://localhost:5173/auth/callback");
    }
}
