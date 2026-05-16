package com.example.polyusigwebsite.config;

import com.example.polyusigwebsite.security.OAuth2DatabaseRoleMergeFilter;
import com.example.polyusigwebsite.security.SimpleRateLimitFilter;
import com.example.polyusigwebsite.security.OAuth2LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SimpleRateLimitFilter rateLimitFilter,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
            OAuth2DatabaseRoleMergeFilter oauth2DatabaseRoleMergeFilter
    ) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/password-reset/**",
                                "/api/auth/oauth2/url",
                                "/api/auth/logout",
                                "/api/auth/approve",
                                "/api/auth/role",
                                "/api/files/upload",
                                "/api/files/**",
                                "/api/folders/**",
                                "/api/posts/**",
                                "/api/join/**",
                                "/api/admin/join/**"
                        )
                )
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'"))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error", "/actuator/health").permitAll()
                        .requestMatchers("/login/**", "/oauth2/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/password-reset/**", "/api/auth/oauth2/url", "/api/auth/csrf", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers("/api/posts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/search/**").permitAll()
                        /** Public-ish read for resources; access still enforced in {@link ResourceFileService}. */
                        .requestMatchers(HttpMethod.GET, "/api/files/download-zip").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/files").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/files/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/*/download").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/*/preview").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/*/archive-list").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/*/archive-entry").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/favourites", "/api/files/favourites/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/files/*").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"status\":403,\"message\":\"Access denied\"}");
                        })
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**")
                        )
                )
                .oauth2Login(oauth2 -> oauth2.successHandler(oAuth2LoginSuccessHandler))
                .oauth2Client(Customizer.withDefaults())
                .logout(logout -> logout.logoutUrl("/api/auth/logout"))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterAfter(oauth2DatabaseRoleMergeFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
