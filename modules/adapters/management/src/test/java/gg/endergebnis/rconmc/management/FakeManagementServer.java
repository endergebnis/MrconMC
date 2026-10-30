package gg.endergebnis.rconmc.management;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimaler Management-Protocol-Server fuer Tests (nur JDK): WebSocket-Handshake mit Bearer-Pruefung, JSON-RPC fuer die
 * Allowlist-Methoden, vor jeder Antwort eine Benachrichtigung ohne id.
 */
final class FakeManagementServer implements Closeable {

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ServerSocket serverSocket;
    private final String secret;
    final List<String> calls = new CopyOnWriteArrayList<>();
    final List<Map<String, Object>> allowlist = new CopyOnWriteArrayList<>();
    final List<Socket> open = new CopyOnWriteArrayList<>();
    final AtomicInteger connections = new AtomicInteger();
    final AtomicInteger rejected = new AtomicInteger();

    FakeManagementServer(String secret) throws IOException {
        this.serverSocket = new ServerSocket(0);
        this.secret = secret;
        Thread.ofVirtual().start(this::acceptLoop);
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    /** Kappt alle bestehenden Verbindungen (der Server bleibt erreichbar). */
    void dropConnections() {
        for (Socket socket : open) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // egal
            }
        }
        open.clear();
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        dropConnections();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                Thread.ofVirtual().start(() -> serve(socket));
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            OutputStream out = socket.getOutputStream();
            if (!handshake(in, out)) {
                rejected.incrementAndGet();
                return;
            }
            connections.incrementAndGet();
            open.add(socket);
            while (true) {
                int b0 = in.read();
                if (b0 < 0) {
                    return;
                }
                int opcode = b0 & 0x0F;
                int b1 = in.readUnsignedByte();
                long length = b1 & 0x7F;
                if (length == 126) {
                    length = in.readUnsignedShort();
                } else if (length == 127) {
                    length = in.readLong();
                }
                byte[] mask = new byte[4];
                in.readFully(mask);
                byte[] payload = new byte[(int) length];
                in.readFully(payload);
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i % 4];
                }
                if (opcode == 8) {
                    writeFrame(out, 8, new byte[0]);
                    return;
                }
                if (opcode == 1) {
                    String request = new String(payload, StandardCharsets.UTF_8);
                    calls.add(request);
                    writeFrame(out, 1, Json.write(Map.of(
                                    "jsonrpc", "2.0",
                                    "method", "minecraft:notification/server/status",
                                    "params", List.of()))
                            .getBytes(StandardCharsets.UTF_8));
                    writeFrame(out, 1, respond(request).getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            // Verbindung beendet
        }
    }

    private boolean handshake(DataInputStream in, OutputStream out) throws IOException {
        String key = null;
        String authorization = null;
        StringBuilder headers = new StringBuilder();
        while (true) {
            int c = in.read();
            if (c < 0) {
                return false;
            }
            headers.append((char) c);
            if (headers.length() >= 4 && headers.substring(headers.length() - 4).equals("\r\n\r\n")) {
                break;
            }
        }
        for (String h : headers.toString().split("\r\n")) {
            int colon = h.indexOf(':');
            if (colon > 0) {
                String name = h.substring(0, colon).trim().toLowerCase();
                String value = h.substring(colon + 1).trim();
                if (name.equals("sec-websocket-key")) {
                    key = value;
                } else if (name.equals("authorization")) {
                    authorization = value;
                }
            }
        }
        if (key == null || !("Bearer " + secret).equals(authorization)) {
            out.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return false;
        }
        try {
            String accept = Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("SHA-1")
                            .digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                            + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private String respond(String request) {
        Map<String, Object> message = (Map<String, Object>) Json.parse(request);
        Object id = message.get("id");
        String method = (String) message.get("method");
        List<Object> params = (List<Object>) message.get("params");
        Object result;
        switch (method) {
            case "minecraft:allowlist" -> result = List.copyOf(allowlist);
            case "minecraft:allowlist/add" -> {
                for (Object p : (List<Object>) params.get(0)) {
                    Map<String, Object> player = new LinkedHashMap<>((Map<String, Object>) p);
                    player.putIfAbsent("id", UUID.nameUUIDFromBytes(((String) player.get("name")).getBytes()).toString());
                    allowlist.add(player);
                }
                result = List.copyOf(allowlist);
            }
            case "minecraft:allowlist/remove" -> {
                for (Object p : (List<Object>) params.get(0)) {
                    Map<String, Object> player = (Map<String, Object>) p;
                    allowlist.removeIf(e -> e.get("id").equals(player.get("id")));
                }
                result = List.copyOf(allowlist);
            }
            default -> {
                return Json.write(Map.of(
                        "jsonrpc", "2.0",
                        "id", id,
                        "error", Map.of("code", -32601, "message", "Method not found")));
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return Json.write(response);
    }

    private static void writeFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        synchronized (out) {
            out.write(0x80 | opcode);
            if (payload.length < 126) {
                out.write(payload.length);
            } else if (payload.length < 65536) {
                out.write(126);
                out.write(payload.length >> 8);
                out.write(payload.length);
            } else {
                out.write(127);
                for (int shift = 56; shift >= 0; shift -= 8) {
                    out.write((int) ((long) payload.length >> shift));
                }
            }
            out.write(payload);
            out.flush();
        }
    }
}
