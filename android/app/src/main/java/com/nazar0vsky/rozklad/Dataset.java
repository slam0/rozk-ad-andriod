package com.nazar0vsky.rozklad;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Jeden wczytany plik rozklad.json (kalendarze + przystanki z odjazdami). Bez zależności od Androida. */
public final class Dataset {

    /** Przystanek (słupek) z odjazdami zapisanymi w zwartych tablicach. */
    public static final class Stop {
        public String name = "";
        public String code = "";     // numer słupka, np. "01" (może być pusty)
        public String key = "";      // nazwa|numer: rozpoznaje przystanek między uruchomieniami
        public String title = "";    // do wyświetlania, np. "Centrum 01"
        public Dataset dataset;
        public int count;
        public int[] minute = new int[0];
        public char[] svc = new char[0];
        public char[] line = new char[0];
        public char[] head = new char[0];
        public byte[] kind = new byte[0];
        public int kindMask;
    }

    public String fileName = "";
    public String generated = "";
    public String end = "";
    public Core.Service[] svcs = new Core.Service[0];
    public final List<String> lines = new ArrayList<String>();
    public final List<String> heads = new ArrayList<String>();
    public final List<Stop> stops = new ArrayList<Stop>();

    private final Map<String, Integer> lineMap = new HashMap<String, Integer>();
    private final Map<String, Integer> headMap = new HashMap<String, Integer>();

    private static int intern(Map<String, Integer> map, List<String> table, String s) {
        Integer n = map.get(s);
        if (n != null) return n.intValue();
        if (table.size() >= 65535) return 0;
        table.add(s);
        map.put(s, Integer.valueOf(table.size() - 1));
        return table.size() - 1;
    }

    /** Wczytuje rozklad.json. Przy błędzie rzuca Scan.Bad z opisem. */
    public static Dataset load(Reader r, String fileName) throws Scan.Bad {
        Dataset ds = new Dataset();
        ds.fileName = fileName;
        Scan s = new Scan(r);
        s.expect('{');
        boolean haveStops = false, haveSvc = false;
        if (!s.empty('}')) {
            do {
                String key = s.string();
                s.expect(':');
                s.ws();
                if (key.equals("gen")) ds.generated = s.peek() == '"' ? s.string() : skipEmpty(s);
                else if (key.equals("end")) ds.end = s.peek() == '"' ? s.string() : skipEmpty(s);
                else if (key.equals("svc")) { ds.readSvc(s); haveSvc = true; }
                else if (key.equals("stops")) { ds.readStops(s); haveStops = true; }
                else s.skip();
            } while (s.more('}'));
        }
        s.end();
        if (!haveStops || !haveSvc) throw new Scan.Bad("to nie jest rozklad.json z generatora");
        return ds;
    }

    private static String skipEmpty(Scan s) throws Scan.Bad {
        s.skip();
        return "";
    }

    private void readSvc(Scan s) throws Scan.Bad {
        s.ws();
        if (s.peek() != '[') throw new Scan.Bad("zły format kalendarzy");
        s.expect('[');
        List<Core.Service> list = new ArrayList<Core.Service>();
        if (!s.empty(']')) {
            do {
                Core.Service sv = new Core.Service();
                s.ws();
                if (s.peek() != '{') { s.skip(); list.add(sv); continue; }
                s.expect('{');
                if (!s.empty('}')) {
                    do {
                        String k = s.string();
                        s.expect(':');
                        s.ws();
                        if (k.equals("d") && s.peek() == '[') {
                            s.expect('[');
                            int i = 0;
                            if (!s.empty(']')) {
                                do {
                                    s.ws();
                                    int c = s.peek();
                                    if (c == '-' || (c >= '0' && c <= '9')) {
                                        long v = s.integer();
                                        if (i < 7) sv.days[i] = v != 0;
                                    } else {
                                        s.skip();
                                    }
                                    i++;
                                } while (s.more(']'));
                            }
                        } else if (k.equals("s") && s.peek() == '"') {
                            sv.start = Core.parseDate(s.string());
                        } else if (k.equals("e") && s.peek() == '"') {
                            sv.end = Core.parseDate(s.string());
                        } else if (k.equals("a") && s.peek() == '[') {
                            sv.add = dates(s);
                        } else if (k.equals("r") && s.peek() == '[') {
                            sv.rem = dates(s);
                        } else {
                            s.skip();
                        }
                    } while (s.more('}'));
                }
                list.add(sv);
            } while (s.more(']'));
        }
        if (list.size() > 65535) list = list.subList(0, 65535);
        svcs = list.toArray(new Core.Service[0]);
    }

