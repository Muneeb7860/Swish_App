package ch.swissqcommerce.backend.config;

import java.util.HashMap;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Aspect for capturing security-relevant event execution telemetry and appending standard immutable
 * records to the outbox event table.
 */
@Aspect
@Component
public class SecurityAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditAspect.class);
    private final SecurityOutboxWriter securityOutboxWriter;

    @Autowired
    public SecurityAuditAspect(SecurityOutboxWriter securityOutboxWriter) {
        this.securityOutboxWriter = securityOutboxWriter;
    }

    @Around("@annotation(securityAudit)")
    public Object audit(ProceedingJoinPoint joinPoint, SecurityAudit securityAudit)
            throws Throwable {
        String actionName = securityAudit.action();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (authentication != null) ? authentication.getName() : "system";

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("action", actionName);
        auditDetails.put("method", methodName);
        auditDetails.put("operator", username);
        auditDetails.put("timestamp", java.time.Instant.now().toString());

        // Extract parameters safely
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        Map<String, Object> params = new HashMap<>();
        if (parameterNames != null && args != null) {
            for (int i = 0; i < Math.min(parameterNames.length, args.length); i++) {
                if (args[i] != null
                        && !args[i].getClass().getSimpleName().contains("HttpServlet")) {
                    params.put(parameterNames[i], args[i].toString());
                }
            }
        }
        auditDetails.put("parameters", params);

        Object result = null;
        try {
            result = joinPoint.proceed();
            auditDetails.put("status", "SUCCESS");
            securityOutboxWriter.writeToOutbox(actionName, username, auditDetails);
            return result;
        } catch (Throwable throwable) {
            auditDetails.put("status", "FAILED");
            auditDetails.put("error", throwable.getMessage());
            securityOutboxWriter.writeToOutbox("security.anomaly", username, auditDetails);
            throw throwable;
        }
    }
}
