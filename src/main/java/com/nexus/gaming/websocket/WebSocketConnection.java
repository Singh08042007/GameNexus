package com.nexus.gaming.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Encapsulates a single RFC 6455 WebSocket client connection.
 * Handles framing (masked client reads and unmasked server writes) and player context.
 */
public class WebSocketConnection {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Object writeLock = new Object();
    private volatile boolean open = true;
    private String userId;
    private String username;
    private String roomCode;

    public WebSocketConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    public void sendText(String message) {
        if (!open || message == null) return;
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        synchronized (writeLock) {
            try {
                out.write(0x81); // FIN + Text Frame opcode (0x1)
                int len = payload.length;
                if (len <= 125) {
                    out.write(len);
                } else if (len <= 65535) {
                    out.write(126);
                    out.write((len >> 8) & 0xFF);
                    out.write(len & 0xFF);
                } else {
                    out.write(127);
                    for (int i = 7; i >= 0; i--) {
                        out.write((int) (((long) len >> (8 * i)) & 0xFF));
                    }
                }
                out.write(payload);
                out.flush();
            } catch (IOException e) {
                close();
            }
        }
    }

    public void sendPong(byte[] payload) {
        if (!open) return;
        synchronized (writeLock) {
            try {
                out.write(0x8A); // FIN + Pong opcode (0xA)
                int len = payload == null ? 0 : Math.min(payload.length, 125);
                out.write(len);
                if (len > 0) {
                    out.write(payload, 0, len);
                }
                out.flush();
            } catch (IOException e) {
                close();
            }
        }
    }

    public String readNextTextFrame() throws IOException {
        while (open) {
            int b1 = in.read();
            if (b1 == -1) {
                close();
                return null;
            }
            int opcode = b1 & 0x0F;
            if (opcode == 0x8) { // Connection Close frame
                close();
                return null;
            }

            int b2 = in.read();
            if (b2 == -1) {
                close();
                return null;
            }
            boolean masked = (b2 & 0x80) != 0;
            long payloadLen = b2 & 0x7F;
            if (payloadLen == 126) {
                int hi = in.read();
                int lo = in.read();
                if (hi == -1 || lo == -1) {
                    close();
                    return null;
                }
                payloadLen = ((hi & 0xFF) << 8) | (lo & 0xFF);
            } else if (payloadLen == 127) {
                payloadLen = 0;
                for (int i = 0; i < 8; i++) {
                    int b = in.read();
                    if (b == -1) {
                        close();
                        return null;
                    }
                    payloadLen = (payloadLen << 8) | (b & 0xFF);
                }
            }

            if (payloadLen > 65536) {
                close();
                return null;
            }

            byte[] maskingKey = new byte[4];
            if (masked) {
                readFully(maskingKey);
            }

            byte[] payload = new byte[(int) payloadLen];
            readFully(payload);

            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ maskingKey[i % 4]);
                }
            }

            if (opcode == 0x9) { // Ping -> reply Pong
                sendPong(payload);
                continue;
            }
            if (opcode == 0x1) { // Text frame
                return new String(payload, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void readFully(byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = in.read(buffer, offset, buffer.length - offset);
            if (read == -1) {
                throw new IOException("Unexpected EOF while reading WebSocket frame");
            }
            offset += read;
        }
    }

    public void close() {
        open = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public boolean isOpen() {
        return open && !socket.isClosed();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode == null ? null : roomCode.toUpperCase();
    }
}

