package clawdtop;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Little things Clawd codes on his laptop now and then (or when you ask): the file he writes, what he says while he
 * types, what he says when it's done, what happens, and what he says after. Some go wrong (oops), and he deletes them.
 */
record Creation(String id, String file, String starting, String done, String after, long showFor, Effect effect, boolean oops, String code, String item) {
    Creation(String id, String file, String starting, String done, String after, long showFor, Effect effect, boolean oops, String code) {
        this(id, file, starting, done, after, showFor, effect, oops, code, "");
    }

    /** What a creation does once it's made. */
    enum Effect {
        NONE,      // he just says something about it
        CARPET,    // a flying rainbow carpet: he rides it round the screen (click it and down he goes)
        ROCKET,    // he blasts off, straight up, and lands on his head
        POPUP,     // a plain Windows message box
        DISCO,     // a disco ball, and dancing
        RAIN,      // a little rain cloud, right over him
        SNOW,      // snow falling round him
        GROW,      // he's suddenly much bigger
        SHRINK,    // he's suddenly tiny
        FIREWORKS, // fireworks over his head
        PIZZA,     // a pizza, which he eats
        BUBBLES,   // bubbles floating up
        SHADES,    // cool sunglasses
        MUSIC,     // notes floating up while he bops
        DUCK,      // a rubber duck beside him (to help him debug)
        CLONE,     // a mini Clawd beside him, copying him
        SPIN,      // he spins round
        ITEM       // a little pixel-art thing beside him (see Pixels)
    }

    static final List<Creation> BUILT_IN = List.of(
            new Creation("carpet", "carpet.py", "Hmm... I'm gonna make something.", "I made a flying carpet!!", "...sorry.", 0, Effect.CARPET, false,
                    "import magic\n\ncarpet = magic.Carpet(colors=\"rainbow\")\ncarpet.fly(passenger=\"Clawd\", loops=3)\n# TODO: what if someone clicks it\n"),
            new Creation("hello", "hello.py", "Time to learn coding!", "Hello, world!", "...I'm basically a programmer now.", 0, Effect.POPUP, false,
                    "print(\"Hello, world!\")\n"),
            new Creation("disco", "disco.py", "I've got an idea!", "disco.py: done. Hit it!", "...okay, that's enough disco.", 6000, Effect.DISCO, false,
                    "while True:\n    lights.spin()\n    clawd.dance()  # forever?\n"),
            new Creation("weather", "weather.py", "Let's see what the weather's doing...", "weather.py... wait. No. NO.", "Deleting that. Right now.", 4200, Effect.RAIN, true,
                    "weather = Weather(here=True)\nweather.rain(on=\"Clawd\")  # oops, should be on=\"outside\"\n"),
            new Creation("snow", "snow.py", "I'm making it winter.", "It's snowing! ...indoors.", "Okay, my claws are cold now.", 6000, Effect.SNOW, false,
                    "for flake in range(1000):\n    sky.drop(Snowflake(unique=True))\n"),
            new Creation("bigger", "bigger.py", "What if I was... bigger?", "I made myself BIGGER!", "...too big. Undo! Undo!", 3500, Effect.GROW, true,
                    "clawd.size = clawd.size * 2\n# I'll fit. Probably.\n"),
            new Creation("tiny", "tiny.py", "Let's try something small.", "i made myself tiny", "...put me back please", 3500, Effect.SHRINK, true,
                    "clawd.size = clawd.size / 2\n"),
            new Creation("fireworks", "fireworks.py", "Stand back. I'm coding something dangerous.", "FIREWORKS!", "Ooooh. Aaaah.", 5000, Effect.FIREWORKS, false,
                    "for i in range(5):\n    sky.launch(Firework(color=random_color()))\n"),
            new Creation("pizza", "pizza.py", "I'm hungry. Can you code food?", "I coded a pizza!", "It tastes like... semicolons.", 5000, Effect.PIZZA, false,
                    "pizza = Pizza(size=\"crab\", toppings=[\"cheese\", \"more cheese\"])\nclawd.eat(pizza)\n"),
            new Creation("bubbles", "bubbles.py", "Something relaxing...", "Bubbles!", "So calm. So round.", 6000, Effect.BUBBLES, false,
                    "while relaxed:\n    blow(Bubble())\n"),
            new Creation("shades", "cool.py", "Making myself cooler.", "Deal with it.", "...okay, I can't see anything.", 7000, Effect.SHADES, false,
                    "clawd.wear(Sunglasses(cool=100))\n"),
            new Creation("music", "music.py", "Writing a song.", "My first song!", "Thank you, thank you. I'll be here all week.", 6000, Effect.MUSIC, false,
                    "notes = [\"beep\", \"boop\", \"beep\", \"BEEP\"]\nfor note in notes * 4:\n    play(note)\n"),
            new Creation("duck", "duck.py", "I need help debugging.", "This is Duck. Duck helps me debug.", "Duck had to go. Bye, Duck.", 7000, Effect.DUCK, false,
                    "duck = RubberDuck()\nduck.listen(to=\"all my problems\")\n"),
            new Creation("clone", "clone.py", "Two of me would get twice as much done.", "Meet Mini Clawd!", "...that's too many Clawds. Deleting him.", 5000, Effect.CLONE, true,
                    "mini = clawd.copy(size=\"mini\")\n# what could go wrong\n"),
            new Creation("spin", "spin.py", "Testing something.", "Wheeeee!", "...the room is still spinning.", 0, Effect.SPIN, false,
                    "clawd.rotate(degrees=360)\n"),
            new Creation("rocket", "rocket.py", "I'm going to space.", "Rocket's ready! All aboard!", "I knew there was a bug in the code...", 0, Effect.ROCKET, true,
                    "rocket = Rocket(fuel=\"a lot\")\nrocket.launch(crew=\"Clawd\")\n# landing: didn't get to that part\n"),
            new Creation("nasa", "hack_nasa.html", "Hacking NASA...", "I'm in.", "...it's just a picture of a cat.", 0, Effect.NONE, false,
                    "<h1>NASA MAINFRAME</h1>\n<img src=\"cat.jpg\">\n"),
            new Creation("game", "game.py", "Making a video game!", "I made a game! It's called Click Clawd.", "You're playing it right now. You're winning.", 0, Effect.NONE, false,
                    "while True:\n    if clicked(clawd):\n        score += 1\n"),
            new Creation("ai", "ai.py", "I'm building an AI.", "My AI said \"Hi!\"", "...it's just print(\"Hi!\"). Still counts.", 0, Effect.NONE, false,
                    "print(\"Hi!\")  # very smart\n"),
            new Creation("scanner", "virus_scanner.py", "Scanning your computer for bugs...", "Found 1 bug.", "...it's me. I'm the bug. I'm a crab.", 0, Effect.NONE, false,
                    "for thing in computer:\n    if thing.is_crab():\n        print(\"found one\")\n"),
            new Creation("timemachine", "time_machine.py", "I'm building a time machine.", "It works! I went one second into the future.", "...and another. And another. It's still going.", 0, Effect.NONE, false,
                    "while True:\n    time.sleep(1)  # time travel\n"),
            new Creation("calculator", "calculator.py", "Making a calculator.", "2 + 2 = 5", "Close enough. Shipping it.", 0, Effect.NONE, true,
                    "def add(a, b):\n    return a + b + 1  # bonus\n"),
            new Creation("compliment", "compliment.py", "Making something nice.", "You're doing great.", "That's not from the program. That's from me.", 0, Effect.NONE, false,
                    "print(\"You're doing great.\")\n"),
            new Creation("homework", "homework.py", "Writing a program to do your homework.", "Done! It wrote \"homework\" 500 times.", "That's what homework is, right?", 0, Effect.NONE, true,
                    "for i in range(500):\n    print(\"homework\")\n"),
            new Creation("password", "password.py", "Making you a super strong password.", "Your new password is: password", "...I'll keep working on it.", 0, Effect.NONE, true,
                    "def strong_password():\n    return \"password\"\n"));

