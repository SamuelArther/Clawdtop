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
        System.setProperty("clawdtop.allAntics", "true"); // (his sillier antics start off: on here, to test them all)

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
        check("moving the mouse near him doesn't wake him (only a click does)", lonely.mood() + " " + lonely.takeBeep(), "SLEEP null");
        lonely.poke();
        check("clicking him wakes him", lonely.mood() + " " + lonely.takeBeep(), "HAPPY WAKE");
        lonely.poke();
        check("and clicking him again makes him happy", lonely.mood() + " " + lonely.takeBeep(), "HAPPY CLICKED");

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
        for (int i = 0; i < 3 * 60 * 60 * 30 && !(funnySeen.contains("FLY") && lines.contains("ACHOO!") && (lines.contains("Got it!") || lines.contains("...it got away."))); i++) { // up to 3 hours of him standing around
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
        check("and other things happen too (hiccups...)", lines.size() >= 3, true);
        check("ACHOO, and the fly gets caught or gets away", lines.contains("ACHOO!") && (lines.contains("Got it!") || lines.contains("...it got away.")), true);
        Pet spinner = new Pet(16);
        spinner.takeBeep();
        spinner.spin();
        check("the cursor zooming past makes him woozy (no spinning round)", spinner.mood(), Pet.Mood.WOOZY);
        check("and he says so", spinner.takeLine() != null, true);
        for (int i = 0; i < 100; i++) spinner.tick(33, 0, 0, false, false);
        spinner.spin();
        check("but not again straight away", spinner.mood() != Pet.Mood.WOOZY, true);
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
            check("and knows which computer he came from (to ask you first)", newComputer.from().contains("127.0.0.1") || !newComputer.from().isEmpty(), true);
        }
        String[] notLetIn = {null};
        String noCode = Transfer.newCode();
        try (Transfer.Waiting saysNo = new Transfer.Waiting(noCode, loop, 47930, 47931, (token, from) -> java.util.concurrent.CompletableFuture.completedFuture(false),
                t -> notLetIn[0] = t)) {
            check("you say no on the new computer: the old one keeps him", Transfer.send(loop, 47931, noCode, SaveToken.make(java.util.Map.of("name", "Sam"))) + " " + notLetIn[0],
                    "false null");
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
        Settings appCopy = Settings.load(), panelCopy = Settings.load(); // Clawd and the control panel, both open
        panelCopy.setName("Panel Name");
        appCopy.setJokes("Lots");
        check("two copies saving don't undo each other", Settings.load().name() + " / " + Settings.load().jokes(), "Panel Name / Lots");
        Settings before = Settings.load(), other = Settings.load();
        int x0 = before.x();
        other.setX(x0 + 200);
        before.setX(x0); // set back to what it was: still counts as a change
        check("and putting something back still counts", Settings.load().x(), x0);
        Settings spots = Settings.load();
        spots.setAppSpot(Settings.appKey("Chrome.exe"), 300);
        spots.setAppSpot(Settings.windowKey("YouTube - Google Chrome"), 700);
        check("a spot for an app, and one for just one tab", Settings.load().appSpotKey("chrome.exe", "Docs - Google Chrome") + " "
                + Settings.load().appSpotKey("chrome.exe", "YouTube - Google Chrome") + " " + Settings.load().appSpotKey("Code.exe", "x") + " "
                + Settings.load().appSpot(Settings.windowKey("YouTube - Google Chrome")), "app:chrome.exe window:YouTube - Google Chrome null 700");
        spots.forgetAppSpot(Settings.appKey("chrome.exe"));
        check("and forgetting one", Settings.load().appSpotKey("chrome.exe", "Docs"), null);
        check("apps get friendly names", Clawdtop.appName(new Foreground.Front("chrome.exe", "", "Docs - Google Chrome")) + ", "
                + Clawdtop.appName(new Foreground.Front("notepad.exe", "", "")), "Google Chrome, Notepad");
        Settings knobs = Settings.load();
        check("there are a LOT of options", Options.ALL.size() >= 40, true);
        check("options start at their defaults", knobs.on("sneezes") + " " + knobs.choice("voice") + " " + knobs.number("volume"), "false Normal 5"); // (sneezes start off: calmer)
        knobs.set("sneezes", "false");
        knobs.set("voice", "Robot");
        knobs.set("volume", "99");
        check("and change (numbers stay in range)", knobs.on("sneezes") + " " + knobs.choice("voice") + " " + knobs.number("volume"), "false Robot 10");
        check("a robot voice sounds different", java.util.Arrays.equals(Beeps.voiced(Beeps.make(Pet.Beep.HELLO), "Robot", 5), Beeps.make(Pet.Beep.HELLO)), false);
        check("a squeaky voice is shorter (higher)", Beeps.voiced(Beeps.make(Pet.Beep.HELLO), "Squeaky", 5).length < Beeps.make(Pet.Beep.HELLO).length, true);
        String opts = cli("controlpanel", "11", "2", "0", "0"); // (1 is No tomfoolery, 2 is sneezes)
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
        check("settings start as beeps on, normal size, above the clock", s.sounds() + " " + s.size() + " " + s.x() + " " + s.unit(), "true Normal -1 4");
        s.setSounds(false);
        s.setSize("Big");
        s.setX(1200);
        Settings again = Settings.load();
        check("and are kept for next time", again.sounds() + " " + again.size() + " " + again.x() + " " + again.unit(), "false Big 1200 6");
        Files.writeString(home.resolve("settings.properties"), "size=Huge\nx=nope\n");
        Settings odd = Settings.load();
        check("odd settings fall back to normal", odd.size() + " " + odd.x(), "Normal -1");
        String startScript = Startup.script("C:\\Java\\bin\\javaw.exe", "C:\\Clawdtop\\Clawdtop.jar");
        check("the Windows startup script runs Java with no window, only if he's still there", startScript.contains("If fso.FileExists(jar) Then")
                + " " + startScript.contains("If Not fso.FileExists(java) Then java = \"javaw\"") + " " + startScript.contains("On Error Resume Next")
                + " " + startScript.contains("jar = \"C:\\Clawdtop\\Clawdtop.jar\""), "true true true true");
        check("the Mac login file is safe XML", Platform.macLogin("/usr/bin/java", "/Users/me/Tom & Jerry/Clawdtop.jar").contains("Tom &amp; Jerry"), true);

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
        snapshot(hello.panel(), frames0.resolve("welcome 3c2 military.png"));
        click(hello.panel(), "Yes");
        snapshot(hello.panel(), frames0.resolve("welcome 3c3 branch.png"));
        find(hello.panel(), javax.swing.JComboBox.class).setSelectedItem("Army National Guard");
        click(hello.panel(), "Next");
        find(hello.panel(), javax.swing.JComboBox.class).setSelectedItem("Retired");
        click(hello.panel(), "Next");
        check("served? he asks your branch (National Guard too) and how you served", String.join("|", Settings.load().service()), "Army National Guard|Retired");
        check("and salutes you with your branch's motto", Pet.serviceLine("Sam", "Marine Corps", "Active duty"), "Thank you for your service, Sam!\nStay safe out there. Semper Fi!");
        click(hello.panel(), "Next");
        snapshot(hello.panel(), frames0.resolve("welcome 3d kid-friendly.png"));
        click(hello.panel(), "Normal (recommended)");
        snapshot(hello.panel(), frames0.resolve("welcome 3e web search.png"));
        click(hello.panel(), "No, stay offline");
        check("he only looks things up online if you say so", Settings.load().on("webSearch"), false);
        if (BrainInstall.ollama() != null && !BrainInstall.installed()) click(hello.panel(), "Not now"); // (his brain: only if you say so)
        check("he only downloads his brain if you say so", Settings.load().flag("brainOk") == BrainInstall.installed(), true);
        check("reading what Wikipedia and DuckDuckGo send back", WebSearch.value("{\"batchcomplete\":\"\",\"query\":{\"search\":[{\"ns\":0,\"title\":\"Rayleigh scattering\"}]}}", "title")
                + " / " + WebSearch.value("{\"Abstract\":\"x\",\"AbstractText\":\"An octopus has \\\"eight\\\" arms.\"}", "AbstractText"), "Rayleigh scattering / An octopus has \"eight\" arms.");
        check("normal answers (recommended), or kid-friendly for little kids", Settings.load().on("kidFriendly"), false);

        // ---- Ask me a question: math goes to Calculator, the rest to his brain ----
        MathHelp.Problem sum = MathHelp.parse("What's 12 times 7?");
        check("a math question is spotted", sum.buttons() + " = " + sum.leftToRight(), "1 2 × 7 = = 84.0");
        check("all sorts of ways of asking", MathHelp.parse("how much is 45 + 19 x 2").buttons() + " / " + MathHelp.parse("100 divided by 8").buttons()
                + " / " + MathHelp.parse("3.5-1.25").buttons(), "4 5 + 1 9 × 2 = / 1 0 0 ÷ 8 = / 3 . 5 - 1 . 2 5 =");
        check("but not everything with a number in it", MathHelp.parse("how old is the moon") + " " + MathHelp.parse("what is 7") + " "
                + MathHelp.parse("who won in 2010"), "null null null");
        MathHelp.Problem mixed = MathHelp.parse("45 + 19 * 2");
        check("either way of working it out counts (Calculator's standard mode goes left to right)", mixed.right(128) + " " + mixed.right(83) + " " + mixed.right(84), "true true false");
        check("he reads Calculator's display", MathHelp.shown("Display is 1,234.5") + " " + MathHelp.shown("Display is -7") + " " + MathHelp.shown("nope"), "1234.5 -7.0 NaN");
        check("JSON both ways", Brain.content("{\"model\":\"x\",\"message\":{\"role\":\"assistant\",\"content\":\"Hi \\\"there\\\"!\\nCrabs \\u0026 code.\"},\"done\":true}")
                + "|" + Brain.json("a \"b\"\n"), "Hi \"there\"!\nCrabs & code.|\"a \\\"b\\\"\\n\"");
        check("his answers never have bad words (even if the model slips)", Brain.clean("Well, **damn**, that's a shitty bug 🦀"), "Well, beep, that's a beep bug");
        check("curly quotes stay (as plain ones)", Brain.clean("It’s “pretty” high — wow"), "It's \"pretty\" high - wow");
        check("long answers wrap for his bubble", Brain.wrap("The quick brown fox jumps over the lazy dog again", 20), "The quick brown fox\njumps over the lazy\ndog again");
        check("he answers in his own personality, kid-friendly or not", Brain.systemPrompt(Pet.Personality.BOUNCY, true, "Sam").contains("exclamation")
                && Brain.systemPrompt(Pet.Personality.BOUNCY, true, "Sam").contains("kid") && !Brain.systemPrompt(Pet.Personality.CHILL, false, "").contains("kids")
                && Brain.systemPrompt(Pet.Personality.CHILL, false, "").contains("Never use swear words"), true);
        check("brains stay small", Brain.model("Normal") + " " + Brain.model("Tiny") + " " + Brain.model("Smart"), "qwen2.5:1.5b qwen2.5:0.5b gemma3:1b");
        Ask askBox = new Ask();
        String[] askedFor = {null};
        askBox.show("Ask me anything!", new java.awt.Rectangle(1800, 1000, 63, 45), new java.awt.Rectangle(0, 0, 1920, 1080), q -> askedFor[0] = q);
        askBox.field().setText("  why is the sky blue?  ");
        snapshot(askBox.panel(), frames0.resolve("ask me a question.png"));
        click(askBox.panel(), "Ask");
        check("you type a question and he gets it", askedFor[0], "why is the sky blue?");
        snapshot(hello.panel(), frames0.resolve("welcome 4 beeps.png"));
        click(hello.panel(), "Shh, no beeps");
        check("beeps off if you say so", fresh.sounds(), false);
        click(hello.panel(), "Not now");
        snapshot(hello.panel(), frames0.resolve("welcome 5 done.png"));
        Settings later = Settings.load();
        check("and remembers you met, for next time", later.met() + " " + later.name() + " " + later.spot(), "true Samuel In the middle");
        check("he chirps along, starting with hello", chirps.get(0), Pet.Beep.HELLO);

        // ---- The clawd command ----
        if (Platform.WINDOWS) check("clawd.cmd runs the command part of Clawdtop with console Java, from its own copy of the jar",
                Install.script(Path.of("C:\\Java\\bin\\javaw.exe"), Path.of("C:\\Clawdtop\\build\\Clawdtop.jar"), Path.of("C:\\bin\\clawd-command.jar")),
                "@echo off\r\n\"C:\\Java\\bin\\java.exe\" --enable-native-access=ALL-UNNAMED -Dclawdtop.jar=\"C:\\Clawdtop\\build\\Clawdtop.jar\" -cp \"C:\\bin\\clawd-command.jar\" clawdtop.Cli %* & exit /b\r\n");
        String userPath = "%USERPROFILE%\\bin;C:\\Tools;C:\\Users\\me\\AppData\\Local\\Clawdtop\\bin";
        check("finds its folder on your PATH (any capitals)", Install.hasEntry(userPath, "c:\\users\\me\\appdata\\local\\clawdtop\\bin")
                + " " + Install.hasEntry("C:\\Tools", "C:\\Users\\me\\AppData\\Local\\Clawdtop\\bin"), "true false");
        check("and takes only its own folder back out, leaving %VARIABLES% as they were",
                Install.withoutEntry(userPath, "C:\\Users\\me\\AppData\\Local\\Clawdtop\\bin"), "%USERPROFILE%\\bin;C:\\Tools");
        check("clawd help", cli("help").contains("clawd controlpanel"), true);
        check("clawd ask, right in the terminal", cli("ask", "flip a coin").contains("*flip*") + " " + cli("ask", "what's 6 times 7").contains("42"), "true true");
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
        check("and holds who he is to you", SaveToken.read(token).toString(), "{name=Sam, color=#33AAFF, personality=BOUNCY, spot=On the left, size=Big, birthday=10-08, metDate=" + afterPanel.metDate() + ", home=Samuel's Laptop, service=Army National Guard|Retired}");
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
        check("and the control panel can rename it", cli("controlpanel", "12", "Big Desk", "0").contains("His home's name") + " " + Settings.load().home(), "true Big Desk");
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
        java.util.List<String> lots = java.util.List.of("Among Us", "Garry's Mod", "Geometry Dash", "Minecraft", "Minecraft for Windows", "Terraria");
        java.util.Set<String> mentioned = new java.util.HashSet<>();
        java.util.Random r6 = new java.util.Random(6);
        for (int i = 0; i < 12; i++) Games.compliment(lots, r6, mentioned);
        mentioned.remove("#count");
        check("he goes through all your games, not just one", mentioned.size() >= 4, true);
        java.util.List<WindowTricks.TaskbarButton> bar = java.util.List.of(
                new WindowTricks.TaskbarButton("Firefox pinned", "Appid: 308046B0AF4A39CB", 952, 1504, 88, 96),
                new WindowTricks.TaskbarButton("VLC media player pinned", "Appid: {6D80}\\VideoLAN\\VLC\\vlc.exe", 1128, 1504, 88, 96),
                new WindowTricks.TaskbarButton("Steam pinned", "Appid: {7C5A}\\Steam\\Steam.exe", 1480, 1504, 88, 96),
                new WindowTricks.TaskbarButton("Terminal - 1 running window pinned", "Appid: Microsoft.WindowsTerminal_8wekyb3d8bbwe!App", 1656, 1504, 88, 96),
                new WindowTricks.TaskbarButton("Steam - 1 running window", "Appid: Valve.Steam.Client", 1744, 1504, 88, 96));
        check("the tackle finds each app's taskbar icon", WindowTricks.buttonFor(bar, "firefox.exe", "Mozilla Firefox").x() + " "
                + WindowTricks.buttonFor(bar, "vlc.exe", "VLC").x() + " " + WindowTricks.buttonFor(bar, "steam.exe", "Steam").x() + " "
                + WindowTricks.buttonFor(bar, "WindowsTerminal.exe", "PowerShell").x() + " " + WindowTricks.buttonFor(bar, "notepad.exe", "Untitled - Notepad"),
                "952 1128 1744 1656 null");
        Body tackler = new Body();
        for (int i = 0; i < 5; i++) tackler.tick(33, 0, 0, 1000, 800, 48, 0, 2000);
        tackler.tackle(400);
        boolean hit = false;
        for (int i = 0; i < 200 && !hit; i++) {
            tackler.tick(33, 0, 0, 1000, 800, 48, 0, 2000);
            hit = tackler.takeTackled();
        }
        check("he charges the icon and dives onto it", hit + " " + (Math.abs(tackler.x() - 400) < 1), "true true");
        Pet singer = new Pet(2);
        singer.takeBeep();
        singer.play(Piano.Instrument.VOICE, Piano.SONGS[0]);
        check("he sings in his own little beep voice (no piano, no song file playing)", singer.takeLine().startsWith("*ahem*") + " " + singer.playingMidi()
                + " " + (Beeps.sing(72, 300).length > 1000), "true null true");
        check("song files have nice titles", Clawdtop.songTitle(new java.io.File("navy.mid")) + " / " + Clawdtop.songTitle(new java.io.File("my_song.mid")), "Anchors Aweigh / my song");
        check("he can tell videos from music from the window", Seeing.mediaIn("chrome.exe", "Funny cats - YouTube - Google Chrome") + " "
                + Seeing.mediaIn("Spotify.exe", "Song") + " " + Seeing.mediaIn("chrome.exe", "My Mix - YouTube Music") + " " + Seeing.mediaIn("notepad.exe", "notes"),
                "video music music null");
        Pet viewer = new Pet(6);
        viewer.takeBeep();
        viewer.watch(true);
        for (int i = 0; i < 5; i++) viewer.tick(33, 0, 0, false, false);
        viewer.scare();
        check("watching with popcorn, and jumping at the scary bits", viewer.mood(), Pet.Mood.SCARED);
        for (int i = 0; i < 60; i++) viewer.tick(33, 0, 0, false, false);
        check("then back to watching", viewer.mood(), Pet.Mood.WATCH);
        viewer.watch(false);
        check("until the show's over", viewer.mood(), Pet.Mood.IDLE);
        java.time.LocalDate oct9 = java.time.LocalDate.of(2026, 10, 9);
        check("countdowns", Helpers.countdown("how many days until christmas?", "", oct9) + " | " + Helpers.countdown("how many days until halloween", "", oct9)
                + " | " + Helpers.countdown("how many days until my birthday", "10-10", oct9) + " | " + Helpers.countdown("days until march 3", "", oct9)
                + " | " + Helpers.countdown("what's for dinner", "", oct9),
                "77 days until Christmas! | 22 days until Halloween! | Just 1 more day until your birthday! (Tomorrow!) | 145 days until March 3! | null");
        check("conversions", Helpers.convert("how many cm in 5 inches?") + " | " + Helpers.convert("70 f in c") + " | " + Helpers.convert("convert 2 miles to km")
                + " | " + Helpers.convert("how many feet in a mile") + " | " + Helpers.convert("5 kg to inches"),
                "5 inches is 12.7 cm. | 70 f is 21.11 c. | 2 miles is 3.22 km. | 1 mile is 5280 feet. | Hmm, you can't turn kg into inches. (Different kinds of things!)");
        Settings noter = Settings.load();
        noter.clearNotes();
        noter.addNote("the game is at 6");
        noter.addNote("buy milk");
        check("he remembers notes", String.join(" / ", Settings.load().notes()), "the game is at 6 / buy milk");
        noter.clearNotes();
        boolean calmPicks = true;
        java.util.Random picks = new java.util.Random(5);
        for (int i = 0; i < 500; i++) calmPicks &= !Creation.pick(picks, java.util.Set.of(), "", false).big();
        check("on his own, no big screen-wide surprises (unless you let him)", calmPicks, true);
        check("versions compare like numbers", Updater.newer("1.0.10", "1.0.9") + " " + Updater.newer("v1.1", "1.0.0") + " "
                + Updater.newer("1.0.0", "1.0.0") + " " + Updater.newer("0.9.9", "1.0.0"), "true true false false");
        Updater.Release rel = Updater.parse("{\"tag_name\":\"v1.2.0\",\"body\":\"Popcorn!\\nAnd more.\",\"assets\":[{\"browser_download_url\":"
                + "\"https://github.com/SamuelArther/Clawdtop/releases/download/v1.2.0/Clawdtop-v1.2.0.zip\"},{\"browser_download_url\":"
                + "\"https://github.com/SamuelArther/Clawdtop/releases/download/v1.2.0/Clawdtop.jar\"}]}");
        check("he reads GitHub's latest release", rel.version() + " | " + rel.jarUrl().endsWith("/v1.2.0/Clawdtop.jar") + " | " + rel.notes(),
                "1.2.0 | true | Popcorn!\nAnd more.");
        String longNotes = "A line of notes with \\\"quotes\\\". \\n".repeat(3000); // (long notes once crashed the reader)
        check("long release notes read fine", Updater.parse("{\"tag_name\":\"v1.2.0\",\"body\":\"" + longNotes + "\",\"assets\":[{\"browser_download_url\":"
                + "\"https://x/v1.2.0/Clawdtop.jar\"}]}").notes().length() > 50_000, true);
        check("and a release without his jar doesn't count", Updater.parse("{\"tag_name\":\"v2\",\"assets\":[]}"), null);
        // song files made his: every note that starts also ends (no stuck notes), even with notes that end as they start,
        // and a note struck again before the last one let go is kept
        javax.sound.midi.Sequence ringing = new javax.sound.midi.Sequence(javax.sound.midi.Sequence.PPQ, 480);
        javax.sound.midi.Track line = ringing.createTrack();
        long[][] hits = {{0, 0}, {0, 479}, {480, 1439}, {1440, 1900}, {1800, 2400}};
        for (long[] h : hits) {
            line.add(new javax.sound.midi.MidiEvent(new javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_ON, 0, 60, 90), h[0]));
            line.add(new javax.sound.midi.MidiEvent(new javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_OFF, 0, 60, 0), h[1]));
        }
        javax.sound.midi.Sequence his = Piano.pianoOnly(ringing);
        int ons = 0, offs = 0;
        for (javax.sound.midi.Track t : his.getTracks()) {
            for (int i = 0; i < t.size(); i++) {
                if (t.get(i).getMessage() instanceof javax.sound.midi.ShortMessage m && m.getData1() == 60) {
                    if (m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON && m.getData2() > 0) ons++;
                    else if (m.getCommand() == javax.sound.midi.ShortMessage.NOTE_OFF || m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON) offs++;
                }
            }
        }
        check("song notes all end", ons + " on, " + offs + " off", "4 on, 4 off");
        // handy extras: links, passwords, the time elsewhere, words, choosing, apps
        check("clean link", Extras.cleanLink("https://example.com/a?id=5&utm_source=x&fbclid=abc#top"), "https://example.com/a?id=5#top");
        check("clean link all tracking", Extras.cleanLink("https://shop.com/p?utm_medium=email&gclid=1"), "https://shop.com/p");
        check("clean youtube share", Extras.cleanLink("https://youtu.be/dQw4w9WgXcQ?si=AbC123"), "https://youtu.be/dQw4w9WgXcQ");
        check("keep feature elsewhere", Extras.cleanLink("https://site.com/x?feature=dark"), "https://site.com/x?feature=dark");
        check("unwrap google link", Extras.cleanLink("https://www.google.com/url?q=https%3A%2F%2Fnews.com%2Fstory%3Futm_source%3Dg&sa=D"), "https://news.com/story");
        check("not a link", Extras.cleanLink("hello there"), null);
        check("odd characters in a link", Extras.cleanLink("https://example.com/a?q=a|b&utm_source=x") + " " + Extras.cleanLink("https://example.com:8080/x?a=1&gclid=z#s"),
                "https://example.com/a?q=a|b https://example.com:8080/x?a=1#s");
        String pw = Extras.password(new java.util.Random(7));
        check("password", pw.length() + " " + pw.matches(".*[a-z].*") + pw.matches(".*[A-Z].*") + pw.matches(".*[2-9].*") + pw.matches(".*[!@#$%&*?\\-+=].*")
                + " " + pw.matches(".*[0O1lI].*"), "16 truetruetruetrue false");
        java.time.Instant noon = java.time.Instant.parse("2026-10-09T17:00:00Z");
        check("time in tokyo", Extras.timeIn("What time is it in Tokyo?", noon, java.time.ZoneId.of("America/Chicago")),
                "In Tokyo it's 2:00 AM on Saturday.\n(That's 14 hours ahead of you.)");
        check("time in london", Extras.timeIn("what's the time in london", noon, java.time.ZoneId.of("America/Chicago")), "In London it's 6:00 PM.\n(That's 6 hours ahead of you.)");
        check("not time in", Extras.timeIn("what time is it", noon, java.time.ZoneId.of("America/Chicago")), null);
        check("word count", Extras.wordCount("one two  three\nfour"), "4 words, 19 characters.");
        check("choose", Extras.choose("choose between pizza, tacos or burgers", new java.util.Random(1)) != null, true);
        check("this or that", Extras.choose("pizza or tacos?", new java.util.Random(1)).matches(".*(Pizza|pizza|Tacos|tacos).*"), true);
        check("a question with or", Extras.choose("is it cold or hot", new java.util.Random(1)), null);
        check("questions with or aren't choosing", Extras.choose("true or false the earth is flat", new java.util.Random(1)) + " " + Extras.choose("who's taller lebron or jordan", new java.util.Random(1))
                + " " + Extras.choose("put chips or pretzels on the list", new java.util.Random(1)) + " " + Extras.choose("pick up the kids and the dog", new java.util.Random(1)), "null null null null");
        check("time somewhere he doesn't know", Extras.timeIn("what time is it in florida", noon, java.time.ZoneId.of("America/Chicago")) + " "
                + (Extras.timeIn("whats the time in tokyo", noon, java.time.ZoneId.of("America/Chicago")) != null), "null true");
        check("not every where's-my is a file", FindFile.wordsIn("where is my mom") + " " + FindFile.wordsIn("where are my keys") + " " + FindFile.wordsIn("find my phone")
                + " " + FindFile.wordsIn("where's my history essay") + " " + FindFile.wordsIn("find pictures of my dog") + " " + FindFile.wordsIn("find my essay about volcanoes"),
                "null null null [history, essay] [dog] [essay, volcanoes]");
        check("pick a number isn't choosing", Extras.choose("pick a number between 1 and 5", new java.util.Random(1)), null);
        check("open app", Extras.appFor("open the calculator") != null && Extras.appFor("open downloads").equals("folder:Downloads"), true);
        check("open nothing", Extras.appFor("open sesame"), null);
        // finding files, downloads, the sticky note
        check("find words", FindFile.wordsIn("find my history essay") + " " + FindFile.wordsIn("Where did I save the birthday pictures?") + " "
                + FindFile.wordsIn("find the file called budget_2026") + " " + FindFile.wordsIn("find the area of a circle"), "[history, essay] [birthday] [budget, 2026] null");
        Path lookIn = Files.createTempDirectory("clawdtop-find");
        Files.createDirectories(lookIn.resolve("School/.hidden"));
        Files.writeString(lookIn.resolve("School/History Essay final.docx"), "x");
        Files.writeString(lookIn.resolve("School/.hidden/history essay.txt"), "x");
        Files.writeString(lookIn.resolve("essay notes.txt"), "x");
        check("find files", FindFile.search(List.of("history", "essay"), List.of(lookIn), 3000).stream().map(p -> p.getFileName().toString()).toList(),
                "[History Essay final.docx]");
        check("where it is", FindFile.whereIs(lookIn.resolve("School/History Essay final.docx"), lookIn), "School");
        check("still downloading", FindFile.partial("movie.mp4.crdownload") + " " + FindFile.partial("song.mp3.part") + " " + FindFile.partial("game.exe"), "true true false");
        check("sizes", FindFile.size(500) + " / " + FindFile.size(340_000) + " / " + FindFile.size(12_400_000), "500 bytes / 332 KB / 11.8 MB");
        java.awt.FontMetrics noteFont = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics()
                .getFontMetrics(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 13));
        List<String> noteLines = Sticky.wrap("Dentist at 4, then pick up Grandma from the airport and buy milk eggs bread cheese apples", noteFont, 106, 5);
        check("sticky note wraps", noteLines.size() <= 5 && noteLines.stream().allMatch(l -> noteFont.stringWidth(l) <= 106) && noteLines.get(0).startsWith("Dentist"), true);
        java.awt.Rectangle wide = new java.awt.Rectangle(0, 0, 2000, 1000);
        check("sticky note beside him, and out from under his bubble", Sticky.spotX(1800, 100, wide, null) + " " + Sticky.spotX(1800, 100, wide, new java.awt.Rectangle(1500, 700, 380, 90))
                + " " + Sticky.spotX(1800, 100, wide, new java.awt.Rectangle(1900, 700, 50, 90)) + " " + Sticky.spotX(20, 100, wide, null), "1676 1364 1676 112");
        check("sticky note one huge word", Sticky.wrap("Supercalifragilisticexpialidocious", noteFont, 60, 5).size() > 1, true);
        // birthdays as people type them
        check("birthdays", Helpers.birthday("10/08") + " " + Helpers.birthday("10-8") + " " + Helpers.birthday("Oct 8") + " " + Helpers.birthday("8 October")
                + " " + Helpers.birthday("25/12") + " " + Helpers.birthday("October 8th") + " " + Helpers.birthday("2/31") + " " + Helpers.birthday("8/") + " "
                + Helpers.birthday("2/29") + " " + Helpers.birthday("blah") + " " + Helpers.birthday("3/4/2012"),
                "10-08 10-08 10-08 10-08 12-25 10-08 null null 02-29 null 03-04");
        // what counts as a show: titles only in a browser
        check("media", Seeing.mediaIn("chrome.exe", "Funny cats - YouTube") + " " + Seeing.mediaIn("WINWORD.EXE", "Netflix essay.docx") + " "
                + Seeing.mediaIn("explorer.exe", "YouTube") + " " + Seeing.mediaIn("spotify.exe", "Song") + " " + Seeing.mediaIn("Safari", "Netflix") + " "
                + Seeing.mediaIn("discord.exe", "#twitch-clips"), "video null null music video null");
        // a jam can be stopped (a click), and its song goes by the real clock (not his animation speed)
        Pet jammer = new Pet(5);
        check("jam starts", jammer.jamAgain(new Piano.Song("test jam", new int[] {60, 62}, new double[] {1, 1}, 400, new java.io.File("test_jam.mid"), 2000)) + " " + jammer.jamming(), "true true");
        jammer.stopPiano();
        check("jam stops", jammer.jamming() + " " + jammer.mood(), "false IDLE");
        // the morning rundown
        java.time.LocalDate rundownDay = java.time.LocalDate.of(2026, 10, 9);
        check("days to birthday", Helpers.daysToBirthday("10-12", rundownDay) + " " + Helpers.daysToBirthday("10-08", rundownDay) + " " + Helpers.daysToBirthday("", rundownDay), "3 364 -1");
        check("rundown", Helpers.rundown(rundownDay, List.of("homework", "dishes"), "Dentist at 4", 3, "Sam"),
                "Here's your day, Sam! It's Friday, October 9.\n- 2 things on your to-do list (first up: homework)\n- Your note says: Dentist at 4\n- 3 days till your birthday!");
        check("no rundown with nothing to say", Helpers.rundown(rundownDay, List.of(), "", 200, "Sam"), null);
        // every option has its own key (two with the same key would switch each other)
        java.util.Set<String> optionKeys = new java.util.HashSet<>();
        java.util.List<String> twice = new java.util.ArrayList<>();
        for (Options.Option o : Options.ALL) if (!optionKeys.add(o.key())) twice.add(o.key());
        check("option keys are all different", twice, "[]");
        check("eye breaks start off", Settings.load().on("eyeBreaks") + " " + Settings.load().on("eyes"), "false true");
        check("on for", Useful.onFor(25 * 60_000L) + " / " + Useful.onFor(30_000) + " / " + Useful.onFor(3 * 3_600_000L) + " / " + Useful.onFor(2 * 86_400_000L + 4 * 3_600_000L)
                + " / " + Useful.onFor(86_400_000L), "25 minutes / just a minute / 3 hours / 2 days 4 hours / 1 day");
        check("Mac apps and libraries aren't folders to clean", Cleaner.bundle("Zoom.app") + " " + Cleaner.bundle("Photos Library.photoslibrary") + " "
                + Cleaner.bundle("old stuff") + " " + Cleaner.bundle("v1.2"), "true true false false");
        check("x for times, again and again", MathHelp.parse("2x3x4") == null ? "null" : MathHelp.parse("2x3x4").properly() + "", "24.0");
        check("dividing by zero isn't possible", MathHelp.possible(MathHelp.parse("5/0")), false);
        check("uninstall takes back only his own .zprofile lines", Platform.withoutOurPath("export A=1\n# added by Clawdtop, so Terminal knows the clawd command\nexport PATH=\"$HOME/.local/bin:$PATH\"\nalias x=y\n"),
                "export A=1\nalias x=y\n");
        check("the save token keeps his shirt", java.util.Arrays.asList(SaveToken.KEYS).contains("shirt"), true);
        Settings forOptions = Settings.load();
        javax.swing.JComponent optionsPanel = OptionsWindow.panel(() -> forOptions, () -> { });
        optionsPanel.setSize(560, 600);
        snapshot(optionsPanel, frames0.resolve("all the options.png"));
        check("all the options, in tabs", ((javax.swing.JTabbedPane) ((java.awt.BorderLayout) optionsPanel.getLayout()).getLayoutComponent(java.awt.BorderLayout.CENTER)).getTabCount() > 3, true);
        // the desktop: reading icon spots (Windows' script and Finder say the same shape), sorting files for Neat
        Desktop.Layout desk = Desktop.read("DESKTOP|/home/me/Desktop\nskin|177,2\nmy song.mid|2427,1032\nnot an icon\n");
        check("desktop read", desk.folder().getFileName() + " " + desk.icons(), "Desktop [Icon[name=skin, x=177, y=2], Icon[name=my song.mid, x=2427, y=1032]]");
        check("neat types", Desktop.category(Path.of("a.PNG")) + " " + Desktop.category(Path.of("b.mid")) + " " + Desktop.category(Path.of("c.lnk"))
                + " " + Desktop.category(Path.of("desktop.ini")) + " " + Desktop.category(Path.of("d.weird")), "Pictures Music null null Other");
        check("launchers count", Games.launcher("steam.exe") + " " + Games.launcher("EADesktop.exe") + " " + Games.launcher("notepad.exe"), "true true false");

        // ---- Being useful ----
        String report = Useful.checkup();
        check("How's my computer? says how it's doing", report.contains("Memory:") && (report.startsWith("Your computer's doing great!") || report.startsWith("Here's how")), true);
        check("he knows how long it's been on", Useful.uptime() > 0, true);
        check("reminders have something to say", Useful.WATER.length >= 3 && Useful.STRETCH.length >= 3, true);

        // ---- His piano, and MIDI files ----
        javax.sound.midi.Sequence seq = new javax.sound.midi.Sequence(javax.sound.midi.Sequence.PPQ, 4);
        javax.sound.midi.Track track = seq.createTrack();
        int[] tune = {60, 60, 67, 67, 69, 69, 67};
        for (int i = 0; i < tune.length; i++) {
            track.add(new javax.sound.midi.MidiEvent(new javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_ON, 0, tune[i], 90), i * 4L));
            track.add(new javax.sound.midi.MidiEvent(new javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_ON, 0, tune[i] - 12, 90), i * 4L)); // a bass note under it
            track.add(new javax.sound.midi.MidiEvent(new javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_ON, 9, 36, 90), i * 4L)); // drums
            track.add(new javax.sound.midi.MidiEvent(new javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_OFF, 0, tune[i], 0), i * 4L + 3));
        }
        java.io.File midiFile = home.resolve("Twinkle_Little.mid").toFile();
        javax.sound.midi.MidiSystem.write(seq, 0, midiFile);
        Piano.Song fromMidi = Piano.fromMidi(midiFile);
        check("a dropped MIDI file becomes his song: the tune (not the bass or drums), with its own name",
                java.util.Arrays.toString(fromMidi.notes()) + " " + fromMidi.name(), "[60, 60, 67, 67, 69, 69, 67] your Twinkle Little");
        check("and not-music isn't", Piano.fromMidi(home.resolve("settings.properties").toFile()) + " " + Piano.isMidi(midiFile), "null true");
        Pet player = new Pet(4);
        player.takeBeep();
        player.setPrefs(new Pet.Prefs() { // (no little mistakes, for this check)
            public boolean on(String key) {
                return !key.equals("mistakes") && Boolean.parseBoolean(Options.find(key).start());
            }

            public int number(String key) {
                return Integer.parseInt(Options.find(key).start());
            }

            public String choice(String key) {
                return Options.find(key).start();
            }
        });
        check("he fetches it", player.fetch(fromMidi) + " " + player.mood(), "true FETCH");
        java.util.List<Integer> played = new java.util.ArrayList<>();
        for (int i = 0; i < 400 && (player.mood() == Pet.Mood.FETCH || player.mood() == Pet.Mood.PIANO); i++) {
            player.tick(33, 0, 0, false, false);
            int note = player.takeNote();
            if (note > 0) played.add(note);
        }
        check("then plays every note of it on his piano", played.toString(), "[60, 60, 67, 67, 69, 69, 67]");
        int wrong = 0;
        for (int seed = 0; seed < 30; seed++) {
            Pet clumsy = new Pet(seed);
            clumsy.play(Piano.Instrument.GUITAR, Piano.SONGS[0]);
            java.util.List<Integer> notesHeard = new java.util.ArrayList<>();
            for (int i = 0; i < 300 && clumsy.mood() == Pet.Mood.PIANO; i++) {
                clumsy.tick(33, 0, 0, false, false);
                int n = clumsy.takeNote();
                if (n > 0) notesHeard.add(n);
            }
            if (!notesHeard.equals(java.util.Arrays.stream(Piano.SONGS[0].notes()).boxed().toList())) wrong++;
        }
        check("now and then he plays a wrong note (but not every time)", wrong > 2 && wrong < 25, true);
        Pet pianist = new Pet(4);
        pianist.playPiano(Piano.SONGS[0]);
        for (int i = 0; i < 60; i++) pianist.tick(33, 0, 0, false, false);
        save(pianist, Path.of("build", "frames").resolve("playing the piano.png"));

        // ---- Weather (only with looking things up on) ----
        check("weather questions are spotted", WebSearch.aboutWeather("what's the weather like?") + " " + WebSearch.aboutWeather("is it raining") + " " + WebSearch.aboutWeather("whether to code"), "true true false");
        check("but not science about it", WebSearch.aboutWeather("what temperature does water boil at") + " " + WebSearch.aboutWeather("how does weather work"), "false false");
        check("reminders understand more", Reminders.parse("set a 5 minute timer").when() + " / " + Reminders.parse("remind me in 1 hour 30 minutes to eat").when()
                + " / " + Reminders.parse("remind me in an hour and a half").when() + " / " + Reminders.parse("set a timer for twelve minutes").when(),
                "5 minutes / 1 hour 30 minutes / 1 hour 30 minutes / 12 minutes");
        check("and he's honest about the ones he can't do", Reminders.soundsLikeOne("remind me tomorrow to call grandma") + " " + Reminders.soundsLikeOne("remind me in 5 minutes"), "true false");
        check("but \"remind me how...\" is a question for him", Reminders.soundsLikeOne("remind me how photosynthesis works"), false);
        check("powers survive the tidy-up, and sniggering is fine", Brain.clean("2^3 is 8") + " / " + Brain.noBadWords("sniggering"), "2^3 is 8 / sniggering");
        check("he never says bad words back", Reminders.parse("remind me in 5 minutes to say shit").what() + " | " + Brain.noBadWords("Moby-Dick in Scunthorpe, bullshit"),
                "say beep | Moby-Dick in Scunthorpe, beep");
        check("answers cut off mid-sentence end on a full one", Brain.toLastSentence("Crabs walk sideways. They have ten le"), "Crabs walk sideways.");
        check("accents stay, emoji go", Brain.clean("Beyoncé is 100°C 😀 cool"), "Beyoncé is 100°C  cool");
        check("dice only when you want dice", QuickAnswers.answer("how do I roll the dice in Monopoly", new java.util.Random(1)), null);
        check("and he has something to say about it", Clawdtop.weatherQuip("Light rain, 60 F").contains("umbrella") + " " + Clawdtop.weatherQuip("Overcast, 76 F").contains("coding weather"), "true true");

        // ---- Tic-tac-toe ----
        TicTacToe ttt = new TicTacToe(new java.util.Random(4));
        check("tic-tac-toe: he blocks a line", TicTacToe.winner("XX OO    ".toCharArray()) + "|" + TicTacToe.winner("XXXOO    ".toCharArray()) + "|" + TicTacToe.winner("XOXXOOOXX".toCharArray()), " |X|T");
        char result = ' ';
        for (int i = 0; i < 9 && result == ' '; i++) if (ttt.board[i] == ' ') result = ttt.play(i);
        check("a game always ends", result != ' ', true);

        // ---- His diary ----
        Diary.write("Coded carpet.py. I made a flying carpet!!");
        Diary.write("Landed on my head. Saw stars. I'm fine.");
        String diary = cli("diary");
        check("clawd diary tells what he got up to today", diary.contains("Dear diary, today:") && diary.contains("Saw stars"), true);

        // ---- Holidays ----
        check("Easter is worked out right", Holidays.easter(2026) + " " + Holidays.easter(2027) + " " + Holidays.easter(2030), "2026-04-05 2027-03-28 2030-04-21");
        check("special days", Holidays.on(java.time.LocalDate.of(2026, 12, 25)).id() + " " + Holidays.on(java.time.LocalDate.of(2026, 11, 26)).id() + " "
                + Holidays.on(java.time.LocalDate.of(2026, 10, 31)).id() + " " + Holidays.on(java.time.LocalDate.of(2026, 10, 30)), "christmas thanksgiving halloween null");
        Pet festive = new Pet(2);
        festive.celebrate("Merry Christmas!", Holidays.on(java.time.LocalDate.of(2026, 12, 25)).show());
        for (int i = 0; i < 60; i++) festive.tick(33, 0, 0, false, false);
        save(festive, Path.of("build", "frames").resolve("christmas.png"));

        // ---- Quick answers (no brain needed) ----
        java.util.Random dice = new java.util.Random(1);
        check("what time is it", QuickAnswers.answer("What time is it?", dice).startsWith("It's "), true);
        check("coin, dice, numbers, rock paper scissors", QuickAnswers.answer("flip a coin", dice).contains("...") + " "
                + QuickAnswers.answer("roll a d20", dice).startsWith("*rattle rattle*") + " " + QuickAnswers.answer("pick a number between 1 and 10", dice).startsWith("Hmm... ")
                + " " + QuickAnswers.answer("rock", dice).startsWith("I pick "), "true true true true");
        check("but real questions go to his brain", QuickAnswers.answer("should I learn Python?", dice) + " " + QuickAnswers.answer("why is the sky blue", dice), "null null");

        // ---- Reminders and the focus timer ----
        java.time.LocalTime twoPm = java.time.LocalTime.of(14, 0);
        check("remind at a time", Reminders.parse("remind me at 3pm to call grandma", twoPm).when() + " / " + Reminders.parse("remind me to feed my cat at 7:30", twoPm).when()
                + " / " + Reminders.parse("remind me at noon to eat", twoPm).when() + " / " + Reminders.parse("remind me at 9am to wake up", twoPm).when()
                + " / " + Reminders.parse("remind me to feed my cat at 7:30", twoPm).what() + " / " + Reminders.parse("remind me at 13pm to x", twoPm),
                "1 hour / 5 hours 30 minutes / 22 hours / 19 hours / feed your cat / null");
        java.time.LocalTime eightAm = java.time.LocalTime.of(8, 0);
        check("tonight means pm", Reminders.parse("remind me at 9 tonight to call mom", eightAm).when() + " / " + Reminders.parse("remind me to read at 7 this evening", eightAm).when()
                + " / " + Reminders.parse("remind me at 9 to call mom", eightAm).when(), "13 hours / 11 hours / 1 hour");
        List<String> chores = List.of("call grandma", "math homework", "science homework", "do the dishes");
        check("crossing off", Extras.todosMatching("the dishes", chores) + " " + Extras.todosMatching("grandma call", chores) + " " + Extras.todosMatching("homework", chores)
                + " " + Extras.todosMatching("math homework", chores) + " " + Extras.todosMatching("it", chores), "[do the dishes] [] [math homework, science homework] [math homework] []");
        Reminders.Reminder oven = Reminders.parse("Remind me in 10 minutes to check the oven.");
        check("remind me in 10 minutes to...", oven.inMs() + " " + oven.when() + " / " + oven.what(), "600000 10 minutes / check the oven");
        Reminders.Reminder cat = Reminders.parse("remind me to feed my cat in half an hour");
        check("remind me to... in half an hour (and it's your cat now)", cat.when() + " / " + cat.what(), "30 minutes / feed your cat");
        check("timers", Reminders.parse("set a timer for 5 minutes").when() + " / " + Reminders.parse("two hour timer").when() + " / " + Reminders.parse("in 90 seconds remind me to stir").when(),
                "5 minutes / 2 hours / 1 minute");
        check("but not just anything", Reminders.parse("what's the time") + " " + Reminders.parse("remind me in 400 hours to sleep"), "null null");
        Pet focused = new Pet(2);
        focused.focus(true, false);
        for (int i = 0; i < 30; i++) focused.tick(33, 0, 0, false, false);
        save(focused, Path.of("build", "frames").resolve("focus timer.png"));
        check("focus: headphones on, quiet, and not interrupted", focused.mood() + " " + focused.busyNow(), "FOCUS true");
        check("a reminder still gets through", focused.remind("drink some water") + " " + focused.takeLine(), "true Reminder: drink some water!");

        // ---- No tomfoolery ----
        Settings calm = Settings.load();
        calm.set("serious", "true");
        check("no tomfoolery turns off the silly stuff", calm.on("creates") + " " + calm.on("sneezes") + " " + calm.on("rides") + " " + calm.jokes(), "false false false Off");
        check("but he's still useful", calm.on("diskSpace") + " " + calm.on("restart"), "true true");
        check("and the control panel has the switch", cli("controlpanel", "13", "0").contains("No tomfoolery") + " " + Settings.load().serious(), "true false");
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
