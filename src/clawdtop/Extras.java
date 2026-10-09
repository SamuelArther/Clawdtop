package clawdtop;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handy things you can ask him that work straight away, no brain and no internet: cleaning the tracking junk off a
 * link, making a strong password, the time somewhere else, counting words, choosing for you, and opening an app.
 */
final class Extras {
    private Extras() {
    }

    // ---- Cleaning links ----

    /** The bits of a link that only track you (where you came from, who shared it): safe to take off. */
    private static final Pattern TRACKING = Pattern.compile("(?i)^(utm_[a-z_]+|fbclid|gclid|gclsrc|dclid|msclkid|mc_eid|mc_cid|igsh|igshid|si|ref_src|ref_url"
            + "|_hsenc|_hsmi|yclid|twclid|ttclid|li_fat_id|wickedid|oly_enc_id|oly_anon_id|vero_id|spm|scid|s_cid|feature|pp)$");

    /** A link with the tracking taken off (and unwrapped, if it's a Google or Facebook "you're leaving" link), or null if it isn't a link. */
    static String cleanLink(String text) {
        if (text == null) return null;
        String link = text.strip();
        // (taken apart by hand, not by java.net.URI: real links often have characters URI won't accept, like "|")
        Matcher parts = Pattern.compile("(?i)(https?://([^/?#\\s:]+)(?::\\d+)?([^?#\\s]*))(\\?[^#\\s]*)?(#\\S*)?").matcher(link);
        if (!parts.matches()) return null;
        try {
            String host = parts.group(2).toLowerCase(Locale.ROOT), path = parts.group(3);
            String query = parts.group(4) == null ? null : parts.group(4).substring(1);
            // a redirect wrapper: the real link is inside
            if (query != null && (host.matches("(www\\.)?google\\.[a-z.]+") && path.equals("/url") || host.matches("l\\.(facebook|instagram)\\.com"))) {
                for (String pair : query.split("&")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2 && (kv[0].equals("q") || kv[0].equals("url") || kv[0].equals("u"))) {
                        String inner = cleanLink(URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                        if (inner != null) return inner;
                    }
                }
            }
            if (query == null) return link;
            List<String> kept = new ArrayList<>();
            for (String pair : query.split("&")) {
                String name = pair.split("=", 2)[0];
                // ("feature", "si" and "pp" are only tracking on YouTube and Spotify-style share links)
                boolean shareJunk = name.matches("(?i)feature|si|pp") && !host.matches(".*(youtube\\.com|youtu\\.be|spotify\\.com)$");
                if (pair.isEmpty() || (TRACKING.matcher(name).matches() && !shareJunk)) continue;
                kept.add(pair);
            }
            String fragment = parts.group(5) == null ? "" : parts.group(5);
            return parts.group(1) + (kept.isEmpty() ? "" : "?" + String.join("&", kept)) + fragment;
        } catch (RuntimeException notALink) {
            return null;
        }
    }

    // ---- Passwords ----

    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz", UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ", DIGITS = "23456789", SYMBOLS = "!@#$%&*?-+=";

