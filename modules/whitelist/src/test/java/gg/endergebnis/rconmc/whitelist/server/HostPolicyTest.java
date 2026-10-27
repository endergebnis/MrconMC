package gg.endergebnis.rconmc.whitelist.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HostPolicyTest {

    private final HostPolicy strict = new HostPolicy(new ServerLimits(5, false));
    private final HostPolicy open = new HostPolicy(new ServerLimits(5, true));

    @Test
    void strictModeRejectsPrivateAndLocalAddresses() {
        for (String host : new String[] {"127.0.0.1", "10.1.2.3", "192.168.0.5", "172.16.0.1", "169.254.1.1",
                "100.64.0.1", "0.0.0.0", "::1", "fd00::1"}) {
            assertThat(strict.check(host)).as(host).isPresent();
        }
    }

    @Test
    void strictModeAllowsPublicAddresses() {
        assertThat(strict.check("8.8.8.8")).isEmpty();
        assertThat(strict.check("2606:4700:4700::1111")).isEmpty();
    }

    @Test
    void openModeAllowsEverything() {
        assertThat(open.check("127.0.0.1")).isEmpty();
        assertThat(open.check("10.0.0.1")).isEmpty();
    }
}
