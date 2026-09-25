package com.example.noteapp.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser/writer for the import/export and backup features.
 *
 * <p>Deliberately hand-written instead of pulling in Gson/Jackson: the app must
 * stay lightweight and fully offline, and the JSON we need is simple
 * (objects, arrays, strings, numbers, booleans, null).
 */
public final class JsonUtil {

    private JsonUtil() { }

    // ------------------------------------------------------------------ writing

    /** Writes a {@code Map<String, Object>} as compact JSON. */
    public static String write(Map<String, Object> map) {
        return write(map, false);
    }

    /** Writes a {@code Map<String, Object>} as JSON; when {@code pretty} the output is indented. */
    public static String write(Map<String, Object> map, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, map, pretty, 0);
        if (pretty) {
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value, boolean pretty, int depth) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            writeObject(sb, map, pretty, depth);
        } else if (value instanceof List<?> list) {
            writeArray(sb, list, pretty, depth);
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> map, boolean pretty, int depth) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append('{');
        String indent = pretty ? "\n" + "  ".repeat(depth + 1) : "";
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            sb.append(first ? indent : "," + indent);
            first = false;
            writeString(sb, String.valueOf(entry.getKey()));
            sb.append(pretty ? ": " : ":");
            writeValue(sb, entry.getValue(), pretty, depth + 1);
        }
        sb.append(pretty ? "\n" + "  ".repeat(depth) : "").append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> list, boolean pretty, int depth) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append('[');
        String indent = pretty ? "\n" + "  ".repeat(depth + 1) : "";
        boolean first = true;
        for (Object item : list) {
            sb.append(first ? indent : "," + indent);
            first = false;
            writeValue(sb, item, pretty, depth + 1);
        }
        sb.append(pretty ? "\n" + "  ".repeat(depth) : "").append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
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
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ parsing

    /** Parses a JSON document into {@code Map} / {@code List} / {@code String} / {@code Long} / {@code Double} / {@code Boolean} / null. */
    public static Object parse(String json) {
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Unexpected trailing content at position " + parser.pos);
        }
        return value;
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        boolean atEnd() { return pos >= text.length(); }

        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        char peek() {
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            return text.charAt(pos);
        }

        void expect(char c) {
            skipWhitespace();
            if (atEnd() || text.charAt(pos) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseKeyword("true", Boolean.TRUE);
                case 'f' -> parseKeyword("false", Boolean.FALSE);
                case 'n' -> parseKeyword("null", null);
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (!atEnd() && peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                expect(':');
                map.put(key, parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    return map;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
                }
            }
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (!atEnd() && peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    return list;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
                }
            }
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new IllegalArgumentException("Unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw new IllegalArgumentException("Unterminated escape sequence");
                    }
                    char escaped = text.charAt(pos++);
                    switch (escaped) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> sb.append(parseUnicodeEscape());
                        default -> throw new IllegalArgumentException("Invalid escape '\\" + escaped + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private String parseUnicodeEscape() {
            if (pos + 4 > text.length()) {
                throw new IllegalArgumentException("Invalid \\u escape");
            }
            String hex = text.substring(pos, pos + 4);
            pos += 4;
            return String.valueOf((char) Integer.parseInt(hex, 16));
        }

        Object parseKeyword(String keyword, Object value) {
            if (text.startsWith(keyword, pos)) {
                pos += keyword.length();
                return value;
            }
            throw new IllegalArgumentException("Invalid token at position " + pos);
        }

        Object parseNumber() {
            int start = pos;
            while (!atEnd() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("Invalid number at position " + pos);
            }
            try {
                return Long.parseLong(text.substring(start, pos));
            } catch (NumberFormatException longFailure) {
                try {
                    return Double.parseDouble(text.substring(start, pos));
                } catch (NumberFormatException notANumber) {
                    throw new IllegalArgumentException("Invalid number at position " + start);
                }
            }
        }
    }
}
