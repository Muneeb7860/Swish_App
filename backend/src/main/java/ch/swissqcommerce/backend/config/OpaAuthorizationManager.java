package ch.swissqcommerce.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Dynamic policy enforcement manager. Queries a local Open Policy Agent (OPA)
 * daemon for request decisions with structured data. Falls back to static
 * role-matching rules on connection failures.
 */
@Component
public class OpaAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Logger log = LoggerFactory.getLogger(OpaAuthorizationManager.class);

    @Value("${opa.client.enabled:false}")
    private boolean opaEnabled;

    @Value("${opa.client.url:http://localhost:8181/v1/data/swish/authz}")
    private String opaUrl;

    private final RestTemplate restTemplate;

    public OpaAuthorizationManager(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(300))
                .setReadTimeout(Duration.ofMillis(300))
                .build();
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authenticationSupplier, RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();
        // AnonymousAuthenticationToken reports isAuthenticated()==true, so reject it
        // explicitly — otherwise requests with no JWT pass this guard and get
        // role-matched as ROLE_ANONYMOUS (the open /api/rider/onboard rule would
        // then admit unauthenticated callers).
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return new AuthorizationDecision(false);
        }

        String uri = context.getRequest().getRequestURI();
        String method = context.getRequest().getMethod();
        String username = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // Skip auth checks for open endpoints
        if (uri.startsWith("/api/auth/") || uri.startsWith("/v3/api-docs") || uri.startsWith("/swagger-ui") || uri.equals("/actuator/health")) {
            return new AuthorizationDecision(true);
        }

        if (opaEnabled) {
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("uri", uri);
                input.put("method", method);
                input.put("username", username);
                input.put("roles", roles);

                Map<String, Object> payload = new HashMap<>();
                payload.put("input", input);

                ResponseEntity<Map> response = restTemplate.postForEntity(opaUrl, payload, Map.class);
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Map resultContainer = (Map) response.getBody().get("result");
                    if (resultContainer != null && Boolean.TRUE.equals(resultContainer.get("allow"))) {
                        log.debug("OPA Authz [ALLOW]: user={}, uri={}, method={}", username, uri, method);
                        return new AuthorizationDecision(true);
                    }
                }
                log.warn("OPA Authz [DENY]: user={}, uri={}, method={}", username, uri, method);
                return new AuthorizationDecision(false);
            } catch (Exception e) {
                log.warn("OPA Connection failed: {}. Falling back to Spring Security static rules.", e.getMessage());
            }
        }

        // Fallback static rules if OPA is disabled or unreachable
        return evaluateFallbackRules(uri, roles);
    }

    private AuthorizationDecision evaluateFallbackRules(String uri, List<String> roles) {
        // Admin-only paths
        if (uri.startsWith("/api/admin") || uri.startsWith("/api/security")) {
            boolean granted = roles.contains("ROLE_ADMIN");
            if (!granted) log.warn("OPA Fallback [DENY - admin-only path]: uri={}, roles={}", uri, roles);
            return new AuthorizationDecision(granted);
        }
        // Customer + admin paths — note: no trailing slash so /api/orders (list)
        // and /api/orders/{id} (detail) are both matched correctly.
        if (uri.startsWith("/api/orders") || uri.startsWith("/api/customer")
                || uri.startsWith("/api/payments")) {
            return new AuthorizationDecision(roles.contains("ROLE_CUSTOMER") || roles.contains("ROLE_ADMIN"));
        }
        // Onboarding gate approval is admin-only (matches @PreAuthorize on the
        // controller) — must be checked BEFORE the open onboarding rule below,
        // which would otherwise match it by prefix.
        if (uri.startsWith("/api/rider/onboard") && uri.endsWith("/approve")) {
            return new AuthorizationDecision(roles.contains("ROLE_ADMIN"));
        }
        // Rider onboarding is open to any authenticated user — a customer applies
        // to become a rider before they hold ROLE_RIDER.
        if (uri.startsWith("/api/rider/onboard")) {
            return new AuthorizationDecision(true);
        }
        // All other rider paths require ROLE_RIDER or ROLE_ADMIN.
        if (uri.startsWith("/api/rider")) {
            return new AuthorizationDecision(roles.contains("ROLE_RIDER") || roles.contains("ROLE_ADMIN"));
        }
        // Picker/inventory paths — ROLE_PICKER or ROLE_ADMIN only
        if (uri.startsWith("/api/inventory")) {
            return new AuthorizationDecision(roles.contains("ROLE_PICKER") || roles.contains("ROLE_ADMIN"));
        }
        // Wholesaler B2B paths — ROLE_WHOLESALER or ROLE_ADMIN only
        if (uri.startsWith("/api/wholesaler")) {
            return new AuthorizationDecision(roles.contains("ROLE_WHOLESALER") || roles.contains("ROLE_ADMIN"));
        }
        // Device/cold-chain telemetry — riders post ticks, admins inspect
        if (uri.startsWith("/api/telemetry")) {
            return new AuthorizationDecision(roles.contains("ROLE_RIDER") || roles.contains("ROLE_ADMIN"));
        }
        // Any path without an explicit rule (e.g. /api/ai, /api/dispatch,
        // /api/governance/hitl, /api/ledger, /api/v1/* domain APIs) is admin-only
        // rather than denied outright: non-admin access still fails closed, but
        // operators are not locked out of paths nobody remembered to list here.
        boolean adminOnly = roles.contains("ROLE_ADMIN");
        if (!adminOnly) log.warn("OPA Fallback [DENY - unmatched path]: uri={}, roles={}", uri, roles);
        return new AuthorizationDecision(adminOnly);
    }
}
