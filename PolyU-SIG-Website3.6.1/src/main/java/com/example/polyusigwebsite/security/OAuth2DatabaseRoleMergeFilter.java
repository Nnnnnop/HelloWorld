package com.example.polyusigwebsite.security;

import com.example.polyusigwebsite.entity.UserAccount;
import com.example.polyusigwebsite.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/**
 * Replaces {@link OAuth2AuthenticationToken} (IdP scopes only) with {@link CustomUserPrincipal} from the DB
 * so {@code hasRole('ADMIN')} works for {@code /api/admin/**}. Resolution mirrors
 * {@link com.example.polyusigwebsite.service.impl.AuthServiceImpl#currentUser}: email claim, then
 * {@code principal.getName()} as username, then as {@code provider_user_id}.
 */
@Component
public class OAuth2DatabaseRoleMergeFilter extends OncePerRequestFilter {

    private final UserAccountRepository userAccountRepository;

    public OAuth2DatabaseRoleMergeFilter(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        SecurityContext ctx = SecurityContextHolder.getContext();
        Authentication auth = ctx.getAuthentication();
        if (auth instanceof OAuth2AuthenticationToken token && auth.isAuthenticated()) {
            resolveAccountForOAuth(token).ifPresent(user -> {
                CustomUserPrincipal principal = new CustomUserPrincipal(user);
                UsernamePasswordAuthenticationToken replacement = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities());
                replacement.setDetails(token.getDetails());
                ctx.setAuthentication(replacement);
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
                }
            });
        }
        filterChain.doFilter(request, response);
    }

    private Optional<UserAccount> resolveAccountForOAuth(OAuth2AuthenticationToken token) {
        OAuth2User principal = token.getPrincipal();
        String email = resolveEmail(principal);
        if (email != null && !email.isBlank()) {
            Optional<UserAccount> byEmail = userAccountRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }
        String key = principal.getName();
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        Optional<UserAccount> byUsername = userAccountRepository.findByUsername(key);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        return userAccountRepository.findByProviderUserId(key);
    }

    /** Best-effort: claim shapes vary by IdP. */
    private static String resolveEmail(OAuth2User oauthUser) {
        String fromEmail = coerceEmailString(oauthUser.getAttribute("email"));
        if (fromEmail != null) {
            return fromEmail;
        }
        String preferred = coerceEmailString(oauthUser.getAttribute("preferred_username"));
        if (preferred != null && preferred.contains("@")) {
            return preferred;
        }
        String upn = coerceEmailString(oauthUser.getAttribute("upn"));
        if (upn != null && upn.contains("@")) {
            return upn;
        }
        return null;
    }

    private static String coerceEmailString(Object raw) {
        if (raw instanceof String s && !s.isBlank()) {
            return s;
        }
        if (raw instanceof Iterable<?> iter) {
            for (Object o : iter) {
                if (o instanceof String s && !s.isBlank() && s.contains("@")) {
                    return s;
                }
            }
        }
        return null;
    }
}
