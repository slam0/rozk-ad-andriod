package com.nazar0vsky.rozklad;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Wczytane rozkłady, wybór przystanków, ulubione i ustawienia (port RZSchedule z wersji na iOS).
 * Bez zależności od Androida: pliki w folderze, ustawienia przez interfejs Kv.
 */
public final class Schedule {

    public static final class Departure {
        public int time;        // minuty od północy dzisiaj
        public String line = "";
        public String head = "";
        public char kind;
    }

    private final File folder;
    private final Kv kv;
    private List<Dataset> datasets = new ArrayList<Dataset>();
    private List<Dataset.Stop> stops = new ArrayList<Dataset.Stop>();
    private String loadError;
    private String signature = "";

    public Schedule(File folder, Kv kv) {
        this.folder = folder;
        this.kv = kv;
    }

    public File folder() { return folder; }
    public List<Dataset> datasets() { return datasets; }
    public List<Dataset.Stop> stops() { return stops; }
    public String loadError() { return loadError; }

    // ------------------------------------------------------------------ pliki

    private List<File> jsonFiles() {
        List<File> r = new ArrayList<File>();
        File[] all = folder.listFiles();
        if (all == null) return r;
        for (File f : all) {
            String n = f.getName();
            if (f.isFile() && !n.startsWith(".") && n.toLowerCase(Locale.ROOT).endsWith(".json")) r.add(f);
        }
        Collections.sort(r, new Comparator<File>() {
            public int compare(File a, File b) { return a.getName().compareTo(b.getName()); }
        });
        return r;
    }

    private String currentSignature() {
        StringBuilder b = new StringBuilder();
        for (File f : jsonFiles()) b.append(f.getName()).append('|').append(f.length()).append('|').append(f.lastModified()).append(';');
        return b.toString();
    }

    /** Wczytuje od nowa tylko, gdy pliki się zmieniły; zwraca true, jeśli wczytano. */
    public boolean reloadIfChanged() {
        if (currentSignature().equals(signature)) return false;
        reload();
        return true;
    }

