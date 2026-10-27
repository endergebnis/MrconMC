package gg.endergebnis.rconmc.whitelist.server;

/** Ausgang von {@link ServerService#add}. */
public sealed interface AddServerResult {

    record Added(String name) implements AddServerResult {}

    record Invalid(String reason) implements AddServerResult {}

    record LimitReached(int max) implements AddServerResult {}

    record Duplicate(String name) implements AddServerResult {}

    record HostNotAllowed(String reason) implements AddServerResult {}

    record ConnectionFailed(String reason) implements AddServerResult {}
}
