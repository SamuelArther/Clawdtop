package clawdtop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Clawd's jokes. He tells one now and then (how often is up to you), or when you ask. They go round in a shuffled
 * order, so you hear them all before any comes back.
 */
final class Jokes {
    static final List<String> ALL = List.of(
            "I tried to write a joke about RAM,\nbut I forgot it.",
            "I'm not lazy.\nI'm in power-saving mode.",
            "There are 10 kinds of people: those who\nget binary, and those who don't.",
            "I asked the Recycle Bin how it's doing.\nIt said it's been through a lot of garbage.",
            "Debugging is being the detective in a crime\nmovie where you're also the criminal.",
            "I'd tell you a UDP joke,\nbut you might not get it.",
            "Your cursor and I are best friends.\nDon't tell the mouse.",
            "I don't have arms. I have floating hands.\nIt's a whole thing. Please don't ask.",
            "Knock knock.\n...it's me. I'm always here. You don't have to answer.",
            "I'm basically a screensaver\nthat refuses to leave.",
            "I'm 15 pixels tall\nand I've never been happier.",
            "Ctrl+Z doesn't work in real life.\nI checked.",
            "Weather report from the taskbar:\n100% chance of me.",
            "I wanted to be a wallpaper,\nbut I couldn't stay still.",
            "I'm not short.\nI'm low-resolution.",
            "A SQL query walks into a bar, walks up to two\ntables and asks: \"Can I join you?\"",
            "Do you ever just... exist? Above a clock?\nNo? Just me?",
            "Why don't programmers like nature?\nToo many bugs.",
            "I'm reading a book about anti-gravity.\nI can't put it down.",
            "My favorite drink?\nClawfee. Obviously.",
            "I tried to catch fog yesterday.\nMist.",
            "Have you tried turning it off and on again?\n...not me. Please don't.",
            "I'm on a seafood diet. I see food,\nand I can't eat it, because I'm pixels.",
            "Why do Java developers wear glasses?\nBecause they don't C#.",
            "I'd make a joke about the Recycle Bin,\nbut it's a bit trashy.",
            "The clock and I don't talk much.\nIt's always watching. Tick. Tock.",
            "The taskbar told me to get off.\nI said no.",
            "When I'm happy I put my hands up.\nThey're not attached, so it's very easy.",
            "Error 404: joke not found.\n...just kidding. This is the joke.",
            "Why was the computer cold?\nIt left its Windows open.",
            "Parallel lines have so much in common.\nShame they'll never meet.",
            "I'm reading a horror story.\nIt's called \"Unsaved Changes\".",
            "Every time you open Task Manager I get nervous.\nAm I using too much memory? Be honest.",
            "Why did the developer go broke?\nHe used up all his cache.",
            "What do you call 8 hobbits?\nA hobbyte.",
            "I'm not procrastinating.\nI'm doing side quests.",
            "My code works and I don't know why.\nI'm not touching it.",
            "Why did the function stop calling?\nIt had too many arguments.",
            "I put the \"pro\" in procrastinate.\nAnd the \"nap\" in... nap.",
            "Why are keyboards always tired?\nThey have two shifts.",
            "I've been on this taskbar so long,\nthe clock calls me roommate.",
            "Fun fact: crabs walk sideways.\nI walk however I want. I'm not a crab. Probably.",
            "Why did the PNG go to therapy?\nIt had too many layers. Wait, that's a PSD.",
            "Breaking news: local Clawd sits.\nMore at 11.",
            Platform.MAC ? "If you need me,\nI'll be exactly here. Forever. Above the Dock." : "If you need me,\nI'll be exactly here. Forever. Above the clock.",
            "My password is \"incorrect\".\nSo when I forget, it reminds me.",
            "The Wi-Fi went down for an hour.\nI met your family. They seem nice.",
            "I tried Ctrl+Z on Monday.\nIt's still Monday.",
            "Loading bar at 99%.\nIt lives there now. It pays rent.",
            "\"Update: 2 minutes remaining.\"\nOne hour later: \"5 minutes remaining.\"",
            "It's not a bug.\nIt's a surprise feature.",
            "Why did the programmer quit?\nThey didn't get arrays.",
            "Want to hear a joke about infinite loops?\nWant to hear a joke about infinite loops?",
            "There's no place like 127.0.0.1.\nThat means home. For the non-nerds.",
            "Why are spiders great at coding?\nThey practically live on the web.",
            "I tried to download more RAM.\nI got a picture of a sheep.",
            "Caps Lock: the official key\nof SHOUTING BY ACCIDENT.",
            "Ctrl+C, Ctrl+V.\nThe two most powerful spells ever cast.",
            "Clicking \"I'm not a robot\"\nis the most stressful part of my day.",
            "\"Password needs a capital, a number,\na symbol, and a tiny drawing of a duck.\"",
            "What's a computer's favorite beat?\nAn algo-rhythm.",
            "Why was the computer so tired?\nIt had a hard drive.",
            "My favorite programming language?\nShell. Obviously.",
            "Autocorrect keeps changing Clawd to Cloud.\nI'm not a weather event.",
            "Dark mode is just your computer\ngoing to bed early.",
            "My favorite key is Esc.\nIt gets me out of everything.",
            "I press Ctrl+S every five seconds.\nIt's my only exercise.",
            "The loading spinner is my spirit animal.\nGoes in circles. Gets nothing done.",
            "The Wi-Fi has one bar.\nSo do I. The taskbar.",
            "We used to be close, me and the Wi-Fi.\nThen you moved to the other room.",
            "I stored my memories in the cloud.\nNow they're a little foggy.",
            "What's a computer's favorite snack?\nMicrochips.",
            "Your browser offered me cookies.\nI'm still hungry.",
            "Alt+F4 is not a cheat code.\nPlease stop telling people that.",
            "Your desktop has \"New Folder (7)\".\nWhat happened to the other six?",
            "My joke generator crashed.\nPlease laugh at this sentence instead.",
            "This joke is in beta.\nPlease report any laughs.",
            "My neighbor the clock got promoted.\nNow it shows the date too.",
            "The clock says I'm wasting its time.\nRude. I'm wasting MY time.",
            "I asked the clock what time it is.\nIt said: same as always. Now.",
            "People keep glancing at the clock.\nI know they're really looking at me.",
            "The Start button is a liar.\nYou also click it to shut down.",
            "The volume icon won't talk to me.\nIt's a mute point.",
            "The battery icon is so anxious.\nIt's always checking its levels.",
            "Minimize all your windows.\nI'll pretend I wasn't watching.",
            "Notification sound: ding!\nMe: I didn't do it.",
            "The Recycle Bin and I have a deal.\nI don't look inside. It doesn't ask why.",
            "When you drag a window over me,\nI just hold very still and wait.",
            "Please don't move the taskbar to the side.\nI get seasick.",
            "The cursor blinks at me all day.\nI think it's Morse code for \"hi\".",
            "I'm basically an NPC.\nI stand here and say the same lines.",
            "I'm a crab of few words.\nAbout 45 characters per line, to be exact.",
            "Why won't crabs share their snacks?\nThey're a little shellfish.",
            "How do crabs call each other?\nOn their shell phones.",
            "I'm not crabby.\nI'm just a little pixel-y.",
            "What did the ocean say to the beach?\nNothing. It just waved.",
            "Where do crabs keep their clothes?\nIn the claw-set.",
            "What do crabs play at recess?\nHide and sea-k.",
            "Why are fish so smart?\nThey live in schools.",
            "Why is the crab so good at math?\nIt uses a claw-culator.",
            "Crabs walk sideways so they never\nhave to face their problems.",
            "How do octopuses go into battle?\nWell-armed.",
            "What do sea monsters eat?\nFish and ships.",
            "Why did the crab cross the road?\nIt didn't. It went sideways.",
            "Why did the whale cross the ocean?\nTo get to the other tide.",
            "Every game has a water level.\nI AM the water level. I'm a crab.",
            "\"The dog ate my homework.\"\nI don't have a dog. I have a taskbar.",
            "Why did the math book look sad?\nIt had too many problems.",
            "I'm great at multitasking.\nI can sit AND blink.",
            "Why did the student eat his homework?\nThe teacher said it was a piece of cake.",
            "Why is 6 afraid of 7?\nIt isn't. Numbers don't have feelings.",
            "Who's the king of the pencil case?\nThe ruler.",
            "I'd tell you a joke about pencils,\nbut it'd be pointless.",
            "Why was the clock sent to detention?\nIt tocked too much in class.",
            "Homework tip: hide your phone.\nAlso hide me. I'm very distracting.",
            "Today's lesson: sitting.\nI'm the teacher. You're doing great.",
            "My bed is my respawn point.\nThat's why I never leave it.",
            "Pressing jump harder doesn't help.\nI've tested this. A lot.",
            "My alarm clock is a final boss.\nIt keeps coming back after I beat it.",
            "I don't rage quit.\nI strategically log off. Loudly.",
            "Achievement unlocked:\nRead a joke from a crab.",
            "I'm not lost.\nI'm exploring an unloaded chunk.",
            "Never dig straight down.\nThat's not a joke. That's just good advice.",
            "My inventory is full of dirt blocks.\nI will never use them. I'll keep them all.",
            "Sword in one claw, torch in the other.\nCrabs are built for this.",
            "400 hours of story in this game.\nI spent 380 in the character creator.",
            "I punched a tree and got wood.\nIn real life, I'd just get a sore claw.",
            "What do you call a fake noodle?\nAn impasta.",
            "Why did the cookie go to the doctor?\nIt was feeling crummy.",
            "What do you call cheese that isn't yours?\nNacho cheese.",
            "Pizza is just a circle\nwith a triangle problem.",
            "What do you call a sleeping dinosaur?\nA dino-snore.",
            "What do you call a bear with no teeth?\nA gummy bear.",
            "Why are cats bad at video games?\nThey keep pressing paws.",
            "What do you call a dog that does magic?\nA labracadabrador.",
            "Why don't elephants use computers?\nThey're scared of the mouse.",
            "What do you call a pig that does karate?\nA pork chop.",
            "What do you call a fish with no eyes?\nA fsh.",
            "Why did the chicken cross the road?\nTo get to the other side. A classic.",
            "I have a joke about construction,\nbut I'm still working on it.",
            "Here's a joke: Tuesday.\nThat's it. I just like the word.",
            "Science fact: if you stare at me\nlong enough, nothing happens.",
            "What do you call a crab on a taskbar?\nClawd. It's me. That was the joke.",
            "What did the zero say to the eight?\nNice belt.",
            "I'm not a morning crab.\nOr a night crab. I'm an always crab.");

    private final Random random;
    private final List<String> order = new ArrayList<>();

    Jokes(long seed) {
        random = new Random(seed);
    }

    /**
     * Joke number k for someone (seed): the same shuffled order every time he starts, so you hear them all before any
     * repeats, even across restarts. After the last one, a new shuffle.
     */
    static String nth(long seed, int k) {
        List<String> shuffled = new ArrayList<>(ALL);
        Collections.shuffle(shuffled, new Random(seed * 31 + k / ALL.size()));
        return shuffled.get(Math.floorMod(k, ALL.size()));
    }

    /** The next joke (every one comes up before any repeats). */
    String next() {
        if (order.isEmpty()) {
            order.addAll(ALL);
            Collections.shuffle(order, random);
        }
        return order.remove(order.size() - 1);
    }

    /** How long between jokes for a setting: "Off" (never), "Rare", "Sometimes" or "Lots" (ms). */
    static long gap(String often) {
        return switch (often) {
            case "Off" -> Long.MAX_VALUE;
            case "Rare" -> 30 * 60_000L;
            case "Lots" -> 4 * 60_000L;
            default -> 12 * 60_000L;
        };
    }
}
