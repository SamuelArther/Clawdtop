package clawdtop;

/**
 * Checks his brain for real (GitHub Actions runs it on a fresh Windows computer): installs Ollama and a small model
 * the way he does, then asks him things. Downloads a lot: don't run it on someone's computer uninvited.
 */
public final class BrainCheck {
    public static void main(String[] args) {
        Brain brain = new Brain();
        String model = args.length > 0 ? args[0] : "qwen2.5:0.5b";
        long started = System.currentTimeMillis();
        boolean ok = BrainInstall.ensure(brain, model, note -> System.out.println("he says: " + note.replace("\n", " / ")));
        System.out.println("brain ready: " + ok + " (" + (System.currentTimeMillis() - started) / 1000 + " s)");
        if (!ok) System.exit(1);
        String[] questions = {"What is the capital of France?", "Why is the sky blue?", "Tell me a fun fact about crabs."};
        for (String q : questions) {
            long t = System.currentTimeMillis();
            String a = brain.ask(q, model, Pet.Personality.BOUNCY, false, "Sam");
            System.out.println("Q: " + q + "\nA: " + a + "  (" + (System.currentTimeMillis() - t) + " ms)");
            if (a == null) System.exit(1);
        }
        WebSearch.Found found = WebSearch.lookUp("how tall is mount everest");
        System.out.println("looked up: " + (found == null ? null : found.source()));
        System.out.println("A (with web): " + brain.ask("How tall is Mount Everest?", model, Pet.Personality.HELPFUL, true, "Sam", found));
    }
}
