package clawdtop;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Asking your permission the computer's own way: a real system dialog (Windows' message box, a macOS dialog, or
 * zenity/kdialog on Linux), not one of his speech bubbles. The answer comes back on the Swing thread.
 */
final class Consent {
    private Consent() {
    }

    /** Asks question (with a little more explanation under it); answer gets true for Allow. */
    static void ask(String question, String detail, Consumer<Boolean> answer) {
        Thread t = new Thread(() -> {
            boolean yes;
            try {
                yes = Platform.WINDOWS ? windows(question, detail) : Platform.MAC ? mac(question, detail) : linux(question, detail);
            } catch (Throwable noDialog) {
                yes = false;
            }
            boolean said = yes;
            javax.swing.SwingUtilities.invokeLater(() -> answer.accept(said));
        }, "Clawdtop asking permission");
        t.setDaemon(true);
        t.start();
    }

    private static final int MB_YESNO = 0x4, MB_ICONQUESTION = 0x20, MB_SETFOREGROUND = 0x10000, MB_TOPMOST = 0x40000, IDYES = 6;

    private static boolean windows(String question, String detail) throws Throwable {
        MethodHandle box = Linker.nativeLinker().downcallHandle(
                SymbolLookup.libraryLookup("user32", Arena.global()).find("MessageBoxW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment text = arena.allocateFrom(question + "\n\n" + detail, StandardCharsets.UTF_16LE);
            MemorySegment title = arena.allocateFrom("Clawdtop: permission", StandardCharsets.UTF_16LE);
            return (int) box.invokeExact(MemorySegment.NULL, text, title, MB_YESNO | MB_ICONQUESTION | MB_SETFOREGROUND | MB_TOPMOST) == IDYES;
        }
    }

    private static boolean mac(String question, String detail) throws Exception {
        String text = (question + "\n\n" + detail).replace("\\", "\\\\").replace("\"", "\\\"");
        Process p = new ProcessBuilder("osascript", "-e", "display dialog \"" + text + "\" with title \"Clawdtop\" buttons {\"Don't Allow\", \"Allow\"}"
                + " default button \"Allow\" with icon caution").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return out.contains("button returned:Allow");
    }

    private static boolean linux(String question, String detail) throws Exception {
        if (Platform.onPath("zenity")) {
            return new ProcessBuilder("zenity", "--question", "--title=Clawdtop", "--ok-label=Allow", "--cancel-label=Don't Allow",
                    "--text=" + question + "\n\n" + detail).start().waitFor() == 0;
        }
        if (Platform.onPath("kdialog")) {
            return new ProcessBuilder("kdialog", "--title", "Clawdtop", "--yesno", question + "\n\n" + detail).start().waitFor() == 0;
        }
        return javax.swing.JOptionPane.showConfirmDialog(null, question + "\n\n" + detail, "Clawdtop", javax.swing.JOptionPane.YES_NO_OPTION)
                == javax.swing.JOptionPane.YES_OPTION;
    }
}
