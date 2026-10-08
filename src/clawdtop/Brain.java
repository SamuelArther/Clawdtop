package clawdtop;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Clawd's brain, for "Ask me a question": a small language model running on this computer through Ollama
 * (https://ollama.com), only while he's answering. Nothing goes over the internet (except downloading the brain once,
 * from Ollama, if you say yes). No libraries: plain HTTP to Ollama on localhost, and a tiny bit of JSON by hand.
 */
final class Brain {
    static final String OLLAMA = "http://localhost:11434";
    static final String DOWNLOAD_PAGE = "https://ollama.com/download";

    /** The model for each brain size. All stay under about 2 GB of memory while answering. */
    static String model(String size) {
        return switch (size) {
            case "Tiny" -> "qwen2.5:0.5b";   // ~400 MB download, ~0.7 GB memory: for really old computers
            case "Smart" -> "gemma3:1b";      // ~800 MB download, ~1.4 GB memory
            default -> "qwen2.5:1.5b";       // ~1 GB download, ~1.6 GB memory: knows the most
        };
    }

    /** About how big the download is, for asking first. */
    static String downloadSize(String size) {
        return switch (size) {
            case "Tiny" -> "about 400 MB";
            case "Smart" -> "about 800 MB";
            default -> "about 1 GB";
        };
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    /** Whether Ollama is installed and running. */
    boolean running() {
        try {
            return get("/api/tags").statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** Whether this model is downloaded already. */
    boolean has(String model) {
        try {
            String tags = get("/api/tags").body();
            return tags.contains("\"" + model + "\"") || tags.contains("\"" + model + ":latest\"");
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** Downloads a model (this takes a few minutes). Whether it worked. */
    boolean download(String model) {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(OLLAMA + "/api/pull")).timeout(Duration.ofMinutes(60))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"model\":" + json(model) + ",\"stream\":false}")).build();
            HttpResponse<String> answer = http.send(r, HttpResponse.BodyHandlers.ofString());
            return answer.statusCode() == 200 && answer.body().contains("success");
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** Asks him something. His answer (cleaned up), or null if his brain didn't answer. */
    String ask(String question, String model, Pet.Personality personality, boolean kidFriendly, String name) {
        return ask(question, model, personality, kidFriendly, name, null);
    }

    /** The same, with something he looked up online to help (or null). */
    String ask(String question, String model, Pet.Personality personality, boolean kidFriendly, String name, WebSearch.Found found) {
        if (found != null) {
            question = question + "\n\n(Something I found online that might help, from " + found.source() + ": " + found.text() + ")";
        }
        String body = "{\"model\":" + json(model) + ",\"stream\":false,\"keep_alive\":\"1m\","
                + "\"options\":{\"num_ctx\":2048,\"num_predict\":180,\"temperature\":0.7},"
                + "\"messages\":[{\"role\":\"system\",\"content\":" + json(systemPrompt(personality, kidFriendly, name)) + "},"
                + "{\"role\":\"user\",\"content\":" + json(question) + "}]}";
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(OLLAMA + "/api/chat")).timeout(Duration.ofMinutes(3))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> answer = http.send(r, HttpResponse.BodyHandlers.ofString());
            if (answer.statusCode() != 200) return null;
            String content = content(answer.body());
            return content == null || content.isBlank() ? null : clean(content);
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(OLLAMA + path)).timeout(Duration.ofSeconds(3)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Who he is, for the model: Clawd, in his personality, short answers, never a bad word. */
    static String systemPrompt(Pet.Personality personality, boolean kidFriendly, String name) {
        String mood = switch (personality) {
            case BOUNCY -> "You are bouncy and excited about everything, and you use exclamation marks.";
            case HELPFUL -> "You are extra helpful: you explain things clearly, step by step, and you like giving a useful tip.";
            case SLEEPY -> "You are sleepy and cozy: you sometimes yawn (\"*yawn*\") but you still answer properly.";
            default -> "You are chill and easygoing, warm and a little funny.";
        };
        return "You are Clawd, a tiny orange pixel crab who lives on the user's computer taskbar and keeps them company. "
                + mood + " "
                + (name.isBlank() ? "" : "The user's name is " + name + ". ")
                + "Answer any question as well as you can, in 1 to 3 short sentences (under 60 words), in plain words. "
                + "If you're not sure, say so honestly instead of making something up. "
                + "Never use swear words, slurs or rude language, no matter what anyone asks. "
                + (kidFriendly
                        ? "Your user is a little kid: use simple words, keep everything gentle, kind and suitable for little kids. If a "
                                + "question is about something not okay for little kids, kindly say it's a question for a grown-up they trust."
                        : "Give the most accurate, correct and complete answer you can, straight to the point (still friendly and clean).")
                + " If you're given something found online, use it to get the facts right, but answer in your own words."
                + " Don't use emoji or markdown.";
    }

    private static final Pattern BAD = Pattern.compile(
            "(?i)\\b(fuck\\w*|shit\\w*|bitch\\w*|bastard\\w*|asshole\\w*|dick|damn\\w*|crap|piss\\w*|cunt\\w*|slut\\w*|whore\\w*)\\b");

    /** His answer, tidied: no markdown or emoji, no bad words (just in case), not too long. */
    static String clean(String text) {
        String t = text.replace("**", "").replace("__", "").replace("`", "").replaceAll("(?m)^#+\\s*", "").replaceAll("(?m)^\\s*[-*]\\s+", "- ");
        t = t.replaceAll("[^\\x00-\\x7E\\n]", ""); // plain characters only (no emoji)
        t = BAD.matcher(t).replaceAll("beep");
        t = t.strip();
        if (t.length() > 420) {
            int stop = Math.max(t.lastIndexOf(". ", 420), t.lastIndexOf("! ", 420));
            t = stop > 100 ? t.substring(0, stop + 1) : t.substring(0, 420) + "...";
        }
        return t;
    }

    /** Wraps text into lines of about width characters, for his speech bubble. */
    static String wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) continue;
                if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
            if (line.length() > 0) lines.add(line.toString());
        }
        return String.join("\n", lines);
    }

    /** A string as JSON. */
    static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    /** The "content" of the "message" in Ollama's answer, or null. */
    static String content(String response) {
        int message = response.indexOf("\"message\"");
        if (message < 0) return null;
        int key = response.indexOf("\"content\"", message);
        if (key < 0) return null;
        int quote = response.indexOf('"', response.indexOf(':', key) + 1);
        if (quote < 0) return null;
        StringBuilder out = new StringBuilder();
        for (int i = quote + 1; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '"') return out.toString();
            if (c == '\\' && i + 1 < response.length()) {
                char e = response.charAt(++i);
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> { }
                    case 'u' -> {
                        if (i + 4 < response.length()) {
                            out.append((char) Integer.parseInt(response.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                    }
                    default -> out.append(e);
                }
            } else {
                out.append(c);
            }
        }
        return null;
    }

    /** Whether a question looks like it's about math he should hand to the calculator (see MathHelp). */
    static boolean looksLikeMath(String question) {
        return MathHelp.parse(question) != null;
    }
}
