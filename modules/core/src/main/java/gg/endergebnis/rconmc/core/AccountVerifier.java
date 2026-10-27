package gg.endergebnis.rconmc.core;

import java.util.Optional;

/** Austauschbare Verifizierung: Eine Bedrock/Floodgate-Variante kann dieselbe Schnittstelle implementieren. */
public interface AccountVerifier {

    /**
     * @return den Account, oder leer, wenn der Name nicht existiert
     * @throws VerificationException wenn die Pruefung selbst fehlschlaegt
     */
    Optional<VerifiedAccount> verify(String name);
}
