import com.nazar0vsky.rozklad.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Wczytywanie rozklad.json, wybór przystanków, ulubione i odjazdy (kontra niezależne liczenie na drzewie JSON). */
public class ScheduleTest {
    static int fails = 0;
    static void check(boolean c, String m) { if (!c) { System.out.println("BŁĄD: " + m); fails++; } }

    static class MemKv implements Kv {
        Map<String, String> m = new HashMap<String, String>();
        public String get(String k) { return m.get(k); }
        public void put(String k, String v) { m.put(k, v); }
        public void remove(String k) { m.remove(k); }
    }

    public static void main(String[] a) throws Exception {
        File src = new File(a[0]);
        File dir = Files.createTempDirectory("rz").toFile();
        Files.copy(src.toPath(), new File(dir, "rozklad.json").toPath());
        Lang.setCurrent(0);
        MemKv kv = new MemKv();
        Schedule sc = new Schedule(dir, kv);
        check(sc.reloadIfChanged(), "pierwsze wczytanie");
        check(!sc.reloadIfChanged(), "bez zmian nie wczytuje ponownie");
        check(sc.loadError() == null, "brak błędu wczytywania");
        check(sc.datasets().size() == 1, "jeden plik");

        // niezależne drzewo
        Map<String, Object> root = Json.obj(Json.parse(new String(Files.readAllBytes(src.toPath()), StandardCharsets.UTF_8)));
        List<Object> jstops = Json.arr(root.get("stops"));
        check(sc.stops().size() == jstops.size(), "liczba przystanków " + sc.stops().size() + " vs " + jstops.size());
        Dataset ds = sc.datasets().get(0);
        List<Object> jsvc = Json.arr(root.get("svc"));
        check(ds.svcs.length == jsvc.size(), "liczba kalendarzy");

        TimeZone tz = TimeZone.getTimeZone("Europe/Warsaw");
        Random rnd = new Random(4);
        int compared = 0;
        for (int trial = 0; trial < 300; trial++) {
            int si = rnd.nextInt(jstops.size());
            Map<String, Object> js = Json.obj(jstops.get(si));
            Dataset.Stop st = sc.stops().get(si);
            check(st.name.equals(Json.str(js.get("n"))) && st.code.equals(Json.str(js.get("c"))), "nazwa przystanku");
            Calendar c = Calendar.getInstance(tz);
            c.clear();
            c.set(2026, 9, 20 + rnd.nextInt(10), rnd.nextInt(24), rnd.nextInt(60), 0);
            long now = c.getTimeInMillis();
            for (char kind : new char[] { 'b', 't', 'p', 'm' }) {
                int limit = new int[] { 10, 20, 30 }[rnd.nextInt(3)];
                List<Schedule.Departure> got = sc.nextDepartures(st, kind, now, tz, limit);
                // wzorzec: wszystkie odjazdy w oknie, posortowane po czasie, pierwsze `limit`
                int[] ymd = new int[3], wd = new int[3];
                for (int i = 0; i < 3; i++) {
                    Calendar d = Calendar.getInstance(tz);
                    d.setTimeInMillis(now);
                    d.add(Calendar.DAY_OF_MONTH, i - 1);
                    ymd[i] = d.get(Calendar.YEAR) * 10000 + (d.get(Calendar.MONTH) + 1) * 100 + d.get(Calendar.DAY_OF_MONTH);
                    int dow = d.get(Calendar.DAY_OF_WEEK);       // 1=niedziela
                    wd[i] = dow == 1 ? 6 : dow - 2;
                }
                int nowMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
                int horizon = kind == 'p' ? 1440 : 720;
                List<int[]> exp = new ArrayList<int[]>();      // czas, hash linii+kierunku
                List<String> expText = new ArrayList<String>();
                for (Object eo : Json.arr(js.get("d"))) {
                    List<Object> e = Json.arr(eo);
                    String k = Json.str(e.get(4));
                    if (k.charAt(0) != kind) continue;
                    int svi = Json.intOf(e.get(2));
                    Map<String, Object> jsv = Json.obj(jsvc.get(svi));
                    Core.Service cs = new Core.Service();
                    List<Object> days = Json.arr(jsv.get("d"));
                    for (int i = 0; i < 7; i++) cs.days[i] = Json.intOf(days.get(i)) != 0;
                    cs.start = Core.parseDate(Json.str(jsv.get("s")));
                    cs.end = Core.parseDate(Json.str(jsv.get("e")));
                    List<Object> aa = Json.arr(jsv.get("a")), rr = Json.arr(jsv.get("r"));
                    cs.add = new int[aa.size()];
                    for (int i = 0; i < aa.size(); i++) cs.add[i] = Core.parseDate(Json.str(aa.get(i)));
                    cs.rem = new int[rr.size()];
                    for (int i = 0; i < rr.size(); i++) cs.rem[i] = Core.parseDate(Json.str(rr.get(i)));
                    for (int di = 0; di < 3; di++) {
                        int t = (di - 1) * 1440 + Json.intOf(e.get(3));
                        if (t < nowMin || t >= nowMin + horizon) continue;
                        if (!Core.serviceRuns(cs, ymd[di], wd[di])) continue;
                        expText.add(t + "|" + Json.str(e.get(0)) + "|" + Json.str(e.get(1)));
                    }
                }
                Collections.sort(expText, new Comparator<String>() {
                    public int compare(String x, String y) {
                        int tx = Integer.parseInt(x.substring(0, x.indexOf('|'))), ty = Integer.parseInt(y.substring(0, y.indexOf('|')));
                        return tx - ty;
                    }
                });
                int n = Math.min(limit, expText.size());
                check(got.size() == n, "liczba odjazdów " + got.size() + " vs " + n);
                for (int i = 0; i < Math.min(n, got.size()); i++) {
                    int tw = Integer.parseInt(expText.get(i).substring(0, expText.get(i).indexOf('|')));
                    check(got.get(i).time == tw, "czas odjazdu " + got.get(i).time + " vs " + tw);
                }
                // zbiór z ostatniego czasu może się różnić przy remisach; porównaj tylko pełne grupy < ostatni czas
                if (n > 0 && got.size() == n) {
                    int last = got.get(n - 1).time;
                    Map<String, Integer> g1 = new HashMap<String, Integer>(), g2 = new HashMap<String, Integer>();
                    for (Schedule.Departure d : got) if (d.time < last) g1.merge(d.time + "|" + d.line + "|" + d.head, 1, Integer::sum);
                    for (int i = 0; i < expText.size(); i++) {
                        String t = expText.get(i);
                        if (Integer.parseInt(t.substring(0, t.indexOf('|'))) < last) g2.merge(t, 1, Integer::sum);
                    }
                    check(g1.equals(g2), "zestaw odjazdów");
                }
                compared++;
            }
        }

        // wybór przystanków
        char kind = 'b';
        List<Dataset.Stop> all = sc.sortedStopsOfKind(kind);
        check(!all.isEmpty(), "są autobusy");
        check(sc.needsSelectionForKind(kind), "trzeba wybrać");
        check(sc.selectedStopsOfKind(kind).isEmpty(), "nic nie wybrano");
        sc.setSelectedStops(all.subList(0, Math.min(2, all.size())), kind);
        check(!sc.needsSelectionForKind(kind), "wybrano");
        check(sc.selectedStopsOfKind(kind).size() == Math.min(2, all.size()), "wybrane 2");
        sc.setSelectedStops(new ArrayList<Dataset.Stop>(), kind);
        check(!sc.needsSelectionForKind(kind) && sc.selectedStopsOfKind(kind).isEmpty(), "świadomie nic");
        sc.selectAllStops();
        check(sc.selectedStopsOfKind(kind).size() == all.size(), "wszystkie");

        // ulubione
        Dataset.Stop f = all.get(0);
        check(!sc.isFavorite(f, kind), "nie ulubiony");
        sc.setFavorite(true, f, kind);
        check(sc.isFavorite(f, kind) && sc.favoriteCount() == 1, "ulubiony");
        check(!sc.isFavorite(f, 't'), "ulubiony tylko dla rodzaju");
        sc.setFavorite(false, f, kind);
        check(!sc.isFavorite(f, kind) && sc.favoriteCount() == 0, "usunięty");
        sc.setFavorite(true, f, kind); sc.clearFavorites();
        check(sc.favoriteCount() == 0, "wyczyszczone");

        // limit
        check(sc.departureLimit() == 20, "domyślny limit");
        sc.setDepartureLimit(30);
        check(sc.departureLimit() == 30, "limit 30");
        sc.setDepartureLimit(7);
        check(sc.departureLimit() == 20, "zły limit wraca do 20");

        // opis
        String d = sc.descriptionForStop(all.get(0), kind);
        check(d.startsWith("linie: "), "opis: " + d);
        Lang.setCurrent(1);
        check(sc.descriptionForStop(all.get(0), kind).startsWith("lines: "), "opis po angielsku");
        Lang.setCurrent(0);

        // ważność
        long exp = sc.expiryDate();
        check(exp > 0, "data ważności");
        check(sc.expiredFileCount(exp + 1000) == 1 && sc.expiredFileCount(exp - 1000) == 0, "przeterminowany plik");
        check(sc.fileCount() == 1, "plików: 1");
        check(sc.removeExpiredFiles(exp - 1000) == 0 && sc.fileCount() == 1, "nic nie usunięto");
        check(sc.removeExpiredFiles(exp + 1000) == 1 && sc.fileCount() == 0 && sc.stops().isEmpty(), "usunięto przeterminowany");

        // zepsuty plik
        Files.write(new File(dir, "zly.json").toPath(), "{\"a\":1}".getBytes("UTF-8"));
        sc.reload();
        check(sc.loadError() != null && sc.stops().isEmpty(), "zły plik daje komunikat");
        check(sc.removeAllFiles() == 1 && sc.loadError() == null, "usunięcie wszystkiego");

        // sortowanie naturalne
        java.text.Collator col = java.text.Collator.getInstance(Locale.ROOT);
        check(Schedule.naturalCompare(col, "Centrum 2", "Centrum 10") < 0, "2 < 10");
        check(Schedule.naturalCompare(col, "centrum 01", "Centrum 02") < 0, "bez wielkości liter");
        check(Schedule.naturalCompare(col, "A1", "A1") == 0, "równe");

        System.out.println("porównano " + compared + " zestawów odjazdów");
        if (fails > 0) { System.out.println("\n" + fails + " błędów"); System.exit(1); }
        System.out.println("schedule_test: OK");
    }
}
