package gg.endergebnis.rconmc.management;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON-RPC-2.0-Client fuer das Minecraft Server Management Protocol (WebSocket, ab Minecraft 1.21.9). Ein Client
 * gehoert zu genau einem Server und haelt eine Verbindung, die bei Bedarf neu aufgebaut wird; Aufrufe sind
 * serialisiert ({@code synchronized}), weil ein {@link WebSocket} nur eine ausstehende Sendung erlaubt.
 *
 * <p>Authentifizierung: HTTP-Header {@code Authorization: Bearer <management-server-secret>} beim Handshake. Das
 * Secret taucht in keiner Fehlermeldung auf. Server-Benachrichtigungen (Nachrichten ohne {@code id}, z. B.
 * Heartbeats oder Join-Events) werden verworfen.
 */
public class ManagementClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ManagementClient.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final long RETRY_DELAY_MILLIS = 500;
    private static final int MAX_MESSAGE_LENGTH = 4 * 1024 * 1024;

    /** Die Verbindung ist abgebrochen oder gar nicht erst zustande gekommen: ein neuer Versuch ist sinnvoll. */
    static final class ConnectionLost extends ManagementException {
        ConnectionLost(String message) {
            super(message);
        }

        ConnectionLost(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final String host;
    private final int port;
    private final String secret;
    private final boolean tls;
    private final SSLContext sslContext;

    private HttpClient http;
    private WebSocket socket;
    private Session session;
    private long nextId = 1;
    private boolean closed;

    /**
     * @param tls {@code true} fuer {@code wss://}, sonst {@code ws://}
     * @param sslContext Vertrauensbasis fuer {@code wss://}; {@code null} = Standard-Truststore der JVM
     */
    public ManagementClient(String host, int port, String secret, boolean tls, SSLContext sslContext) {
        this.host = host;
        this.port = port;
        this.secret = secret;
        this.tls = tls;
        this.sslContext = sslContext;
    }

    /**
     * Ruft eine Methode auf und liefert das {@code result}-Feld der Antwort.
     *
     * @param params positionale Parameter (das Protokoll erwartet z. B. {@code [[{"name":"x"}]]})
     * @param retries Wiederholungen bei Verbindungsfehlern; Authentifizierungs- und JSON-RPC-Fehler werden nie wiederholt
     */
    public synchronized Object call(String method, List<Object> params, int retries) {
        if (closed) {
            throw new ManagementException("Client ist geschlossen");
        }
        ConnectionLost last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            if (attempt > 0) {
                sleep(RETRY_DELAY_MILLIS);
            }
            try {
                ensureConnected();
                return exchange(method, params);
            } catch (ConnectionLost e) {
                log.debug("Management-Verbindung zu {}:{} unterbrochen (Versuch {}): {}", host, port, attempt + 1, e.getMessage());
                dropConnection();
                last = e;
            }
        }
        throw last;
    }

    @Override
    public synchronized void close() {
        closed = true;
        dropConnection();
    }

    // ---- Verbindung ----

    private void ensureConnected() {
        if (socket != null && session != null && !session.closed) {
            return;
        }
        dropConnection();
        URI uri;
        try {
            uri = new URI(tls ? "wss" : "ws", null, host, port, "", null, null);
        } catch (URISyntaxException e) {
            throw new ManagementException("Ungültiger Host für das Management Protocol");
        }
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(TIMEOUT);
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        http = builder.build();
        Session newSession = new Session();
        try {
            socket = http.newWebSocketBuilder()
                    .header("Authorization", "Bearer " + secret)
                    .connectTimeout(TIMEOUT)
                    .buildAsync(uri, newSession)
                    .get(TIMEOUT.toMillis() * 2, TimeUnit.MILLISECONDS);
            session = newSession;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dropConnection();
            throw new ManagementException("Verbindungsaufbau unterbrochen", e);
        } catch (TimeoutException e) {
            dropConnection();
            throw new ConnectionLost("Zeitüberschreitung beim Verbindungsaufbau zu " + host + ":" + port, e);
        } catch (ExecutionException e) {
            dropConnection();
            throw connectFailure(e.getCause());
        }
    }

    private ManagementException connectFailure(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof WebSocketHandshakeException handshake) {
                int status = handshake.getResponse().statusCode();
                if (status == 401 || status == 403) {
                    return new ManagementException(
                            "Authentifizierung fehlgeschlagen (HTTP " + status + ") - management-server-secret prüfen");
                }
                return new ManagementException("WebSocket-Handshake abgelehnt (HTTP " + status + ")");
            }
            if (t instanceof SSLException) {
                return new ManagementException(
                        "TLS-Handshake fehlgeschlagen - Zertifikat nicht vertrauenswürdig oder der Server nutzt kein TLS "
                                + "(management-server-tls-enabled prüfen)");
            }
        }
        String reason = cause == null ? "unbekannter Fehler" : String.valueOf(cause.getMessage());
        String hint = tls ? "" : " (Server mit TLS? Dann den TLS-Servertyp verwenden)";
        return new ConnectionLost("Verbindung zu " + host + ":" + port + " fehlgeschlagen: " + reason + hint, cause);
    }

    private void dropConnection() {
        if (session != null) {
            session.fail(new ConnectionLost("Verbindung geschlossen"));
        }
        if (socket != null) {
            socket.abort();
        }
        if (http != null) {
            http.shutdownNow();
        }
        socket = null;
        session = null;
        http = null;
    }

    // ---- Anfrage/Antwort ----

    private Object exchange(String method, List<Object> params) {
        long id = nextId++;
        CompletableFuture<Object> reply = new CompletableFuture<>();
        session.pending.put(id, reply);
        try {
            String request = Json.write(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params));
            socket.sendText(request, true).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return reply.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ManagementException("Aufruf unterbrochen", e);
        } catch (TimeoutException e) {
            throw new ConnectionLost("Zeitüberschreitung bei " + method, e);
        } catch (ExecutionException | CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ManagementException managementException) {
                throw managementException;
            }
            throw new ConnectionLost("Senden von " + method + " fehlgeschlagen", cause);
        } finally {
            session.pending.remove(id);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Empfangsseite einer Verbindung: setzt Textframes zusammen und ordnet Antworten ihrer Anfrage zu. */
    private static final class Session implements WebSocket.Listener {

        final ConcurrentHashMap<Long, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();
        private final StringBuilder buffer = new StringBuilder();
        volatile boolean closed;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (buffer.length() + data.length() > MAX_MESSAGE_LENGTH) {
                buffer.setLength(0);
                fail(new ConnectionLost("Nachricht des Servers zu groß"));
                webSocket.abort();
                return null;
            }
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                handle(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            fail(new ConnectionLost("Server hat die Verbindung geschlossen (" + statusCode + ")"));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fail(new ConnectionLost("Verbindungsfehler: " + error.getMessage(), error));
        }

        void fail(ManagementException error) {
            closed = true;
            pending.values().forEach(f -> f.completeExceptionally(error));
        }

        private void handle(String message) {
            Object parsed;
            try {
                parsed = Json.parse(message);
            } catch (IllegalArgumentException e) {
                log.debug("Nicht lesbare Nachricht vom Management-Server verworfen: {}", e.getMessage());
                return;
            }
            if (!(parsed instanceof Map<?, ?> map) || !(map.get("id") instanceof Number idNumber)) {
                return; // Benachrichtigung (Heartbeat, Join-Event, ...)
            }
            CompletableFuture<Object> future = pending.get(idNumber.longValue());
            if (future == null) {
                return;
            }
            Object error = map.get("error");
            Object result = map.get("result");
            // Die Doku zeigt Fehler teils verschachtelt in "result" - beides als Fehler behandeln.
            if (error == null && result instanceof Map<?, ?> nested && nested.get("error") != null) {
                error = nested.get("error");
            }
            if (error != null) {
                future.completeExceptionally(rpcError(error));
            } else {
                future.complete(result);
            }
        }

        private static ManagementException rpcError(Object error) {
            if (error instanceof Map<?, ?> e) {
                Object code = e.get("code") instanceof Number n ? (Object) n.longValue() : e.get("code");
                return new ManagementException("Server meldet Fehler " + code + ": " + e.get("message"));
            }
            return new ManagementException("Server meldet Fehler: " + error);
        }
    }
}
