package clawdtop;

import java.util.List;

/**
 * Clawd Points and what they buy. He earns points as you spend time together (rides, pets, jobs, tips, just being
 * around), and you spend them on hats, t-shirts and new tricks.
 */
final class Shop {
    enum Kind { HAT, SHIRT, TRICK }

    /** Something to buy: its id (kept in settings), name, kind and price. */
    record Item(String id, String name, Kind kind, int price, String about) {
    }

    static final List<Item> ITEMS = List.of(
            new Item("party-hat", "Party hat", Kind.HAT, 15, "a pointy party hat"),
            new Item("top-hat", "Top hat", Kind.HAT, 40, "very fancy"),
            new Item("crown", "Crown", Kind.HAT, 100, "king of the taskbar"),
            new Item("bow", "Tiny bow", Kind.HAT, 10, "a little red bow"),
            new Item("beanie", "Beanie", Kind.HAT, 15, "cozy, with a pom-pom"),
            new Item("cap", "Baseball cap", Kind.HAT, 20, "worn backwards, obviously"),
            new Item("propeller", "Propeller cap", Kind.HAT, 25, "it spins!"),
            new Item("flowers", "Flower crown", Kind.HAT, 30, "very springtime"),
            new Item("chef", "Chef hat", Kind.HAT, 35, "for coding up something tasty"),
            new Item("cowboy", "Cowboy hat", Kind.HAT, 45, "yeehaw"),
            new Item("grad", "Graduation cap", Kind.HAT, 60, "Clawd, PhD"),
            new Item("wizard", "Wizard hat", Kind.HAT, 75, "with stars on"),
            new Item("viking", "Viking helmet", Kind.HAT, 90, "horns and all"),
            new Item("tee-red", "Red t-shirt", Kind.SHIRT, 15, "a classic"),
            new Item("tee-star", "Star t-shirt", Kind.SHIRT, 25, "blue, with a gold star"),
            new Item("stripes", "Striped shirt", Kind.SHIRT, 25, "very sailor"),
            new Item("heart-tee", "Heart t-shirt", Kind.SHIRT, 30, "he loves you"),
            new Item("hoodie", "Hoodie", Kind.SHIRT, 40, "for coding at night"),
            new Item("jersey", "Number 1 jersey", Kind.SHIRT, 45, "the best crab"),
            new Item("hawaiian", "Hawaiian shirt", Kind.SHIRT, 50, "vacation mode"),
            new Item("tuxedo", "Tiny tuxedo", Kind.SHIRT, 80, "with a bow tie"),
            new Item("juggling", "Juggling", Kind.TRICK, 30, "he juggles when he's bored"),
            new Item("dancing", "Dancing", Kind.TRICK, 50, "double-click him, then Fun > Dance!"),
            new Item("waving", "Waving", Kind.TRICK, 10, "he waves at you now and then"));

    /** Points for things you do together. */
    static final int RIDE = 3, PET = 1, HOLD_PET = 2, JOB = 10, TIP = 1, TIME = 1; // TIME: every 5 minutes you're both around
    static final int GAME = 4; // a game of tic-tac-toe
    static final int FOCUS = 20, LAP = 3, SONG = 2, MIDI = 5, ASK = 1, REMINDER = 1, MADE = 2; // focus timer finished, a lap, a song...
    static final int PETS_A_DAY = 30;

    private Shop() {
    }

    static Item find(String id) {
        for (Item i : ITEMS) if (i.id().equals(id)) return i;
        return null;
    }

    /** Buys something: false if it's already his or he hasn't the points. */
    static boolean buy(Settings settings, Item item) {
        if (settings.owns(item.id()) || settings.points() < item.price()) return false;
        settings.setPoints(settings.points() - item.price());
        settings.own(item.id());
        if (item.kind() == Kind.HAT) settings.setWearing("hat", item.id());
        if (item.kind() == Kind.SHIRT) settings.setWearing("shirt", item.id());
        return true;
    }
}
