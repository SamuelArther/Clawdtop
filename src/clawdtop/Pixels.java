package clawdtop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Map;

/** Little pixel-art things Clawd can code up (Creation.Effect.ITEM): a cat, a cookie, a pet rock... */
final class Pixels {
    private static final Map<Character, Color> PALETTE = Map.ofEntries(
            Map.entry('k', new Color(30, 30, 34)), Map.entry('w', new Color(250, 250, 250)),
            Map.entry('r', new Color(230, 70, 70)), Map.entry('o', new Color(245, 150, 50)),
            Map.entry('y', new Color(250, 215, 70)), Map.entry('g', new Color(100, 195, 95)),
            Map.entry('G', new Color(50, 130, 60)), Map.entry('b', new Color(80, 140, 240)),
            Map.entry('c', new Color(130, 215, 255)), Map.entry('p', new Color(165, 105, 225)),
            Map.entry('P', new Color(255, 140, 185)), Map.entry('n', new Color(160, 105, 60)),
            Map.entry('N', new Color(105, 65, 38)), Map.entry('s', new Color(165, 170, 180)),
            Map.entry('S', new Color(105, 110, 122)), Map.entry('t', new Color(235, 195, 135)),
            Map.entry('l', new Color(255, 245, 190)));

    /** Each thing, 7 wide, a row per string ('.' is see-through). */
    static final Map<String, String[]> ITEMS = Map.ofEntries(
            Map.entry("heart", new String[] {".rr.rr.", "rrrrrrr", "rrrrrrr", ".rrrrr.", "..rrr..", "...r..."}),
            Map.entry("star", new String[] {"...y...", "..yyy..", "yyyyyyy", ".yyyyy.", ".yy.yy.", "y.....y"}),
            Map.entry("coin", new String[] {"..yyy..", ".yoooy.", "yoyyyoy", "yoyoyoy", "yoyyyoy", ".yoooy.", "..yyy.."}),
            Map.entry("cat", new String[] {"o.....o", "oo...oo", "ooooooo", "okoooko", "ooopooo", ".ooooo.", ".o...o."}),
            Map.entry("ghost", new String[] {"..www..", ".wwwww.", "wwkwkww", "wwwwwww", "wwwwwww", "wwwwwww", "w.w.w.w"}),
            Map.entry("tree", new String[] {"...g...", "..ggg..", ".ggGgg.", "ggggGgg", ".gGggg.", "...n...", "...n..."}),
            Map.entry("flower", new String[] {".P.P...", "PPyPP..", ".PPP...", "..g....", "..g.g..", "..gg...", "..g...."}),
            Map.entry("ball", new String[] {"..rrr..", ".rrwrr.", "rrrrrrr", "wwwwwww", "rrrrrrr", ".rrrrr.", "..rrr.."}),
            Map.entry("cookie", new String[] {"..ttt..", ".tNttt.", "ttttNtt", "tNttttt", "tttNttt", ".ttttN.", "..ttt.."}),
            Map.entry("gem", new String[] {".ccccc.", "cclcccc", "ccccccc", ".ccccc.", "..ccc..", "...c..."}),
            Map.entry("sun", new String[] {"y..y..y", ".yyyyy.", ".yyyyy.", "yyyyyyy", ".yyyyy.", ".yyyyy.", "y..y..y"}),
            Map.entry("moon", new String[] {"..lll..", ".ll....", "ll.....", "ll.....", "ll.....", ".ll....", "..lll.."}),
            Map.entry("robot", new String[] {"...r...", ".sssss.", ".scscs.", ".sssss.", "SsssssS", ".sssss.", ".S...S."}),
            Map.entry("fish", new String[] {".......", ".bbb..b", "bbkbbbb", "bbbbbbb", ".bbb..b", "......."}),
            Map.entry("crown", new String[] {"y..y..y", "yy.y.yy", "yyyyyyy", "yryyyry", "yyyyyyy"}),
            Map.entry("trophy", new String[] {"yyyyyyy", "y.yyy.y", ".yyyyy.", "..yyy..", "...y...", "..yyy..", ".NNNNN."}),
            Map.entry("cupcake", new String[] {"...r...", "..PPP..", ".PPPPP.", "PPPPPPP", ".ttttt.", ".tntnt.", "..ttt.."}),
            Map.entry("frog", new String[] {".w...w.", "gkg.gkg", "ggggggg", "gwwwwwg", "ggggggg", "g.g.g.g"}),
            Map.entry("balloon", new String[] {".rrr...", "rrwrr..", "rrrrr..", "rrrrr..", ".rrr...", "..r....", "...s...", "..s...."}),
            Map.entry("alien", new String[] {"g.....g", ".g...g.", ".ggggg.", "gkgggkg", "ggggggg", ".g.g.g.", "g.....g"}),
            Map.entry("bug", new String[] {".k...k.", "..k.k..", ".rrkrr.", "rkrkrkr", "rrrkrrr", ".rrkrr.", "k.....k"}),
            Map.entry("lightbulb", new String[] {"..yyy..", ".yylyy.", ".yyyyy.", ".yyyyy.", "..yyy..", "..sss..", "..SSS.."}),
            Map.entry("rock", new String[] {".......", "..sss..", ".sssss.", "sskssks", "sssssss", ".sSSSs."}),
            Map.entry("sock", new String[] {"..www..", "..rrr..", "..www..", "..rrr..", "..wwww.", ".wwwwww", ".wwwww."}),
            Map.entry("mushroom", new String[] {"..rrr..", ".rwrrr.", "rrrrwrr", "rwrrrrr", "..ttt..", "..ttt..", ".ttttt."}),
            Map.entry("cactus", new String[] {"...g...", "g..g...", "g..g..g", "gggg..g", "...gggg", "...g...", ".nnnnn."}),
            Map.entry("donut", new String[] {"..PPP..", ".PyPwP.", "PPt.tPP", "Pt...tP", "PPt.tPP", ".PPPPP.", "..ttt.."}),
            Map.entry("potato", new String[] {".......", "..ttt..", ".ttNtt.", "tttttNt", "tNttttt", ".ttttt.", "..ttt.."}),
            Map.entry("plant", new String[] {".g...g.", "..g.g..", "g..g..g", ".ggggg.", "..rrr..", "..rrr..", "...r..."}),
            Map.entry("bomb", new String[] {"....y..", "...n...", "..kkk..", ".kkkkk.", ".kwkkk.", ".kkkkk.", "..kkk.."}));

    private Pixels() {
    }

    /** Draws the thing called name with its top-left at (x, y) in units, each of its pixels size units. */
    static void draw(Graphics2D g, int unit, String name, double x, double y, double size) {
        String[] rows = ITEMS.get(name);
        if (rows == null) rows = ITEMS.get("star");
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                Color color = PALETTE.get(rows[r].charAt(c));
                if (color == null) continue;
                g.setColor(color);
                int x0 = (int) Math.round((x + c * size) * unit), y0 = (int) Math.round((y + r * size) * unit);
                int x1 = (int) Math.round((x + (c + 1) * size) * unit), y1 = (int) Math.round((y + (r + 1) * size) * unit);
                g.fillRect(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
            }
        }
    }
}
