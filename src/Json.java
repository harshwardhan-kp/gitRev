/**
 * Tiny JSON helpers so the project needs no external libraries.
 * Only handles what we need: escaping a string and reading one string field.
 */
public class Json {

    public static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    /** Finds the first "field": "..." in the JSON and returns its (unescaped) value. */
    public static String readStringField(String json, String field) {
        int keyPos = json.indexOf("\"" + field + "\"");
        if (keyPos < 0) throw new IllegalArgumentException("Field not found: " + field);
        int start = json.indexOf('"', json.indexOf(':', keyPos) + 1) + 1;

        StringBuilder value = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') break;
            if (c == '\\') {
                char next = json.charAt(++i);
                switch (next) {
                    case 'n' -> value.append('\n');
                    case 't' -> value.append('\t');
                    case 'r' -> value.append('\r');
                    case 'u' -> {
                        value.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                    default -> value.append(next); // \" \\ \/
                }
            } else {
                value.append(c);
            }
        }
        return value.toString();
    }
}
