package com.nazar0vsky.rozklad;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Pobieranie rozkładu ze strony i składanie rozklad.json. Bez zależności od Androida
 * (tylko java.net), więc da się sprawdzić na komputerze.
 */
public final class Importer {

    public interface Listener {
        /** Wołane z wątku roboczego: postęp 0..1 i tekst do pokazania. */
        void progress(float p, String text);
    }

    public static final class Result {
        public boolean ok;
        public boolean cancelled;
        public String error = "";
        public int stops;
        public long bytes;
        public String base = "";
    }

    private volatile boolean cancelled;

    public void cancel() { cancelled = true; }

    /** Komunikat z kodu składającego (po polsku) przetłumaczony; „(przyczyna)” tłumaczone osobno. */
    static String errText(String e) {
        String[] prefixes = { "Plik z kalendarzami jest uszkodzony (", "Plik słupka jest uszkodzony (" };
        for (String p : prefixes) {
            if (e.startsWith(p) && e.endsWith(").")) {
                String reason = e.substring(p.length(), e.length() - 2);
                String pattern = Lang.t(p + "%s).");
                return pattern.replace("%s", Lang.t(reason));
            }
        }
        return Lang.t(e);
    }

    private byte[] download(String url, int limit, String[] reason) {
        String last = Lang.t("brak odpowiedzi");
        for (int attempt = 0; attempt < 3; attempt++) {
            if (cancelled) return null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(45000);
                c.setUseCaches(false);
                c.setRequestProperty("User-Agent", "Rozklad-Android");
                int code = c.getResponseCode();
                if (code == 200) {
                    InputStream in = c.getInputStream();
                    try {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream(Math.min(limit, 1 << 20));
                        byte[] buf = new byte[32768];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            if (cancelled) return null;
                            bo.write(buf, 0, n);
                            if (bo.size() > limit) {
                                reason[0] = Lang.t("Plik ze strony jest zbyt duży, więc go odrzuciłem.");
                                return null;
                            }
                        }
                        return bo.toByteArray();
                    } finally {
                        in.close();
                    }
                }
                if (code == 404) {
                    reason[0] = Lang.t("Na stronie nie ma już tych danych (błąd 404). Dane zostały odświeżone – otwórz stronę jeszcze raz, wybierz przystanki i ponownie dotknij „Otwórz w aplikacji Rozkład”.");
                    return null;
                }
                last = Lang.f("błąd serwera %d", code);
            } catch (javax.net.ssl.SSLException e) {
                reason[0] = Lang.t("Telefon nie mógł nawiązać bezpiecznego połączenia (HTTPS). Sprawdź, czy data i godzina w telefonie są poprawne.");
                return null;
            } catch (IOException e) {
                last = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            } finally {
                if (c != null) c.disconnect();
            }
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                return null;
            }
        }
        reason[0] = Lang.f("Nie udało się pobrać danych. Sprawdź połączenie z internetem i spróbuj jeszcze raz.\n\n(%s)", last);
        return null;
    }

    /** Pobiera pliki, składa rozkład i zapisuje folder/rozklad.json. Wywołuj w wątku roboczym. */
    public Result run(ImportReq req, File folder, Listener l) {
        Result res = new Result();
        res.base = req.base;
        File tmp = new File(folder, "rozklad.tmp");
        File part = new File(folder, "rozklad.part");
        Assembler as = null;
        String failure = null;
        int total = req.count() + 1;
        try {
            folder.mkdirs();
            for (int i = -1; i < req.count() && failure == null; i++) {
                if (cancelled) { res.cancelled = true; failure = ""; break; }
                String[] why = new String[1];
                byte[] d = download(req.fileUrl(i), i < 0 ? ImportReq.MAX_SVC_BYTES : ImportReq.MAX_POLE_BYTES, why);
                if (d == null) {
                    if (cancelled) res.cancelled = true;
                    failure = why[0] != null ? why[0] : "";
                    break;
                }
                try {
                    String text = new String(d, Charset.forName("UTF-8"));
                    if (i < 0) as = new Assembler(text, tmp);
                    else as.add(text, req.mask.get(i));
                } catch (Assembler.Err e) {
                    failure = errText(e.getMessage());
                }
                if (l != null) l.progress((float) (i + 2) / (float) total, Lang.f("Pobrano %d z %d", i + 2, total));
            }
            if (failure == null) {
                res.stops = as.stops();
                if (res.stops == 0) {
                    failure = Lang.t("Wybrane przystanki nie mają żadnych odjazdów. Spróbuj wybrać inne.");
                } else {
                    try {
                        as.finish(part);
                        res.bytes = part.length();
                        File dest = new File(folder, "rozklad.json");
                        if (!part.renameTo(dest)) {
                            dest.delete();
                            if (!part.renameTo(dest)) failure = Lang.t("Nie udało się zapisać pliku w telefonie.");
                        }
                    } catch (Assembler.Err e) {
                        failure = Lang.t("Nie udało się zapisać pliku w telefonie.");
                    }
                }
            }
        } catch (OutOfMemoryError e) {
            failure = Lang.t("Brak pamięci w telefonie, żeby złożyć rozkład. Wybierz mniej przystanków.");
        } finally {
            if (as != null) as.close();
            part.delete();
        }
        if (failure != null) {
            res.ok = false;
            res.error = failure;
        } else {
            res.ok = true;
        }
        return res;
    }

    static void writeFile(File f, String text) throws IOException {
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            w.write(text);
        } finally {
            w.close();
        }
    }
}
