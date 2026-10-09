package clawdtop;

import javax.swing.JPanel;
import javax.swing.JWindow;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** His hut from the shop: it sits on the taskbar right beside his spot. */
final class Hut {
    static final int WIDTH = 14;   // in his pixels
    static final int HEIGHT = 14;

    private final JWindow window = new JWindow();
    private String kind = "";
    private int unit;

    Hut() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(java.awt.AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(java.awt.AlphaComposite.SrcOver);
                draw(g2, unit, kind);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        Platform.seeThrough(window);
        window.setContentPane(panel);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setType(java.awt.Window.Type.UTILITY);
    }

    /** Shows this kind of hut (or none, for "") with its right edge at rightX, standing on groundY. */
    void show(String kind, int unit, int rightX, int groundY, boolean visible) {
        this.kind = kind;
        this.unit = unit;
        if (kind.isEmpty() || !visible) {
            window.setVisible(false);
            return;
        }
        window.setSize(WIDTH * unit, HEIGHT * unit);
        window.setLocation(rightX - WIDTH * unit, groundY - HEIGHT * unit); // (its floor level with his feet)
        window.setVisible(true);
        window.repaint();
    }

    static void draw(Graphics2D g, int unit, String kind) {
        switch (kind) {
            case "cardboard-hut" -> {
                Color card = new Color(196, 148, 92), dark = new Color(160, 115, 66), hole = new Color(70, 48, 30);
                box(g, unit, 1, 6, 12, 8, card);
                box(g, unit, 0, 5, 14, 1.5, dark);
                box(g, unit, 4.5, 9, 5, 5, hole);           // a door cut out
                box(g, unit, 2, 7.5, 1.5, 1.5, hole);       // a little window
            }
            case "wooden-hut" -> {
                Color wood = new Color(150, 98, 60), dark = new Color(110, 70, 42), roof = new Color(180, 60, 50);
                box(g, unit, 2, 7, 10, 7, wood);
                for (int i = 0; i < 4; i++) box(g, unit, 2, 8 + i * 1.6, 10, 0.3, dark); // planks
                for (int i = 0; i < 5; i++) box(g, unit, 1 + i, 6 - i, 12 - i * 2, 1, roof); // a pointy roof
                box(g, unit, 5.5, 10, 3, 4, dark);          // the door
                box(g, unit, 9, 8.5, 2, 2, new Color(255, 230, 150)); // a lit window
            }
            case "castle" -> {
                Color stone = new Color(170, 170, 180), dark = new Color(120, 120, 132);
                box(g, unit, 2, 6, 10, 8, stone);
                for (int i = 0; i < 5; i++) box(g, unit, 2 + i * 2, 4.5, 1, 1.5, stone); // battlements
                box(g, unit, 5.5, 9.5, 3, 4.5, dark);       // the gate
                box(g, unit, 6.5, 0.5, 0.5, 4, dark);       // flagpole
                box(g, unit, 7, 0.5, 3, 2, new Color(215, 119, 87)); // an orange flag
                box(g, unit, 3.5, 7.5, 1, 1.5, dark);
                box(g, unit, 9.5, 7.5, 1, 1.5, dark);
            }
            default -> { }
        }
    }

    private static void box(Graphics2D g, int unit, double x, double y, double w, double h, Color c) {
        g.setColor(c);
        g.fillRect((int) Math.round(x * unit), (int) Math.round(y * unit), Math.max(1, (int) Math.round(w * unit)), Math.max(1, (int) Math.round(h * unit)));
    }
}
