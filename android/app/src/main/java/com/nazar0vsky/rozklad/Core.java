package com.nazar0vsky.rozklad;

import java.util.ArrayList;
import java.util.List;

/**
 * Czysta logika rozkładu (port rz_core.c z wersji na iOS). Bez zależności od Androida.
 */
public final class Core {
    private Core() {}

    public static final int MAX_NEXT = 32;

    /** Kalendarz kursowania (z calendar.txt i calendar_dates.txt). */
    public static final class Service {
        public int start, end;                         // yyyymmdd, 0 gdy nieznane
        public final boolean[] days = new boolean[7];  // poniedziałek..niedziela
        public int[] add = new int[0];                 // dodatkowe dni kursowania
        public int[] rem = new int[0];                 // dni, w których nie jeździ
    }

    /** Jeden z najbliższych odjazdów. */
    public static final class Next {
        public int time;   // minuty od północy DZISIAJ
        public int line;
        public int head;
    }

    private static boolean has(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return true;
        return false;
    }

    /** weekday: 0 = poniedziałek ... 6 = niedziela */
    public static boolean serviceRuns(Service s, int ymd, int weekday) {
        if (has(s.rem, ymd)) return false;
        if (has(s.add, ymd)) return true;
        if (weekday < 0 || weekday > 6 || !s.days[weekday]) return false;
        return s.start <= ymd && ymd <= s.end;
    }

    /**
     * Znajduje najbliższe odjazdy rodzaju kind. ymd[0..2] i weekday[0..2] opisują wczoraj, dziś i jutro.
     * now = minuty od północy dzisiaj, horizon = ile minut do przodu szukać.
     */
    public static List<Next> next(int n, int[] minute, char[] svc, char[] line, char[] head, byte[] kinds,
                                  Service[] svcs, int[] ymd, int[] weekday,
                                  int now, int horizon, char kind, int limit) {
        List<Next> res = new ArrayList<Next>();
        if (limit > MAX_NEXT) limit = MAX_NEXT;
        int nsvc = svcs == null ? 0 : svcs.length;
        if (limit <= 0 || n == 0 || nsvc == 0) return res;

        boolean[][] ok = new boolean[3][nsvc];
        for (int di = 0; di < 3; di++)
            for (int k = 0; k < nsvc; k++) ok[di][k] = serviceRuns(svcs[k], ymd[di], weekday[di]);

        int[] ot = new int[limit];
        int[] ol = new int[limit];
        int[] oh = new int[limit];
        int count = 0;
        for (int di = 0; di < 3; di++) {
            int off = (di - 1) * 1440;
            for (int i = 0; i < n; i++) {
                if (kinds[i] != kind) continue;
                int t = off + minute[i];
                if (t < now || t >= now + horizon) continue;
                int sv = svc[i];
                if (sv >= nsvc || !ok[di][sv]) continue;
                if (count == limit && t >= ot[count - 1]) continue;
                int j = count < limit ? count : limit - 1;
                while (j > 0 && ot[j - 1] > t) {
                    ot[j] = ot[j - 1];
                    ol[j] = ol[j - 1];
                    oh[j] = oh[j - 1];
                    j--;
                }
                ot[j] = t;
                ol[j] = line[i];
                oh[j] = head[i];
                if (count < limit) count++;
            }
        }
        for (int i = 0; i < count; i++) {
            Next x = new Next();
            x.time = ot[i];
            x.line = ol[i];
            x.head = oh[i];
            res.add(x);
        }
        return res;
    }

    // ------------------------------------------------------------------ teksty

    /** Rosyjska liczba mnoga: 0 = "1 минуту", 1 = "2 минуты", 2 = "5 минут" */
    static int ruForm(int n) {
        int a = n % 10, b = n % 100;
        if (a == 1 && b != 11) return 0;
        if (a >= 2 && a <= 4 && (b < 12 || b > 14)) return 1;
        return 2;
    }

    public static String eta(int m) {
        int lang = Lang.current();
        if (lang == 1) {
            if (m <= 0) return "departing now";
            if (m == 1) return "in 1 minute";
            if (m < 60) return "in " + m + " minutes";
            if (m % 60 != 0) return "in " + (m / 60) + " h " + (m % 60) + " min";
            return "in " + (m / 60) + " h";
        }
        if (lang == 2) {
            String[] mins = { "минуту", "минуты", "минут" };
            if (m <= 0) return "отправляется сейчас";
            if (m < 60) return "через " + m + " " + mins[ruForm(m)];
            if (m % 60 != 0) return "через " + (m / 60) + " ч " + (m % 60) + " мин";
            return "через " + (m / 60) + " ч";
        }
        if (m <= 0) return "odjeżdża teraz";
        if (m == 1) return "za 1 minutę";
        if (m < 60) {
            int last = m % 10;
            String w = (last >= 2 && last <= 4 && (m < 10 || m > 20)) ? "minuty" : "minut";
            return "za " + m + " " + w;
        }
        int h = m / 60, r = m % 60;
        if (r != 0) return "za " + h + " godz. " + r + " min";
        return "za " + h + " godz.";
    }

    public static String clock(int time) {
        if (time < 0) time = 0;
        int h = (time / 60) % 24, m = time % 60;
        return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }

    public static String duration(long seconds) {
        if (seconds < 0) seconds = 0;
        long m = seconds / 60;
        long d = m / 1440;
        long h = (m % 1440) / 60;
        m = m % 60;
        StringBuilder b = new StringBuilder();
        int lang = Lang.current();
        if (lang == 1) {
            if (d != 0) b.append(d).append(d == 1 ? " day" : " days");
            if (d != 0 || h != 0) b.append(b.length() == 0 ? "" : " ").append(h).append(" h");
            b.append(b.length() == 0 ? "" : " ").append(m).append(" min");
        } else if (lang == 2) {
            String[] days = { "день", "дня", "дней" };
            if (d != 0) b.append(d).append(' ').append(days[ruForm((int) (d % 100000))]);
            if (d != 0 || h != 0) b.append(b.length() == 0 ? "" : " ").append(h).append(" ч");
            b.append(b.length() == 0 ? "" : " ").append(m).append(" мин");
        } else {
            if (d != 0) b.append(d).append(d == 1 ? " dzień" : " dni");
            if (d != 0 || h != 0) b.append(b.length() == 0 ? "" : " ").append(h).append(" godz.");
            b.append(b.length() == 0 ? "" : " ").append(m).append(" min");
        }
        return b.toString();
    }

    /** Odliczanie do odjazdu w sekundach, np. "za 4 min 20 s" (poniżej 10 minut), inaczej jak eta. */
    public static String countdown(int secondsLeft) {
        if (secondsLeft <= 0) return Lang.t("odjeżdża teraz");
        if (secondsLeft >= 600) return eta((secondsLeft + 59) / 60);
        int m = secondsLeft / 60, s = secondsLeft % 60;
        if (m == 0) return Lang.f("za %d s", s);
        return Lang.f("za %d min %02d s", m, s);
    }

    /** "20261231" -> 20261231; cokolwiek innego -> 0 */
    public static int parseDate(String s) {
        if (s == null || s.length() != 8) return 0;
        int v = 0;
        for (int i = 0; i < 8; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return 0;
            v = v * 10 + (c - '0');
        }
        return v;
    }

    public static int kindBit(char c) {
        switch (c) {
            case 'b': return 1;
            case 't': return 2;
            case 'p': return 4;
            case 'm': return 8;
        }
        return 0;
    }
}
