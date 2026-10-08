package clawdtop;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * A save token: a short code that holds who Clawd is to you (your name, his color, personality, spot, size and Clawd
 * Points). You get one when you uninstall him; type it in when you set him up again and he remembers you.
 *
 * It's just those settings, packed up with a check so a typo is caught: "CLAWD-" + the settings + "-" + the check.
 */
final class SaveToken {
    static final String[] KEYS = {"name", "color", "personality", "spot", "size", "points", "owned", "hat", "hut", "birthday", "metDate"};

    private SaveToken() {
    }

    static String make(Map<String, String> values) {
        StringBuilder text = new StringBuilder();
        for (String key : KEYS) {
            String v = values.getOrDefault(key, "");
            if (!v.isEmpty()) text.append(key).append('=').append(v.replace("\n", " ")).append('\n');
        }
        String packed = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toString().getBytes(StandardCharsets.UTF_8));
        return "CLAWD-" + packed + "-" + check(packed);
    }

    /** The settings in a token, or null if it isn't one (or was mistyped). */
    static Map<String, String> read(String token) {
        if (token == null) return null;
        String t = token.strip();
        if (!t.startsWith("CLAWD-")) return null;
        int last = t.lastIndexOf('-');
        if (last <= 6) return null;
        String packed = t.substring(6, last);
        if (!t.substring(last + 1).equalsIgnoreCase(check(packed))) return null;
        try {
            String text = new String(Base64.getUrlDecoder().decode(packed), StandardCharsets.UTF_8);
            Map<String, String> values = new LinkedHashMap<>();
            for (String line : text.split("\n")) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq);
                for (String known : KEYS) if (known.equals(key)) values.put(key, line.substring(eq + 1));
            }
            return values;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String check(String packed) {
        CRC32 crc = new CRC32();
        crc.update(packed.getBytes(StandardCharsets.US_ASCII));
        return String.format("%04X", crc.getValue() & 0xFFFF);
    }
}
