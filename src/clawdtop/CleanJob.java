package clawdtop;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Clawd cleaning a folder, step by step: he hops onto your cursor, you open the folder in File Explorer, he hops onto
 * that window, pulls out his laptop and looks through it. Then he asks before anything goes: once for the plain junk,
 * and one at a time for anything that might matter (for those, his hands peek over the window's top first, then he
 * climbs up to ask). Everything he cleans goes to the Recycle Bin. When he's done he hops down and walks home.
 */
final class CleanJob {
    /** What the job needs from the screen. Tests give it a pretend one. */
    interface Ui {
        Foreground.Front front();
        /** The folder an Explorer window shows, or null (may be slow: it's only called in the background). */
        Path folderOf(long handle);
        /** Where he should sit on this window (screen coordinates, the point between his feet), or null. */
        double[] spot(Foreground.Front window);
        void say(String text);
        void ask(String text, String[] buttons, IntConsumer answer);
        void hideBubble();
        /** Runs slow work away from the screen; then must run on the screen's thread again. */
        void background(Runnable work, Runnable then);
        int recycle(List<Cleaner.Item> items);
    }

    enum Step { BOARD, HOP_IN, WORK, ASK_SAFE, CLEANING, PEEK, CLIMB, ASK_RISKY, BETWEEN, DONE, LEAVING, OVER }

    static final long BOARD_TIMEOUT = 120_000;
    static final long MIN_WORK = 2500;    // he looks busy for at least this long, so you see him work
    static final long PEEK_TIME = 1200;
    static final long CLIMB_TIME = 700;
    static final long BETWEEN_TIME = 700;
    static final long DONE_TIME = 2600;

    private final Ui ui;
    private Step step = Step.BOARD;
    private long stepFor;
    private long ticks;
    private long askedHandle;          // the Explorer window whose folder is being looked up
    private Foreground.Front window;   // the window he's working on
    private Path folder;
    private volatile Cleaner.Plan plan;
    private volatile String problem;
    private int riskyIndex;
    private int moved;
    private long movedSize;
    private boolean waiting;           // a question is up, or background work is going

    CleanJob(Ui ui) {
        this.ui = ui;
    }

    void start(Body body) {
        body.board();
        ui.say("Hop on! Now open the folder you\nwant me to clean in File Explorer.");
    }

    Step step() {
        return step;
    }

    boolean over() {
        return step == Step.OVER;
    }

    /** How far he is still behind the window's top edge (in his own pixels) while he climbs up to ask. */
    double sunk() {
        if (step == Step.CLIMB) return 10 * (1 - Math.min(1, stepFor / (double) CLIMB_TIME));
        return 0;
    }

    /** Stops the job (you asked him to, or he was shaken off): he goes home. */
    void stop(Body body, Pet pet) {
        pet.job(null);
        ui.hideBubble();
        body.leave();
        go(Step.LEAVING);
    }

    void tick(long ms, Body body, Pet pet) {
        stepFor += ms;
        ticks++;
        switch (step) {
            case BOARD -> board(body, pet);
            case HOP_IN, WORK, ASK_SAFE, CLEANING, PEEK, CLIMB, ASK_RISKY, BETWEEN, DONE -> {
                stay(body);
                onWindow(body, pet);
            }
            case LEAVING -> { if (body.state() == Body.State.HOME) go(Step.OVER); }
            case OVER -> { }
        }
    }

    private void board(Body body, Pet pet) {
        if (!body.riding() && body.state() != Body.State.HOP_ON) { // shaken off, or never got on
            pet.job(null);
            ui.hideBubble();
            go(body.state() == Body.State.HOME ? Step.OVER : Step.LEAVING);
            return;
        }
        if (stepFor > BOARD_TIMEOUT) {
            ui.say("Never mind, maybe later!");
            body.leave();
            go(Step.LEAVING);
            return;
        }
        if (ticks % 15 != 0 || waiting) return;
        Foreground.Front front = ui.front();
        if (!front.windowClass().equals("CabinetWClass") || front.handle() == askedHandle) return;
        askedHandle = front.handle();
        waiting = true;
        Path[] found = new Path[1];
        ui.background(() -> found[0] = ui.folderOf(front.handle()), () -> {
            waiting = false;
            if (step != Step.BOARD) return;
            String why = Cleaner.refuse(found[0]);
            if (why != null) {
                ui.say(why);
                return;
            }
            folder = found[0];
            window = front;
            ui.hideBubble();
            go(Step.HOP_IN);
        });
    }

    /** Keeps him sitting on the window's top edge, even if it moves. */
    private void stay(Body body) {
        if (ticks % 15 == 0) {
            Foreground.Front front = ui.front();
            if (front.handle() == window.handle()) window = front; // moved or resized
        }
        double[] spot = ui.spot(window);
        if (spot != null) body.perchAt(spot[0], spot[1]);
    }

