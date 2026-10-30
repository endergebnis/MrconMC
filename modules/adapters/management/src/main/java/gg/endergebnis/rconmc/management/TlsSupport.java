package gg.endergebnis.rconmc.management;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Baut den {@link SSLContext} fuer {@code wss://}. Das Management Protocol laeuft standardmaessig mit TLS, die
 * Server-Zertifikate sind aber oft selbst signiert; dafuer kann ein eigener Truststore (PKCS12 oder JKS) angegeben
 * werden. Die Zertifikatspruefung wird nie abgeschaltet.
 */
public final class TlsSupport {

    private TlsSupport() {}

    /** @return den Standard-Kontext der JVM, wenn kein Truststore angegeben ist */
    public static SSLContext sslContext(Path truststore, String password) {
        try {
            if (truststore == null) {
                return SSLContext.getDefault();
            }
            KeyStore store = KeyStore.getInstance(isPkcs12(truststore) ? "PKCS12" : KeyStore.getDefaultType());
            try (InputStream in = Files.newInputStream(truststore)) {
                store.load(in, password == null ? null : password.toCharArray());
            }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(store);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            return context;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Truststore für das Management Protocol nicht ladbar: " + truststore, e);
        }
    }

    private static boolean isPkcs12(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".p12") || name.endsWith(".pfx");
    }
}
