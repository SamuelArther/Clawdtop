package clawdtop;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;

/** The laptop's battery, asked from Windows: how full it is, and whether it's plugged in. Nothing on a desktop. */
final class Power {
    /** The battery right now: percent (0 to 100) and whether it's charging. */
    record State(int percent, boolean pluggedIn) {
    }

    private static final MethodHandle GET_SYSTEM_POWER_STATUS;

    static {
        MethodHandle status = null;
        try {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
                SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
                status = Linker.nativeLinker().downcallHandle(kernel32.find("GetSystemPowerStatus").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            }
        } catch (Throwable notHere) {
            status = null;
        }
        GET_SYSTEM_POWER_STATUS = status;
    }

    private Power() {
    }

    private static volatile int critical = -1;

    /**
     * The battery level where Windows shuts the laptop down by itself (its "critical battery level", 5% unless
     * changed). Asked once (powercfg), in the background.
     */
    static int criticalLevel() {
        if (critical >= 0) return critical;
        int level = 5;
        try {
            Process p = new ProcessBuilder("powercfg", "/query", "SCHEME_CURRENT", "SUB_BATTERY", "BATLEVELCRIT").redirectErrorStream(true).start();
            String out = Desktop.output(p, 10);
            if (out == null) out = "";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("DC Power Setting Index: 0x([0-9a-fA-F]+)").matcher(out);
            if (m.find()) level = Integer.parseInt(m.group(1), 16);
        } catch (Exception e) {
            // keep Windows' usual 5%
        }
        critical = level;
        return level;
    }

    /** The battery, or null if there isn't one (or Windows won't say). */
    static State now() {
        if (GET_SYSTEM_POWER_STATUS == null) return Platform.WINDOWS ? null : Platform.battery();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(12); // SYSTEM_POWER_STATUS: 4 bytes, then two 4-byte times
            if ((int) GET_SYSTEM_POWER_STATUS.invokeExact(s) == 0) return null;
            int ac = s.get(ValueLayout.JAVA_BYTE, 0) & 0xFF;
            int flag = s.get(ValueLayout.JAVA_BYTE, 1) & 0xFF;
            int percent = s.get(ValueLayout.JAVA_BYTE, 2) & 0xFF;
            if ((flag & 128) != 0 || percent > 100) return null; // 128: no battery (a desktop)
            return new State(percent, ac == 1);
        } catch (Throwable e) {
            return null;
        }
    }
}
