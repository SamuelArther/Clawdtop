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
    }

    /** Shows it and asks for the keyboard (Windows may only give it once you click in it). */
    void showAndFocus(java.awt.Component focus) {
        setVisible(true);
        toFront();
        requestFocus();
        if (focus != null) focus.requestFocusInWindow();
    }
}