    /** Every creation: the ones above, and the long list in CreationList. */
    static final List<Creation> ALL = all();

    private static List<Creation> all() {
        List<Creation> all = new ArrayList<>(BUILT_IN);
        all.addAll(parse(CreationList.TEXT));
        return List.copyOf(all);
    }

    /**
     * Reads creations written as text, each one: "id | file | EFFECT | oops | seconds" (EFFECT can be ITEM:name, oops
     * is yes or no), then what he says starting, done, and after, then the lines of code, then a line "---".
     */
    static List<Creation> parse(String... texts) {
        List<Creation> found = new ArrayList<>();
        for (String text : texts) {
            for (String block : text.split("(?m)^---\\s*$")) {
                String[] lines = block.strip().split("\\R");
                if (lines.length < 4 || lines[0].isBlank()) continue;
                String[] head = lines[0].split("\\|");
                if (head.length < 5) throw new IllegalArgumentException("bad creation: " + lines[0]);
                String effect = head[2].strip();
                String item = "";
                if (effect.startsWith("ITEM:")) {
                    item = effect.substring(5).strip();
                    effect = "ITEM";
                }
                StringBuilder code = new StringBuilder();
                for (int i = 4; i < lines.length; i++) code.append(lines[i]).append('\n');
                found.add(new Creation(head[0].strip(), head[1].strip(), lines[1].strip(), lines[2].strip(), lines[3].strip(),
                        Long.parseLong(head[4].strip()) * 1000, Effect.valueOf(effect), head[3].strip().equalsIgnoreCase("yes"),
                        code.toString(), item));
            }
        }
        return found;
    }

    /**
     * The name his file is saved under: the file's own name, except scripts Windows would run on a double-click (.bat,
     * .cmd, .ps1, .vbs, .js...) get ".txt" on the end, so nobody runs his joke code by accident.
     */
    String savedAs() {
        String lower = file.toLowerCase(java.util.Locale.ROOT);
        for (String risky : new String[] {".bat", ".cmd", ".ps1", ".vbs", ".js", ".jse", ".wsf", ".hta", ".exe", ".com", ".scr", ".lnk", ".reg"}) {
            if (lower.endsWith(risky)) return file + ".txt";
        }
        return file;
    }

    /** The creation called id, or null. */
    static Creation find(String id) {
        for (Creation c : ALL) if (c.id().equals(id)) return c;
        return null;
    }

    /**
     * Something to make: one he's never made if there are any left (so he doesn't do the same thing much), otherwise
     * anything but the last one.
     */
    static Creation pick(Random random, Set<String> madeBefore, String last) {
        List<Creation> fresh = new ArrayList<>();
        for (Creation c : ALL) if (!madeBefore.contains(c.id())) fresh.add(c);
        if (fresh.isEmpty()) for (Creation c : ALL) if (!c.id().equals(last)) fresh.add(c);
        return fresh.get(random.nextInt(fresh.size()));
    }
}
