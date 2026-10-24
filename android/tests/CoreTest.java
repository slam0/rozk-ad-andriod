import com.nazar0vsky.rozklad.*;
import java.util.List;

/** Port tests/core_test.c z wersji na iOS. */
public class CoreTest {
    static int fails = 0;
    static void check(boolean c, String m) { if (!c) { System.out.println("BŁĄD: " + m); fails++; } }
    static void eq(String got, String want, String m) {
        if (!got.equals(want)) { System.out.println("BŁĄD " + m + ": '" + got + "' zamiast '" + want + "'"); fails++; }
    }
    static void eta(int m, String want) { eq(Core.eta(m), want, "eta(" + m + ")"); }

    static List<Core.Next> run(int[] minute, int[] svc, int[] line, int[] head, String kinds, Core.Service[] sv,
                               int[] ymd, int[] wd, int now, int horizon, char kind, int limit) {
        int n = minute.length;
        char[] s = new char[n], l = new char[n], h = new char[n];
        byte[] k = new byte[n];
        for (int i = 0; i < n; i++) { s[i] = (char) svc[i]; l[i] = (char) line[i]; h[i] = (char) head[i]; k[i] = (byte) kinds.charAt(i); }
        return Core.next(n, minute, s, l, h, k, sv, ymd, wd, now, horizon, kind, limit);
    }