    /** A strong password: 16 characters, with capitals, numbers and symbols, and none of the look-alikes (0/O, 1/l/I). */
    static String password(Random random) {
        String all = LOWER + UPPER + DIGITS + SYMBOLS;
        char[] p = new char[16];
        for (int i = 0; i < p.length; i++) p[i] = all.charAt(random.nextInt(all.length()));
        // at least one of each kind, at random places
        String[] kinds = {LOWER, UPPER, DIGITS, SYMBOLS};
        List<Integer> spots = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));
        java.util.Collections.shuffle(spots, random);
        for (int k = 0; k < kinds.length; k++) p[spots.get(k)] = kinds[k].charAt(random.nextInt(kinds[k].length()));
        return new String(p);
    }

    // ---- The time somewhere else ----

    private static final Map<String, String> CITIES = Map.ofEntries(
            Map.entry("tokyo", "Asia/Tokyo"), Map.entry("japan", "Asia/Tokyo"), Map.entry("london", "Europe/London"), Map.entry("england", "Europe/London"),
            Map.entry("uk", "Europe/London"), Map.entry("paris", "Europe/Paris"), Map.entry("france", "Europe/Paris"), Map.entry("berlin", "Europe/Berlin"),
            Map.entry("germany", "Europe/Berlin"), Map.entry("rome", "Europe/Rome"), Map.entry("italy", "Europe/Rome"), Map.entry("madrid", "Europe/Madrid"),
            Map.entry("spain", "Europe/Madrid"), Map.entry("moscow", "Europe/Moscow"), Map.entry("russia", "Europe/Moscow"), Map.entry("dubai", "Asia/Dubai"),
            Map.entry("india", "Asia/Kolkata"), Map.entry("delhi", "Asia/Kolkata"), Map.entry("mumbai", "Asia/Kolkata"), Map.entry("beijing", "Asia/Shanghai"),
            Map.entry("china", "Asia/Shanghai"), Map.entry("shanghai", "Asia/Shanghai"), Map.entry("hong kong", "Asia/Hong_Kong"), Map.entry("seoul", "Asia/Seoul"),
            Map.entry("korea", "Asia/Seoul"), Map.entry("singapore", "Asia/Singapore"), Map.entry("sydney", "Australia/Sydney"), Map.entry("australia", "Australia/Sydney"),
            Map.entry("melbourne", "Australia/Melbourne"), Map.entry("auckland", "Pacific/Auckland"), Map.entry("new zealand", "Pacific/Auckland"),
            Map.entry("new york", "America/New_York"), Map.entry("nyc", "America/New_York"), Map.entry("boston", "America/New_York"), Map.entry("miami", "America/New_York"),
            Map.entry("washington", "America/New_York"), Map.entry("dc", "America/New_York"), Map.entry("atlanta", "America/New_York"), Map.entry("chicago", "America/Chicago"),
            Map.entry("dallas", "America/Chicago"), Map.entry("houston", "America/Chicago"), Map.entry("austin", "America/Chicago"), Map.entry("texas", "America/Chicago"),
            Map.entry("denver", "America/Denver"), Map.entry("phoenix", "America/Phoenix"), Map.entry("arizona", "America/Phoenix"), Map.entry("los angeles", "America/Los_Angeles"),
            Map.entry("la", "America/Los_Angeles"), Map.entry("california", "America/Los_Angeles"), Map.entry("seattle", "America/Los_Angeles"),
            Map.entry("san francisco", "America/Los_Angeles"), Map.entry("las vegas", "America/Los_Angeles"), Map.entry("alaska", "America/Anchorage"),
            Map.entry("hawaii", "Pacific/Honolulu"), Map.entry("honolulu", "Pacific/Honolulu"), Map.entry("toronto", "America/Toronto"), Map.entry("canada", "America/Toronto"),
            Map.entry("vancouver", "America/Vancouver"), Map.entry("mexico", "America/Mexico_City"), Map.entry("mexico city", "America/Mexico_City"),
            Map.entry("brazil", "America/Sao_Paulo"), Map.entry("sao paulo", "America/Sao_Paulo"), Map.entry("rio", "America/Sao_Paulo"),
            Map.entry("buenos aires", "America/Argentina/Buenos_Aires"), Map.entry("argentina", "America/Argentina/Buenos_Aires"), Map.entry("cairo", "Africa/Cairo"),
            Map.entry("egypt", "Africa/Cairo"), Map.entry("nairobi", "Africa/Nairobi"), Map.entry("kenya", "Africa/Nairobi"), Map.entry("lagos", "Africa/Lagos"),
            Map.entry("johannesburg", "Africa/Johannesburg"), Map.entry("south africa", "Africa/Johannesburg"), Map.entry("israel", "Asia/Jerusalem"),
            Map.entry("jerusalem", "Asia/Jerusalem"), Map.entry("istanbul", "Europe/Istanbul"), Map.entry("turkey", "Europe/Istanbul"), Map.entry("ireland", "Europe/Dublin"),
            Map.entry("dublin", "Europe/Dublin"), Map.entry("amsterdam", "Europe/Amsterdam"), Map.entry("athens", "Europe/Athens"), Map.entry("greece", "Europe/Athens"),
            Map.entry("bangkok", "Asia/Bangkok"), Map.entry("thailand", "Asia/Bangkok"), Map.entry("manila", "Asia/Manila"), Map.entry("philippines", "Asia/Manila"),
            Map.entry("jakarta", "Asia/Jakarta"), Map.entry("vatican", "Europe/Rome"), Map.entry("hawaii time", "Pacific/Honolulu"));

    private static final Pattern WHAT_TIME_IN = Pattern.compile("^(?:what(?:'?s| is) the time|what time is it|what time's it|time) (?:right now |now )?in ([a-z .]+?)(?: right now| now)?$");

    /** "What time is it in Tokyo?": the time there (and how far ahead or behind you), or null if it isn't that question. */
    static String timeIn(String question, Instant now, ZoneId here) {
        Matcher m = WHAT_TIME_IN.matcher(question.toLowerCase(Locale.ROOT).strip().replaceAll("[?!]+$", ""));
        if (!m.matches()) return null;
        String place = m.group(1).replaceAll("\\.", "").replaceFirst("^the ", "").strip();
        String zone = CITIES.get(place);
        if (zone == null) return null; // (somewhere he doesn't know: his brain, or a lookup, might)
        ZonedDateTime there = now.atZone(ZoneId.of(zone)), mine = now.atZone(here);
        String day = there.toLocalDate().equals(mine.toLocalDate()) ? "" : " on " + there.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        long diff = (there.getOffset().getTotalSeconds() - mine.getOffset().getTotalSeconds()) / 60;
        String ahead = diff == 0 ? "the same as here" : Helpers.pretty(Math.abs(diff) / 60.0) + (Math.abs(diff) == 60 ? " hour " : " hours ") + (diff > 0 ? "ahead of you" : "behind you");
        String name = place.length() <= 3 ? place.toUpperCase(Locale.ROOT) : Character.toUpperCase(place.charAt(0)) + place.substring(1);
        return "In " + name + " it's " + there.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)) + day + ".\n(That's " + ahead + ".)";
    }

    // ---- Counting words ----

    /** How long some text is: words, characters, and about how long to read. */
    static String wordCount(String text) {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) return null;
        int words = t.split("\\s+").length, minutes = (int) Math.round(words / 230.0);
        return words + (words == 1 ? " word, " : " words, ") + t.length() + " characters"
                + (words < 120 ? "." : ".\nAbout " + Math.max(1, minutes) + (Math.max(1, minutes) == 1 ? " minute" : " minutes") + " to read.");
    }

    // ---- Choosing for you ----

    private static final Pattern CHOOSE = Pattern.compile("^(?:can you |please )?(?:choose|pick|decide)(?: for me)?(?: between)?:? (.+)$");
    private static final Pattern THIS_OR_THAT = Pattern.compile("^(?:should i (?:do |get |have |eat |play |watch )?)?([a-z0-9' -]{1,40}) or ([a-z0-9' -]{1,40})$");

    /** "Choose between pizza, tacos or burgers" / "pizza or tacos?": he picks one. Null if it isn't that. */
    static String choose(String question, Random random) {
        String q = question.toLowerCase(Locale.ROOT).strip().replaceAll("[?!.]+$", "");
        List<String> options = new ArrayList<>();
        Matcher m = CHOOSE.matcher(q);
        // (a real "choose": "choose between...", "pick for me...", "decide: ..." or a list with "or": not "pick up the kids")
        boolean clearly = m.matches() && (q.matches(".*\\b(between|for me|one)\\b.*") || q.contains(":") || m.group(1).contains(" or "));
        if (clearly && !m.group(1).startsWith("a number") && !m.group(1).startsWith("a random number")) {
            for (String o : m.group(1).split(",\\s*(?:or\\s+|and\\s+)?|\\s+or\\s+|\\s+and\\s+")) if (!o.isBlank()) options.add(o.strip());
        } else {
            Matcher t = THIS_OR_THAT.matcher(q);
            if (!t.matches() || q.matches("(heads or tails|yes or no|true or false)( .*)?") || q.matches(".* (yes or no|true or false)")) return null;
            // (just "this or that", not a question with an "or" in it: "is it cold or hot", "who's taller lebron or jordan")
            String a = t.group(1).strip(), b = t.group(2).strip();
            boolean asking = a.matches("(is|are|am|do|does|did|can|could|what|what's|whats|how|how's|why|who|who's|whos|where|where's|when|will|would|was|were|which|have|has|should|put|add|tell|explain|give) .*");
            if (!q.startsWith("should i ") && (a.split(" ").length > 3 || asking)) return null;
            if (b.split(" ").length > 3) return null; // ("pizza or tacos for dinner tonight with friends": a sentence)
            options.add(a);
            options.add(b);
        }
        if (options.size() < 2) return null;
        String pick = options.get(random.nextInt(options.size()));
        String[] lines = {"Hmm... %s!", "Easy. %s.", "*thinks hard* ...%s!", "%s, for sure.", "I'd go with %s."};
        String line = String.format(lines[random.nextInt(lines.length)], pick);
        return Character.toUpperCase(line.charAt(0)) + line.substring(1);
    }

    // ---- Your to-do list ----

    /**
     * The items on your list a phrase is about ("the dishes" -> "do the dishes"): the words you said, in that order, as
     * whole words in it. Just the one if it's said exactly.
     */
    static List<String> todosMatching(String phrase, List<String> todos) {
        String want = phrase.toLowerCase(Locale.ROOT).replaceAll("^(the|my|a|an|all|all the|all my) ", "").replaceAll("[.!]+$", "").strip();
        if (want.length() < 3 || want.matches("(it|that|this|them|those|everything|all|stuff|something|nothing|good|great|well|my best)")) return List.of();
        for (String t : todos) if (t.toLowerCase(Locale.ROOT).equals(want)) return List.of(t);
        String[] said = want.split("\\s+");
        List<String> found = new ArrayList<>();
        for (String t : todos) {
            String[] words = t.toLowerCase(Locale.ROOT).split("[^a-z0-9']+");
            int at = 0;
            for (String w : words) if (at < said.length && w.equals(said[at])) at++; // (in order: "grandma call" isn't "call grandma")
            if (at == said.length) found.add(t);
        }
        return found;
    }

    // ---- Opening apps ----

    /** "Open the calculator": what to open (for Useful.open), by app, for this computer. Null if he doesn't know it. */
    static String appFor(String question) {
        String q = question.toLowerCase(Locale.ROOT).strip().replaceAll("[?!.]+$", "");
        Matcher m = Pattern.compile("^(?:please |can you )?(?:open|start|launch|run)(?: up| me)?(?: the| my)? (.+?)(?: app| program| please)?$").matcher(q);
        if (!m.matches()) return null;
        String app = m.group(1);
        boolean win = Platform.WINDOWS, mac = Platform.MAC;
        return switch (app) {
            case "calculator", "calc" -> win ? "calc" : "calc";
            case "notepad", "text editor", "notes" -> win ? "notepad" : mac ? "app:TextEdit" : "cmd:gedit";
            case "paint", "drawing" -> win ? "mspaint" : mac ? "app:Preview" : null;
            case "settings", "system settings", "control panel" -> win ? "ms-settings:" : mac ? "app:System Settings" : "cmd:gnome-control-center";
            case "task manager", "activity monitor", "system monitor" -> win ? "taskmgr" : mac ? "app:Activity Monitor" : "cmd:gnome-system-monitor";
            case "file explorer", "explorer", "files", "finder", "file manager" -> win ? "explorer" : mac ? "app:Finder" : "cmd:nautilus";
            case "downloads", "downloads folder" -> "folder:Downloads";
            case "documents", "documents folder" -> "folder:Documents";
            case "desktop", "desktop folder" -> "folder:Desktop";
            case "pictures", "photos folder", "pictures folder" -> "folder:Pictures";
            case "music", "music folder" -> "folder:Music";
            case "camera" -> win ? "microsoft.windows.camera:" : mac ? "app:Photo Booth" : "cmd:cheese";
            case "clock", "alarms" -> win ? "ms-clock:" : mac ? "app:Clock" : null;
            case "terminal", "command prompt", "cmd", "powershell" -> win ? "cmd.exe" : mac ? "app:Terminal" : "cmd:gnome-terminal";
            case "browser", "web browser", "internet" -> win ? "https://www.google.com" : "url:https://www.google.com";
            case "store", "app store", "microsoft store" -> win ? "ms-windows-store:" : mac ? "app:App Store" : null;
            case "calendar" -> win ? "outlookcal:" : mac ? "app:Calendar" : null;
            case "mail", "email" -> win ? "mailto:" : mac ? "app:Mail" : null;
            default -> null;
        };
    }
}
