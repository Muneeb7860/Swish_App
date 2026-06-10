package ch.swissqcommerce.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // activates @PreAuthorize / @PostAuthorize on @Bean-managed methods
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OpaAuthorizationManager opaAuthorizationManager;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          OpaAuthorizationManager opaAuthorizationManager) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.opaAuthorizationManager = opaAuthorizationManager;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // CSRF is unnecessary for a stateless JWT API: the JWT lives in the
            // Authorization header (not a cookie), so browsers cannot be tricked
            // into submitting credentials cross-site.  Keeping CSRF here would
            // cause every unauthenticated POST/PUT/DELETE to return 403 (CSRF
            // failure) instead of the correct 401 (unauthenticated), which breaks
            // the E2E suite's auth-guard assertions.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Return 401 (not Spring's default 403) when an anonymous user hits a
            // protected endpoint.  Authenticated users who are denied get 403 via
            // Spring Security's default AccessDeniedHandlerImpl (sendError(403)).
            // NOTE: /error MUST be in permitAll so Tomcat's error dispatch (which
            // re-enters the filter chain without a JWT) reaches BasicErrorController
            // and returns the original 403 body rather than triggering a second
            // security denial that would overwrite the status with 401.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .authorizeHttpRequests(auth -> auth
                // Open endpoints — no token needed
                .requestMatchers(
                    "/api/auth/**", "/api/v1/auth/**",
                    "/actuator/health", "/actuator/prometheus",   // prometheus: scraped without auth
                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                    "/error"   // Spring Boot error dispatch — must be open for accessDeniedHandler to work
                ).permitAll()
                .requestMatchers("/api/**").access(opaAuthorizationManager)
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOriginPatterns(java.util.Arrays.asList("http://localhost:[*]", "https://*.swissqcommerce.ch"));
        configuration.setAllowedMethods(java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(java.util.Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "X-XSRF-TOKEN", "X-Session-Id"));
        configuration.setExposedHeaders(java.util.Arrays.asList("X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
