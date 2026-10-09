package clawdtop;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Looking things up online, only if you said he could (asked at setup, off unless you turn it on): your question goes
 * to Wikipedia and DuckDuckGo's free instant answers (no account, no key), and he uses what comes back.
 */
final class WebSearch {
    private WebSearch() {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    /** What he found: a short bit of text and where it's from. */
    record Found(String text, String source) {
    }

    /** Looks the question up. What he found, or null (nothing found, or no internet). */
    static Found lookUp(String question) {
        String q = question.strip().replaceAll("[?!.]+$", "");
        // DuckDuckGo's instant answers first: quick facts, definitions, conversions
        String ddg = get("https://api.duckduckgo.com/?format=json&no_html=1&skip_disambig=1&q=" + enc(q));
        if (ddg != null) {
            String answer = value(ddg, "Answer");
            if (answer != null && !answer.isBlank() && !answer.startsWith("{")) return new Found(trim(answer), "DuckDuckGo");
            String abstractText = value(ddg, "AbstractText");
            if (abstractText != null && abstractText.length() > 40) return new Found(trim(abstractText), value(ddg, "AbstractSource"));
        }
        // then Wikipedia: the best matching article's summary
        // (searching for what it's about works better than the whole question: "how tall is Mount Everest" -> "Mount Everest")
        String about = q.toLowerCase(java.util.Locale.ROOT).replaceAll("^(hey |clawd,? )?(what|who|where|when|why|how|which)('s| is| are| was| were| did| do| does)?"
                + "( tall| old| big| far| long| fast| many| much| heavy| deep| hot| cold)?( is| are| was| were| did| do| does)?\\s+", "")
                .replaceAll("^(tell me about|explain|define|what does) ", "").strip();
        if (about.isBlank()) about = q;
        String search = get("https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=1&srsearch=" + enc(about));
        String title = search == null ? null : value(search, "title");
        if (title == null || title.isBlank()) return null;
        String summary = get("https://en.wikipedia.org/api/rest_v1/page/summary/" + enc(title.replace(' ', '_')).replace("+", "%20"));
        String extract = summary == null ? null : value(summary, "extract");
        return extract == null || extract.isBlank() ? null : new Found(trim(extract), "Wikipedia (" + title + ")");
    }

    /** Whether a question is about the weather. */
    static boolean aboutWeather(String question) {
        return question.toLowerCase(java.util.Locale.ROOT).matches(".*\\b(weather|temperature|is it (raining|cold|hot|sunny|snowing))\\b.*");
    }

    /** The weather where you are (from wttr.in, which goes by your internet address), like "Overcast, 76 F", or null. */
    static String weather() {
        String w = get("https://wttr.in/?format=%25C,+%25t+(feels+like+%25f)&u");
        if (w == null || w.isBlank() || w.length() > 120 || w.contains("<")) return null;
        return w.strip().replace(" ,", ",").replace("+", "").replace("°", " ").replaceAll("\\s+", " ");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String trim(String text) {
        String t = text.strip();
        if (t.length() <= 600) return t;
        int stop = t.lastIndexOf(". ", 600);
        return stop > 150 ? t.substring(0, stop + 1) : t.substring(0, 600) + "...";
    }

    private static String get(String url) {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "Clawdtop/1.0 (a desktop pet; https://github.com/SamuelArther/Clawdtop)").GET().build();
            HttpResponse<String> response = HTTP.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200 ? response.body() : null;
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            return null;
        }
    }

    /** The first string value for "key" in some JSON (no libraries: it's always a simple string we want), or null. */
    static String value(String json, String key) {
        int at = json.indexOf("\"" + key + "\"");
        while (at >= 0) {
            int colon = json.indexOf(':', at + key.length() + 2);
            int i = colon + 1;
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i < json.length() && json.charAt(i) == '"') return Brain.content("{\"message\":{\"content\":" + json.substring(i));
            at = json.indexOf("\"" + key + "\"", at + 1);
        }
        return null;
    }
}
