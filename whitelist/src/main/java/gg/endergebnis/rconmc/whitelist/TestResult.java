package gg.endergebnis.rconmc.whitelist;

/** Ausgang von {@link ServerService#test}. */
public sealed interface TestResult {

    record NotFound() implements TestResult {}

    record Ok() implements TestResult {}

    record Failed(String reason) implements TestResult {}
}
