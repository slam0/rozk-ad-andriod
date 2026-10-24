package com.nazar0vsky.rozklad;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Składanie rozklad.json z małych plików ze strony (svc.json + pliki słupków).
 * Port rz_asm_* z wersji na iOS. Bez zależności od Androida.
 */
public final class Assembler {

    public static final class Err extends Exception {
        public Err(String m) { super(m); }
    }

    private final Map<String, Object> svcRoot;
    private final List<Object> svcList;
    private final int[] remap;
    private final int[] used;
    private int nused;
    private final File tmp;
    private Writer stops;
    private int nstops;

    /** svcText = zawartość svc.json; tmp = plik tymczasowy na przystanki (zostanie usunięty w close()). */
    public Assembler(String svcText, File tmp) throws Err {
        this.tmp = tmp;
        if (svcText == null || svcText.length() == 0 || svcText.length() > ImportReq.MAX_SVC_BYTES)
            throw new Err("Plik z kalendarzami ma złą wielkość.");
        Object root;
        try {
            root = Json.parse(svcText);
        } catch (Json.ParseException e) {
            throw new Err("Plik z kalendarzami jest uszkodzony (" + e.getMessage() + ").");
        }
        svcRoot = Json.obj(root);
        if (svcRoot == null || Json.arr(svcRoot.get("svc")) == null
            || !(svcRoot.get("gen") instanceof String) || !(svcRoot.get("end") instanceof String))
            throw new Err("Plik z kalendarzami ma zły format.");
        svcList = Json.arr(svcRoot.get("svc"));
        if (svcList.size() > 65535) throw new Err("Za dużo kalendarzy.");
        for (Object o : svcList) {
            Map<String, Object> c = Json.obj(o);
            if (c == null || Json.arr(c.get("d")) == null || Json.arr(c.get("d")).size() != 7
                || !(c.get("s") instanceof String) || !(c.get("e") instanceof String)
                || !strArray(c.get("a")) || !strArray(c.get("r")))
                throw new Err("Kalendarz w pliku ma zły format.");
            for (Object dd : Json.arr(c.get("d"))) {
                if (!(dd instanceof Long) || ((Long) dd).longValue() < 0 || ((Long) dd).longValue() > 1)
                    throw new Err("Kalendarz ma złe dni tygodnia.");
            }
        }
        remap = new int[svcList.size()];
        Arrays.fill(remap, -1);
        used = new int[svcList.size()];
        try {
            stops = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"), 65536);
        } catch (IOException e) {
            throw new Err("Brak miejsca na zapis pliku.");
        }
    }

    private static boolean strArray(Object o) {
        List<Object> a = Json.arr(o);
        if (a == null) return false;
        for (Object x : a) if (!(x instanceof String)) return false;
        return true;
    }

    /**
     * Dodaje plik jednego słupka z maską rodzajów. Nic się nie dzieje (bez błędu), gdy słupek
     * nie ma odjazdów tych rodzajów.
     */
    public void add(Reader pole, int mask) throws Err {
        String n = null, c = null;
        boolean haveG = false;
        List<String> gLine = new ArrayList<String>();
        List<String> gHead = new ArrayList<String>();
        List<Integer> gSvc = new ArrayList<Integer>();
        List<Character> gKind = new ArrayList<Character>();
        List<int[]> gMins = new ArrayList<int[]>();
        int total = 0;
        try {
            Scan s = new Scan(pole);
            s.ws();
            if (s.peek() != '{') throw new Err("Plik słupka ma zły format.");
            s.expect('{');
            if (!s.empty('}')) {
                do {
                    String key = s.string();
                    s.expect(':');
                    s.ws();
                    if (key.equals("n")) {
                        if (s.peek() != '"') throw new Err("Plik słupka ma zły format.");
                        n = s.string();
                    } else if (key.equals("c")) {
                        if (s.peek() != '"') throw new Err("Plik słupka ma zły format.");
                        c = s.string();
                    } else if (key.equals("g")) {
                        if (s.peek() != '[') throw new Err("Plik słupka ma zły format.");
                        haveG = true;
                        gLine.clear(); gHead.clear(); gSvc.clear(); gKind.clear(); gMins.clear();
                        total = 0;
                        s.expect('[');
                        if (!s.empty(']')) {
                            do {
                                total += readGroup(s, mask, gLine, gHead, gSvc, gKind, gMins);
                            } while (s.more(']'));
                        }
                    } else {
                        s.skip();
                    }
                } while (s.more('}'));
            }
            s.end();
        } catch (Scan.Bad e) {
            throw new Err("Plik słupka jest uszkodzony (" + e.getMessage() + ").");
        }
        if (n == null || c == null || !haveG) throw new Err("Plik słupka ma zły format.");
        if (total == 0) return;       // nic z tych rodzajów - słupek pominięty

        int ngr = gLine.size();
        long[] keys = new long[total];
        int nd = 0;
        for (int g = 0; g < ngr; g++) {
            for (int m : gMins.get(g)) keys[nd++] = ((long) m << 32) | g;
        }
        Arrays.sort(keys, 0, nd);

        // kalendarze użyte po raz pierwszy dostają kolejne numery
        for (int g = 0; g < ngr; g++) {
            int sv = gSvc.get(g);
            if (remap[sv] < 0) {
                remap[sv] = nused;
                used[nused++] = sv;
            }
        }

        try {
            StringBuilder b = new StringBuilder(1024);
            if (nstops > 0) b.append(',');
            b.append("{\"n\":").append(Json.quote(n)).append(",\"c\":").append(Json.quote(c)).append(",\"d\":[");
            stops.write(b.toString());
            b.setLength(0);
            // teksty linii i kierunków zapisane raz
            String[] ql = new String[ngr], qh = new String[ngr];
            for (int g = 0; g < ngr; g++) {
                ql[g] = Json.quote(gLine.get(g));
                qh[g] = Json.quote(gHead.get(g));
            }
            for (int i = 0; i < nd; i++) {
                int g = (int) (keys[i] & 0xffffffffL);
                int minute = (int) (keys[i] >> 32);
                if (i > 0) b.append(',');
                b.append('[').append(ql[g]).append(',').append(qh[g]).append(',')
                    .append(remap[gSvc.get(g)]).append(',').append(minute).append(",\"")
                    .append(gKind.get(g).charValue()).append("\"]");
                if (b.length() > 32768) { stops.write(b.toString()); b.setLength(0); }
            }
            b.append("]}");
            stops.write(b.toString());
            nstops++;
        } catch (IOException e) {
            throw new Err("Brak miejsca na zapis pliku.");
        }
    }

