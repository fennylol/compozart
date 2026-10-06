package critter.io;

import java.util.*;

/**
 * A small JSON reader and writer.
 * Values map to {@code Map<String, Object>} (insertion ordered), {@code List<Object>}, {@code String},
 * {@code Long} (integers), {@code Double}, {@code Boolean} and {@code null}.
 */
public final class Json {
    private Json() {
    }

    public static final class ParseException extends RuntimeException {
        public ParseException(String message, int pos) {
            super(message + " at offset " + pos);
        }
    }

    // ---- reading ----

    public static Object parse(String text) {
        Reader r = new Reader(text);
        r.ws();
        Object v = r.value();
        r.ws();
        if (r.pos != text.length()) throw new ParseException("trailing characters", r.pos);
        return v;
    }

    private static final class Reader {
        final String s;
        int pos;

        Reader(String s) {
            this.s = s;
        }

        void ws() {
            while (pos < s.length() && " \t\r\n".indexOf(s.charAt(pos)) >= 0) pos++;
        }

        char peek() {
            if (pos >= s.length()) throw new ParseException("unexpected end", pos);
            return s.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) throw new ParseException("expected '" + c + "'", pos);
            pos++;
        }

        Object value() {
            char c = peek();
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) yield number();
                    throw new ParseException("unexpected '" + c + "'", pos);
                }
            };
        }

        Object literal(String word, Object v) {
            if (!s.startsWith(word, pos)) throw new ParseException("expected " + word, pos);
            pos += word.length();
            return v;
        }

        Map<String, Object> object() {
            expect('{');
            Map<String, Object> m = new LinkedHashMap<>();
            ws();
            if (peek() == '}') {
                pos++;
                return m;
            }
            while (true) {
                ws();
                String k = string();
                ws();
                expect(':');
                ws();
                m.put(k, value());
                ws();
                if (peek() == ',') {
                    pos++;
                    continue;
                }
                expect('}');
                return m;
            }
        }

        List<Object> array() {
            expect('[');
            List<Object> l = new ArrayList<>();
            ws();
            if (peek() == ']') {
                pos++;
                return l;
            }
            while (true) {
                ws();
                l.add(value());
                ws();
                if (peek() == ',') {
                    pos++;
                    continue;
                }
                expect(']');
                return l;
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                pos++;
                if (c == '"') return sb.toString();
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = peek();
                pos++;
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw new ParseException("bad unicode escape", pos);
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new ParseException("bad escape '\\" + e + "'", pos);
                }
            }
        }

        Object number() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            boolean integral = true;
            if (pos < s.length() && s.charAt(pos) == '.') {
                integral = false;
                pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                integral = false;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            String n = s.substring(start, pos);
            try {
                return integral ? (Object) Long.parseLong(n) : (Object) Double.parseDouble(n);
            } catch (NumberFormatException ex) {
                throw new ParseException("bad number " + n, start);
            }
        }
    }

    // ---- writing ----

    /** Pretty-prints with two-space indents. Arrays of numbers and empty containers stay on one line. */
    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int indent) {
        if (v == null) sb.append("null");
        else if (v instanceof String s) quote(sb, s);
        else if (v instanceof Boolean || v instanceof Long || v instanceof Integer) sb.append(v);
        else if (v instanceof Number n) sb.append(n.doubleValue());
        else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            String inline = inline(m);
            if (inline != null) {
                sb.append(inline);
                return;
            }
            sb.append("{\n");
            int i = 0;
            for (var e : m.entrySet()) {
                pad(sb, indent + 1);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(": ");
                write(sb, e.getValue(), indent + 1);
                if (++i < m.size()) sb.append(',');
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append('}');
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                sb.append("[]");
                return;
            }
            if (l.stream().allMatch(x -> x instanceof Number)) {
                sb.append('[');
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) sb.append(", ");
                    write(sb, l.get(i), indent);
                }
                sb.append(']');
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < l.size(); i++) {
                pad(sb, indent + 1);
                write(sb, l.get(i), indent + 1);
                if (i + 1 < l.size()) sb.append(',');
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append(']');
        } else throw new IllegalArgumentException("not a JSON value: " + v.getClass());
    }

    /** Small objects of plain values fit on one line, which keeps palettes and references compact. */
    private static String inline(Map<?, ?> m) {
        if (m.size() > 4) return null;
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (var e : m.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map || v instanceof List) return null;
            if (i++ > 0) sb.append(", ");
            quote(sb, String.valueOf(e.getKey()));
            sb.append(": ");
            write(sb, v, 0);
        }
        sb.append('}');
        return sb.length() <= 72 ? sb.toString() : null;
    }

    private static void pad(StringBuilder sb, int indent) {
        sb.append("  ".repeat(indent));
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ---- typed access ----

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object v, String what) {
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new IllegalArgumentException(what + " should be an object");
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object v, String what) {
        if (v instanceof List<?> l) return (List<Object>) l;
        throw new IllegalArgumentException(what + " should be an array");
    }

    public static String str(Map<String, Object> m, String key, String fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        if (v instanceof String s) return s;
        throw new IllegalArgumentException(key + " should be a string");
    }

    public static long num(Map<String, Object> m, String key, long fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        if (v instanceof Long l) return l;
        if (v instanceof Double d && d == Math.rint(d)) return d.longValue();
        throw new IllegalArgumentException(key + " should be an integer");
    }

    public static boolean bool(Map<String, Object> m, String key, boolean fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        if (v instanceof Boolean b) return b;
        throw new IllegalArgumentException(key + " should be true or false");
    }
}
