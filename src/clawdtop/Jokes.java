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
            "If you need me,\nI'll be exactly here. Forever. Above the clock.");

    private final Random random;
    private final List<String> order = new ArrayList<>();

    Jokes(long seed) {
        random = new Random(seed);
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
