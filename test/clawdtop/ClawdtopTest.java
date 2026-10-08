package clawdtop;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Clawdtop's tests: no window opens. Pictures of every mood go to build/frames, to look at. */
public class ClawdtopTest {
    private static int failures;

    static void check(String what, Object got, Object want) {
        boolean ok = Objects.equals(String.valueOf(got), String.valueOf(want));
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + what + " -> " + got + (ok ? "" : "   (wanted " + want + ")"));
    }

    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("clawdtop-test");
        System.setProperty("clawdtop.home", home.toString());

        // ---- How he behaves ----
        Pet pet = new Pet(1);
        check("he says hello when he starts", pet.takeBeep(), Pet.Beep.HELLO);
        check("and only once", pet.takeBeep(), null);
        pet.tick(33, 0, 0, false, false);
        check("he starts standing", pet.mood(), Pet.Mood.IDLE);

        pet.tick(33, 0, 0, false, true);
        check("a coding app in front makes him happy, with lit eyes", pet.mood() + " " + pet.eyesLit() + " " + pet.takeBeep(), "HAPPY true HAPPY");
        for (int i = 0; i < 100; i++) pet.tick(33, 0, 0, false, true);
        check("after a moment he calms down, but his eyes stay lit while it's in front", pet.mood() + " " + pet.eyesLit(), "IDLE true");
        pet.tick(33, 0, 0, false, false);
        check("eyes go back to normal when it's gone", pet.eyesLit(), false);

        pet.tick(33, 400, 0, true, false);
        for (int i = 0; i < 30; i++) pet.tick(33, 400, 0, false, false);
        check("his eyes follow the cursor (all the way right)", Math.round(pet.lookX() * 10) / 10.0, 1.0);
        for (int i = 0; i < 30; i++) pet.tick(33, -100, -400, false, false);
        check("and up and left", Math.round(pet.lookX() * 10) / 10.0 + " " + Math.round(pet.lookY() * 10) / 10.0, "-0.5 -1.0");

        // Leave him alone: he sits, lies down, then falls asleep
        Pet lonely = new Pet(2);
        lonely.takeBeep();
        String seen = "";
        for (int i = 0; i < 20 * 60 * 30; i++) { // 20 minutes, no mouse
            lonely.tick(33, 0, 0, false, false);
            String m = lonely.mood().name();
            if (!seen.endsWith(m)) seen += (seen.isEmpty() ? "" : ">") + m;
        }
        check("alone, he sits, lies down and falls asleep", seen.startsWith("IDLE>SIT>LIE") && seen.endsWith("SLEEP"), true);
        check("he yawns as he falls asleep", lonely.takeBeep(), Pet.Beep.YAWN);
        check("asleep, his eyes are shut and he doesn't watch the cursor", lonely.eyesShut() + " " + lonely.lookX(), "true 0.0");
        lonely.tick(33, 500, 0, true, false);
        check("moving the mouse far away doesn't wake him", lonely.mood(), Pet.Mood.SLEEP);
        lonely.tick(33, 40, 10, true, false);
        check("moving it near him does, with a beep", lonely.mood() + " " + lonely.takeBeep(), "IDLE WAKE");
        lonely.poke();
        check("clicking him makes him happy", lonely.mood() + " " + lonely.takeBeep(), "HAPPY CLICKED");

        // ---- His voice ----
        for (Pet.Beep beep : Pet.Beep.values()) {
            byte[] sound = Beeps.make(beep);
            int loudest = 0;
            for (int i = 0; i < sound.length; i += 2) loudest = Math.max(loudest, Math.abs((short) ((sound[i] & 0xFF) | sound[i + 1] << 8)));
            check(beep + " is a short, quiet beep", sound.length > 4000 && sound.length < 44100 && loudest < 32767 * 0.2 && loudest > 1000, true);
        }

        // ---- Coding apps ----
        check("coding apps", Foreground.isDevApp("Code.exe") + " " + Foreground.isDevApp("WindowsTerminal.exe") + " "
                + Foreground.isDevApp("idea64.exe") + " " + Foreground.isDevApp("chrome.exe") + " " + Foreground.isDevApp(null), "true true true false false");

        // ---- Tips ----
        Foreground.Front run = new Foreground.Front("explorer.exe", "#32770", "Run");
        Foreground.Front folder = new Foreground.Front("explorer.exe", "CabinetWClass", "Downloads");
        Foreground.Front code = new Foreground.Front("Code.exe", "Chrome_WidgetWin_1", "Clawd.java - Visual Studio Code");
        Foreground.Front game = new Foreground.Front("javaw.exe", "SDL_app", "Minecraft 26.3");
        check("the Run box, File Explorer and VS Code each have their tips; a game doesn't",
                Tips.kind(run) + " " + Tips.kind(folder) + " " + Tips.kind(code) + " " + Tips.kind(game), "run explorer vscode null");
        check("the Run box in Spanish counts too", Tips.kind(new Foreground.Front("explorer.exe", "#32770", "Ejecutar")), "run");
        check("other explorer dialogs aren't the Run box", Tips.kind(new Foreground.Front("explorer.exe", "#32770", "Copying 3 items")), "null");
        check("the Run box's tips show straight away, others wait their turn", Tips.urgent(run) + " " + Tips.urgent(code), "true false");
        Tips tips = new Tips();
        String first = tips.tipFor(code);
        String second = tips.tipFor(code);
        check("tips come round in turn, not the same twice in a row", !first.equals(second), true);
        check("Run box tips list commands", tips.tipFor(run).contains("%temp%"), true);
        pet.speak();
        check("he chirps when he has a tip", pet.takeBeep(), Pet.Beep.TIP);

        // ---- Settings and starting with Windows ----
        Settings s = Settings.load();
        check("settings start as beeps on, normal size, above the clock", s.sounds() + " " + s.size() + " " + s.x() + " " + s.unit(), "true Normal -1 3");
        s.setSounds(false);
        s.setSize("Big");
        s.setX(1200);
        Settings again = Settings.load();
        check("and are kept for next time", again.sounds() + " " + again.size() + " " + again.x() + " " + again.unit(), "false Big 1200 5");
        Files.writeString(home.resolve("settings.properties"), "size=Huge\nx=nope\n");
        Settings odd = Settings.load();
        check("odd settings fall back to normal", odd.size() + " " + odd.x(), "Normal -1");
        check("the Windows startup script runs Java with no window", Startup.script("C:\\Java\\bin\\javaw.exe", "C:\\Clawdtop\\Clawdtop.jar"),
                "Set shell = CreateObject(\"WScript.Shell\")\r\nshell.Run \"\"\"C:\\Java\\bin\\javaw.exe\"\" --enable-native-access=ALL-UNNAMED -jar \"\"C:\\Clawdtop\\Clawdtop.jar\"\"\", 0, False\r\n");

        // ---- Pictures of every mood, to look at ----
        Path frames = Path.of("build", "frames");
        Files.createDirectories(frames);
        Pet model = new Pet(3);
        model.takeBeep();
        save(model, frames.resolve("idle.png"));
        model.tick(33, 300, -50, true, false);
        for (int i = 0; i < 20; i++) model.tick(33, 300, -50, false, false);
        save(model, frames.resolve("looking right.png"));
        model.tick(33, 0, 0, false, true);
        for (int i = 0; i < 4; i++) model.tick(33, 0, 0, false, true);
        save(model, frames.resolve("happy (coding app).png"));
        Pet sleepy = new Pet(4);
        for (int i = 0; i < 20 * 60 * 30 && sleepy.mood() != Pet.Mood.SIT; i++) sleepy.tick(33, 0, 0, false, false);
        save(sleepy, frames.resolve("sitting.png"));
        for (int i = 0; i < 20 * 60 * 30 && sleepy.mood() != Pet.Mood.LIE; i++) sleepy.tick(33, 0, 0, false, false);
        save(sleepy, frames.resolve("lying down.png"));
        for (int i = 0; i < 20 * 60 * 30 && sleepy.mood() != Pet.Mood.SLEEP; i++) sleepy.tick(33, 0, 0, false, false);
        for (int i = 0; i < 40; i++) sleepy.tick(33, 0, 0, false, false);
        save(sleepy, frames.resolve("asleep.png"));
        String[] bubbleLines = new Tips().tipFor(run).split("\n");
        java.awt.Dimension bubbleSize = Bubble.size(bubbleLines);
        BufferedImage bubble = new BufferedImage(bubbleSize.width, bubbleSize.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = bubble.createGraphics();
        bg.setColor(new java.awt.Color(32, 32, 36));
        bg.fillRect(0, 0, bubble.getWidth(), bubble.getHeight());
        Bubble.paint(bg, bubbleLines, bubble.getWidth(), bubble.getHeight());
        bg.dispose();
        ImageIO.write(bubble, "png", frames.resolve("tip bubble.png").toFile());
        check("pictures of every mood (and a tip) are in build/frames", Files.list(frames).count() >= 7, true);

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** One picture, 8 screen pixels to his pixel, on a taskbar-gray background so the see-through parts show. */
    static void save(Pet pet, Path file) throws Exception {
        int unit = 8;
        BufferedImage image = new BufferedImage(Sprite.WIDTH * unit, Sprite.HEIGHT * unit, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new java.awt.Color(32, 32, 36));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        Sprite.draw(g, pet, unit);
        g.dispose();
        ImageIO.write(image, "png", file.toFile());
    }
}
