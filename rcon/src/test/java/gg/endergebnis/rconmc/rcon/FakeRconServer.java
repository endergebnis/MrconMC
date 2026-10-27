package gg.endergebnis.rconmc.rcon;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** Minimaler RCON-Server nach Minecraft-Vorbild (Auth, Exec, "Unknown request 0" fuer Typ-0-Pakete). */
final class FakeRconServer implements Closeable {

    private final ServerSocket serverSocket;
    private final String password;
    private final Function<String, List<String>> responder;
    final List<String> commands = new CopyOnWriteArrayList<>();
    final AtomicInteger connections = new AtomicInteger();
    private volatile boolean dropNextConnectionAfterAuth;

    FakeRconServer(String password, Function<String, List<String>> responder) throws IOException {
        this.serverSocket = new ServerSocket(0);
        this.password = password;
        this.responder = responder;
        Thread.ofVirtual().start(this::acceptLoop);
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    void dropNextConnectionAfterAuth() {
        dropNextConnectionAfterAuth = true;
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                connections.incrementAndGet();
                Thread.ofVirtual().start(() -> serve(socket));
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            while (true) {
                ByteBuffer header = readHeader(in);
                int id = header.getInt();
                int type = header.getInt();
                byte[] rest = new byte[header.capacity() - 8];
                // header enthaelt bereits den kompletten Rest; Nutzlast steht dahinter
                System.arraycopy(header.array(), 8, rest, 0, rest.length);
                String body = new String(rest, 0, rest.length - 2, StandardCharsets.UTF_8);
                switch (type) {
                    case 3 -> {
                        if (body.equals(password)) {
                            write(out, id, 2, "");
                            if (dropNextConnectionAfterAuth) {
                                dropNextConnectionAfterAuth = false;
                                return;
                            }
                        } else {
                            write(out, -1, 2, "");
                        }
                    }
                    case 2 -> {
                        commands.add(body);
                        for (String chunk : responder.apply(body)) {
                            write(out, id, 0, chunk);
                        }
                    }
                    default -> write(out, id, 0, "Unknown request " + Integer.toHexString(type));
                }
            }
        } catch (IOException ignored) {
            // Client hat getrennt
        }
    }

    private static ByteBuffer readHeader(DataInputStream in) throws IOException {
        byte[] lengthBytes = new byte[4];
        in.readFully(lengthBytes);
        int length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] rest = new byte[length];
        in.readFully(rest);
        return ByteBuffer.wrap(rest).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static void write(OutputStream out, int id, int type, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(payload.length + 14).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(payload.length + 10).putInt(id).putInt(type).put(payload).put((byte) 0).put((byte) 0);
        out.write(buffer.array());
        out.flush();
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }
}
