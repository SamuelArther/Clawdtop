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
        Pet napper = new Pet(2);
        napper.takeBeep();
        for (int i = 0; i < 20 * 60 * 30 && napper.mood() != Pet.Mood.SLEEP; i++) napper.tick(33, 0, 0, false, false);
        napper.takeBeep();
        check("asleep, he's sleepy", napper.sleepy(), true);
        napper.poke();
        check("tapping him wakes him up with a little hop", napper.mood() + " " + napper.takeBeep() + " " + napper.sleepy(), "HAPPY WAKE false");
        lonely.tick(33, 40, 10, true, false);
        check("moving it near him does, with a beep", lonely.mood() + " " + lonely.takeBeep(), "IDLE WAKE");
        lonely.poke();
        check("clicking him makes him happy", lonely.mood() + " " + lonely.takeBeep(), "HAPPY CLICKED");

        // ---- Riding your cursor ----
        Body body = new Body();
        double perch = 1800, ground = 1032, reach = 36;
        body.tick(33, 1000, 500, perch, ground, reach, 0, 1920);
        check("he sits on his perch", body.state() + " " + body.x() + " " + body.y(), "HOME 1800.0 1032.0");
        for (int i = 0; i < 20; i++) body.tick(33, perch + 5, ground, perch, ground, reach, 0, 1920);
        check("the cursor on top of him doesn't make him hop on", body.state(), Body.State.HOME);
        for (int i = 0; i < 20; i++) body.tick(33, perch - 25, ground + 3, perch, ground, reach, 0, 1920);
        check("waiting on the taskbar's edge beside him: he hops on", body.state(), Body.State.HOP_ON);
        for (int i = 0; i < 12; i++) body.tick(33, perch - 25, ground + 3, perch, ground, reach, 0, 1920);
        check("and rides the cursor", body.state() + " " + body.x() + " " + body.y(), "RIDE 1775.0 1035.0");
        Pet rider = new Pet(5);
        rider.takeBeep();
        rider.follow(body.state());
        check("riding makes him go whee", rider.mood() + " " + rider.takeBeep(), "RIDE WHEE");
        double cx = 1000;
        for (int i = 0; i < 30; i++) {
            cx += 20; // a smooth trip across the screen
            body.tick(33, cx, 600, perch, ground, reach, 0, 1920);
        }
        check("he stays on for a smooth ride", body.state() + " " + body.x() + " " + body.y(), "RIDE 1600.0 600.0");
        for (int i = 0; i < 16 && body.state() == Body.State.RIDE; i++) body.tick(33, 1600 + (i % 2 == 0 ? 120 : -120), 600, perch, ground, reach, 0, 1920);
        check("shaking the cursor throws him off", body.state(), Body.State.FALL);
        for (int i = 0; i < 6; i++) body.tick(33, 0, 0, perch, ground, reach, 0, 1920);
        check("he flips over, head first, as he falls", Math.round(body.angle() * 100) / 100.0, Math.round(Math.PI * 100) / 100.0);
        int fallTicks = 0;
        while (body.state() == Body.State.FALL && fallTicks++ < 200) body.tick(33, 0, 0, perch, ground, reach, 0, 1920);
        check("and lands on his head on the taskbar", body.state() + " " + body.y(), "DIZZY 1032.0");
        rider.follow(Body.State.FALL);
        rider.follow(body.state());
        check("oof", rider.mood() + " " + rider.takeBeep(), "DIZZY OOF");
        while (body.state() == Body.State.DIZZY) body.tick(33, 0, 0, perch, ground, reach, 0, 1920);
        check("then gets back on his feet and shakes it off", body.state() + " " + body.angle(), "SHAKE 0.0");
        while (body.state() == Body.State.SHAKE) body.tick(33, 0, 0, perch, ground, reach, 0, 1920);
        check("and walks home", body.state(), Body.State.WALK);
        int walkTicks = 0;
        while (body.state() == Body.State.WALK && walkTicks++ < 2000) body.tick(33, 0, 0, perch, ground, reach, 0, 1920);
        check("all the way back to his perch", body.state() + " " + body.x(), "HOME 1800.0");
        rider.follow(body.state());
        check("and he's himself again", rider.mood(), Pet.Mood.IDLE);
        Body boxBody = new Body();
        boxBody.tick(33, 0, 0, perch, ground, reach, 0, 1920);
        boxBody.launch(300);
        String boxSeen = "";
        double highest = ground;
        for (int i = 0; i < 2000 && (i == 0 || boxBody.state() != Body.State.HOME); i++) {
            boxBody.tick(33, 0, 0, perch, ground, reach, 0, 1920);
            highest = Math.min(highest, boxBody.y());
            String st = boxBody.state().name();
            if (!boxSeen.endsWith(st)) boxSeen += (boxSeen.isEmpty() ? "" : ">") + st;
        }
        check("out of his box he shoots up high, lands on his head, gets up, shakes it off and walks back",
                boxSeen + " " + (ground - highest > 400), "FALL>DIZZY>SHAKE>WALK>HOME true");
        Body gentle = new Body();
        for (int i = 0; i < 40; i++) gentle.tick(33, perch - 25, ground, perch, ground, reach, 0, 1920);
        for (int i = 0; i < 40; i++) gentle.tick(33, 1500, ground + 2, perch, ground, reach, 0, 1920);
        check("bring him back down to the taskbar and stop: he hops off and walks home", gentle.state(), Body.State.WALK);

        // ---- Colors, moods, sadness and goodbye ----
        Pet painted = new Pet(11);
        painted.takeBeep();
        painted.changeColor(java.awt.Color.decode("#5B8DEF"));
        for (int i = 0; i < 90; i++) painted.tick(33, 0, 0, false, false);
        check("a new color waits a few seconds (still orange)", painted.color().getRGB() == new java.awt.Color(215, 119, 87).getRGB(), true);
        for (int i = 0; i < 30; i++) painted.tick(33, 0, 0, false, false);
        check("then he's suddenly blue and freaks out", painted.mood() + " " + painted.takeBeep() + " " + painted.takeLine(),
                "FREAKOUT PANIC WHAT?! WHAT HAPPENED TO ME?!");
        Path freakFrames = Path.of("build", "frames");
        Files.createDirectories(freakFrames);
        save(painted, freakFrames.resolve("freaking out (new color).png"));
        for (int i = 0; i < 120; i++) painted.tick(33, 0, 0, false, false);
        check("and calms down", painted.mood() + " " + painted.takeLine(), "IDLE ...huh. Actually, I kinda like it.");
        painted.sad();
        check("a coding app closed: sad for a moment", painted.mood() + " " + painted.takeBeep(), "SAD AWW");
        save(painted, freakFrames.resolve("sad (coding app closed).png"));
        for (int i = 0; i < 60; i++) painted.tick(33, 0, 0, false, false);
        check("then back to normal", painted.mood(), Pet.Mood.IDLE);
        painted.ask("asleep");
        check("the control panel can send him to sleep", painted.mood(), Pet.Mood.SLEEP);
        painted.ask("awake");
        check("and wake him", painted.mood(), Pet.Mood.IDLE);
        Pet leaving = new Pet(12);
        leaving.takeBeep();
        leaving.ask("goodbye");
        check("goodbye: \"Well..... bye.....\"", leaving.takeLine(), "Well..... bye.....");
        for (int i = 0; i < 130; i++) leaving.tick(33, 0, 0, false, false);
        double half = leaving.crumbled();
        BufferedImage dust = new BufferedImage((Sprite.WIDTH + 14) * 8, (Sprite.HEIGHT + 14) * 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D dg = dust.createGraphics();
        dg.setColor(new java.awt.Color(32, 32, 36));
        dg.fillRect(0, 0, dust.getWidth(), dust.getHeight());
        dg.translate(0, 14 * 8);
        Sprite.drawCrumbling(dg, leaving, 8, half);
        dg.dispose();
        ImageIO.write(dust, "png", freakFrames.resolve("crumbling away.png").toFile());
        for (int i = 0; i < 200 && !leaving.gone(); i++) leaving.tick(33, 0, 0, false, false);
        check("then he crumbles away and is gone", (half > 0 && half < 1) + " " + leaving.gone(), "true true");

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
        check("and his mouth moves while he beeps", pet.talking() + " " + pet.mouthOpen(), "true true");
        pet.tick(80, 0, 0, false, false);
        check("flapping shut and open", pet.mouthOpen(), false);
        for (int i = 0; i < 20; i++) pet.tick(33, 0, 0, false, false);
        check("then it's gone when he's quiet", pet.talking(), false);

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

        // ---- Meeting him the first time ----
        Files.deleteIfExists(home.resolve("settings.properties"));
        Settings fresh = Settings.load();
        check("before you meet, he doesn't know you", fresh.met() + " '" + fresh.name() + "' " + fresh.spot(), "false '' Above the clock");
        java.util.List<Pet.Beep> chirps = new java.util.ArrayList<>();
        int[] movedCount = {0};
        Welcome hello = new Welcome(fresh, () -> movedCount[0]++, chirps::add);
        hello.start(new java.awt.Rectangle(1800, 1000, 63, 45), new java.awt.Rectangle(0, 0, 1920, 1080));
        Path frames0 = Path.of("build", "frames");
        Files.createDirectories(frames0);
        snapshot(hello.panel(), frames0.resolve("welcome 1 name.png"));
        javax.swing.JTextField nameBox = find(hello.panel(), javax.swing.JTextField.class);
        nameBox.setText("  Samuel  ");
        click(hello.panel(), "Next");
        check("he learns your name", fresh.name(), "Samuel");
        snapshot(hello.panel(), frames0.resolve("welcome 2 where.png"));
        click(hello.panel(), "In the middle");
        check("and where you want him", fresh.spot() + " " + movedCount[0], "In the middle 1");
        snapshot(hello.panel(), frames0.resolve("welcome 3 personality.png"));
        click(hello.panel(), "Sleepy");
        check("and what he's like", fresh.personality(), Pet.Personality.SLEEPY);
        snapshot(hello.panel(), frames0.resolve("welcome 4 beeps.png"));
        click(hello.panel(), "Shh, no beeps");
        check("beeps off if you say so", fresh.sounds(), false);
        click(hello.panel(), "Not now");
        snapshot(hello.panel(), frames0.resolve("welcome 5 done.png"));
        Settings later = Settings.load();
        check("and remembers you met, for next time", later.met() + " " + later.name() + " " + later.spot(), "true Samuel In the middle");
        check("he chirps along, starting with hello", chirps.get(0), Pet.Beep.HELLO);

        // ---- The clawd command ----
        check("clawd.cmd runs the command part of Clawdtop with console Java",
                Install.script(Path.of("C:\\Java\\bin\\javaw.exe"), Path.of("C:\\Clawdtop\\build\\Clawdtop.jar")),
                "@echo off\r\n\"C:\\Java\\bin\\java.exe\" --enable-native-access=ALL-UNNAMED -cp \"C:\\Clawdtop\\build\\Clawdtop.jar\" clawdtop.Cli %*\r\n");
        String userPath = "%USERPROFILE%\\bin;C:\\Tools;C:\\Users\\me\\AppData\\Local\\Clawdtop\\bin";
        check("finds its folder on your PATH (any capitals)", Install.hasEntry(userPath, "c:\\users\\me\\appdata\\local\\clawdtop\\bin")
                + " " + Install.hasEntry("C:\\Tools", "C:\\Users\\me\\AppData\\Local\\Clawdtop\\bin"), "true false");
        check("and takes only its own folder back out, leaving %VARIABLES% as they were",
                Install.withoutEntry(userPath, "C:\\Users\\me\\AppData\\Local\\Clawdtop\\bin"), "%USERPROFILE%\\bin;C:\\Tools");
        check("clawd help", cli("help").contains("clawd controlpanel"), true);
        check("clawd status says who he knows", cli("status").contains("Name:        Samuel"), true);
        check("an unknown command says what he can do", cli("dance").contains("I don't know \"dance\""), true);
        String panel = cli("controlpanel", "1", "Sam", "7", "8", "2", "3", "3", "4", "2", "5", "2", "5", "9", "#33aaff", "0");
        Settings afterPanel = Settings.load();
        check("the control panel changes his name, beeps, tips, spot, size, personality and color", afterPanel.name() + " "
                + afterPanel.sounds() + " " + afterPanel.tips() + " " + afterPanel.spot() + " " + afterPanel.size() + " "
                + afterPanel.personality() + " " + afterPanel.color(), "Sam true false On the left Big BOUNCY #33AAFF");

        // ---- Save tokens ----
        String token = afterPanel.saveToken();
        check("a save token starts with CLAWD-", token.startsWith("CLAWD-"), true);
        check("and holds who he is to you", SaveToken.read(token).toString(), "{name=Sam, color=#33AAFF, personality=BOUNCY, spot=On the left, size=Big}");
        check("a mistyped token is caught", SaveToken.read(token.substring(0, 10) + "x" + token.substring(11)) + " " + SaveToken.read("hello"), "null null");
        Files.deleteIfExists(home.resolve("settings.properties"));
        Settings reborn = Settings.load();
        check("a fresh Clawd with your token remembers you", reborn.useToken(token) + " " + reborn.name() + " " + reborn.color() + " "
                + reborn.personality() + " " + reborn.restored(), "true Sam #33AAFF BOUNCY true");
        check("a wrong token changes nothing", Settings.load().useToken("CLAWD-nope-0000"), false);
        check("and shows his settings", panel.contains("Clawd's control panel"), true);
        check("uninstall asks first, and no means no", cli("uninstall", "n").contains("He's staying"), true);

        // ---- Cleaning a folder (on a pretend mini PC) ----
        CleanerTest.run();

        // ---- Pictures of every mood, to look at ----
        Path frames = Path.of("build", "frames");
        Files.createDirectories(frames);
        Pet model = new Pet(3);
        model.takeBeep();
        for (int i = 0; i < 20; i++) model.tick(33, 0, 0, false, false); // done saying hello
        save(model, frames.resolve("idle.png"));
        model.speak();
        model.takeBeep();
        save(model, frames.resolve("talking.png"));
        for (int i = 0; i < 20; i++) model.tick(33, 0, 0, false, false);
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
        Pet moving = new Pet(6);
        moving.takeBeep();
        moving.follow(Body.State.RIDE);
        save(moving, frames.resolve("riding.png"));
        moving.follow(Body.State.FALL);
        save(moving, frames.resolve("falling.png"), Math.PI * 0.5);
        moving.follow(Body.State.DIZZY);
        for (int i = 0; i < 5; i++) moving.tick(33, 0, 0, false, false);
        save(moving, frames.resolve("dizzy, head first.png"), Math.PI);
        moving.follow(Body.State.WALK);
        save(moving, frames.resolve("walking home.png"));
        moving.follow(Body.State.SHAKE);
        save(moving, frames.resolve("shaking it off.png"));
        BufferedImage boxPicture = new BufferedImage((Box.WIDTH + 8) * 8, (Box.HEIGHT + 6) * 8 * 2 + 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D bxg = boxPicture.createGraphics();
        bxg.setColor(new java.awt.Color(32, 32, 36));
        bxg.fillRect(0, 0, boxPicture.getWidth(), boxPicture.getHeight());
        Box.draw(bxg, 8, 1000, false);
        bxg.translate(0, (Box.HEIGHT + 6) * 8 + 8);
        Box.draw(bxg, 8, 1000, true);
        bxg.dispose();
        ImageIO.write(boxPicture, "png", frames.resolve("box (shut, then open).png").toFile());
        Pet worker = new Pet(7);
        worker.takeBeep();
        worker.job(Pet.Mood.WORK);
        worker.follow(Body.State.PERCH);
        for (int i = 0; i < 20; i++) worker.tick(33, 0, 0, false, false);
        save(worker, frames.resolve("working on his laptop.png"));
        worker.job(Pet.Mood.PEEK);
        worker.follow(Body.State.PERCH);
        save(worker, frames.resolve("peeking hands.png"));
        worker.job(null);
        worker.follow(Body.State.PERCH);
        BufferedImage climb = new BufferedImage(Sprite.WIDTH * 8, Sprite.HEIGHT * 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D cg = climb.createGraphics();
        cg.setColor(new java.awt.Color(32, 32, 36));
        cg.fillRect(0, 0, climb.getWidth(), climb.getHeight());
        Sprite.drawRising(cg, worker, 8, 5);
        cg.dispose();
        ImageIO.write(climb, "png", frames.resolve("climbing up to ask.png").toFile());
        String[] question = {"Is it OK to delete setup_game.exe?", "It's an installer from 3 months ago (34.0 MB)."};
        String[] answers = {"Yes", "No", "Stop"};
        java.awt.Dimension qs = Bubble.size(question, answers);
        BufferedImage ask = new BufferedImage(qs.width, qs.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ag = ask.createGraphics();
        ag.setColor(new java.awt.Color(32, 32, 36));
        ag.fillRect(0, 0, ask.getWidth(), ask.getHeight());
        Bubble.paint(ag, question, answers, qs.width, qs.height);
        ag.dispose();
        ImageIO.write(ask, "png", frames.resolve("asking.png").toFile());
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

    /** Runs a clawd command with typed answers, and gives back what it printed. */
    static String cli(String command, String... typed) throws Exception {
        java.io.ByteArrayOutputStream printed = new java.io.ByteArrayOutputStream();
        java.io.PrintStream out = new java.io.PrintStream(printed, true, java.nio.charset.StandardCharsets.UTF_8);
        java.io.BufferedReader in = new java.io.BufferedReader(new java.io.StringReader(String.join("\n", typed) + "\n"));
        new Cli(out, in).run(command);
        return printed.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Lays out a Swing panel with no window and saves a picture of it. */
    static void snapshot(javax.swing.JComponent panel, Path file) throws Exception {
        java.awt.Dimension size = panel.getPreferredSize();
        panel.setSize(size);
        layout(panel);
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new java.awt.Color(32, 32, 36));
        g.fillRect(0, 0, size.width, size.height);
        panel.printAll(g);
        g.dispose();
        ImageIO.write(image, "png", file.toFile());
    }

    static void layout(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component child : c.getComponents()) if (child instanceof java.awt.Container cc) layout(cc);
    }

    static <T> T find(java.awt.Container c, Class<T> kind) {
        for (java.awt.Component child : c.getComponents()) {
            if (kind.isInstance(child)) return kind.cast(child);
            if (child instanceof java.awt.Container cc) {
                T found = find(cc, kind);
                if (found != null) return found;
            }
        }
        return null;
    }

    static void click(java.awt.Container c, String label) {
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof javax.swing.JButton b && b.getText().equals(label)) {
                b.doClick();
                return;
            }
            if (child instanceof java.awt.Container cc && !(child instanceof javax.swing.JButton)) {
                int before = cc.getComponentCount();
                click(cc, label);
            }
        }
    }

    /** One picture, 8 screen pixels to his pixel, on a taskbar-gray background so the see-through parts show. */
    static void save(Pet pet, Path file) throws Exception {
        save(pet, file, 0);
    }

    static void save(Pet pet, Path file, double angle) throws Exception {
        int unit = 8;
        BufferedImage image = new BufferedImage(Sprite.WIDTH * unit, Sprite.HEIGHT * unit, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new java.awt.Color(32, 32, 36));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        Sprite.drawTurned(g, pet, unit, angle);
        g.dispose();
        ImageIO.write(image, "png", file.toFile());
    }
}
