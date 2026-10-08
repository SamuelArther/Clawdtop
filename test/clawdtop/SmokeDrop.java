package clawdtop;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The screen test for dropping a MIDI file on him with a real drag (from a little window that holds the file). */
public final class SmokeDrop {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        Files.writeString(home.resolve("settings.properties"), "met=true\nname=Tester\nbeeps=false\ntips=false\nmetDate=2026-10-08\n");
        javax.sound.midi.Sequence seq = new javax.sound.midi.Sequence(javax.sound.midi.Sequence.PPQ, 4);
        javax.sound.midi.Track track = seq.createTrack();
        int[] tune = {64, 62, 60, 62, 64, 64, 64};
        for (int i = 0; i < tune.length; i++) track.add(new javax.sound.midi.MidiEvent(new javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_ON, 0, tune[i], 90), i * 4L));
        File midi = home.resolve("Mary.mid").toFile();
        javax.sound.midi.MidiSystem.write(seq, 0, midi);

        Clawdtop[] clawd = new Clawdtop[1];
        JFrame[] source = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            clawd[0] = new Clawdtop();
            clawd[0].start();
            JLabel label = new JLabel("  drag me: Mary.mid  ");
            label.setTransferHandler(new TransferHandler() {
                @Override
                public int getSourceActions(javax.swing.JComponent c) {
                    return COPY;
                }

                @Override
                protected Transferable createTransferable(javax.swing.JComponent c) {
                    return new Transferable() {
                        public DataFlavor[] getTransferDataFlavors() {
                            return new DataFlavor[] {DataFlavor.javaFileListFlavor};
                        }

                        public boolean isDataFlavorSupported(DataFlavor f) {
                            return f.equals(DataFlavor.javaFileListFlavor);
                        }

                        public Object getTransferData(DataFlavor f) {
                            return List.of(midi);
                        }
                    };
                }
            });
            label.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    label.getTransferHandler().exportAsDrag(label, e, TransferHandler.COPY);
                }
            });
            source[0] = new JFrame("drag source");
            source[0].add(label);
            source[0].setSize(220, 80);
            source[0].setLocation(500, 400);
            source[0].setAlwaysOnTop(true);
            source[0].setVisible(true);
        });
        Robot robot = new Robot();
        Rectangle all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        Thread.sleep(3000);
        double[] spot = new double[3];
        SwingUtilities.invokeAndWait(() -> System.arraycopy(clawd[0].smokeHome(), 0, spot, 0, 3));
        robot.mouseMove(600, 435);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        int tx = (int) spot[0], ty = (int) (spot[1] - 5 * spot[2]);
        for (int i = 1; i <= 40; i++) {
            robot.mouseMove(600 + (tx - 600) * i / 40, 435 + (ty - 435) * i / 40);
            Thread.sleep(25);
        }
        Thread.sleep(500);
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "1 dragging over him.png"));
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        SwingUtilities.invokeAndWait(() -> source[0].dispose());
        robot.mouseMove(all.width / 3, all.height / 3);
        for (int i = 0; i < 12; i++) {
            Thread.sleep(400);
            ImageIO.write(robot.createScreenCapture(all), "png", new File(out, String.format("2 after drop %02d.png", i)));
        }
        String[] mood = {null};
        SwingUtilities.invokeAndWait(() -> mood[0] = clawd[0].smokeTyped());
        System.out.println("after the drop: " + mood[0]);
        System.exit(0);
    }
}
