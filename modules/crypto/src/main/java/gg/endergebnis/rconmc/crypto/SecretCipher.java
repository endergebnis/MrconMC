package gg.endergebnis.rconmc.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM fuer Secrets in der Datenbank (RCON-Passwoerter). Format: {@code v1:<base64(iv || ciphertext+tag)>},
 * mit zufaelliger 12-Byte-IV pro Wert. Das Praefix erlaubt spaeter eine Key-Rotation. Der {@code context} wird als
 * AAD authentifiziert: Ein Ciphertext, der in eine Zeile einer anderen Guild kopiert wird, laesst sich nicht entschluesseln.
 */
public final class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("Der Master-Key muss genau 32 Byte lang sein (AES-256).");
        }
        this.key = new SecretKeySpec(keyBytes.clone(), "AES");
    }

    /** @param base64Key 32 zufaellige Byte, base64-kodiert, z. B. {@code openssl rand -base64 32} */
    public static SecretCipher fromBase64(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("Master-Key fehlt.");
        }
        try {
            return new SecretCipher(Base64.getDecoder().decode(base64Key.trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Master-Key ist kein gueltiges base64 mit 32 Byte.", e);
        }
    }

    public String encrypt(String plain, String context) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, out, IV_BYTES, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Verschluesselung fehlgeschlagen", e);
        }
    }

    public String decrypt(String stored, String context) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unbekanntes Secret-Format (Praefix " + PREFIX + " erwartet).");
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (in.length < IV_BYTES + TAG_BITS / 8) {
                throw new IllegalArgumentException("Secret ist zu kurz.");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Entschluesselung fehlgeschlagen (falscher Key oder manipulierte Daten)", e);
        }
    }

    @Override
    public String toString() {
        return "SecretCipher[key=[REDACTED]]";
    }
}
