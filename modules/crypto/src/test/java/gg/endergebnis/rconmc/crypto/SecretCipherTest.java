package gg.endergebnis.rconmc.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private static final String KEY_A = Base64.getEncoder().encodeToString(new byte[32]);
    private static final byte[] OTHER = new byte[32];

    static {
        OTHER[0] = 1;
    }

    private final SecretCipher cipher = SecretCipher.fromBase64(KEY_A);

    @Test
    void roundTripsAndNeverStoresPlaintext() {
        String stored = cipher.encrypt("hunter2", "guild:1");

        assertThat(stored).startsWith("v1:").doesNotContain("hunter2");
        assertThat(cipher.decrypt(stored, "guild:1")).isEqualTo("hunter2");
    }

    @Test
    void usesAFreshIvForEveryValue() {
        assertThat(cipher.encrypt("same", "guild:1")).isNotEqualTo(cipher.encrypt("same", "guild:1"));
    }

    @Test
    void ciphertextCannotBeMovedToAnotherContext() {
        String stored = cipher.encrypt("hunter2", "guild:1");

        assertThatThrownBy(() -> cipher.decrypt(stored, "guild:2")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongKeyAndTamperedDataAreRejected() {
        String stored = cipher.encrypt("hunter2", "guild:1");
        String tampered = stored.substring(0, stored.length() - 4) + "AAAA";

        assertThatThrownBy(() -> new SecretCipher(OTHER).decrypt(stored, "guild:1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(tampered, "guild:1")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsKeysOfTheWrongSizeAndUnknownFormats() {
        assertThatThrownBy(() -> new SecretCipher(new byte[16])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecretCipher.fromBase64("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher.decrypt("plain", "guild:1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringHidesTheKey() {
        assertThat(cipher.toString()).doesNotContain(KEY_A);
    }
}