    private static int[] dates(Scan s) throws Scan.Bad {
        s.expect('[');
        int[] a = new int[8];
        int n = 0;
        if (!s.empty(']')) {
            do {
                s.ws();
                int d = 0;
                if (s.peek() == '"') d = Core.parseDate(s.string());
                else s.skip();
                if (n == a.length) a = Arrays.copyOf(a, n * 2);
                a[n++] = d;
            } while (s.more(']'));
        }
        return Arrays.copyOf(a, n);
    }

    private void readStops(Scan s) throws Scan.Bad {
        s.ws();
        if (s.peek() != '[') throw new Scan.Bad("zły format przystanków");
        s.expect('[');
        if (s.empty(']')) return;
        do {
            s.ws();
            if (s.peek() != '{') { s.skip(); continue; }
            Stop st = new Stop();
            st.dataset = this;
            s.expect('{');
            if (!s.empty('}')) {
                do {
                    String k = s.string();
                    s.expect(':');
                    s.ws();
                    if (k.equals("n") && s.peek() == '"') st.name = s.string();
                    else if (k.equals("c") && s.peek() == '"') st.code = s.string();
                    else if (k.equals("d") && s.peek() == '[') readDeps(s, st);
                    else s.skip();
                } while (s.more('}'));
            }
            st.key = st.name + "|" + st.code;
            st.title = st.code.length() > 0 ? st.name + " " + st.code : st.name;
            stops.add(st);
        } while (s.more(']'));
    }

    private void readDeps(Scan s, Stop st) throws Scan.Bad {
        s.expect('[');
        int cap = 16, n = 0, mask = 0;
        int[] minute = new int[cap];
        char[] svc = new char[cap], line = new char[cap], head = new char[cap];
        byte[] kind = new byte[cap];
        if (!s.empty(']')) {
            do {
                s.ws();
                if (s.peek() != '[') { s.skip(); continue; }
                s.expect('[');
                // [linia, kierunek, kalendarz, minuta, rodzaj]
                String ln = elemString(s);
                s.expect(',');
                String hd = elemString(s);
                s.expect(',');
                long sv = elemInt(s);
                s.expect(',');
                long mi = elemInt(s);
                s.expect(',');
                String ks = elemString(s);
                while (s.more(']')) s.skip();    // ewentualne dodatkowe elementy
                if (sv < 0 || sv > 65534) continue;
                char kc = ks.length() > 0 ? ks.charAt(0) : 'b';
                if (Core.kindBit(kc) == 0) kc = 'b';
                if (n == cap) {
                    cap *= 2;
                    minute = Arrays.copyOf(minute, cap);
                    svc = Arrays.copyOf(svc, cap);
                    line = Arrays.copyOf(line, cap);
                    head = Arrays.copyOf(head, cap);
                    kind = Arrays.copyOf(kind, cap);
                }
                minute[n] = (int) mi;
                svc[n] = (char) sv;
                line[n] = (char) intern(lineMap, lines, ln);
                head[n] = (char) intern(headMap, heads, hd);
                kind[n] = (byte) kc;
                mask |= Core.kindBit(kc);
                n++;
            } while (s.more(']'));
        }
        st.count = n;
        st.minute = Arrays.copyOf(minute, n);
        st.svc = Arrays.copyOf(svc, n);
        st.line = Arrays.copyOf(line, n);
        st.head = Arrays.copyOf(head, n);
        st.kind = Arrays.copyOf(kind, n);
        st.kindMask = mask;
    }

    private static String elemString(Scan s) throws Scan.Bad {
        s.ws();
        int c = s.peek();
        if (c == '"') return s.string();
        if (c == '-' || (c >= '0' && c <= '9')) return Long.toString(s.integer());
        s.skip();
        return "";
    }

    private static long elemInt(Scan s) throws Scan.Bad {
        s.ws();
        int c = s.peek();
        if (c == '-' || (c >= '0' && c <= '9')) return s.integer();
        if (c == '"') {
            try { return Long.parseLong(s.string().trim()); } catch (NumberFormatException e) { return 0; }
        }
        s.skip();
        return 0;
    }
}
