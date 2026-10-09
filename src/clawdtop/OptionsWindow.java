package clawdtop;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "All the options": every switch and dial he has, in a window, a tab per group (the same ones as the control panel in
 * the terminal). A change takes effect straight away.
 */
final class OptionsWindow {
    private OptionsWindow() {
    }

    private static JFrame open; // (one at a time: asking again brings it to the front)

    /** Shows the options; changed runs after each change (so he can use it right away). */
    static void show(java.util.function.Supplier<Settings> settings, Runnable changed) {
        if (open != null && open.isDisplayable()) {
            open.toFront();
            return;
        }
        JFrame window = new JFrame("Clawd's options");
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setAlwaysOnTop(true);
        window.setContentPane(panel(settings, changed));
        window.setIconImage(icon(new java.awt.Color(215, 119, 87)));
        window.setSize(new Dimension(560, 600));
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        open = window;
    }

    /** A little Clawd for the window's corner (instead of Java's coffee cup). */
    static java.awt.image.BufferedImage icon(java.awt.Color body) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(body);
        g.fillRect(6, 9, 20, 13);  // body
        g.fillRect(2, 13, 4, 5);   // arms
        g.fillRect(26, 13, 4, 5);
        for (int x : new int[] {8, 12, 19, 23}) g.fillRect(x, 22, 2, 5); // legs
        g.setColor(new java.awt.Color(30, 30, 30));
        g.fillRect(10, 12, 3, 4);  // eyes
        g.fillRect(19, 12, 3, 4);
        g.dispose();
        return img;
    }

    /** The tabs (also drawn by the tests, without a screen). */
    static JComponent panel(java.util.function.Supplier<Settings> settings, Runnable changed) {
        Map<String, JPanel> groups = new LinkedHashMap<>();
        for (Options.Option o : Options.ALL) {
            JPanel list = groups.computeIfAbsent(o.group(), g -> {
                JPanel p = new JPanel();
                p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
                p.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                return p;
            });
            list.add(row(o, settings, changed));
        }
        JTabbedPane tabs = new JTabbedPane();
        for (var g : groups.entrySet()) {
            JPanel holder = new JPanel(new BorderLayout());
            holder.add(g.getValue(), BorderLayout.NORTH); // (rows at the top, not stretched)
            JScrollPane scroll = new JScrollPane(holder);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            tabs.addTab(g.getKey(), scroll);
        }
        JPanel all = new JPanel(new BorderLayout());
        JLabel hint = new JLabel("Changes work straight away. (The same options are in the terminal too: clawd controlpanel)");
        hint.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        all.add(tabs, BorderLayout.CENTER);
        all.add(hint, BorderLayout.SOUTH);
        return all;
    }

    private static Component row(Options.Option o, java.util.function.Supplier<Settings> settings, Runnable changed) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        switch (o.kind()) {
            case SWITCH -> {
                JCheckBox box = new JCheckBox(o.label(), settings.get().on(o.key()));
                box.addActionListener(e -> {
                    settings.get().set(o.key(), String.valueOf(box.isSelected()));
                    changed.run();
                });
                row.add(box);
            }
            case CHOICE -> {
                row.add(new JLabel(o.label() + ":"));
                JComboBox<String> pick = new JComboBox<>(o.choices().toArray(new String[0]));
                pick.setSelectedItem(settings.get().choice(o.key()));
                pick.addActionListener(e -> {
                    settings.get().set(o.key(), String.valueOf(pick.getSelectedItem()));
                    changed.run();
                });
                row.add(pick);
            }
            case NUMBER -> {
                row.add(new JLabel(o.label() + ":"));
                JSpinner number = new JSpinner(new SpinnerNumberModel(settings.get().number(o.key()), o.min(), o.max(), 1));
                number.addChangeListener(e -> {
                    settings.get().set(o.key(), String.valueOf(number.getValue()));
                    changed.run();
                });
                row.add(number);
            }
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        row.add(Box.createHorizontalGlue());
        return row;
    }
}