    public static void main(String[] a) {
        Lang.setCurrent(0);
        eta(0, "odjeżdża teraz"); eta(-3, "odjeżdża teraz");
        eta(1, "za 1 minutę"); eta(2, "za 2 minuty"); eta(5, "za 5 minut"); eta(11, "za 11 minut");
        eta(12, "za 12 minut"); eta(14, "za 14 minut"); eta(22, "za 22 minuty"); eta(45, "za 45 minut");
        eta(59, "za 59 minut"); eta(60, "za 1 godz."); eta(65, "za 1 godz. 5 min"); eta(125, "za 2 godz. 5 min");
        eq(Core.clock(10 * 60 + 44), "10:44", "clock");
        eq(Core.clock(25 * 60 + 10), "01:10", "clock po północy");
        eq(Core.duration((20L * 1440 + 13 * 60 + 24) * 60), "20 dni 13 godz. 24 min", "duration 20 dni");
        eq(Core.duration((1L * 1440 + 5) * 60), "1 dzień 0 godz. 5 min", "duration 1 dzień");
        eq(Core.duration((13L * 60 + 24) * 60), "13 godz. 24 min", "duration godziny");
        eq(Core.duration(7 * 60), "7 min", "duration minuty");

        Lang.setCurrent(1);
        eta(0, "departing now"); eta(1, "in 1 minute"); eta(5, "in 5 minutes"); eta(65, "in 1 h 5 min"); eta(120, "in 2 h");
        eq(Core.duration((1L * 1440 + 2 * 60 + 5) * 60), "1 day 2 h 5 min", "duration en 1 day");
        eq(Core.duration((20L * 1440 + 13 * 60 + 24) * 60), "20 days 13 h 24 min", "duration en days");
        Lang.setCurrent(2);
        eta(0, "отправляется сейчас"); eta(1, "через 1 минуту"); eta(2, "через 2 минуты"); eta(5, "через 5 минут");
        eta(11, "через 11 минут"); eta(12, "через 12 минут"); eta(14, "через 14 минут"); eta(21, "через 21 минуту");
        eta(22, "через 22 минуты"); eta(25, "через 25 минут"); eta(60, "через 1 ч"); eta(65, "через 1 ч 5 мин");
        eq(Core.duration((21L * 1440 + 13 * 60 + 24) * 60), "21 день 13 ч 24 мин", "duration ru 21");
        eq(Core.duration((3L * 1440 + 5) * 60), "3 дня 0 ч 5 мин", "duration ru 3");
        eq(Core.duration(11L * 1440 * 60), "11 дней 0 ч 0 мин", "duration ru 11");
        Lang.setCurrent(0);

        // odliczanie
        eq(Core.countdown(0), "odjeżdża teraz", "countdown 0");
        eq(Core.countdown(45), "za 45 s", "countdown s");
        eq(Core.countdown(260), "za 4 min 20 s", "countdown min s");
        eq(Core.countdown(605), "za 11 minut", "countdown 10+");
        Lang.setCurrent(1);
        eq(Core.countdown(260), "in 4 min 20 s", "countdown en");
        Lang.setCurrent(2);
        eq(Core.countdown(260), "через 4 мин 20 с", "countdown ru");
        Lang.setCurrent(0);

        check(Core.parseDate("20261231") == 20261231, "parse date");
        check(Core.parseDate("") == 0 && Core.parseDate("2026-12-31") == 0 && Core.parseDate(null) == 0, "parse date zły");

        Core.Service s0 = new Core.Service(), s1 = new Core.Service();
        s0.start = 20261001; s0.end = 20261231;
        for (int i = 0; i < 5; i++) s0.days[i] = true;
        s0.add = new int[] { 20261121 }; s0.rem = new int[] { 20261111 };
        s1.days[0] = true;
        check(Core.serviceRuns(s0, 20261023, 4), "piątek jedzie");
        check(!Core.serviceRuns(s0, 20261024, 5), "sobota nie jedzie");
        check(!Core.serviceRuns(s0, 20261111, 2), "11.11 nie jedzie");
        check(Core.serviceRuns(s0, 20261121, 5), "21.11 jedzie");
        check(!Core.serviceRuns(s0, 20260930, 2), "przed początkiem");
        check(!Core.serviceRuns(s0, 20270105, 1), "po końcu");
        check(!Core.serviceRuns(s1, 20261026, 0), "brak dat");

        Core.Service[] svc = { s0, s1 };
        int[] ymd = { 20261022, 20261023, 20261024 };
        int[] wd = { 3, 4, 5 };
        int[] mi = { 605, 617, 640, 1510, 605, 700, 1330, 1510 };
        int[] sv = { 0, 0, 0, 0, 0, 1, 0, 0 };
        int[] ln = { 1, 2, 1, 3, 9, 4, 5, 6 };
        int[] hd = { 1, 1, 1, 2, 1, 1, 1, 2 };
        String kinds = "bbbbtbbb";
        List<Core.Next> out = run(mi, sv, ln, hd, kinds, svc, ymd, wd, 600, 720, 'b', 5);
        check(out.size() == 3, "3 odjazdy w 12 h, jest " + out.size());
        check(out.size() >= 3 && out.get(0).time == 605 && out.get(0).line == 1, "pierwszy");
        check(out.size() >= 3 && out.get(1).time == 617 && out.get(1).line == 2, "drugi");
        check(out.size() >= 3 && out.get(2).time == 640, "trzeci");
        out = run(mi, sv, ln, hd, kinds, svc, ymd, wd, 600, 720, 'b', 2);
        check(out.size() == 2 && out.get(0).time == 605 && out.get(1).time == 617, "limit 2");

        out = run(new int[] { 1470 }, new int[] { 0 }, new int[] { 7 }, new int[] { 3 }, "b", svc, ymd, wd, 10, 720, 'b', 5);
        check(out.size() == 1 && out.get(0).time == 30 && out.get(0).line == 7, "kurs nocny z wczoraj");
        out = run(new int[] { 1470 }, new int[] { 0 }, new int[] { 7 }, new int[] { 3 }, "b", svc, ymd, new int[] { 5, 6, 0 }, 10, 720, 'b', 5);
        check(out.size() == 0, "kurs nocny, gdy wczoraj nie jeździł");

        out = run(mi, sv, ln, hd, kinds, svc, ymd, wd, 600, 720, 't', 5);
        check(out.size() == 1 && out.get(0).line == 9, "tramwaj");
        out = run(mi, sv, ln, hd, kinds, svc, ymd, wd, 600, 720, 'm', 5);
        check(out.size() == 0, "brak metra");
        out = run(mi, sv, ln, hd, kinds, svc, ymd, wd, 600, 1440, 'b', 10);
        check(out.size() == 6, "doba: 6, jest " + out.size());
        check(out.size() == 6 && out.get(3).time == 1330 && out.get(4).time == 1510 && out.get(5).time == 1510, "doba: kolejność");

        if (fails > 0) { System.out.println("\n" + fails + " błędów"); System.exit(1); }
        System.out.println("OK: wszystkie testy logiki przeszły");
    }
}
