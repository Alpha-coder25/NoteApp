package com.example.noteapp.util;

import com.example.noteapp.exception.SettingsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Salted hashing for the optional application-lock PIN.
 *
 * <p><b>Security:</b> the PIN itself is never stored. A random 16-byte salt is
 * generated per PIN and only {@code SHA-256(salt + PIN)} is persisted. A slower
 * KDF (bcrypt/argon2) would be stronger, but for a purely local convenience
 * lock a salted SHA-256 is a reasonable trade-off - and the hard requirement
 * holds: no plaintext credential ever touches disk.
 */
public final class PinHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PinHasher() { }

    /** Generates a fresh random salt, Base64-encoded. */
    public static String newSalt() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Salted SHA-256 over UTF-8 bytes of {@code salt + pin}, Base64-encoded. */
    public static String hash(String pin, String saltBase64) throws SettingsException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] salted = (saltBase64 + pin).getBytes(StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(digest.digest(salted));
        } catch (NoSuchAlgorithmException e) {
            throw new SettingsException("Unable to secure the PIN (SHA-256 unavailable).", e);
        }
    }
}
