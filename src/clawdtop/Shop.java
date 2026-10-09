package clawdtop;

import java.util.List;

/**
 * Clawd Points and what they buy. He earns points as you spend time together (rides, pets, jobs, tips, just being
 * around), and you spend them on hats, a hut to sit by, and new tricks.
 */
final class Shop {
    enum Kind { HAT, HUT, TRICK }

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
            new Item("cardboard-hut", "Cardboard hut", Kind.HUT, 20, "like his box, but cozier"),
            new Item("wooden-hut", "Wooden hut", Kind.HUT, 60, "with a little window"),
            new Item("castle", "Tiny castle", Kind.HUT, 150, "with a flag on top"),
            new Item("juggling", "Juggling", Kind.TRICK, 30, "he juggles when he's bored"),
            new Item("dancing", "Dancing", Kind.TRICK, 50, "click him and pick Dance!"),
            new Item("waving", "Waving", Kind.TRICK, 10, "he waves at you now and then"));

    /** Points for things you do together. */
    static final int RIDE = 3, PET = 1, HOLD_PET = 2, JOB = 10, TIP = 1, TIME = 1; // TIME: every 5 minutes you're both around
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
        if (item.kind() == Kind.HUT) settings.setWearing("hut", item.id());
        return true;
    }
}
