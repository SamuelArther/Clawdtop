package clawdtop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.function.Consumer;

/**
 * Moving Clawd to another computer on the same wifi. The new computer (in setup) waits and shows a 4-digit code; the
 * old one (clawd controlpanel) finds it with that code and sends his save token straight over. Only on your own
 * network, and only to the computer showing that code.
 */
final class Transfer {
    static final int FIND_PORT = 47820;  // the new computer answers "who's there?" here (UDP)
    static final int MOVE_PORT = 47821;  // and takes the save token here (TCP)

    private Transfer() {
    }

    /** A fresh 4-digit code for the new computer to show. */
    static String newCode() {
        return String.format("%04d", new SecureRandom().nextInt(10_000));
    }

    /** The new computer, waiting: answers the old one's search, and takes its save token. Stop it with close(). */
    static final class Waiting implements AutoCloseable {
        private final DatagramSocket finder;
        private final ServerSocket mover;
        private volatile boolean open = true;

        /** Waits for a computer with this code; gotToken gets the save token (on a background thread). */
        Waiting(String code, InetAddress on, int findPort, int movePort, Consumer<String> gotToken) throws IOException {
            finder = new DatagramSocket(null);
            finder.setReuseAddress(true);
            finder.bind(on == null ? new InetSocketAddress(findPort) : new InetSocketAddress(on, findPort));
            mover = new ServerSocket(movePort, 4, on);
            Thread answer = new Thread(() -> {
                byte[] buffer = new byte[256];
                while (open) {
                    try {
                        DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                        finder.receive(p);
                        String[] asked = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).strip().split(" ");
                        // "CLAWDTOP-FIND <nonce> <proof>": the other computer knows the code (without saying it out loud)
                        if (asked.length == 3 && asked[0].equals("CLAWDTOP-FIND")) {
                            if (!asked[2].equals(proof(code, "find:" + asked[1]))) continue; // (a mistyped code: just no answer)
                            byte[] here = ("CLAWDTOP-HERE " + proof(code, "here:" + asked[1])).getBytes(StandardCharsets.UTF_8);
                            finder.send(new DatagramPacket(here, here.length, p.getAddress(), p.getPort()));
                        }
                    } catch (IOException e) {
                        if (!open) return;
                    }
                }
            }, "Clawdtop move: answering");
            answer.setDaemon(true);
            answer.start();
            Thread take = new Thread(() -> {
                int wrong = 0;
                while (open) {
                    try (Socket s = mover.accept()) {
                        s.setSoTimeout(10_000);
                        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                        PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);
                        String hello = in.readLine();
                        String token = in.readLine();
                        if (!("CLAWDTOP-MOVE " + code).equals(hello) || SaveToken.read(token) == null) {
                            out.println("NO");
                            wrong++;
                            Thread.sleep(Math.min(5000, 500L * wrong)); // slower after each wrong try: no guessing all 10,000 codes
                            continue;
                        }
                        out.println("OK");
                        gotToken.accept(token);
                        return;
                    } catch (IOException e) {
                        if (!open) return;
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }, "Clawdtop move: taking");
            take.setDaemon(true);
            take.start();
        }

        /** Waits on every network the computer is on (the first time, Windows asks whether Java may). */
        Waiting(String code, Consumer<String> gotToken) throws IOException {
            this(code, null, FIND_PORT, MOVE_PORT, gotToken);
        }

        @Override
        public void close() {
            open = false;
            finder.close();
            try {
                mover.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    /** The old computer: looks on the wifi for the computer showing this code. Its address, or null if none answered. */
    static InetAddress find(String code, int findPort, long waitMs) {
        try (DatagramSocket s = new DatagramSocket()) {
            s.setBroadcast(true);
            s.setSoTimeout(500);
            String nonce = Long.toHexString(new java.security.SecureRandom().nextLong());
            byte[] ask = ("CLAWDTOP-FIND " + nonce + " " + proof(code, "find:" + nonce)).getBytes(StandardCharsets.UTF_8);
            long until = System.currentTimeMillis() + waitMs;
            while (System.currentTimeMillis() < until) {
                for (String where : new String[] {"255.255.255.255", "127.0.0.1"}) {
                    try {
                        s.send(new DatagramPacket(ask, ask.length, InetAddress.getByName(where), findPort));
                    } catch (IOException notAllowedHere) {
                        // (some computers won't broadcast; the other way may still work)
                    }
                }
                try {
                    byte[] buffer = new byte[64];
                    DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                    s.receive(reply);
                    // only the computer that knows the code can answer like this
                    if (new String(buffer, 0, reply.getLength(), StandardCharsets.UTF_8).strip().equals("CLAWDTOP-HERE " + proof(code, "here:" + nonce))) {
                        return reply.getAddress();
                    }
                } catch (SocketTimeoutException again) {
                    // ask again
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /** Proof of knowing the code, for this message (an HMAC of it, keyed with the code), without sending the code itself. */
    static String proof(String code, String message) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(("clawdtop-move:" + code).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Sends the save token to the new computer. True if it took it. */
    static boolean send(InetAddress to, int movePort, String code, String token) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(to, movePort), 5000);
            s.setSoTimeout(10_000);
            PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.println("CLAWDTOP-MOVE " + code);
            out.println(token);
            return "OK".equals(in.readLine());
        } catch (IOException e) {
            return false;
        }
    }
}
