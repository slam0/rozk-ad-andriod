package com.nazar0vsky.rozklad;

import java.io.IOException;
import java.io.Reader;

/**
 * Strumieniowy czytnik JSON do dużych plików (nie buduje drzewa w pamięci).
 * Używany do wczytywania rozklad.json i plików słupków. Bez zależności od Androida.
 */
public final class Scan {

    public static final class Bad extends Exception {
        public Bad(String m) { super(m); }
    }

    private static final int MAX_DEPTH = 24;

    private final Reader in;
    private final char[] buf = new char[16384];
    private int pos, lim;
    private boolean eof;

    public Scan(Reader in) { this.in = in; }

    public static Scan of(String s) { return new Scan(new java.io.StringReader(s)); }

    // ---- znaki ----

    private boolean fill() throws Bad {
        if (eof) return false;
        try {
            int n = in.read(buf, 0, buf.length);
            if (n <= 0) { eof = true; return false; }
            pos = 0;
            lim = n;
            return true;
        } catch (IOException e) {
            throw new Bad("błąd odczytu pliku");
        }
    }

    /** Zwraca następny znak lub -1 na końcu, nie zjadając go. */
    public int peek() throws Bad {
        if (pos >= lim && !fill()) return -1;
        return buf[pos];
    }

    private int take() throws Bad {
        if (pos >= lim && !fill()) return -1;
        return buf[pos++];
    }

    public void ws() throws Bad {
        for (;;) {
            int c = peek();
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == 0xFEFF) pos++;
            else return;
        }
    }

    /** Kończy czytanie: poza białymi znakami nic już nie może być. */
    public void end() throws Bad {
        ws();
        if (peek() != -1) throw new Bad("dodatkowe znaki na końcu");
    }

    public void expect(char ch) throws Bad {
        ws();
        int c = take();
        if (c != ch) throw new Bad(c < 0 ? "niedokończony JSON" : "oczekiwano " + ch);
    }

    /** Czy następny znak to ch (po białych znakach); jeśli tak, zjada go. */
    public boolean accept(char ch) throws Bad {
        ws();
        if (peek() == ch) { pos++; return true; }
        return false;
    }

    /** W tablicy/obiekcie: po wartości przecinek (true = jest następny element) albo zamknięcie. */
    public boolean more(char close) throws Bad {
        ws();
        int c = take();
        if (c == ',') return true;
        if (c == close) return false;
        throw new Bad(c < 0 ? "niedokończony JSON" : "oczekiwano , lub koniec");
    }

    /** Po '[' lub '{': czy kontener jest pusty (zjada zamknięcie). */
    public boolean empty(char close) throws Bad {
        ws();
        if (peek() == close) { pos++; return true; }
        return false;
    }

    // ---- wartości ----

    public String string() throws Bad {
        ws();
        if (take() != '"') throw new Bad("oczekiwano tekstu");
        StringBuilder b = new StringBuilder();
        for (;;) {
            int c = take();
            if (c < 0) throw new Bad("niedokończony tekst");
            if (c == '"') return b.toString();
            if (c < 0x20) throw new Bad("niedozwolony znak w tekście");
            if (c == '\\') {
                int q = take();
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
                        int v = 0;
                        for (int k = 0; k < 4; k++) {
                            int h = take();
                            int d = Character.digit(h < 0 ? 'x' : (char) h, 16);
                            if (d < 0) throw new Bad("zły zapis \\u");
                            v = v * 16 + d;
                        }
                        b.append((char) v);
                        break;
                    }
                    default: throw new Bad("zły znak po \\");
                }
            } else {
                b.append((char) c);
            }
        }
    }

    /** Liczba całkowita; ułamki i wykładniki są błędem. */
    public long integer() throws Bad {
        ws();
        boolean neg = false;
        int c = peek();
        if (c == '-') { neg = true; pos++; }
        long v = 0;
        int digits = 0;
        for (;;) {
            c = peek();
            if (c < '0' || c > '9') break;
            if (digits >= 15) throw new Bad("zła liczba");
            v = v * 10 + (c - '0');
            digits++;
            pos++;
        }
        if (digits == 0) throw new Bad("zła liczba");
        c = peek();
        if (c == '.' || c == 'e' || c == 'E') throw new Bad("oczekiwano liczby całkowitej");
        return neg ? -v : v;
    }

    /** Pomija dowolną wartość (sprawdzając, czy jest poprawna). */
    public void skip() throws Bad { skip(0); }

    private void skip(int depth) throws Bad {
        ws();
        int c = peek();
        if (c < 0) throw new Bad("niedokończony JSON");
        if (c == '"') { string(); return; }
        if (c == '{' || c == '[') {
            if (depth >= MAX_DEPTH) throw new Bad("zbyt głęboki JSON");
            boolean obj = c == '{';
            char close = obj ? '}' : ']';
            pos++;
            if (empty(close)) return;
            do {
                if (obj) { string(); expect(':'); }
                skip(depth + 1);
            } while (more(close));
            return;
        }
        if (c == '-' || (c >= '0' && c <= '9')) { skipNumber(); return; }
        if (lit("true") || lit("false") || lit("null")) return;
        throw new Bad("nieznana wartość");
    }

    private void skipNumber() throws Bad {
        if (peek() == '-') pos++;
        int d = 0;
        while (peek() >= '0' && peek() <= '9') { pos++; d++; }
        if (d == 0) throw new Bad("zła liczba");
        if (peek() == '.') {
            pos++;
            int f = 0;
            while (peek() >= '0' && peek() <= '9') { pos++; f++; }
            if (f == 0) throw new Bad("zła liczba");
        }
        if (peek() == 'e' || peek() == 'E') {
            pos++;
            if (peek() == '+' || peek() == '-') pos++;
            int f = 0;
            while (peek() >= '0' && peek() <= '9') { pos++; f++; }
            if (f == 0) throw new Bad("zła liczba");
        }
    }

    private boolean lit(String w) throws Bad {
        for (int i = 0; i < w.length(); i++) {
            if (peek() != w.charAt(i)) {
                if (i == 0) return false;
                throw new Bad("nieznana wartość");
            }
            pos++;
        }
        return true;
    }
}