    /** Czyta jedną grupę [linia, kierunek, kalendarz, rodzaj, [minuty]]; zwraca liczbę odjazdów dodanych do wyniku. */
    private int readGroup(Scan s, int mask, List<String> gLine, List<String> gHead, List<Integer> gSvc,
                          List<Character> gKind, List<int[]> gMins) throws Scan.Bad, Err {
        final Err bad = new Err("Plik słupka ma zły format odjazdów.");
        s.ws();
        if (s.peek() != '[') throw bad;
        s.expect('[');
        s.ws();
        if (s.peek() != '"') throw bad;
        String line = s.string();
        s.expect(',');
        s.ws();
        if (s.peek() != '"') throw bad;
        String head = s.string();
        s.expect(',');
        s.ws();
        int pk = s.peek();
        if (pk != '-' && (pk < '0' || pk > '9')) throw bad;
        long sv = s.integer();
        if (sv < 0 || sv > 65534) throw bad;
        s.expect(',');
        s.ws();
        if (s.peek() != '"') throw bad;
        String kind = s.string();
        if (kind.length() != 1 || Core.kindBit(kind.charAt(0)) == 0) throw bad;
        s.expect(',');
        s.ws();
        if (s.peek() != '[') throw bad;
        s.expect('[');
        int[] mins = new int[16];
        int nm = 0;
        if (!s.empty(']')) {
            do {
                s.ws();
                int c0 = s.peek();
                if (c0 != '-' && (c0 < '0' || c0 > '9')) throw bad;
                long v = s.integer();
                if (v < 0 || v > 20000) throw bad;
                if (nm == mins.length) mins = Arrays.copyOf(mins, nm * 2);
                mins[nm++] = (int) v;
            } while (s.more(']'));
        }
        if (s.more(']')) throw bad;       // za dużo elementów w grupie
        if ((mask & Core.kindBit(kind.charAt(0))) == 0) return 0;
        if (sv >= svcList.size()) throw new Err("Plik słupka odwołuje się do nieistniejącego kalendarza.");
        gLine.add(line);
        gHead.add(head);
        gSvc.add((int) sv);
        gKind.add(Character.valueOf(kind.charAt(0)));
        gMins.add(Arrays.copyOf(mins, nm));
        return nm;
    }

    /** Wygodny wariant: tekst w pamięci. */
    public void add(String pole, int mask) throws Err {
        if (pole == null || pole.length() == 0 || pole.length() > ImportReq.MAX_POLE_BYTES)
            throw new Err("Plik słupka ma złą wielkość.");
        add(new java.io.StringReader(pole), mask);
    }

    /** Ile słupków z odjazdami dodano. */
    public int stops() { return nstops; }

    /** Zapisuje gotowy rozklad.json do pliku out. */
    public void finish(File out) throws Err {
        try {
            stops.close();
            stops = null;
            Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"), 65536);
            try {
                StringBuilder b = new StringBuilder();
                b.append("{\"gen\":").append(Json.quote((String) svcRoot.get("gen")));
                b.append(",\"end\":").append(Json.quote((String) svcRoot.get("end")));
                b.append(",\"stops\":[");
                w.write(b.toString());
                Reader r = new InputStreamReader(new FileInputStream(tmp), "UTF-8");
                try {
                    char[] cb = new char[32768];
                    int k;
                    while ((k = r.read(cb)) > 0) w.write(cb, 0, k);
                } finally {
                    r.close();
                }
                b.setLength(0);
                b.append("],\"svc\":[");
                for (int i = 0; i < nused; i++) {
                    if (i > 0) b.append(',');
                    Json.write(svcList.get(used[i]), b);
                }
                b.append("]}");
                w.write(b.toString());
            } finally {
                w.close();
            }
        } catch (IOException e) {
            throw new Err("Brak miejsca na zapis pliku.");
        }
    }

    public void close() {
        try { if (stops != null) stops.close(); } catch (IOException ignored) { }
        stops = null;
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
    }
}