    private void onWindow(Body body, Pet pet) {
        switch (step) {
            case HOP_IN -> {
                if (body.state() != Body.State.PERCH) return;
                pet.job(Pet.Mood.WORK);
                ui.say("On it! Looking through\n" + folder.getFileName() + "...");
                waiting = true;
                ui.background(() -> {
                    try {
                        plan = Cleaner.scan(folder, Instant.now());
                    } catch (Exception e) {
                        problem = "I couldn't look through that folder.";
                    }
                }, () -> waiting = false);
                go(Step.WORK);
            }
            case WORK -> {
                if (waiting || stepFor < MIN_WORK) return;
                if (problem != null || plan == null) {
                    finish(body, pet, problem != null ? problem : "Something went wrong. I didn't touch anything.");
                    return;
                }
                if (plan.safe().isEmpty() && plan.risky().isEmpty()) {
                    finish(body, pet, "This folder's already clean!");
                    return;
                }
                if (plan.safe().isEmpty()) {
                    go(Step.BETWEEN);
                    return;
                }
                askSafe();
            }
            case CLEANING -> {
                if (!waiting) go(Step.BETWEEN);
            }
            case BETWEEN -> {
                pet.job(Pet.Mood.WORK);
                if (stepFor < BETWEEN_TIME) return;
                if (riskyIndex >= plan.risky().size()) {
                    finish(body, pet, null);
                } else {
                    pet.job(Pet.Mood.PEEK); // he ducks behind the window's top: just his hands show
                    go(Step.PEEK);
                }
            }
            case PEEK -> {
                if (stepFor < PEEK_TIME) return;
                pet.job(null); // and climbs up
                go(Step.CLIMB);
            }
            case CLIMB -> {
                if (stepFor < CLIMB_TIME) return;
                askRisky();
            }
            case DONE -> {
                if (stepFor < DONE_TIME) return;
                body.leave();
                go(Step.LEAVING);
            }
            default -> { }
        }
    }

    private void askSafe() {
        List<Cleaner.Item> safe = plan.safe();
        String text = "I found " + safe.size() + (safe.size() == 1 ? " bit" : " bits") + " of junk (" + Cleaner.size(plan.safeSize()) + "):\n"
                + summary(safe) + "\nMove " + (safe.size() == 1 ? "it" : "them") + " to the Recycle Bin?";
        go(Step.ASK_SAFE);
        ui.ask(text, new String[] {"Yes", "Show me", "No"}, choice -> {
            if (choice == 1) {
                showList();
                return;
            }
            if (choice == 0) clean(safe);
            else go(Step.BETWEEN);
        });
    }

    private void showList() {
        List<Cleaner.Item> safe = plan.safe();
        StringBuilder list = new StringBuilder("Here's what I'd move:\n");
        for (int i = 0; i < Math.min(8, safe.size()); i++) {
            Path p = safe.get(i).path();
            list.append(folder.relativize(p)).append('\n');
        }
        if (safe.size() > 8) list.append("...and ").append(safe.size() - 8).append(" more like those\n");
        list.append("Move them to the Recycle Bin?");
        ui.ask(list.toString(), new String[] {"Yes", "No"}, choice -> {
            if (choice == 0) clean(safe);
            else go(Step.BETWEEN);
        });
    }

    private void askRisky() {
        Cleaner.Item item = plan.risky().get(riskyIndex);
        go(Step.ASK_RISKY);
        ui.ask("Is it OK to delete " + item.path().getFileName() + "?\nIt's " + item.why() + " (" + Cleaner.size(item.size()) + ").",
                new String[] {"Yes", "No", "Stop"}, choice -> {
                    riskyIndex++;
                    if (choice == 0) clean(List.of(item));
                    else if (choice == 1) go(Step.BETWEEN);
                    else {
                        riskyIndex = plan.risky().size(); // stop asking
                        go(Step.BETWEEN);
                    }
                });
    }

    private void clean(List<Cleaner.Item> items) {
        go(Step.CLEANING);
        waiting = true;
        int[] count = new int[1];
        ui.background(() -> count[0] = ui.recycle(items), () -> {
            moved += count[0];
            for (Cleaner.Item item : items) movedSize += item.size();
            waiting = false;
        });
    }

    private void finish(Body body, Pet pet, String message) {
        pet.job(null);
        if (message == null) {
            message = moved == 0 ? "OK, I left everything where it was."
                    : "All done! I moved " + moved + (moved == 1 ? " thing" : " things") + " (" + Cleaner.size(movedSize)
                    + ")\nto the Recycle Bin. You can put them back from there.";
        }
        ui.say(message);
        go(Step.DONE);
    }

    /** "12 temporary files, 3 empty folders, ..." for the list he found. */
    static String summary(List<Cleaner.Item> items) {
        java.util.Map<String, Integer> kinds = new java.util.TreeMap<>(java.util.Comparator.comparingInt(k -> java.util.List.of(
                "temporary files", "unfinished downloads", "empty folders", "Mac leftovers", "picture-preview caches", "Office leftovers").indexOf(k)));
        for (Cleaner.Item item : items) {
            String kind = switch (item.why()) {
                case "a temporary file" -> "temporary files";
                case "an empty folder" -> "empty folders";
                case "a download that never finished" -> "unfinished downloads";
                case "a Mac leftover" -> "Mac leftovers";
                case "a leftover from a closed Office file" -> "Office leftovers";
                default -> "picture-preview caches";
            };
            kinds.merge(kind, 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        kinds.forEach((k, n) -> parts.add(n + " " + k));
        return String.join(", ", parts);
    }

    private boolean doneCounted;

    /** True once, the first time it's asked after the job finishes (to count the job's Clawd Points once). */
    boolean stepTimeIsNew() {
        if (step != Step.DONE || doneCounted) return false;
        doneCounted = true;
        return true;
    }

    private void go(Step next) {
        step = next;
        stepFor = 0;
    }
}
