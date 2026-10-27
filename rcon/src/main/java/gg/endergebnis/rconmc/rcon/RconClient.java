package gg.endergebnis.rconmc.rcon;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimale Source-RCON-Implementierung mit Reconnect- und Retry-Logik, frei von Framework-Abhaengigkeiten. Ein
 * Client gehoert zu genau einem Server und haelt eine einzelne Verbindung; Aufrufe sind serialisiert ({@code synchronized}), genau wie die Mutex-geschuetzte Verbindung in
 * der Rust-Vorlage.
 *
 * <p>Die Antwort eines Befehls wird ueber einen Sentinel-Paket-Trick abgeschlossen: Direkt nach dem Befehl wird
 * ein leeres {@code RESPONSE_VALUE}-Paket mit eigener ID gesendet. Der Server beantwortet Pakete in
 * Reihenfolge, daher ist die Antwort vollstaendig, sobald das Echo mit der Sentinel-ID eintrifft - auch bei
 * Antworten, die der Server auf mehrere Pakete aufteilt.
 */
public class RconClient {

    private static final Logger log = LoggerFactory.getLogger(RconClient.class);

    private static final int AUTH = 3;
    private static final int EXEC = 2;
    private static final int AUTH_RESPONSE = 2;
    private static final int RESPONSE_VALUE = 0;

    private static final int TIMEOUT_MILLIS = 5_000;
    private static final long RETRY_DELAY_MILLIS = 500;
    private static final int MAX_PACKET_LENGTH = 8_192;

    private final String host;
    private final int port;
    private final String password;
    private boolean closed;

    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private int nextId;

    public RconClient(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
    }

    /**
     * Fuehrt einen Befehl aus. Bei Fehlern wird neu verbunden und bis zu {@code retries}-mal erneut versucht.
     *
     * @return die Textantwort des Servers (kann leer sein)
     * @throws RconException wenn auch der letzte Versuch fehlschlaegt
     */
    public synchronized String execute(String command, int retries) {
        int attempt = 0;
        while (true) {
            if (closed) {
                throw new RconException("RCON-Client ist geschlossen");
            }
            try {
                return tryExecute(command);
            } catch (RconException e) {
                attempt++;
                disconnect();
                if (attempt > retries) {
                    throw e;
                }
                log.warn("RCON-Befehl fehlgeschlagen (Versuch {}/{}), verbinde neu: {}", attempt, retries, e.getMessage());
                sleepBeforeRetry();
            }
        }
    }

    /** Schliesst die Verbindung endgueltig; weitere {@link #execute} -Aufrufe schlagen fehl. */
    public synchronized void close() {
        closed = true;
        disconnect();
    }

    private String tryExecute(String command) {
        if (socket == null) {
            connect();
        }
        int id = newId();
        int sentinel = newId();
        sendPacket(id, EXEC, command);
        sendPacket(sentinel, RESPONSE_VALUE, "");

        StringBuilder output = new StringBuilder();
        while (true) {
            Packet packet = readPacket();
            if (packet.id() == sentinel) {
                return output.toString();
            }
            if (packet.id() == id && packet.type() == RESPONSE_VALUE) {
                output.append(packet.payload());
            }
        }
    }

    private void connect() {
        String address = host + ":" + port;
        Socket newSocket = new Socket();
        try {
            newSocket.connect(new InetSocketAddress(host, port), TIMEOUT_MILLIS);
            newSocket.setSoTimeout(TIMEOUT_MILLIS);
            newSocket.setTcpNoDelay(true);
            this.in = new DataInputStream(new BufferedInputStream(newSocket.getInputStream()));
            this.out = newSocket.getOutputStream();
            this.socket = newSocket;
        } catch (IOException e) {
            closeQuietly(newSocket);
            throw new RconException("RCON-Verbindung zu " + address + " fehlgeschlagen: " + e.getMessage(), e);
        }

        int id = newId();
        sendPacket(id, AUTH, password);
        Packet response;
        do {
            // Manche Server senden vor der Auth-Antwort ein leeres RESPONSE_VALUE-Paket.
            response = readPacket();
        } while (response.type() != AUTH_RESPONSE);
        if (response.id() == -1) {
            throw new RconException("RCON-Authentifizierung fehlgeschlagen (falsches Passwort?)");
        }
    }

    private void sendPacket(int id, int type, String body) {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(payload.length + 14).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(payload.length + 10);
        buffer.putInt(id);
        buffer.putInt(type);
        buffer.put(payload);
        buffer.put((byte) 0).put((byte) 0);
        try {
            out.write(buffer.array());
            out.flush();
        } catch (IOException e) {
            throw new RconException("RCON-Senden fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private Packet readPacket() {
        try {
            byte[] lengthBytes = new byte[4];
            in.readFully(lengthBytes);
            int length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (length < 10 || length > MAX_PACKET_LENGTH) {
                throw new RconException("RCON-Paket mit ungueltiger Laenge (" + length + ")");
            }
            byte[] rest = new byte[length];
            in.readFully(rest);
            ByteBuffer buffer = ByteBuffer.wrap(rest).order(ByteOrder.LITTLE_ENDIAN);
            int id = buffer.getInt();
            int type = buffer.getInt();
            String payload = new String(rest, 8, length - 10, StandardCharsets.UTF_8);
            return new Packet(id, type, payload);
        } catch (IOException e) {
            throw new RconException("RCON-Lesen fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private int newId() {
        nextId = nextId >= Integer.MAX_VALUE - 2 ? 1 : nextId + 1;
        return nextId;
    }

    private void disconnect() {
        closeQuietly(socket);
        socket = null;
        in = null;
        out = null;
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // nichts zu tun
        }
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RconException("RCON-Retry unterbrochen", e);
        }
    }

    private record Packet(int id, int type, String payload) {}
}
