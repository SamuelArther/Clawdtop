package clawdtop;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JWindow;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.function.Consumer;

/** "Ask me a question": a little bubble above Clawd that you can type in. */
final class Ask {
    private static final Color PAPER = new Color(255, 250, 242);
    private static final Color INK = new Color(40, 38, 36);
    private static final Color ORANGE = new Color(215, 119, 87);

    private final JWindow window = java.awt.GraphicsEnvironment.isHeadless() ? null : new JWindow();
    private final JPanel panel;
    private final JTextField field = new JTextField(26);

    Ask() {
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

    /** Shows the box above Clawd; asked gets your question (not called if you close it). */
    void show(String prompt, Rectangle clawd, Rectangle screen, Consumer<String> asked) {
        panel.removeAll();
        JLabel label = new JLabel(prompt);
        label.setFont(Bubble.FIRST_LINE.deriveFont(15f));
        label.setForeground(INK);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(8));
        field.setText("");
        field.setFont(Bubble.FONT.deriveFont(14f));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(320, 30));
        for (var l : field.getActionListeners()) field.removeActionListener(l);
        Runnable go = () -> {
            String q = field.getText().strip();
            hide();
            if (!q.isEmpty()) asked.accept(q.length() > 300 ? q.substring(0, 300) : q);
        };
        field.addActionListener(e -> go.run());
        panel.add(field);
        panel.add(Box.createVerticalStrut(10));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(button("Never mind", this::hide));
        row.add(button("Ask", go));
        panel.add(row);
        panel.revalidate();
        Dimension size = panel.getPreferredSize();
        panel.setSize(size);
        if (window == null) return;
        window.setSize(size);
        int x = Math.max(screen.x + 4, Math.min(screen.x + screen.width - size.width - 4, clawd.x + clawd.width / 2 - size.width + 30));
        window.setLocation(x, Math.max(screen.y + 4, clawd.y - size.height));
        window.setVisible(true);
        window.toFront();
        field.requestFocusInWindow();
    }

    void hide() {
        if (window != null) window.setVisible(false);
    }

    /** For tests: the panel, and typing into it. */
    JPanel panel() {
        return panel;
    }

    JTextField field() {
        return field;
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
