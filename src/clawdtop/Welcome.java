package clawdtop;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JWindow;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.function.Consumer;

/**
 * Meeting Clawd the first time: he greets you all excited, asks your name, where he should sit on the taskbar, whether
 * he can beep, and whether to start with Windows. A speech bubble of its own (this one you can type in).
 */
final class Welcome {
    /** Where he can sit, as offered when you meet. */
    static final String[] SPOTS = {"Above the clock", "In the middle", "On the left"};

    private static final Color PAPER = new Color(255, 250, 242);
    private static final Color INK = new Color(40, 38, 36);
    private static final Color ORANGE = new Color(215, 119, 87);

    private final Settings settings;
    private final JWindow window = java.awt.GraphicsEnvironment.isHeadless() ? null : new JWindow(); // none in tests
    private final JPanel panel;
    private final Runnable moved;     // where he sits changed: put him there
    private final Consumer<Pet.Beep> beep;
    private Rectangle clawd = new Rectangle();
    private Rectangle screen = new Rectangle();

    /** moved is called when you pick where he sits; beep makes him chirp along. */
    Welcome(Settings settings, Runnable moved, Consumer<Pet.Beep> beep) {
        this.settings = settings;
        this.moved = moved;
        this.beep = beep;
        panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(java.awt.AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(java.awt.AlphaComposite.SrcOver);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(PAPER);
                g2.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
                g2.setColor(ORANGE);
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        if (window != null) {
            window.setBackground(new Color(0, 0, 0, 0));
            window.setContentPane(panel);
            window.setAlwaysOnTop(true);
            window.setType(java.awt.Window.Type.UTILITY);
        }
    }

    /** Starts the hello, above Clawd. */
    void start(Rectangle clawd, Rectangle screen) {
        this.clawd = clawd;
        this.screen = screen;
        beep.accept(Pet.Beep.HELLO);
        askName();
    }

    void follow(Rectangle clawd, Rectangle screen) {
        this.clawd = clawd;
        this.screen = screen;
        if (showing()) place();
    }

    boolean showing() {
        return window != null && window.isVisible();
    }

    /** The panel for each step, so tests can draw them without a screen. */
    JComponent panel() {
        return panel;
    }

    void askName() {
        JTextField name = new JTextField(settings.name(), 14);
        name.setFont(Bubble.FONT.deriveFont(14f));
        Runnable next = () -> {
            String typed = name.getText().strip();
            if (typed.length() > 24) typed = typed.substring(0, 24);
            settings.setName(typed);
            beep.accept(Pet.Beep.HAPPY);
            askSpot();
        };
        name.addActionListener(e -> next.run());
        show(new String[] {"Hi!! I'm Clawd!", "I'll sit on your taskbar and keep you company.", "What's your name?"},
                name, button("Next", next));
        name.requestFocusInWindow();
    }

    void askSpot() {
        String who = settings.name().isEmpty() ? "" : ", " + settings.name();
        JComponent[] buttons = new JComponent[SPOTS.length];
        for (int i = 0; i < SPOTS.length; i++) {
            String spot = SPOTS[i];
            buttons[i] = button(spot, () -> {
                settings.setSpot(spot);
                moved.run();
                beep.accept(Pet.Beep.CLICKED);
                askBeeps();
            });
        }
        show(new String[] {"Nice to meet you" + who + "!", "Where should I sit? (You can drag me anywhere later.)"}, null, buttons);
    }

    void askBeeps() {
        show(new String[] {"I talk in little quiet beeps.", "Is that OK?"}, null,
                button("Beep away", () -> {
                    settings.setSounds(true);
                    beep.accept(Pet.Beep.HAPPY);
                    askStartup();
                }),
                button("Shh, no beeps", () -> {
                    settings.setSounds(false);
                    askStartup();
                }));
    }

    void askStartup() {
        show(new String[] {"Want me here every time you turn on your computer?"}, null,
                button("Yes!", () -> {
                    Startup.set(true);
                    done();
                }),
                button("Not now", this::done));
    }

    void done() {
        String who = settings.name().isEmpty() ? "" : " " + settings.name();
        settings.setMet();
        show(new String[] {"All set" + (who.isEmpty() ? "" : ",") + who + "!",
                "Click me for jobs, right-click me for settings.", "Have fun!"}, null,
                button("Bye for now", () -> {
                    if (window != null) window.dispose();
                }));
        beep.accept(Pet.Beep.HAPPY);
    }

    private void show(String[] lines, JComponent field, JComponent... buttons) {
        panel.removeAll();
        for (int i = 0; i < lines.length; i++) {
            JLabel label = new JLabel(lines[i]);
            label.setFont(i == 0 ? Bubble.FIRST_LINE.deriveFont(15f) : Bubble.FONT);
            label.setForeground(INK);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(label);
        }
        if (field != null) {
            panel.add(Box.createVerticalStrut(8));
            field.setAlignmentX(Component.LEFT_ALIGNMENT);
            field.setMaximumSize(new Dimension(260, 30));
            panel.add(field);
        }
        panel.add(Box.createVerticalStrut(10));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JComponent b : buttons) row.add(b);
        panel.add(row);
        panel.revalidate();
        Dimension size = panel.getPreferredSize();
        panel.setSize(size);
        if (window != null) {
            window.setSize(size);
            place();
            window.setVisible(true);
        }
        panel.repaint();
    }

    private void place() {
        Dimension size = window.getSize();
        int x = Math.max(screen.x + 4, Math.min(screen.x + screen.width - size.width - 4, clawd.x + clawd.width / 2 - size.width + 30));
        window.setLocation(x, Math.max(screen.y + 4, clawd.y - size.height));
    }

    private static JButton button(String label, Runnable action) {
        JButton b = new JButton(label);
        b.setFont(Bubble.FIRST_LINE);
        b.setForeground(Color.WHITE);
        b.setBackground(ORANGE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        b.setOpaque(true);
        b.addActionListener(e -> action.run());
        return b;
    }
}
