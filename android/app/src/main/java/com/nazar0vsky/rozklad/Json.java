package com.nazar0vsky.rozklad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mały parser JSON bez zależności od Androida (dzięki temu da się go testować na komputerze).
 * Obiekty to Map&lt;String,Object&gt;, tablice to List&lt;Object&gt;, liczby całkowite to Long, inne Double.
 */
public final class Json {

    public static final class ParseException extends Exception {
        public ParseException(String m) { super(m); }
    }

    private static final int MAX_DEPTH = 24;

    private final String s;
    private int p;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) throws ParseException {
        Json j = new Json(text);
        j.skip();
        Object v = j.value(0);
        j.skip();
        if (j.p != j.s.length()) throw new ParseException("dodatkowe znaki na końcu");
        return v;
    }

    private void skip() {
        while (p < s.length()) {
            char c = s.charAt(p);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '﻿') p++;
            else break;
        }
    }

    private Object value(int depth) throws ParseException {
        if (p >= s.length()) throw new ParseException("niedokończony JSON");
        char c = s.charAt(p);
        if (c == '{') return object(depth);
        if (c == '[') return array(depth);
        if (c == '"') return string();
        if (c == '-' || (c >= '0' && c <= '9')) return number();
        if (s.startsWith("true", p)) { p += 4; return Boolean.TRUE; }
        if (s.startsWith("false", p)) { p += 5; return Boolean.FALSE; }
        if (s.startsWith("null", p)) { p += 4; return null; }
        throw new ParseException("nieznana wartość");
    }

    private Map<String, Object> object(int depth) throws ParseException {
        if (depth >= MAX_DEPTH) throw new ParseException("zbyt głęboki JSON");
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        p++;
        skip();
        if (p < s.length() && s.charAt(p) == '}') { p++; return m; }
        for (;;) {
            skip();
            if (p >= s.length() || s.charAt(p) != '"') throw new ParseException("oczekiwano nazwy pola");
            String k = string();
            skip();
            if (p >= s.length() || s.charAt(p) != ':') throw new ParseException("oczekiwano :");
            p++;
            skip();
            m.put(k, value(depth + 1));
            skip();
            if (p >= s.length()) throw new ParseException("niedokończony JSON");
            char c = s.charAt(p);
            if (c == ',') { p++; continue; }
            if (c == '}') { p++; return m; }
            throw new ParseException("oczekiwano , lub koniec");
        }
    }

    private List<Object> array(int depth) throws ParseException {
        if (depth >= MAX_DEPTH) throw new ParseException("zbyt głęboki JSON");
        List<Object> a = new ArrayList<Object>();
        p++;
        skip();
        if (p < s.length() && s.charAt(p) == ']') { p++; return a; }
        for (;;) {
            skip();
            a.add(value(depth + 1));
            skip();
            if (p >= s.length()) throw new ParseException("niedokończony JSON");
            char c = s.charAt(p);
            if (c == ',') { p++; continue; }
            if (c == ']') { p++; return a; }
            throw new ParseException("oczekiwano , lub koniec");
        }
    }

    private String string() throws ParseException {
        p++;                                   // otwierający cudzysłów
        StringBuilder b = null;
        int start = p;
        while (p < s.length()) {
            char c = s.charAt(p);
            if (c == '"') {
                String r = b == null ? s.substring(start, p) : b.append(s, start, p).toString();
                p++;
                return r;
            }
            if (c < 0x20) throw new ParseException("niedozwolony znak w tekście");
            if (c == '\\') {
                if (b == null) b = new StringBuilder();
                b.append(s, start, p);
                if (p + 1 >= s.length()) break;
                char q = s.charAt(p + 1);
                p += 2;
                switch (q) {
                    case '"': b.append('"'); break;
                    case '\\': b.append('\\'); break;
                    case '/': b.append('/'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'u': {
                        if (p + 4 > s.length()) throw new ParseException("zły zapis \\u");
                        int v = 0;
                        for (int i = 0; i < 4; i++) {
                            int h = Character.digit(s.charAt(p + i), 16);
                            if (h < 0) throw new ParseException("zły zapis \\u");
                            v = v * 16 + h;
                        }
                        p += 4;
                        b.append((char) v);
                        break;
                    }
                    default: throw new ParseException("zły znak po \\");
                }
                start = p;
                continue;
            }
            p++;
        }
        throw new ParseException("niedokończony tekst");
    }

    private Object number() throws ParseException {
        int st = p;
        boolean isInt = true;
        if (s.charAt(p) == '-') p++;
        int digits = 0;
        while (p < s.length() && s.charAt(p) >= '0' && s.charAt(p) <= '9') { p++; digits++; }
        if (digits == 0) throw new ParseException("zła liczba");
        if (p < s.length() && s.charAt(p) == '.') {
            isInt = false;
            p++;
            int f = p;
            while (p < s.length() && s.charAt(p) >= '0' && s.charAt(p) <= '9') p++;
            if (p == f) throw new ParseException("zła liczba");
        }
        if (p < s.length() && (s.charAt(p) == 'e' || s.charAt(p) == 'E')) {
            isInt = false;
            p++;
            if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) p++;
            int f = p;
            while (p < s.length() && s.charAt(p) >= '0' && s.charAt(p) <= '9') p++;
            if (p == f) throw new ParseException("zła liczba");
        }
        String t = s.substring(st, p);
        try {
            if (isInt && digits <= 15) return Long.valueOf(Long.parseLong(t));
            return Double.valueOf(Double.parseDouble(t));
        } catch (NumberFormatException e) {
            throw new ParseException("zła liczba");
        }
    }

    /** Tekst jako literał JSON (znaki spoza ASCII zapisane jako \\uXXXX). */
    public static String quote(String v) {
        StringBuilder b = new StringBuilder(v.length() + 2);
        b.append('"');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20 || c >= 0x7f) {
                        String h = Integer.toHexString(c);
                        b.append("\\u");
                        for (int k = h.length(); k < 4; k++) b.append('0');
                        b.append(h);
                    } else {
                        b.append(c);
                    }
            }
        }
        b.append('"');
        return b.toString();
    }

    /** Zapisuje wartość (Map, List, String, Number, Boolean, null) jako JSON; znaki spoza ASCII jako \\uXXXX. */
    public static void write(Object o, StringBuilder b) {
        if (o == null) b.append("null");
        else if (o instanceof String) b.append(quote((String) o));
        else if (o instanceof Boolean || o instanceof Long || o instanceof Integer) b.append(o.toString());
        else if (o instanceof Number) b.append(o.toString());
        else if (o instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append(quote(String.valueOf(e.getKey()))).append(':');
                write(e.getValue(), b);
            }
            b.append('}');
        } else if (o instanceof List) {
            b.append('[');
            boolean first = true;
            for (Object x : (List<?>) o) {
                if (!first) b.append(',');
                first = false;
                write(x, b);
            }
            b.append(']');
        } else {
            b.append(quote(o.toString()));
        }
    }

    // ---- wygodne odczyty ----

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    public static String str(Object o) {
        if (o instanceof String) return (String) o;
        if (o instanceof Number) {
            Number n = (Number) o;
            return o instanceof Long ? Long.toString(n.longValue()) : n.toString();
        }
        return "";
    }

    public static int intOf(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try { return Integer.parseInt(((String) o).trim()); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
