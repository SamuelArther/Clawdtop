package clawdtop;

import javax.swing.JFrame;
import java.awt.Color;

/**
 * A see-through window you can type in (his hello, the question box, your piano). A plain JWindow can't take the
 * keyboard on Windows (it has no real owner), so these are frames with no border that stay off the taskbar.
 */
final class TypingWindow extends JFrame {
    TypingWindow() {
        setUndecorated(true);
        setType(Type.UTILITY);
        Platform.seeThrough(this);
        setAlwaysOnTop(true);
        setFocusableWindowState(true);
        setAutoRequestFocus(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // (Alt+F4 can't leave a half-finished setup or question behind)
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (onClose != null) onClose.run();
            }
        });
    }

    private Runnable onClose;

    /** What Alt+F4 does (nothing, unless this is set: like "never mind" on a question). */
    void onClose(Runnable r) {
        onClose = r;
    }

    /** Shows it and asks for the keyboard (Windows may only give it once you click in it). */
    void showAndFocus(java.awt.Component focus) {
        setVisible(true);
        toFront();
        requestFocus();
        if (focus != null) focus.requestFocusInWindow();
    }
}
