package com.nazar0vsky.rozklad;

import java.util.ArrayList;
import java.util.List;

/**
 * Adres rozklad://import?u=&lt;strona&gt;&amp;v=&lt;wersja&gt;&amp;p=&lt;słupek&gt;.&lt;maska&gt;,... z przycisku
 * „Otwórz w aplikacji Rozkład” na stronie. Port rz_import_parse z wersji na iOS.
 */
public final class ImportReq {
    public static final int MAX_POLES = 400;
    public static final int MAX_SVC_BYTES = 24 * 1024 * 1024;
    public static final int MAX_POLE_BYTES = 6 * 1024 * 1024;

    public String base = "";      // adres strony, zawsze z "/" na końcu
    public String host = "";      // sama nazwa serwera
    public String version = "";   // wersja danych, same cyfry
    public final List<Integer> num = new ArrayList<Integer>();
    public final List<Integer> mask = new ArrayList<Integer>();   // bity rodzajów: b=1 t=2 p=4 m=8

    public int count() { return num.size(); }

    /** Błąd z komunikatem po polsku (do przetłumaczenia przez Lang.t). */
    public static final class Err extends Exception {
        public Err(String m) { super(m); }
    }

    private static int hexv(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    /** Odkodowuje %XX; adres musi być zwykłym ASCII. null = błąd. */
    private static String pdecode(String s, int from, int to) {
        StringBuilder o = new StringBuilder();
        for (int i = from; i < to; i++) {
            int c = s.charAt(i);
            if (c == '%') {
                if (i + 2 >= to) return null;
                int h = hexv(s.charAt(i + 1)), l = hexv(s.charAt(i + 2));
                if (h < 0 || l < 0) return null;
                c = h * 16 + l;
                i += 2;
            }
            if (c < 0x21 || c >= 0x7f) return null;
            if (o.length() + 1 >= 512) return null;
            o.append((char) c);
        }
        return o.toString();
    }

    public static ImportReq parse(String url) throws Err {
        ImportReq r = new ImportReq();
        if (url == null || !url.startsWith("rozklad://import") || (url.length() > 16 && url.charAt(16) != '?'))
            throw new Err("To nie jest adres z przycisku „Otwórz w aplikacji Rozkład”.");
        String base = "";
        boolean haveU = false, haveV = false, haveP = false;
        int q = url.length() > 16 ? 17 : 16;
        int n = url.length();
        while (q < n) {
            int amp = url.indexOf('&', q);
            int segEnd = amp < 0 ? n : amp;
            int eq = -1;
            for (int i = q; i < segEnd; i++) if (url.charAt(i) == '=') { eq = i; break; }
            if (eq > q) {
                int kl = eq - q;
                int vs = eq + 1;
                int vl = segEnd - vs;
                char key = url.charAt(q);
                if (kl == 1 && key == 'u') {
                    String d = pdecode(url, vs, segEnd);
                    if (d == null) throw new Err("Zły adres strony w linku.");
                    base = d;
                    haveU = true;
                } else if (kl == 1 && key == 'v') {
                    if (vl == 0 || vl >= 24) throw new Err("Zła wersja danych w linku.");
                    for (int i = vs; i < segEnd; i++) {
                        char c = url.charAt(i);
                        if (c < '0' || c > '9') throw new Err("Zła wersja danych w linku.");
                    }
                    r.version = url.substring(vs, segEnd);
                    haveV = true;
                } else if (kl == 1 && key == 'p') {
                    int i = vs;
                    while (i < segEnd) {
                        long num = 0, mk = 0;
                        int dn = 0, dm = 0;
                        while (i < segEnd && url.charAt(i) >= '0' && url.charAt(i) <= '9' && dn < 7) {
                            num = num * 10 + (url.charAt(i) - '0'); i++; dn++;
                        }
                        if (dn == 0 || i >= segEnd || url.charAt(i) != '.') throw new Err("Zła lista przystanków w linku.");
                        i++;
                        while (i < segEnd && url.charAt(i) >= '0' && url.charAt(i) <= '9' && dm < 3) {
                            mk = mk * 10 + (url.charAt(i) - '0'); i++; dm++;
                        }
                        if (dm == 0 || mk < 1 || mk > 15 || (i < segEnd && url.charAt(i) != ','))
                            throw new Err("Zła lista przystanków w linku.");
                        if (r.num.size() >= MAX_POLES) throw new Err("Za dużo przystanków w jednym linku.");
                        r.num.add((int) num);
                        r.mask.add((int) mk);
                        if (i < segEnd) {
                            i++;
                            if (i >= segEnd) throw new Err("Zła lista przystanków w linku.");
                        }
                    }
                    haveP = true;
                }
            }
            q = amp < 0 ? n : amp + 1;
        }
        if (!haveU || !haveV || !haveP || r.num.isEmpty())
            throw new Err("Link nie zawiera wszystkich danych. Wybierz przystanki na stronie jeszcze raz.");
        if (!base.startsWith("https://") && !base.startsWith("http://"))
            throw new Err("Adres strony musi zaczynać się od https:// lub http://.");
        if (!base.endsWith("/")) {
            if (base.length() + 2 > 512) throw new Err("Zbyt długi adres strony.");
            base = base + "/";
        }
        if (base.indexOf('?') >= 0 || base.indexOf('#') >= 0 || base.indexOf('"') >= 0 || base.indexOf('<') >= 0)
            throw new Err("Zły adres strony w linku.");
        r.base = base;
        int hs = base.startsWith("https://") ? 8 : 7;
        int slash = base.indexOf('/', hs);
        int hl = slash - hs;
        if (hl <= 0 || hl >= 128) throw new Err("Zły adres strony w linku.");
        r.host = base.substring(hs, slash);
        return r;
    }

    /** Adres pliku do pobrania: which = -1 -> svc.json, 0..count-1 -> plik słupka. null = zły numer. */
    public String fileUrl(int which) {
        if (which < -1 || which >= count()) return null;
        if (which < 0) return base + "d/" + version + "/svc.json";
        return base + "d/" + version + "/s/" + num.get(which) + ".json";
    }
}
