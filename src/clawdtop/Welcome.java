package clawdtop;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
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
    private final TypingWindow window = java.awt.GraphicsEnvironment.isHeadless() ? null : new TypingWindow(); // none in tests
    private final JPanel panel;
    private final Runnable moved;     // where he sits changed: put him there
    private final Consumer<Pet.Beep> beep;
    private Runnable finished = () -> { };
    private boolean movedIn;        // came over from another computer (he arrives with moving boxes, not in a box)
    private Transfer.Waiting waiting;
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
            Platform.seeThrough(window);
            window.setContentPane(panel);
            window.setAlwaysOnTop(true);
            window.setType(java.awt.Window.Type.UTILITY);
            // a way out: Escape (or closing the window) skips the rest of the questions, with the usual answers
            window.onClose(this::skipRest);
            window.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "skip");
            window.getRootPane().getActionMap().put("skip", new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    skipRest();
                }
            });
        }
    }

    private boolean atEnd; // showing "All set!"

    /** Escape or closing the window: the questions you haven't answered get their usual answers, and he's ready. */
    void skipRest() {
        if (waiting != null) waiting.close();
        waiting = null;
        if (atEnd) { // (on the last page: same as OK)
            if (window != null) window.dispose();
            finished.run();
            return;
        }
        done();
    }

    /** Runs when you've finished meeting him (his box shows up then). */
    void onFinished(Runnable then) {
        finished = then;
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
        show(new String[] {"Hi!! I'm Clawd!", "I'll sit on your " + Platform.BAR + " and keep you company.", "What's your name?"},
                name, button("Moving from another computer", this::askMove), button("I have a save token", this::askToken),
                button("Next", next));
        name.requestFocusInWindow();
    }

    /** Whether he came over from another computer. */
    boolean movedIn() {
        return movedIn;
    }

    void askMove() {
        String code = Transfer.newCode();
        try {
            waiting = new Transfer.Waiting(code, token -> javax.swing.SwingUtilities.invokeLater(() -> {
                stopWaiting();
                if (settings.useToken(token)) {
                    movedIn = true;
                    beep.accept(Pet.Beep.HAPPY);
                    askNewHome(settings.homeNamed() ? settings.home() : "your old computer");
                }
            }));
        } catch (java.io.IOException e) {
            show(new String[] {"I couldn't open the door for the move.", "(Is another Clawd already waiting?)"}, null,
                    button("Back", this::askName));
            return;
        }
        show(new String[] {"Moving in! On your old computer, open a terminal and type:", "    clawd move",
                "Then type this code: " + code, "(Both computers on the same wifi.)"}, null,
                button("Cancel", () -> {
                    stopWaiting();
                    askName();
                }));
    }

    private void stopWaiting() {
        if (waiting != null) waiting.close();
        waiting = null;
    }

    void askToken() {
        askToken(new String[] {"A save token! Paste it in:", "(clawd uninstall gave it to you. It starts with CLAWD-)"});
    }

    private void askToken(String[] message) {
        JTextField token = new JTextField(22);
        token.setFont(Bubble.FONT.deriveFont(13f));
        Runnable use = () -> {
            if (settings.useToken(token.getText())) {
                beep.accept(Pet.Beep.HAPPY);
                moved.run();
                askStartup();
            } else {
                beep.accept(Pet.Beep.OOF);
                askToken(new String[] {"Hmm, that doesn't look like my save token.", "Paste it in again? (It starts with CLAWD-)"});
            }
        };
        token.addActionListener(e -> use.run());
        show(message, token, button("Start fresh", this::askName), button("Bring me back", use));
        token.requestFocusInWindow();
    }

    void askPersonality() {
        JComponent[] buttons = new JComponent[Pet.Personality.values().length];
        for (int i = 0; i < buttons.length; i++) {
            Pet.Personality p = Pet.Personality.values()[i];
            buttons[i] = button(p.shown(), () -> {
                settings.setPersonality(p);
                beep.accept(p == Pet.Personality.SLEEPY ? Pet.Beep.YAWN : Pet.Beep.HAPPY);
                askBirthday();
            });
        }
        show(new String[] {"What am I like?", "Chill: easygoing.  Bouncy: can't sit still.",
                "Helpful: lots of tips.  Sleepy: loves naps."}, null, buttons);
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
                askPersonality();
            });
        }
        show(new String[] {"Nice to meet you" + who + "!", "Where should I sit? (You can drag me anywhere later.)"}, null, buttons);
    }

    void askBirthday() {
        JTextField date = new JTextField(settings.birthday().isEmpty() ? "" : settings.birthday().replace('-', '/'), 6);
        date.setFont(Bubble.FONT.deriveFont(14f));
        Runnable next = () -> {
            String typed = date.getText().strip().replace('-', '/').replace('.', '/');
            String[] parts = typed.split("/");
            try {
                int month = Integer.parseInt(parts[0]);
                int day = Integer.parseInt(parts[1]);
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) settings.setBirthday(String.format("%02d-%02d", month, day));
            } catch (RuntimeException notADate) {
                // left blank or odd: no birthday surprise, that's all
            }
            askHome();
        };
        date.addActionListener(e -> next.run());
        show(new String[] {"When's your birthday?", "Just the month and day, like 10/08. (No year!)"}, date,
                button("Skip", this::askHome), button("Next", next));
        date.requestFocusInWindow();
    }

    /** What he should call this computer: his new home. */
    void askHome() {
        JTextField home = new JTextField(settings.homeNamed() ? settings.home() : settings.suggestedHome(), 16);
        home.setFont(Bubble.FONT.deriveFont(14f));
        Runnable next = () -> {
            settings.setHome(home.getText().isBlank() ? settings.suggestedHome() : home.getText());
            beep.accept(Pet.Beep.HAPPY);
            askMilitary();
        };
        home.addActionListener(e -> next.run());
        show(new String[] {"And what should I call this computer?", "It's my new home!"}, home, button("Next", next));
        home.selectAll();
        home.requestFocusInWindow();
    }

    /** Have you served in the military? Then he salutes you each day. */
    void askMilitary() {
        show(new String[] {"Have you ever served in the military?", "(I like to say thank you.)"}, null,
                button("No", () -> {
                    settings.setService(null, null);
                    askKidFriendly();
                }),
                button("Yes", this::askBranch));
    }

    void askBranch() {
        javax.swing.JComboBox<String> branch = new javax.swing.JComboBox<>(Settings.BRANCHES);
        branch.setFont(Bubble.FONT.deriveFont(14f));
        show(new String[] {"Which branch?"}, branch, button("Back", this::askMilitary),
                button("Next", () -> askServiceStatus((String) branch.getSelectedItem())));
    }

    void askServiceStatus(String branch) {
        javax.swing.JComboBox<String> how = new javax.swing.JComboBox<>(Settings.SERVICE);
        how.setFont(Bubble.FONT.deriveFont(14f));
        show(new String[] {branch + "! And are you...", "(active duty, reserve, retired, or honorably discharged)"}, how,
                button("Back", this::askBranch),
                button("Next", () -> {
                    settings.setService(branch, (String) how.getSelectedItem());
                    beep.accept(Pet.Beep.HAPPY);
                    show(new String[] {"Thank you for your service.", "I'll salute you every day."}, null, button("Next", this::askKidFriendly));
                }));
    }

    /** Moved in from another computer (oldHome is its name): what's this one called? */
    void askNewHome(String oldHome) {
        JTextField home = new JTextField(settings.suggestedHome(), 16);
        home.setFont(Bubble.FONT.deriveFont(14f));
        Runnable next = () -> {
            settings.movedFrom(oldHome);
            settings.setHome(home.getText().isBlank() ? settings.suggestedHome() : home.getText());
            settings.setMet();
            askStartup(); // (then he walks in with his boxes)
        };
        home.addActionListener(e -> next.run());
        show(new String[] {"Got everything from " + oldHome + "!", "What should I call this new place?"}, home,
                button("Move in!", next));
        home.selectAll();
        home.requestFocusInWindow();
    }

    /** Kid-friendly mode, for his answers when you ask him things. He never says bad words either way. */
    void askKidFriendly() {
        show(new String[] {"How should I answer your questions?", "Normal: the most accurate answers. (Recommended!)",
                "Kid-friendly: simple and extra gentle, for little kids.", "I never say bad words either way."}, null,
                button("Normal (recommended)", () -> {
                    settings.set("kidFriendly", "false");
                    askWebSearch();
                }),
                button("Kid-friendly", () -> {
                    settings.set("kidFriendly", "true");
                    askWebSearch();
                }));
    }

    /** Whether he may look things up online when you ask him something (off unless you say yes). */
    void askWebSearch() {
        show(new String[] {"Can I look things up online when you ask me something?", "Then my answers are more right and up to date.",
                "(Your question goes to Wikipedia and DuckDuckGo. Nothing else does.)"}, null,
                button("Yes, look things up", () -> {
                    settings.set("webSearch", "true");
                    askBeeps();
                }),
                button("No, stay offline", () -> {
                    settings.set("webSearch", "false");
                    askBeeps();
                }));
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
        atEnd = true;
        String who = settings.name().isEmpty() ? "" : " " + settings.name();
        settings.setMet();
        if (movedIn) { // he walks in with his boxes: no box to open
            if (window != null) window.dispose();
            finished.run();
            return;
        }
        java.util.List<String> lines = new java.util.ArrayList<>(java.util.List.of("All set" + (who.isEmpty() ? "" : ",") + who + "!",
                "I'm in a box down on your " + Platform.BAR + ".", "Click it to let me out!"));
        if (settings.on("askMe")) {
            lines.add("(I'm also getting my brain ready in the background, so I can answer your questions:");
            lines.add("Ollama, free, " + Brain.downloadSize(settings.choice("brain")) + " to download. You don't have to do anything.)");
        }
        show(lines.toArray(new String[0]), null,
                button("OK!", () -> {
                    atEnd = false; // (once)
                    if (window != null) window.dispose();
                    finished.run();
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
            window.showAndFocus(field);
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
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI()); // (Windows' own buttons ignore the colors: white words on a white button)
        b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }
}
