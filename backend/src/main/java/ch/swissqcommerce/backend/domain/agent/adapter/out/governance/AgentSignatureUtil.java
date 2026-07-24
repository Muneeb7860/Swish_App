package ch.swissqcommerce.backend.domain.agent.adapter.out.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 inter-agent request signer (ASI07) mirroring the Python side — {@code
 * agentic_redteam/crypto.py sign_payload()} on the client-authoring side, {@code
 * governance/agent_auth.py verify_agent_signature()} on the governance service — byte-for-byte:
 * same canonical string format ({@code agent_id:timestamp:nonce:json_payload}), same HMAC-SHA256,
 * same compact/sorted-key JSON canonicalization.
 *
 * <p><b>Scoped to flat payloads.</b> Canonicalization sorts only the top-level keys (via {@link
 * TreeMap}) to match Python's {@code json.dumps(payload, sort_keys=True)} for the flat {@code
 * {query, expected_format, session_id}} shape this adapter actually sends. It does NOT recursively
 * sort nested maps — if a nested/structured payload is ever introduced here, this canonicalizer
 * must be revisited (and re-verified against the Python side) first, or signatures will silently
 * mismatch.
 */
final class AgentSignatureUtil {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();
    // Non-pretty-printed Jackson output is compact (no extra whitespace) by
    // default, matching Python's separators=(",", ":") — verified against
    // known-good Python-computed canonical strings in
    // AgentSignatureUtilTest#canonicalize_matchesPythonReferenceStrings.
    private static final ObjectMapper CANONICAL_MAPPER =
            new ObjectMapper().disable(SerializationFeature.INDENT_OUTPUT);

    private AgentSignatureUtil() {}

    /** Signs {@code payload} and returns the four X-Agent-* headers to attach. */
    static Map<String, String> sign(String agentId, String secretKey, Map<String, Object> payload) {
        try {
            String ts = String.valueOf(Instant.now().getEpochSecond());
            String nonce = randomHex(16);
            String canonicalBody = canonicalize(payload);
            String stringToSign = agentId + ":" + ts + ":" + nonce + ":" + canonicalBody;
            String signature = hmacSha256Hex(secretKey, stringToSign);

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Agent-ID", agentId);
            headers.put("X-Agent-Timestamp", ts);
            headers.put("X-Agent-Nonce", nonce);
            headers.put("X-Agent-Signature", signature);
            return headers;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign agent request", e);
        }
    }

    static String canonicalize(Map<String, Object> payload) throws Exception {
        return CANONICAL_MAPPER.writeValueAsString(new TreeMap<>(payload));
    }

    static String hmacSha256Hex(String secretKey, String message) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
        return bytesToHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    private static String randomHex(int numBytes) {
        byte[] bytes = new byte[numBytes];
        RANDOM.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
