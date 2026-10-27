package gg.endergebnis.rconmc.verify;

import java.util.Optional;
import java.util.regex.Pattern;

import gg.endergebnis.rconmc.core.AccountVerifier;
import gg.endergebnis.rconmc.core.VerificationException;
import gg.endergebnis.rconmc.core.VerifiedAccount;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@link AccountVerifier} gegen die oeffentliche Mojang-API (Java Edition). */
public class MojangVerifier implements AccountVerifier {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final RestClient restClient;

    public MojangVerifier(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<VerifiedAccount> verify(String name) {
        // Gueltige Java-Namen haben genau dieses Format; alles andere existiert nicht und geht gar nicht erst raus.
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        try {
            return restClient.get()
                    .uri("/users/profiles/minecraft/{name}", name)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.value() == 404 || status.value() == 400 || status.value() == 204) {
                            return Optional.<VerifiedAccount>empty();
                        }
                        if (!status.is2xxSuccessful()) {
                            throw new VerificationException("Mojang-API antwortete mit Status " + status.value());
                        }
                        MojangProfile profile = response.bodyTo(MojangProfile.class);
                        if (profile == null || profile.id() == null || profile.name() == null) {
                            throw new VerificationException("Mojang-API lieferte ein unvollstaendiges Profil");
                        }
                        return Optional.of(new VerifiedAccount(profile.name(), profile.id()));
                    });
        } catch (RestClientException e) {
            throw new VerificationException("Mojang-API nicht erreichbar: " + e.getMessage(), e);
        }
    }
}
