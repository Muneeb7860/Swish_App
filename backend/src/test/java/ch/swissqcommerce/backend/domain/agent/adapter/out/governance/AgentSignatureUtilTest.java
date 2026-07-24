package ch.swissqcommerce.backend.domain.agent.adapter.out.governance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Interop-critical: pins EXACT reference strings computed independently via Python (json.dumps(...,
 * sort_keys=True, separators=(",",":")) and hmac.new(..., hashlib.sha256)) so any drift in
 * canonicalization or HMAC computation between the Java and Python sides is caught immediately,
 * rather than surfacing as every signed request silently failing verification in production.
 */
public class AgentSignatureUtilTest {

    @Test
    public void canonicalize_matchesPythonReferenceStrings() throws Exception {
        // python3 -c "import json; print(json.dumps({'query':'test prompt'},
        //   sort_keys=True, separators=(',',':')))"
        Map<String, Object> p1 = new LinkedHashMap<>();
        p1.put("query", "test prompt");
        assertEquals("{\"query\":\"test prompt\"}", AgentSignatureUtil.canonicalize(p1));

        Map<String, Object> p2 = new LinkedHashMap<>();
        p2.put("session_id", "abc-123"); // inserted out of alphabetical order
        p2.put("query", "test prompt");
        assertEquals(
                "{\"query\":\"test prompt\",\"session_id\":\"abc-123\"}",
                AgentSignatureUtil.canonicalize(p2));

        Map<String, Object> p3 = new LinkedHashMap<>();
        p3.put("query", "test prompt");
        p3.put("expected_format", "json");
        assertEquals(
                "{\"expected_format\":\"json\",\"query\":\"test prompt\"}",
                AgentSignatureUtil.canonicalize(p3));
    }

    @Test
    public void hmacSha256Hex_matchesPythonReferenceSignature() throws Exception {
        // Fixed-input reference computed via Python's hmac/hashlib (same
        // agent_id, secret, timestamp, nonce, payload as below):
        //   agent_id = "agent-java-test"
        //   secret   = "test-shared-secret-fixed"
        //   ts       = "1700000000"
        //   nonce    = "fixed0123456789abcdef0123456789"
        //   payload  = {"query": "test prompt", "session_id": "abc-123"}
        // -> signature c22dd9527a93bac19da2f906b2a13d333309376859cae4a3ede3546769f9893d
        String agentId = "agent-java-test";
        String secret = "test-shared-secret-fixed";
        String ts = "1700000000";
        String nonce = "fixed0123456789abcdef0123456789";
        String canonicalBody = "{\"query\":\"test prompt\",\"session_id\":\"abc-123\"}";
        String stringToSign = agentId + ":" + ts + ":" + nonce + ":" + canonicalBody;

        String signature = AgentSignatureUtil.hmacSha256Hex(secret, stringToSign);

        assertEquals("c22dd9527a93bac19da2f906b2a13d333309376859cae4a3ede3546769f9893d", signature);
    }

    @Test
    public void sign_producesAllFourHeaders() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", "hello");

        Map<String, String> headers = AgentSignatureUtil.sign("agent-1", "secret", payload);

        assertEquals("agent-1", headers.get("X-Agent-ID"));
        assertNotNull(headers.get("X-Agent-Timestamp"));
        assertNotNull(headers.get("X-Agent-Nonce"));
        assertEquals(64, headers.get("X-Agent-Signature").length()); // SHA-256 hex digest
    }

    @Test
    public void sign_differentPayloads_produceDifferentSignatures() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("query", "hello");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("query", "goodbye");

        String sigA = AgentSignatureUtil.sign("agent-1", "secret", a).get("X-Agent-Signature");
        String sigB = AgentSignatureUtil.sign("agent-1", "secret", b).get("X-Agent-Signature");

        assertNotEquals(sigA, sigB);
    }

    @Test
    public void sign_nonceIsRandomPerCall() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", "hello");

        String nonce1 = AgentSignatureUtil.sign("agent-1", "secret", payload).get("X-Agent-Nonce");
        String nonce2 = AgentSignatureUtil.sign("agent-1", "secret", payload).get("X-Agent-Nonce");

        assertNotEquals(nonce1, nonce2);
    }
}
