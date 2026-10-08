package clawdtop;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** The screen test for full-screen games: a pretend game fills the screen; he should sit in the corner and watch. */
public final class SmokeGame {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        Files.writeString(home.resolve("settings.properties"), "met=true\nname=Tester\nbeeps=false\ntips=false\nmetDate=2026-10-08\n");
        Clawdtop[] clawd = new Clawdtop[1];
        JFrame[] game = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            clawd[0] = new Clawdtop();
            clawd[0].start();
        });
        Robot robot = new Robot();
        Rectangle all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        Thread.sleep(2500);
        SwingUtilities.invokeAndWait(() -> {
            game[0] = new JFrame("Pretend Game");
            game[0].setUndecorated(true);
            game[0].setContentPane(new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(new Color(30, 60, 30));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(Color.RED);
                    g.fillRect(20, getHeight() - 40, 200, 18); // a health bar
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("Segoe UI", Font.BOLD, 28));
                    g.drawString("PRETEND GAME", getWidth() / 2 - 110, getHeight() / 2);
                    g.drawString("AMMO 30/90", getWidth() - 220, getHeight() - 24);
                }
            });
            game[0].setBounds(all);
            game[0].setVisible(true);
            game[0].toFront();
        });
        Thread.sleep(3500);
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "1 in a game.png"));
        SwingUtilities.invokeAndWait(() -> game[0].dispose());
        Thread.sleep(3000);
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "2 game closed.png"));
        System.exit(0);
    }
}
