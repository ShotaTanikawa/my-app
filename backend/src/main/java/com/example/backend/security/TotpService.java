package com.example.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/**
 * TOTP(Time-based One-Time Password)を生成/検証するサービス。
 */
@Service
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTE_LENGTH = 20;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;

    private final SecureRandom secureRandom = new SecureRandom();
    private final String issuer;

    public TotpService(@Value("${app.mfa.issuer:MyApp}") String issuer) {
        this.issuer = issuer == null || issuer.isBlank() ? "MyApp" : issuer.trim();
    }

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public boolean verifyCode(String secret, String code) {
        if (secret == null || secret.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        String normalizedCode = code.trim();
        if (!normalizedCode.matches("\\d{" + CODE_DIGITS + "}")) {
            return false;
        }

        byte[] keyBytes = base32Decode(secret);
        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        // 端末時刻のわずかなズレを許容する。
        for (long offset = -1; offset <= 1; offset++) {
            String expected = generateCode(keyBytes, currentStep + offset);
            if (expected.equals(normalizedCode)) {
                return true;
            }
        }
        return false;
    }

    public String buildOtpAuthUri(String username, String secret) {
        String account = username == null || username.isBlank() ? "user" : username.trim();
        String label = urlEncode(issuer + ":" + account);
        String issuerValue = urlEncode(issuer);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuerValue + "&digits=" + CODE_DIGITS;
    }

    private String generateCode(byte[] keyBytes, long timeStep) {
        byte[] stepBytes = ByteBuffer.allocate(8).putLong(timeStep).array();
        byte[] hmac = hmacSha1(keyBytes, stepBytes);
        int offset = hmac[hmac.length - 1] & 0x0f;
        int binary = ((hmac[offset] & 0x7f) << 24)
                | ((hmac[offset + 1] & 0xff) << 16)
                | ((hmac[offset + 2] & 0xff) << 8)
                | (hmac[offset + 3] & 0xff);
        int otp = binary % (int) Math.pow(10, CODE_DIGITS);
        return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", otp);
    }

    private byte[] hmacSha1(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(value);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to generate TOTP", ex);
        }
    }

    private String base32Encode(byte[] data) {
        StringBuilder builder = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1f;
                bitsLeft -= 5;
                builder.append(BASE32_ALPHABET.charAt(index));
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1f;
            builder.append(BASE32_ALPHABET.charAt(index));
        }
        return builder.toString();
    }

    private byte[] base32Decode(String value) {
        String normalized = value
                .replace("-", "")
                .replace(" ", "")
                .trim()
                .toUpperCase(Locale.ROOT);

        int buffer = 0;
        int bitsLeft = 0;
        ByteBuffer output = ByteBuffer.allocate((normalized.length() * 5) / 8 + 1);

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            int index = BASE32_ALPHABET.indexOf(c);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid base32 character");
            }
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                output.put((byte) ((buffer >> bitsLeft) & 0xff));
            }
        }

        byte[] bytes = new byte[output.position()];
        output.flip();
        output.get(bytes);
        return bytes;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