    public void reload() {
        folder.mkdirs();
        List<Dataset> sets = new ArrayList<Dataset>();
        List<Dataset.Stop> all = new ArrayList<Dataset.Stop>();
        String problem = null;
        for (File f : jsonFiles()) {
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"), 65536);
                try {
                    Dataset ds = Dataset.load(r, f.getName());
                    sets.add(ds);
                    all.addAll(ds.stops);
                } finally {
                    r.close();
                }
            } catch (Scan.Bad e) {
                problem = Lang.f("Nie mogę odczytać pliku %s (to nie jest rozklad.json z generatora).", f.getName());
            } catch (IOException e) {
                problem = Lang.f("Nie mogę odczytać pliku %s (to nie jest rozklad.json z generatora).", f.getName());
            } catch (OutOfMemoryError e) {
                problem = Lang.f("Nie mogę odczytać pliku %s (to nie jest rozklad.json z generatora).", f.getName());
            }
        }
        datasets = sets;
        stops = all;
        loadError = problem;
        signature = currentSignature();
    }

    /** Sprawdza, czy plik da się wczytać jako rozklad.json (do importu z telefonu). */
    public static Dataset check(File f) throws Scan.Bad, IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"), 65536);
        try {
            return Dataset.load(r, f.getName());
        } finally {
            r.close();
        }
    }

    public int fileCount() { return jsonFiles().size(); }

    public int expiredFileCount(long nowMillis) {
        int n = 0;
        for (Dataset ds : datasets) {
            long d = expiryOf(ds);
            if (d > 0 && d <= nowMillis) n++;
        }
        return n;
    }

    public int removeAllFiles() {
        int n = 0;
        for (File f : jsonFiles()) if (f.delete()) n++;
        reload();
        return n;
    }

    public int removeExpiredFiles(long nowMillis) {
        Set<String> names = new HashSet<String>();
        for (Dataset ds : datasets) {
            long d = expiryOf(ds);
            if (d > 0 && d <= nowMillis) names.add(ds.fileName.toLowerCase(Locale.ROOT));
        }
        int n = 0;
        for (File f : jsonFiles()) {
            if (names.contains(f.getName().toLowerCase(Locale.ROOT)) && f.delete()) n++;
        }
        if (n > 0) reload();
        return n;
    }

    /** Koniec ostatniego dnia ważności jednego pliku (ms) lub 0, gdy plik nie podaje daty. */
    public static long expiryOf(Dataset ds) {
        int e = Core.parseDate(ds.end);
        if (e <= 0) return 0;
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(e / 10000, ((e / 100) % 100) - 1, (e % 100) + 1, 0, 0, 0);
        return c.getTimeInMillis();
    }

    /** Koniec ważności najwcześniej kończącego się rozkładu (ms) lub 0. */
    public long expiryDate() {
        long best = 0;
        for (Dataset ds : datasets) {
            long d = expiryOf(ds);
            if (d > 0 && (best == 0 || d < best)) best = d;
        }
        return best;
    }

    public String summary() {
        StringBuilder b = new StringBuilder();
        for (Dataset ds : datasets) {
            if (b.length() > 0) b.append(", ");
            b.append(ds.fileName);
            if (ds.generated.length() > 0) b.append(" (").append(ds.generated).append(')');
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ wybór przystanków

    public List<Dataset.Stop> stopsOfKind(char kind) {
        List<Dataset.Stop> r = new ArrayList<Dataset.Stop>();
        int bit = Core.kindBit(kind);
        for (Dataset.Stop s : stops) if ((s.kindMask & bit) != 0) r.add(s);
        return r;
    }

    /** Porównanie alfabetyczne, bez wielkości liter, liczby porównywane jako liczby. */
    public static int naturalCompare(Collator col, String a, String b) {
        int i = 0, j = 0, la = a.length(), lb = b.length();
        while (i < la && j < lb) {
            char x = a.charAt(i), y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int si = i, sj = j;
                while (i < la && Character.isDigit(a.charAt(i))) i++;
                while (j < lb && Character.isDigit(b.charAt(j))) j++;
                String na = strip(a.substring(si, i)), nb = strip(b.substring(sj, j));
                if (na.length() != nb.length()) return na.length() < nb.length() ? -1 : 1;
                int c = na.compareTo(nb);
                if (c != 0) return c < 0 ? -1 : 1;
            } else {
                int si = i, sj = j;
                while (i < la && !Character.isDigit(a.charAt(i))) i++;
                while (j < lb && !Character.isDigit(b.charAt(j))) j++;
                int c = col.compare(a.substring(si, i), b.substring(sj, j));
                if (c != 0) return c;
            }
        }
        return (la - i) - (lb - j) < 0 ? -1 : ((la - i) - (lb - j) > 0 ? 1 : 0);
    }

    private static String strip(String d) {
        int k = 0;
        while (k < d.length() - 1 && d.charAt(k) == '0') k++;
        return d.substring(k);
    }

    public List<Dataset.Stop> sortedStopsOfKind(char kind) {
        List<Dataset.Stop> r = stopsOfKind(kind);
        final Collator col = Collator.getInstance(Lang.current() == 0 ? new Locale("pl", "PL") : Locale.getDefault());
        col.setStrength(Collator.SECONDARY);
        Collections.sort(r, new Comparator<Dataset.Stop>() {
            public int compare(Dataset.Stop a, Dataset.Stop b) { return naturalCompare(col, a.title, b.title); }
        });
        return r;
    }

    // ---- listy w ustawieniach (kodowane jako JSON) ----

    List<String> getList(String key) {
        String v = kv.get(key);
        if (v == null) return null;
        List<String> r = new ArrayList<String>();
        try {
            List<Object> a = Json.arr(Json.parse(v));
            if (a == null) return null;
            for (Object o : a) r.add(Json.str(o));
        } catch (Json.ParseException e) {
            return null;
        }
        return r;
    }

    void putList(String key, List<String> list) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(',');
            b.append(Json.quote(list.get(i)));
        }
        b.append(']');
        kv.put(key, b.toString());
    }

    private static String selectionKey(char kind) { return "wybrane_" + kind; }

    public List<Dataset.Stop> selectedStopsOfKind(char kind) {
        List<String> keys = getList(selectionKey(kind));
        List<Dataset.Stop> r = new ArrayList<Dataset.Stop>();
        if (keys == null || keys.isEmpty()) return r;
        Set<String> set = new HashSet<String>(keys);
        for (Dataset.Stop s : sortedStopsOfKind(kind)) if (set.contains(s.key)) r.add(s);
        return r;
    }

    public boolean needsSelectionForKind(char kind) {
        if (stopsOfKind(kind).isEmpty()) return false;
        List<String> keys = getList(selectionKey(kind));
        if (keys == null) return true;
        if (keys.isEmpty()) return false;
        return selectedStopsOfKind(kind).isEmpty();
    }

    public void setSelectedStops(List<Dataset.Stop> list, char kind) {
        List<String> keys = new ArrayList<String>();
        for (Dataset.Stop s : list) keys.add(s.key);
        putList(selectionKey(kind), keys);
    }

    public void selectAllStops() {
        char[] kinds = { 'b', 't', 'p', 'm' };
        for (char k : kinds) {
            List<Dataset.Stop> all = sortedStopsOfKind(k);
            if (!all.isEmpty()) setSelectedStops(all, k);
        }
    }

    /** Krótki opis słupka do listy wyboru, np. "linie: 175, 222 · do: Lotnisko Chopina". */
    public String descriptionForStop(Dataset.Stop stop, char kind) {
        Dataset ds = stop.dataset;
        int nl = ds.lines.size(), nh = ds.heads.size();
        if (stop.count == 0 || nl == 0) return "";
        int[] lc = new int[nl];
        int[] hc = new int[nh == 0 ? 1 : nh];
        for (int i = 0; i < stop.count; i++) {
            if (stop.kind[i] != (byte) kind) continue;
            if (stop.line[i] < nl) lc[stop.line[i]]++;
            if (stop.head[i] < nh) hc[stop.head[i]]++;
        }
        List<String> lines = new ArrayList<String>();
        for (int i = 0; i < nl; i++) if (lc[i] > 0) lines.add(ds.lines.get(i));
        final Collator col = Collator.getInstance(Locale.ROOT);
        Collections.sort(lines, new Comparator<String>() {
            public int compare(String a, String b) { return naturalCompare(col, a, b); }
        });
        int best = -1;
        for (int i = 0; i < nh; i++) if (hc[i] > 0 && (best < 0 || hc[i] > hc[best])) best = i;
        String head = best >= 0 ? ds.heads.get(best) : "";
        if (lines.isEmpty()) return "";
        StringBuilder list = new StringBuilder();
        int shown = Math.min(6, lines.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) list.append(", ");
            list.append(lines.get(i));
        }
        if (lines.size() > 6) list.append('…');
        if (head.length() > 0) return Lang.f("linie: %s · do: %s", list.toString(), head);
        return Lang.f("linie: %s", list.toString());
    }

    // ------------------------------------------------------------------ odjazdy

    public static int minutesOfDay(long millis, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(millis);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    public static int secondsOfDay(long millis, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(millis);
        return (c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) * 60 + c.get(Calendar.SECOND);
    }

    public List<Departure> nextDepartures(Dataset.Stop stop, char kind, long nowMillis, TimeZone tz, int limit) {
        List<Departure> result = new ArrayList<Departure>();
        if (stop.count == 0 || stop.dataset == null) return result;
        int[] ymd = new int[3];
        int[] wd = new int[3];
        for (int i = 0; i < 3; i++) {
            Calendar c = Calendar.getInstance(tz);
            c.setTimeInMillis(nowMillis);
            c.add(Calendar.DAY_OF_MONTH, i - 1);
            ymd[i] = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
            wd[i] = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;     // niedziela=1 -> 6, poniedziałek=2 -> 0
        }
        int nowMin = minutesOfDay(nowMillis, tz);
        int horizon = kind == 'p' ? 1440 : 720;
        Dataset ds = stop.dataset;
        List<Core.Next> found = Core.next(stop.count, stop.minute, stop.svc, stop.line, stop.head, stop.kind,
            ds.svcs, ymd, wd, nowMin, horizon, kind, limit);
        for (Core.Next n : found) {
            Departure d = new Departure();
            d.time = n.time;
            d.kind = kind;
            d.line = n.line < ds.lines.size() ? ds.lines.get(n.line) : "?";
            d.head = n.head < ds.heads.size() ? ds.heads.get(n.head) : "";
            result.add(d);
        }
        return result;
    }

    // ------------------------------------------------------------------ ulubione i ustawienia

    private static String favId(Dataset.Stop stop, char kind) { return kind + "|" + stop.key; }

    public boolean isFavorite(Dataset.Stop stop, char kind) {
        List<String> a = getList("ulubione");
        return a != null && a.contains(favId(stop, kind));
    }

    public void setFavorite(boolean fav, Dataset.Stop stop, char kind) {
        List<String> a = getList("ulubione");
        if (a == null) a = new ArrayList<String>();
        String id = favId(stop, kind);
        a.remove(id);
        if (fav) a.add(id);
        putList("ulubione", a);
    }

    public int favoriteCount() {
        List<String> a = getList("ulubione");
        return a == null ? 0 : a.size();
    }

    public void clearFavorites() { kv.remove("ulubione"); }

    public int departureLimit() {
        try {
            int n = Integer.parseInt(kv.get("limit_odjazdow"));
            return (n == 10 || n == 20 || n == 30) ? n : 20;
        } catch (RuntimeException e) {
            return 20;
        }
    }

    public void setDepartureLimit(int n) { kv.put("limit_odjazdow", Integer.toString(n)); }
}
