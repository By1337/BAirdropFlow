package dev.by1337.baf.util.material;

import dev.by1337.baf.util.WildcardMatcher;
import dev.by1337.core.util.math.FastExpressionParser;
import dev.by1337.yaml.codec.DataResult;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.BitSet;

public class MaterialTester {
    public static final YamlDecoder<Tester> DECODER = YamlDecoder.STRINGS.flatMap(l -> {
        try {
            return DataResult.success(parse(String.join("|", l)));
        } catch (Exception e) {
            return DataResult.error(e.getMessage());
        }
    });

    public static Tester parse(String input) {
        ExpReader reader = new ExpReader(input);
        Tester d = parseOr(reader);
        if (reader.next() != '\0') {
            throw reader.expected("EOF");
        }
        return d;
    }

    public static Tester parseOr(ExpReader reader) {
        Tester tester = parseAnd(reader);
        while (true) {
            switch (reader.next()) {
                case ' ' -> {
                }
                case '|' -> tester = tester.or(parseAnd(reader));
                case '\0' -> {
                    return tester;
                }
                default -> {
                    reader.back();
                    return tester;
                }
            }
        }
    }

    public static Tester parseAnd(ExpReader reader) {
        Tester tester = parseTester(reader);
        while (true) {
            switch (reader.next()) {
                case ' ' -> {
                }
                case '&' -> tester = tester.and(parseTester(reader));
                case '\0' -> {
                    return tester;
                }
                default -> {
                    reader.back();
                    return tester;
                }
            }
        }
    }


    private static Tester parseTester(ExpReader reader) {
        char c = reader.next();
        while (c == ' ') {
            c = reader.next();
        }
        if (c == '\0') {
            throw reader.expected("material");
        }
        if (c == '!') {
            return parseTester(reader).not();
        }
        if (c == '(') {
            var value = parseOr(reader);
            if (reader.next() != ')') {
                throw reader.expected(')');
            }
            return value;
        }
        StringBuilder sb = new StringBuilder();
        while (c != '!' && c != '&' && c != '|' && c != '\0' && c != ')' && c != '(') {
            if (c != ' ')
                sb.append(c);
            c = reader.next();
        }
        reader.back();
        String word = sb.toString();
        if (word.equals("air")) return Material::isAir;
        if (word.equals("solid")) return Material::isSolid;
        if (word.contains("*") || word.contains("?")) return Tester.ofWildcard(word);
        Material m = Registry.MATERIAL.get(new NamespacedKey("minecraft", word));
        if (m == null) throw new IllegalStateException("Unknown material " + word);
        return m1 -> m1 == m;
    }

    public interface Tester {
        boolean test(Material m);

        default BitSet compile() {
            BitSet set = new BitSet();
            for (Material material : Registry.MATERIAL) {
                if (test(material)) {
                    set.set(material.ordinal());
                }
            }
            return set;
        }

        default Tester and(Tester o) {
            return m -> test(m) && o.test(m);
        }

        default Tester or(Tester o) {
            return m -> test(m) || o.test(m);
        }

        default Tester not() {
            return m -> !test(m);
        }

        static Tester ofWildcard(String pattern) {
            return m -> WildcardMatcher.match(pattern, m.getKey().value());
        }
    }

    public static final class ExpReader {
        private int ridx;
        private final String exp;
        private final int size;

        public ExpReader(String exp) {
            this.exp = exp;
            size = exp.length();
        }


        public char next() {
            if (ridx < size) {
                return exp.charAt(ridx++);
            }
            ridx++;
            return '\0';
        }

        public boolean hasNext() {
            return ridx < size;
        }

        public char last() {
            if (size == 0) return '\0';
            return exp.charAt(Math.max(0, Math.min(Math.max(0, ridx), size - 1)));
        }

        public void back() {
            ridx--;
        }

        public int ridx() {
            return ridx;
        }

        public void ridx(int ridx) {
            this.ridx = ridx;
        }

        private String context() {

            return "\n" + exp + "\n" +
                    " ".repeat(Math.max(0, ridx)) +
                    "^ ridx=" + ridx + ", length=" + exp.length() + "\n";
        }

        public FastExpressionParser.MathFormatException badNumber(NumberFormatException e) throws FastExpressionParser.MathFormatException {
            return new FastExpressionParser.MathFormatException("Bad number: " + e.getMessage() + "\n" + context());
        }

        public FastExpressionParser.MathFormatException expected(String s) throws FastExpressionParser.MathFormatException {
            return new FastExpressionParser.MathFormatException("Expected " + s + " but got '" + last() + "' at " + ridx + "\n" + context());
        }

        public FastExpressionParser.MathFormatException expected(char c) throws FastExpressionParser.MathFormatException {
            return new FastExpressionParser.MathFormatException("Expected '" + c + "' but got '" + last() + "' at " + ridx + "\n" + context());
        }

    }
}
