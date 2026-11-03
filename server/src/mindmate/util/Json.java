package mindmate.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer.
 *
 * Written by hand so the server needs no third-party library: the only jar on
 * the classpath is the MySQL driver. It covers the subset the Mindmate API
 * uses - objects, arrays, strings, numbers, booleans and null.
 */
public final class Json {

    private Json() { }

    // ----------------------------------------------------------- writing

    public static String escape(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /** Serialises Map, List, String, Number, Boolean and null. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeTo(sb, value);
        return sb.toString();
    }

    private static void writeTo(StringBuilder sb, Object v) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String)  { sb.append(escape((String) v)); return; }
        if (v instanceof Boolean) { sb.append(v.toString()); return; }
        if (v instanceof Number)  { sb.append(numberText((Number) v)); return; }

        if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(escape(String.valueOf(e.getKey()))).append(':');
                writeTo(sb, e.getValue());
            }
            sb.append('}');
            return;
        }

        if (v instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object o : (Iterable<?>) v) {
                if (!first) sb.append(',');
                first = false;
                writeTo(sb, o);
            }
            sb.append(']');
            return;
        }

        sb.append(escape(String.valueOf(v)));
    }

    private static String numberText(Number n) {
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) return "null";
        if (n instanceof Double || n instanceof Float) {
            if (d == Math.floor(d)) return String.valueOf((long) d);
            return String.valueOf(d);
        }
        return n.toString();
    }

    /** Convenience for the many small responses the API returns. */
    public static Map<String, Object> map() { return new LinkedHashMap<>(); }

    // ----------------------------------------------------------- reading

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object v = p.value();
        p.skipWhitespace();
        if (p.pos < p.src.length()) throw new IllegalArgumentException("Trailing characters in JSON");
        return v;
    }

    /** Parses and insists the top level is an object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new IllegalArgumentException("Expected a JSON object");
        return (Map<String, Object>) v;
    }

    // --------- typed getters, so handlers do not repeat the casting ------

    public static String str(Map<String, Object> o, String key, String fallback) {
        Object v = o.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    public static int intOf(Map<String, Object> o, String key, int fallback) {
        Object v = o.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt(((String) v).trim()); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    public static long longOf(Map<String, Object> o, String key, long fallback) {
        Object v = o.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            try { return Long.parseLong(((String) v).trim()); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    public static boolean boolOf(Map<String, Object> o, String key, boolean fallback) {
        Object v = o.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> o, String key) {
        Object v = o.get(key);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    // ------------------------------------------------------------ parser

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) { this.src = src == null ? "" : src; }

        void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        Object value() {
            skipWhitespace();
            if (pos >= src.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = src.charAt(pos);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': expect("true");  return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null");  return null;
                default:  return number();
            }
        }

        private void expect(String word) {
            if (!src.startsWith(word, pos)) {
                throw new IllegalArgumentException("Bad literal at " + pos);
            }
            pos += word.length();
        }

        private Map<String, Object> object() {
            Map<String, Object> out = new LinkedHashMap<>();
            pos++;                       // {
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == '}') { pos++; return out; }
            while (true) {
                skipWhitespace();
                String key = string();
                skipWhitespace();
                if (pos >= src.length() || src.charAt(pos) != ':') {
                    throw new IllegalArgumentException("Expected ':' at " + pos);
                }
                pos++;
                out.put(key, value());
                skipWhitespace();
                if (pos >= src.length()) throw new IllegalArgumentException("Unterminated object");
                char c = src.charAt(pos++);
                if (c == '}') return out;
                if (c != ',') throw new IllegalArgumentException("Expected ',' or '}' at " + (pos - 1));
            }
        }

        private List<Object> array() {
            List<Object> out = new ArrayList<>();
            pos++;                       // [
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ']') { pos++; return out; }
            while (true) {
                out.add(value());
                skipWhitespace();
                if (pos >= src.length()) throw new IllegalArgumentException("Unterminated array");
                char c = src.charAt(pos++);
                if (c == ']') return out;
                if (c != ',') throw new IllegalArgumentException("Expected ',' or ']' at " + (pos - 1));
            }
        }

        private String string() {
            if (pos >= src.length() || src.charAt(pos) != '"') {
                throw new IllegalArgumentException("Expected a string at " + pos);
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }

                if (pos >= src.length()) break;
                char e = src.charAt(pos++);
                switch (e) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > src.length()) throw new IllegalArgumentException("Bad \\u escape");
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default: throw new IllegalArgumentException("Bad escape \\" + e);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Number number() {
            int start = pos;
            if (pos < src.length() && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) pos++;
            boolean floating = false;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c >= '0' && c <= '9') { pos++; continue; }
                if (c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') { floating = true; pos++; continue; }
                break;
            }
            String text = src.substring(start, pos);
            if (text.isEmpty()) throw new IllegalArgumentException("Expected a number at " + start);
            if (floating) return Double.valueOf(text);
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ex) {
                return Double.valueOf(text);
            }
        }
    }
}
