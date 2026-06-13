package ch.swissqcommerce.backend.domain.agent.adapter.out.pii;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Java-side PII pre-scan — the fail-safe gate that keeps PII-bearing prompts out of any cloud LLM
 * even when the Python governance service is down (ADR-007 #2).
 *
 * <p>The regex set is a 1:1 port of the shared Python patterns in {@code
 * homelab-ai-governance/src/governance/guardrails/pii_patterns.py}, which is the single source of
 * truth for the governed pre-route PII gate. When that file changes, mirror the change here so the
 * Java fail-safe stays aligned with the governed behaviour ("PII never leaves the homelab").
 */
@Component
public class PiiPreScanner {

    // Ported from governance.guardrails.pii_patterns.PII_PATTERNS (order mirrors source).
    private static final Pattern[] PII_PATTERNS = {
        // CONNECTION_STRING
        Pattern.compile("(?i)(postgres|mysql|mongodb|redis)://[^\\s]+"),
        // API_KEY / secret / token / password assignments
        Pattern.compile(
                "(?i)(api[_-]?key|secret|token|password)\\s*[:=]\\s*[\"']?[a-zA-Z0-9_\\-]{16,}"),
        // CREDIT_CARD
        Pattern.compile("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b"),
        // SSN
        Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
        // EMAIL
        Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
        // PHONE_NUMBER
        Pattern.compile(
                "\\+?[-.\\s]?\\(?\\d{1,4}\\)?[-.\\s]?\\d{1,4}[-.\\s]?\\d{2,4}[-.\\s]?\\d{2,4}|\\b\\d{3}-\\d{3}-\\d{4}\\b"),
        // IP_ADDRESS
        Pattern.compile(
                "\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b"),
    };

    /**
     * @return {@code true} if the text contains any PII that must not leave the homelab.
     */
    public boolean containsPii(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (Pattern pattern : PII_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
