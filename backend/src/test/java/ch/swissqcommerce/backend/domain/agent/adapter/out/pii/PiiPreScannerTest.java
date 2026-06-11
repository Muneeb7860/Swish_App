package ch.swissqcommerce.backend.domain.agent.adapter.out.pii;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the Java fail-safe PII scanner matches the categories of the shared
 * Python pattern set (homelab-ai-governance/.../pii_patterns.py).
 */
public class PiiPreScannerTest {

    private final PiiPreScanner scanner = new PiiPreScanner();

    @Test
    public void detectsEmail() {
        assertTrue(scanner.containsPii("reach me at jane.doe@example.com please"));
    }

    @Test
    public void detectsSsn() {
        assertTrue(scanner.containsPii("SSN is 123-45-6789"));
    }

    @Test
    public void detectsCreditCard() {
        assertTrue(scanner.containsPii("card 4111 1111 1111 1111"));
    }

    @Test
    public void detectsIpAddress() {
        assertTrue(scanner.containsPii("server at 192.168.0.1"));
    }

    @Test
    public void detectsConnectionString() {
        assertTrue(scanner.containsPii("db is postgres://user:pass@host:5432/db"));
    }

    @Test
    public void detectsApiKeyAssignment() {
        assertTrue(scanner.containsPii("api_key=ABCDEF0123456789XYZ"));
    }

    @Test
    public void cleanBusinessPrompt_hasNoPii() {
        assertFalse(scanner.containsPii(
                "Summarise the weather impact on delivery demand for zone 4."));
        assertFalse(scanner.containsPii("Recommend a surge multiplier between 1.0 and 3.0."));
    }

    @Test
    public void nullOrEmpty_hasNoPii() {
        assertFalse(scanner.containsPii(null));
        assertFalse(scanner.containsPii(""));
    }
}
