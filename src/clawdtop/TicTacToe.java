package clawdtop;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;
import java.util.function.Consumer;

/** Tic-tac-toe against Clawd: you're X, he's O. He's pretty good, but not perfect (he's a crab). */
final class TicTacToe {
    static final int CELL = 46, PAD = 12, TOP = 30;
    private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

    final char[] board = new char[9];
    private final Random random;
    private final TypingWindow window = java.awt.GraphicsEnvironment.isHeadless() ? null : new TypingWindow();
    private Consumer<String> over = r -> { };

    TicTacToe(Random random) {
        this.random = random;
        java.util.Arrays.fill(board, ' ');
    }

    /** Who's won ('X' or 'O'), 'T' for a tie, or ' ' if it's still going. */
    static char winner(char[] b) {
        for (int[] l : LINES) if (b[l[0]] != ' ' && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]];
        for (char c : b) if (c == ' ') return ' ';
        return 'T';
    }

    /** Clawd's move: win if he can, block you if he must, otherwise the middle, a corner, anything (and sometimes he just
     * picks at random: he's a crab). The square he picks, or -1. */
    int clawdMove() {
        if (random.nextInt(5) == 0) return randomFree(); // a crab moment
        for (char who : new char[] {'O', 'X'}) {
            for (int[] l : LINES) {
                int mine = 0, free = -1;
                for (int i : l) {
                    if (board[i] == who) mine++;
                    else if (board[i] == ' ') free = i;
                }
                if (mine == 2 && free >= 0) return free;
            }
        }
        if (board[4] == ' ') return 4;
        int[] corners = {0, 2, 6, 8};
        for (int k = 0; k < 4; k++) {
            int c = corners[random.nextInt(4)];
            if (board[c] == ' ') return c;
        }
        return randomFree();
    }

    private int randomFree() {
        int free = 0;
        for (char c : board) if (c == ' ') free++;
        if (free == 0) return -1;
        int pick = random.nextInt(free);
        for (int i = 0; i < 9; i++) if (board[i] == ' ' && pick-- == 0) return i;
        return -1;
    }

    /** You play square i; then he answers. Returns the result so far (see winner). */
    char play(int i) {
        if (i < 0 || i > 8 || board[i] != ' ' || winner(board) != ' ') return winner(board);
        board[i] = 'X';
        if (winner(board) == ' ') {
            int m = clawdMove();
            if (m >= 0) board[m] = 'O';
        }
        return winner(board);
    }

    /** Shows the game above Clawd; done gets "you", "clawd" or "tie" when it ends. */
    void show(Rectangle clawd, Rectangle screen, Consumer<String> done) {
        over = done;
        if (window == null) return;
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(java.awt.AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(java.awt.AlphaComposite.SrcOver);
                TicTacToe.paint(g2, board, getWidth(), getHeight());
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getY() < TOP && e.getX() > panel.getWidth() - 26) {
                    window.setVisible(false);
                    return;
                }
                int col = (e.getX() - PAD) / CELL, row = (e.getY() - TOP) / CELL;
                if (col < 0 || col > 2 || row < 0 || row > 2 || winner(board) != ' ') return;
                char r = play(row * 3 + col);
                panel.repaint();
                if (r != ' ') {
                    over.accept(r == 'X' ? "you" : r == 'O' ? "clawd" : "tie");
                    javax.swing.Timer close = new javax.swing.Timer(1800, x -> window.setVisible(false));
                    close.setRepeats(false);
                    close.start();
                }
            }
        });
        window.setContentPane(panel);
        Dimension size = new Dimension(CELL * 3 + PAD * 2, CELL * 3 + TOP + PAD);
        window.setSize(size);
        int x = Math.max(screen.x + 4, Math.min(screen.x + screen.width - size.width - 4, clawd.x + clawd.width / 2 - size.width + 40));
        window.setLocation(x, Math.max(screen.y + 4, clawd.y - size.height - 56)); // above his speech bubble
        window.showAndFocus(panel);
    }

    /** Draws the board: paper, a grid, red X's and orange O's (his are little crab-colored circles). */
    static void paint(Graphics2D g, char[] board, int width, int height) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(255, 250, 242));
        g.fillRoundRect(1, 1, width - 2, height - 2, 14, 14);
        g.setColor(new Color(215, 119, 87));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(1, 1, width - 3, height - 3, 14, 14);
        g.setColor(new Color(40, 38, 36));
        g.setFont(Bubble.FIRST_LINE);
        g.drawString("Tic-tac-toe", PAD, 20);
        g.drawString("x", width - 18, 20);
        g.setColor(new Color(200, 190, 180));
        for (int i = 1; i < 3; i++) {
            g.fillRect(PAD + i * CELL - 1, TOP, 2, CELL * 3);
            g.fillRect(PAD, TOP + i * CELL - 1, CELL * 3, 2);
        }
        g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 9; i++) {
            int x = PAD + (i % 3) * CELL, y = TOP + (i / 3) * CELL, m = 11;
            if (board[i] == 'X') {
                g.setColor(new Color(60, 110, 210));
                g.drawLine(x + m, y + m, x + CELL - m, y + CELL - m);
                g.drawLine(x + CELL - m, y + m, x + m, y + CELL - m);
            } else if (board[i] == 'O') {
                g.setColor(new Color(215, 119, 87));
                g.drawOval(x + m, y + m, CELL - 2 * m, CELL - 2 * m);
            }
        }
    }
}
