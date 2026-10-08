package clawdtop;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

        // ---- Clawd Points and the shop ----
        Files.deleteIfExists(home.resolve("settings.properties"));
        Settings wallet = Settings.load();
        check("no points to start with", wallet.points(), 0);
        check("can't buy a crown with no points", Shop.buy(wallet, Shop.find("crown")), false);
        wallet.earn(50);
        check("buying a party hat costs 15 and he puts it on", Shop.buy(wallet, Shop.find("party-hat")) + " " + wallet.points() + " "
                + wallet.owns("party-hat") + " " + wallet.wearing("hat"), "true 35 true party-hat");
        check("can't buy the same thing twice", Shop.buy(wallet, Shop.find("party-hat")), false);
        Shop.buy(wallet, Shop.find("juggling"));
        check("tricks are learned", wallet.owns("juggling") + " " + wallet.points(), "true 5");
        for (int i = 0; i < 35; i++) {
            if (wallet.petsToday() < Shop.PETS_A_DAY) {
                wallet.countPet();
                wallet.earn(Shop.PET);
            }
        }
        check("petting points stop at 30 a day", wallet.points(), 35);
        check("the save token keeps his points and things", SaveToken.read(wallet.saveToken()).get("owned"), "party-hat,juggling");
        Pet dresser = new Pet(13);
        dresser.takeBeep();
        dresser.setItems(true, true, "crown");
        Path shopFrames = Path.of("build", "frames");
        Files.createDirectories(shopFrames);
        for (String h : new String[] {"party-hat", "top-hat", "crown"}) {
            dresser.setItems(true, true, h);
            for (int i = 0; i < 20; i++) dresser.tick(33, 0, 0, false, false);
            save(dresser, shopFrames.resolve("hat " + h + ".png"));
        }
        dresser.petted();
        for (int i = 0; i < 10; i++) dresser.tick(33, 0, 0, false, false);
        save(dresser, shopFrames.resolve("petted.png"));
        dresser.dance();
        for (int i = 0; i < 8; i++) dresser.tick(33, 0, 0, false, false);
        save(dresser, shopFrames.resolve("dancing.png"));
        check("dancing is a mood", dresser.mood(), Pet.Mood.DANCE);
        for (String h : new String[] {"cardboard-hut", "wooden-hut", "castle"}) {
            BufferedImage hutPicture = new BufferedImage(Hut.WIDTH * 8, Hut.HEIGHT * 8, BufferedImage.TYPE_INT_ARGB);
            Graphics2D hg = hutPicture.createGraphics();
            hg.setColor(new java.awt.Color(32, 32, 36));
            hg.fillRect(0, 0, hutPicture.getWidth(), hutPicture.getHeight());
            Hut.draw(hg, 8, h);
            hg.dispose();
            ImageIO.write(hutPicture, "png", shopFrames.resolve("hut " + h + ".png").toFile());
        }

        // ---- Being yelled at, and clicked too much ----
        Pet ears = new Pet(14);
        ears.takeBeep();
        ears.capsLock(true);
        check("Caps Lock on: WHY ARE WE YELLING", ears.mood() + " " + ears.takeLine(), "YELLED WHY ARE WE YELLING?!");
        save(ears, Path.of("build", "frames").resolve("caps lock.png"));
        ears.capsLock(false);
        check("off again: thanks", ears.takeLine(), "...thank you.");
        ears.annoyed("Okay, okay! I'm awake!");
        check("clicked too much: grumpy", ears.mood(), Pet.Mood.ANNOYED);
        save(ears, Path.of("build", "frames").resolve("annoyed.png"));
        Body huffy = new Body();
        huffy.tick(33, 0, 0, 1800, 1032, 36, 0, 1920);
        huffy.walkOff(2070, 15_000);
        String huffSeen = "";
        for (int i = 0; i < 3000 && (i == 0 || huffy.state() != Body.State.HOME); i++) {
            huffy.tick(33, 0, 0, 1800, 1032, 36, 0, 1920);
            if (!huffSeen.endsWith(huffy.state().name())) huffSeen += (huffSeen.isEmpty() ? "" : ">") + huffy.state().name();
        }
        check("way too many clicks: he stomps off the screen, stays away a while, then walks back", huffSeen, "AWAY>OUT>WALK>HOME");

        // ---- Little things: sneezes, flies, spins, parties ----
        Pet funny = new Pet(15);
        funny.takeBeep();
        String funnySeen = "";
        java.util.Set<String> lines = new java.util.HashSet<>();
        for (int i = 0; i < 3 * 60 * 60 * 30 && !(funnySeen.contains("FLY") && lines.size() >= 3); i++) { // up to 3 hours of him standing around
            funny.tick(33, 0, 0, true, false);
            String l = funny.takeLine();
            if (l != null) lines.add(l);
            if (funny.mood() == Pet.Mood.SNEEZE && !funnySeen.contains("SNEEZE")) funnySeen += " SNEEZE";
            if (funny.mood() == Pet.Mood.FLY && !funnySeen.contains("FLY")) {
                funnySeen += " FLY";
                for (int k = 0; k < 60; k++) funny.tick(33, 0, 0, true, false);
                save(funny, Path.of("build", "frames").resolve("a fly.png"));
            }
        }
        check("left alone a while, he sneezes and a fly comes by", funnySeen.contains("SNEEZE") && funnySeen.contains("FLY"), true);
        check("ACHOO, and the fly gets caught or gets away", lines.contains("ACHOO!") && (lines.contains("Got it!") || lines.contains("...it got away.")), true);
        Pet spinner = new Pet(16);
        spinner.takeBeep();
        spinner.spin();
        check("the cursor zooming past spins him round", spinner.mood() + " " + spinner.takeLine(), "SPIN Whoa!");
        spinner.party("It's FRIDAY!!");
        for (int i = 0; i < 20; i++) spinner.tick(33, 0, 0, false, false);
        check("Friday afternoon: confetti", spinner.mood(), Pet.Mood.PARTY);
        save(spinner, Path.of("build", "frames").resolve("confetti.png"));
        Settings onceOnly = Settings.load();
        check("once-a-day things only happen once", onceOnly.once("monday:test") + " " + onceOnly.once("monday:test"), "true false");

        // ---- Birthdays and missing you ----
        check("how long you were gone, in friendly words", Settings.howLong(3 * 86_400_000L) + ", " + Settings.howLong(20 * 86_400_000L)
                + ", " + Settings.howLong(100 * 86_400_000L) + ", " + Settings.howLong(800 * 86_400_000L) + ", " + Settings.howLong(86_400_000L),
                "3 days, 2 weeks, 3 months, 2 years, 1 day");
        Pet partyAnimal = new Pet(17);
        partyAnimal.takeBeep();
        partyAnimal.birthday("Samuel");
        java.util.List<Pet.Beep> toots = new java.util.ArrayList<>();
        String sung = null;
        boolean blew = false;
        for (int i = 0; i < 160; i++) {
            partyAnimal.tick(33, 0, 0, false, false);
            Pet.Beep b = partyAnimal.takeBeep();
            if (b != null) toots.add(b);
            String l = partyAnimal.takeLine();
            if (l != null) sung = l;
            if (partyAnimal.blower() > 0.9 && !blew) {
                blew = true;
                save(partyAnimal, Path.of("build", "frames").resolve("birthday (blower out).png"));
            }
            if (i == 130) save(partyAnimal, Path.of("build", "frames").resolve("birthday (confetti).png"));
        }
        check("on your birthday: three toots on his party blower, then HAPPY BIRTHDAY", toots.stream().filter(b -> b == Pet.Beep.HORN).count()
                + " " + sung, "3 HAPPY BIRTHDAY, SAMUEL!!");
        check("his party hat stays on all day", partyAnimal.hat(), "birthday");
        check("the party blower toot is a real sound", Beeps.make(Pet.Beep.HORN).length > 20000, true);
        Body dropper = new Body();
        dropper.tick(33, 0, 0, 1800, 1032, 36, 0, 1920);
        dropper.dropIn(1800, -60);
        String dropSeen = "";
        for (int i = 0; i < 400 && (i == 0 || dropper.state() != Body.State.HOME); i++) {
            dropper.tick(33, 0, 0, 1800, 1032, 36, 0, 1920);
            if (!dropSeen.endsWith(dropper.state().name())) dropSeen += (dropSeen.isEmpty() ? "" : ">") + dropper.state().name();
        }
        check("he drops in from the top of the screen and lands on his feet", dropSeen, "FALL>WALK>HOME");

        // ---- Moving to another computer (on this computer only: nothing goes out on the network) ----
        String moveCode = Transfer.newCode();
        String[] arrived = new String[1];
        java.net.InetAddress loop = java.net.InetAddress.getLoopbackAddress();
        try (Transfer.Waiting newComputer = new Transfer.Waiting(moveCode, loop, 47920, 47921, t -> arrived[0] = t)) {
            check("the old computer finds the new one with the wrong code? no", Transfer.find("9999".equals(moveCode) ? "1111" : "9999", 47920, 1200), null);
            java.net.InetAddress found = Transfer.find(moveCode, 47920, 3000);
            check("with the right code it does", found != null, true);
            check("a wrong code can't send", Transfer.send(loop, 47921, "x" + moveCode, SaveToken.make(java.util.Map.of("name", "Sam"))), false);
            check("the right one sends his save token across", Transfer.send(loop, 47921, moveCode, SaveToken.make(java.util.Map.of("name", "Sam"))), true);
            for (int i = 0; i < 50 && arrived[0] == null; i++) Thread.sleep(20);
            check("and the new computer has him", SaveToken.read(arrived[0]).get("name"), "Sam");
        }

        // ---- Moving house ----
        Pet mover = new Pet(18);
        mover.takeBeep();
        mover.moving(true);
        mover.follow(Body.State.WALK);
        for (int i = 0; i < 10; i++) mover.tick(33, 0, 0, false, false);
        check("walking in with a moving box", mover.mood(), Pet.Mood.CARRY);
        save(mover, Path.of("build", "frames").resolve("moving in (carrying a box).png"));
        mover.follow(Body.State.HOME);
        check("home: he unpacks", mover.mood(), Pet.Mood.UNPACK);
        String nice = null;
        for (int i = 0; i < 100; i++) {
            mover.tick(33, 0, 0, false, false);
            String l = mover.takeLine();
            if (l != null) nice = l;
            if (i == 60) save(mover, Path.of("build", "frames").resolve("moving in (unpacking).png"));
        }
        check("and says so", nice, "This place is nice!");
        check("the laptop shuts off by itself at its critical level (5% here)", Power.criticalLevel() >= 0 && Power.criticalLevel() <= 100, true);

        // ---- All the options ----
        Files.deleteIfExists(home.resolve("settings.properties"));
        Settings knobs = Settings.load();
        check("there are a LOT of options", Options.ALL.size() >= 40, true);
        check("options start at their defaults", knobs.on("sneezes") + " " + knobs.choice("voice") + " " + knobs.number("volume"), "true Normal 5");
        knobs.set("sneezes", "false");
        knobs.set("voice", "Robot");
        knobs.set("volume", "99");
        check("and change (numbers stay in range)", knobs.on("sneezes") + " " + knobs.choice("voice") + " " + knobs.number("volume"), "false Robot 10");
        check("a robot voice sounds different", java.util.Arrays.equals(Beeps.voiced(Beeps.make(Pet.Beep.HELLO), "Robot", 5), Beeps.make(Pet.Beep.HELLO)), false);
        check("a squeaky voice is shorter (higher)", Beeps.voiced(Beeps.make(Pet.Beep.HELLO), "Squeaky", 5).length < Beeps.make(Pet.Beep.HELLO).length, true);
        String opts = cli("controlpanel", "13", "2", "0", "0"); // (1 is No tomfoolery, 2 is sneezes)
        check("the control panel lists them all, in groups", opts.contains("Antics") && opts.contains("Riding") && opts.contains("Useful"), true);
        check("and flips one", Settings.load().on("sneezes"), true);

        // ---- Jokes ----
        Jokes jk = new Jokes(1);
        java.util.Set<String> heard = new java.util.HashSet<>();
        for (int i = 0; i < Jokes.ALL.size(); i++) heard.add(jk.next());
        check("he tells every joke before any repeats", heard.size(), Jokes.ALL.size());
        check("jokes fit in his bubble (two short lines at most)", Jokes.ALL.stream().allMatch(j -> j.split("\n").length <= 2
                && java.util.Arrays.stream(j.split("\n")).allMatch(l -> l.length() <= 55)), true);
        check("how often", Jokes.gap("Off") == Long.MAX_VALUE && Jokes.gap("Lots") < Jokes.gap("Sometimes") && Jokes.gap("Sometimes") < Jokes.gap("Rare"), true);

        // ---- His voice ----
        for (Pet.Beep beep : Pet.Beep.values()) {
            byte[] sound = Beeps.make(beep);
            int loudest = 0;
            for (int i = 0; i < sound.length; i += 2) loudest = Math.max(loudest, Math.abs((short) ((sound[i] & 0xFF) | sound[i + 1] << 8)));
            check(beep + " is a short, quiet beep", sound.length > 4000 && sound.length < 100000 && loudest < 32767 * 0.2 && loudest > 1000, true);
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
        check("GitHub in a browser gets git and GitHub tips (not just any browser)", Tips.kind(new Foreground.Front("msedge.exe", "Chrome_WidgetWin_1", "SamuelArther/Clawdtop - GitHub - Edge"))
                + " " + Tips.kind(new Foreground.Front("msedge.exe", "Chrome_WidgetWin_1", "Recipes - Edge")), "github browser");
        Tips coding = new Tips();
        StringBuilder terminalTips = new StringBuilder();
        Foreground.Front term = new Foreground.Front("WindowsTerminal.exe", "CASCADIA_HOSTING_WINDOW_CLASS", "PowerShell");
        for (int i = 0; i < 6; i++) terminalTips.append(coding.tipFor(term)).append("|");
        check("terminals get git and error-message tips too", terminalTips.toString().contains("git ") && terminalTips.toString().contains("PATH"), true);
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
        snapshot(hello.panel(), frames0.resolve("welcome 3b birthday.png"));
        find(hello.panel(), javax.swing.JTextField.class).setText("10/8");
        click(hello.panel(), "Next");
        check("and your birthday (just month and day)", fresh.birthday(), "10-08");
        check("he suggests a name for his new home", find(hello.panel(), javax.swing.JTextField.class).getText().startsWith("Samuel's "), true);
        snapshot(hello.panel(), frames0.resolve("welcome 3c home.png"));
        find(hello.panel(), javax.swing.JTextField.class).setText("Samuel's Laptop");
        click(hello.panel(), "Next");
        check("and calls your computer what you said", fresh.home(), "Samuel's Laptop");
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
        check("and where he lives", cli("status").contains("Lives in:    Samuel's Laptop"), true);
        check("an unknown command says what he can do", cli("dance").contains("I don't know \"dance\""), true);
        String panel = cli("controlpanel", "1", "Sam", "7", "8", "2", "3", "3", "4", "2", "5", "2", "5", "9", "#33aaff", "0");
        Settings afterPanel = Settings.load();
        check("the control panel changes his name, beeps, tips, spot, size, personality and color", afterPanel.name() + " "
                + afterPanel.sounds() + " " + afterPanel.tips() + " " + afterPanel.spot() + " " + afterPanel.size() + " "
                + afterPanel.personality() + " " + afterPanel.color(), "Sam true false On the left Big BOUNCY #33AAFF");

        // ---- Save tokens ----
        String token = afterPanel.saveToken();
        check("a save token starts with CLAWD-", token.startsWith("CLAWD-"), true);
        check("and holds who he is to you", SaveToken.read(token).toString(), "{name=Sam, color=#33AAFF, personality=BOUNCY, spot=On the left, size=Big, birthday=10-08, metDate=" + afterPanel.metDate() + ", home=Samuel's Laptop}");
        check("a mistyped token is caught", SaveToken.read(token.substring(0, 10) + "x" + token.substring(11)) + " " + SaveToken.read("hello"), "null null");
        Files.deleteIfExists(home.resolve("settings.properties"));
        Settings reborn = Settings.load();
        check("a fresh Clawd with your token remembers you", reborn.useToken(token) + " " + reborn.name() + " " + reborn.color() + " "
                + reborn.personality() + " " + reborn.restored(), "true Sam #33AAFF BOUNCY true");
        check("a wrong token changes nothing", Settings.load().useToken("CLAWD-nope-0000"), false);
        reborn.movedFrom(reborn.home());
        reborn.setHome("Gaming PC");
        check("moving: the new computer gets a name, the old one goes on the list", reborn.home() + " / " + reborn.oldHomes()
                + " / " + SaveToken.read(reborn.saveToken()).get("oldHomes"), "Gaming PC / [Samuel's Laptop] / Samuel's Laptop");
        check("and the control panel can rename it", cli("controlpanel", "14", "Big Desk", "0").contains("His home's name") + " " + Settings.load().home(), "true Big Desk");
        check("and shows his settings", panel.contains("Clawd's control panel"), true);
        check("uninstall asks first, and no means no", cli("uninstall", "n").contains("He's staying"), true);

        // ---- Cleaning a folder (on a pretend mini PC) ----
        CleanerTest.run();

        // ---- Your games ----
        Path fakeSteam = Files.createDirectories(home.resolve("steamapps"));
        Files.writeString(fakeSteam.resolve("appmanifest_105600.acf"), "\"AppState\"\n{\n\t\"appid\"\t\t\"105600\"\n\t\"name\"\t\t\"Terraria\"\n}\n");
        Files.writeString(fakeSteam.resolve("appmanifest_228980.acf"), "\"AppState\"\n{\n\t\"name\"\t\t\"Steamworks Common Redistributables\"\n}\n");
        Path fakeEpic = Files.createDirectories(home.resolve("epic"));
        Files.writeString(fakeEpic.resolve("abc.item"), "{\n  \"DisplayName\": \"Rocket League\",\n  \"AppName\": \"Sugar\"\n}");
        java.util.TreeSet<String> found = new java.util.TreeSet<>();
        Games.steamApps(found, fakeSteam);
        Games.epic(found, fakeEpic);
        check("he reads Steam's and Epic's own lists of games", found.toString(), "[Rocket League, Steamworks Common Redistributables, Terraria]");
        java.util.List<String> library = java.util.List.of("Rocket League", "Terraria");
        boolean allNice = true;
        for (int i = 0; i < 40; i++) {
            String kind = Games.compliment(library, new java.util.Random(i));
            allNice &= kind != null && !kind.contains("%");
        }
        check("and always has something nice to say", allNice + " " + Games.compliment(java.util.List.of(), new java.util.Random()), "true null");
        check("launchers count", Games.launcher("steam.exe") + " " + Games.launcher("EADesktop.exe") + " " + Games.launcher("notepad.exe"), "true true false");

        // ---- No tomfoolery ----
        Settings calm = Settings.load();
        calm.set("serious", "true");
        check("no tomfoolery turns off the silly stuff", calm.on("creates") + " " + calm.on("sneezes") + " " + calm.on("rides") + " " + calm.jokes(), "false false false Off");
        check("but he's still useful", calm.on("diskSpace") + " " + calm.on("missedYou"), "true true");
        check("and the control panel has the switch", cli("controlpanel", "15", "0").contains("No tomfoolery") + " " + Settings.load().serious(), "true false");
        calm = Settings.load();
        Pet calmPet = new Pet(3);
        Settings calmSettings = calm;
        calm.set("serious", "true");
        calmPet.setPrefs(new Pet.Prefs() {
            public boolean on(String key) {
                return calmSettings.on(key);
            }

            public int number(String key) {
                return calmSettings.number(key);
            }

            public String choice(String key) {
                return calmSettings.choice(key);
            }
        });
        calmPet.changeColor(new java.awt.Color(80, 140, 240));
        for (int i = 0; i < 150; i++) calmPet.tick(33, 0, 0, false, false);
        check("a new color, no freakout", calmPet.mood() != Pet.Mood.FREAKOUT && calmPet.color().getBlue() == 240, true);
        calm.set("serious", "false");

        // ---- Things he codes ----
        check("he knows how to make lots of things", Creation.ALL.size() >= 26, true);
        java.util.Set<String> ids = new java.util.HashSet<>();
        boolean allGood = true;
        for (Creation c : Creation.ALL) {
            allGood &= ids.add(c.id()) && !c.file().isBlank() && !c.code().isBlank() && !c.starting().isBlank() && !c.done().isBlank()
                    && (c.effect() != Creation.Effect.ITEM || Pixels.ITEMS.containsKey(c.item()));
        }
        check("each has its own name, file, lines and code (and a picture if it's a thing)", allGood, true);
        Creation parsed = Creation.parse("""
                zap | zap.py | ITEM:bomb | yes | 4
                Making a zapper.
                Zap!
                ...oops.
                zap()
                    again()
                ---
                """).get(0);
        check("the long list is read from plain text", parsed.id() + " " + parsed.file() + " " + parsed.effect() + " " + parsed.item()
                + " " + parsed.oops() + " " + parsed.showFor() + " " + parsed.after() + " " + parsed.code().replace("\n", "/"),
                "zap zap.py ITEM bomb true 4000 ...oops. zap()/    again()/");
        java.util.Set<String> allButOne = new java.util.HashSet<>();
        for (Creation c : Creation.ALL) if (!c.id().equals("duck")) allButOne.add(c.id());
        check("joke scripts are saved so they can't run by accident", new Creation("x", "panic.bat", "", "", "", 0, Creation.Effect.NONE, false, "x").savedAs()
                + " " + Creation.find("hello").savedAs(), "panic.bat.txt hello.py");
        check("he makes something he hasn't made before", Creation.pick(new java.util.Random(3), allButOne, "").id(), "duck");
        check("and once he's made everything, anything but the last one", Creation.pick(new java.util.Random(3), ids, "duck").id().equals("duck"), false);

        Pet coder = new Pet(11);
        coder.takeBeep();
        Creation disco = Creation.find("disco");
        check("he gets his laptop out to code", coder.create(disco) + " " + coder.mood() + " " + coder.takeLine(), "true CODING I've got an idea!");
        for (int i = 0; i < 100; i++) coder.tick(33, 0, 0, false, false);
        double halfway = coder.codingProgress();
        check("ask what he's doing and it's a secret", coder.secretlyCoding(), true);
        check("the file fills in as he types", halfway > 0 && halfway < 1, true);
        for (int i = 0; i < 1000 && coder.mood() != Pet.Mood.MADE; i++) coder.tick(33, 0, 0, false, false);
        check("then out comes what he made", coder.mood() + " " + coder.showing().id() + " " + coder.takeMade().id(), "MADE disco disco");
        for (int i = 0; i < 300 && coder.mood() == Pet.Mood.MADE; i++) coder.tick(33, 0, 0, false, false);
        coder.takeLine();
        check("and after a while it's over", coder.mood() + " " + coder.showing(), "IDLE null");

        Creation weather = Creation.find("weather");
        coder.create(weather);
        for (int i = 0; i < 1500 && coder.mood() != Pet.Mood.SORRY; i++) coder.tick(33, 0, 0, false, false);
        check("when it goes wrong, he's sorry", coder.mood(), Pet.Mood.SORRY);
        for (int i = 0; i < 300 && coder.mood() != Pet.Mood.CODING; i++) coder.tick(33, 0, 0, false, false);
        check("and deletes it", coder.mood() + " " + coder.takeLine() + " " + coder.secretlyCoding(), "CODING rm weather.py false");
        for (int i = 0; i < 300 && coder.mood() != Pet.Mood.IDLE; i++) coder.tick(33, 0, 0, false, false);
        check("then it never happened", coder.takeLine() + " / " + coder.takeDeleted().file(), "There. It never happened. / weather.py");

        Pet flyer = new Pet(12);
        Body carpetBody = new Body();
        flyer.create(Creation.find("carpet"));
        for (int i = 0; i < 1500 && flyer.mood() != Pet.Mood.IDLE; i++) flyer.tick(33, 0, 0, false, false);
        Creation carpet = flyer.takeMade();
        check("he made a flying carpet", carpet.effect(), Creation.Effect.CARPET);
        carpetBody.tick(33, 0, 0, 1800, 1040, 96, 0, 1920);
        carpetBody.flyCarpet();
        double carpetTop = 1040;
        for (int i = 0; i < 120; i++) {
            carpetBody.tick(33, 0, 0, 1800, 1040, 96, 0, 1920);
            flyer.follow(carpetBody.state());
            flyer.tick(33, 0, 0, false, false);
            carpetTop = Math.min(carpetTop, carpetBody.y());
        }
        check("and flies round the screen on it", flyer.mood() + " " + (carpetTop < 900), "CARPET true");
        save(flyer, Path.of("build", "frames").resolve("flying carpet.png"));
        carpetBody.knockOff();
        flyer.carpetGone(carpet);
        for (int i = 0; i < 2000 && carpetBody.state() != Body.State.HOME; i++) {
            carpetBody.tick(33, 0, 0, 1800, 1040, 96, 0, 1920);
            flyer.follow(carpetBody.state());
            flyer.tick(33, 0, 0, false, false);
        }
        flyer.follow(carpetBody.state());
        check("click it away and he falls, gets home, and he's sorry", carpetBody.state() + " " + flyer.mood(), "HOME SORRY");
        save(flyer, Path.of("build", "frames").resolve("sorry.png"));

        // a picture of each thing he can make, while it's out
        java.util.List<BufferedImage> made = new java.util.ArrayList<>();
        for (Creation c : Creation.BUILT_IN) {
            if (c.showFor() == 0) continue;
            Pet show = new Pet(5);
            show.create(c);
            for (int i = 0; i < 1500 && show.mood() != Pet.Mood.MADE; i++) show.tick(33, 0, 0, false, false);
            for (int i = 0; i < 40; i++) show.tick(33, 0, 0, false, false);
            made.add(picture(show));
        }
        for (String item : new String[] {"cat", "rock", "ghost", "donut", "robot", "trophy"}) {
            Pet show = new Pet(5);
            show.create(new Creation("x", "x.py", "", "", "", 5000, Creation.Effect.ITEM, false, "x", item));
            for (int i = 0; i < 1500 && show.mood() != Pet.Mood.MADE; i++) show.tick(33, 0, 0, false, false);
            for (int i = 0; i < 20; i++) show.tick(33, 0, 0, false, false);
            made.add(picture(show));
        }
        BufferedImage madeSheet = new BufferedImage(6 * Sprite.WIDTH * 8, (made.size() + 5) / 6 * Sprite.HEIGHT * 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = madeSheet.createGraphics();
        for (int i = 0; i < made.size(); i++) mg.drawImage(made.get(i), i % 6 * Sprite.WIDTH * 8, i / 6 * Sprite.HEIGHT * 8, null);
        mg.dispose();
        ImageIO.write(madeSheet, "png", Path.of("build", "frames").resolve("things he makes.png").toFile());

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
        // Every step of getting the laptop out, typing, and putting it away, in one picture (a row per 0.6 s)
        int cols = 9, cell = 8;
        List<BufferedImage> laptopFrames = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            laptopFrames.add(picture(worker));
            for (int k = 0; k < 2; k++) worker.tick(33, 0, 0, false, false);
            worker.follow(Body.State.PERCH);
        }
        save(worker, frames.resolve("working on his laptop.png"));
        check("after setting up he's still at it", worker.mood(), Pet.Mood.WORK);
        worker.job(Pet.Mood.PEEK);
        worker.follow(Body.State.PERCH);
        check("he puts the laptop away before anything else", worker.mood(), Pet.Mood.PACK);
        for (int i = 0; i < 10; i++) {
            laptopFrames.add(picture(worker));
            for (int k = 0; k < 2; k++) worker.tick(33, 0, 0, false, false);
            worker.follow(Body.State.PERCH);
        }
        check("then it's the next thing (peeking)", worker.mood(), Pet.Mood.PEEK);
        BufferedImage sheet = new BufferedImage(cols * Sprite.WIDTH * cell, (laptopFrames.size() + cols - 1) / cols * Sprite.HEIGHT * cell, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = sheet.createGraphics();
        for (int i = 0; i < laptopFrames.size(); i++) sg.drawImage(laptopFrames.get(i), i % cols * Sprite.WIDTH * cell, i / cols * Sprite.HEIGHT * cell, null);
        sg.dispose();
        ImageIO.write(sheet, "png", frames.resolve("laptop (every step).png").toFile());
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

    /** A picture of him as he is now, on a dark background with a faint line round it (8 pixels a unit). */
    static BufferedImage picture(Pet pet) {
        int unit = 8;
        BufferedImage image = new BufferedImage(Sprite.WIDTH * unit, Sprite.HEIGHT * unit, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new java.awt.Color(32, 32, 36));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new java.awt.Color(60, 60, 66));
        g.drawRect(0, 0, image.getWidth() - 1, image.getHeight() - 1);
        Sprite.draw(g, pet, unit);
        g.dispose();
        return image;
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
