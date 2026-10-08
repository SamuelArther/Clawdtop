package clawdtop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Math questions: Clawd doesn't trust himself with them ("I wouldn't trust myself to answer right....."), so he opens
 * Calculator, tells you which buttons to press, and watches what it shows to see if you got it.
 */
final class MathHelp {
    private MathHelp() {
    }

    /** A sum: its numbers and operators (+ - * /), in order. */
    record Problem(List<Double> numbers, List<Character> ops) {
        /** The buttons to press on Calculator, like "1 2 × 7 =". */
        String buttons() {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < numbers.size(); i++) {
                if (i > 0) b.append(' ').append(symbol(ops.get(i - 1))).append(' ');
                String n = number(numbers.get(i));
                boolean negative = n.startsWith("-");
                b.append(String.join(" ", (negative ? n.substring(1) : n).split("")));
                if (negative) b.append(" +/-"); // Calculator's sign button comes after the number
            }
            return b.append(" =").toString();
        }

        /** What Calculator (standard mode, which works left to right) should show. */
        double leftToRight() {
            double v = numbers.get(0);
            for (int i = 0; i < ops.size(); i++) v = apply(v, ops.get(i), numbers.get(i + 1));
            return v;
        }

        /** The answer with times and divide first (scientific mode, and math class). */
        double properly() {
            List<Double> terms = new ArrayList<>(List.of(numbers.get(0)));
            List<Character> adds = new ArrayList<>();
            for (int i = 0; i < ops.size(); i++) {
                char op = ops.get(i);
                if (op == '*' || op == '/') {
                    int last = terms.size() - 1;
                    terms.set(last, apply(terms.get(last), op, numbers.get(i + 1)));
                } else {
                    adds.add(op);
                    terms.add(numbers.get(i + 1));
                }
            }
            double v = terms.get(0);
            for (int i = 0; i < adds.size(); i++) v = apply(v, adds.get(i), terms.get(i + 1));
            return v;
        }

        /** Whether what Calculator shows is right (either way of working it out). */
        boolean right(double shown) {
            return close(shown, leftToRight()) || close(shown, properly());
        }
    }

    private static double apply(double a, char op, double b) {
        return switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            default -> a / b;
        };
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= 1e-6 * Math.max(1, Math.abs(b));
    }

    private static String symbol(char op) {
        return switch (op) {
            case '+' -> "+";
            case '-' -> "-";
            case '*' -> "×";
            default -> "÷";
        };
    }

    private static String number(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    private static final Pattern SUM = Pattern.compile("(-?\\d+(?:\\.\\d+)?)((?:[+\\-*/]-?\\d+(?:\\.\\d+)?)+)");
    private static final Pattern STEP = Pattern.compile("([+\\-*/])(-?\\d+(?:\\.\\d+)?)");

    /** The sum in a question like "what's 12 times 7?" or "45+19*2", or null if it isn't one. */
    static Problem parse(String question) {
        String q = question.toLowerCase(Locale.ROOT).strip();
        q = q.replaceAll("[?!.]+$", "").replace(",", "");
        q = q.replaceAll("^(hey |clawd,? )?(what'?s|what is|whats|how much is|how much's|calculate|work out|solve|compute|tell me)\\s+", "");
        q = q.replace("multiplied by", "*").replace("divided by", "/").replace("plus", "+").replace("minus", "-")
                .replace("times", "*").replace("over", "/").replace("×", "*").replace("÷", "/").replace("−", "-");
        q = q.replaceAll("(\\d)\\s*x\\s*(\\d)", "$1*$2"); // 12 x 7
        q = q.replaceAll("\\s+", "").replaceAll("=$", "");
        Matcher m = SUM.matcher(q);
        if (!m.matches()) return null;
        List<Double> numbers = new ArrayList<>(List.of(Double.parseDouble(m.group(1))));
        List<Character> ops = new ArrayList<>();
        Matcher step = STEP.matcher(m.group(2));
        while (step.find()) {
            ops.add(step.group(1).charAt(0));
            numbers.add(Double.parseDouble(step.group(2)));
        }
        if (ops.size() > 6) return null; // too long to read out
        return new Problem(numbers, ops);
    }

    /** The number Calculator's display reads out ("Display is 1,234.5"), or NaN. */
    static double shown(String display) {
        Matcher m = Pattern.compile("(-?[\\d,]+(?:\\.\\d+)?)\\s*$").matcher(display.strip());
        if (!m.find()) return Double.NaN;
        try {
            return Double.parseDouble(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * A PowerShell script that watches Calculator (Windows' own accessibility API, read only) and prints
     * "DONE|expression|display" the first time you press = after it starts, "CLOSED" if Calculator closes, or
     * "TIMEOUT" after a couple of minutes.
     */
    static String watcherScript() {
        return String.join("\n",
                "Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes",
                "$A = [System.Windows.Automation.AutomationElement]",
                "$S = [System.Windows.Automation.TreeScope]",
                "$root = $A::RootElement",
                "$byName = New-Object System.Windows.Automation.PropertyCondition($A::NameProperty, 'Calculator')",
                "$expId = New-Object System.Windows.Automation.PropertyCondition($A::AutomationIdProperty, 'CalculatorExpression')",
                "$resId = New-Object System.Windows.Automation.PropertyCondition($A::AutomationIdProperty, 'CalculatorResults')",
                "$deadline = (Get-Date).AddMinutes(3)",
                "$first = $null",
                "$seen = $false",
                "while ((Get-Date) -lt $deadline) {",
                "  $win = $root.FindFirst($S::Children, $byName)",
                "  if ($win) {",
                "    $seen = $true",
                "    $exp = $win.FindFirst($S::Descendants, $expId)",
                "    $res = $win.FindFirst($S::Descendants, $resId)",
                "    if ($res) {",
                "      $e = if ($exp) { $exp.Current.Name } else { '' }",
                "      $r = $res.Current.Name",
                "      $now = $e + '|' + $r",
                "      if ($first -eq $null) { $first = $now }",
                "      elseif ($now -ne $first -and $e.TrimEnd().EndsWith('=')) { Write-Output ('DONE|' + $now); exit }",
                "    }",
                "  } elseif ($seen) { Write-Output 'CLOSED'; exit }",
                "  Start-Sleep -Milliseconds 600",
                "}",
                "Write-Output 'TIMEOUT'");
    }
}
