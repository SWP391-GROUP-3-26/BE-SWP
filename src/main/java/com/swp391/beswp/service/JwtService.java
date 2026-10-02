package com.swp391.beswp.service;

import com.swp391.beswp.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String secret;
    private final long expirationSeconds;

    public JwtService(
            @Value("${security.jwt.secret:}") String secret,
            @Value("${security.jwt.expiration-seconds:3600}") long expirationSeconds
    ) {
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(User user) {
        assertSecretConfigured();

        Instant now = Instant.now();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getEmail());
        payload.put("userId", user.getId());
        payload.put("roleId", user.getRole() == null ? null : user.getRole().getId());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plusSeconds(expirationSeconds).getEpochSecond());

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String signingInput = encodedHeader + "." + encodedPayload;
        return signingInput + "." + sign(signingInput);
    }

    public Map<String, Object> validateToken(String token) {
        assertSecretConfigured();

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = sign(signingInput);
        if (!constantTimeEquals(expectedSignature, parts[2])) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }

        Map<String, Object> claims = decodeJson(parts[1]);
        long exp = toLong(claims.get("exp"));
        if (Instant.now().getEpochSecond() >= exp) {
            throw new IllegalArgumentException("JWT has expired");
        }

        return claims;
    }

    private String encodeJson(Map<String, Object> value) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escapeJson(entry.getKey())).append('"').append(':');
            Object entryValue = entry.getValue();
            if (entryValue == null) {
                json.append("null");
            } else if (entryValue instanceof Number || entryValue instanceof Boolean) {
                json.append(entryValue);
            } else {
                json.append('"').append(escapeJson(String.valueOf(entryValue))).append('"');
            }
        }
        json.append('}');
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> decodeJson(String value) {
        try {
            String json = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            return parseFlatJson(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JWT payload", ex);
        }
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign JWT", ex);
        }
    }

    private void assertSecretConfigured() {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("JWT secret is not configured");
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        if (leftBytes.length != rightBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < leftBytes.length; i++) {
            result |= leftBytes[i] ^ rightBytes[i];
        }
        return result == 0;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Map<String, Object> parseFlatJson(String json) {
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON object");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        String body = json.substring(1, json.length() - 1).trim();
        if (body.isEmpty()) {
            return result;
        }

        for (String pair : splitJsonPairs(body)) {
            int separator = findSeparator(pair);
            if (separator < 0) {
                throw new IllegalArgumentException("Invalid JSON property");
            }

            String key = unquoteJson(pair.substring(0, separator).trim());
            String rawValue = pair.substring(separator + 1).trim();
            result.put(key, parseJsonValue(rawValue));
        }
        return result;
    }

    private String[] splitJsonPairs(String body) {
        return body.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
    }

    private int findSeparator(String pair) {
        boolean inString = false;
        for (int i = 0; i < pair.length(); i++) {
            char current = pair.charAt(i);
            if (current == '"' && (i == 0 || pair.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (current == ':' && !inString) {
                return i;
            }
        }
        return -1;
    }

    private Object parseJsonValue(String rawValue) {
        if ("null".equals(rawValue)) {
            return null;
        }
        if (rawValue.startsWith("\"") && rawValue.endsWith("\"")) {
            return unquoteJson(rawValue);
        }
        return Long.parseLong(rawValue);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unquoteJson(String value) {
        if (!value.startsWith("\"") || !value.endsWith("\"")) {
            throw new IllegalArgumentException("Expected JSON string");
        }
        return value.substring(1, value.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
