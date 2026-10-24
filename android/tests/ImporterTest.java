import com.nazar0vsky.rozklad.*;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Pobieranie przez HTTP z lokalnego serwera: ImporterTest katalog_strony wersja lista_słupków */
public class ImporterTest {
    static int fails = 0;
    static void check(boolean c, String m) { if (!c) { System.out.println("BŁĄD: " + m); fails++; } }

    public static void main(String[] a) throws Exception {
        final File site = new File(a[0]);
        String version = a[1];
        String poles = a[2];
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/", new com.sun.net.httpserver.HttpHandler() {
            public void handle(HttpExchange ex) throws IOException {
                File f = new File(site, ex.getRequestURI().getPath().replaceFirst("^/rozklad/", ""));
                if (!f.isFile()) { ex.sendResponseHeaders(404, -1); ex.close(); return; }
                byte[] b = Files.readAllBytes(f.toPath());
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            }
        });
        srv.start();
        int port = srv.getAddress().getPort();
        String url = "rozklad://import?u=http%3A%2F%2F127.0.0.1%3A" + port + "%2Frozklad&v=" + version + "&p=" + poles;
        ImportReq req = ImportReq.parse(url);
        File folder = Files.createTempDirectory("rzimp").toFile();
        final List<String> log = new ArrayList<String>();
        Importer imp = new Importer();
        Importer.Result r = imp.run(req, folder, new Importer.Listener() {
            public void progress(float p, String t) { log.add(t); }
        });
        check(r.ok, "import udany: " + r.error);
        check(r.stops > 0 && r.bytes > 0, "są przystanki");
        check(new File(folder, "rozklad.json").isFile(), "plik zapisany");
        check(!new File(folder, "rozklad.part").exists() && !new File(folder, "rozklad.tmp").exists(), "bez plików tymczasowych");
        check(log.size() == req.count() + 1, "postęp po każdym pliku: " + log);

        Schedule sc = new Schedule(folder, new Kv() {
            Map<String, String> m = new HashMap<String, String>();
            public String get(String k) { return m.get(k); }
            public void put(String k, String v) { m.put(k, v); }
            public void remove(String k) { m.remove(k); }
        });
        sc.reload();
        check(sc.loadError() == null && sc.stops().size() == r.stops, "wczytany plik ma " + sc.stops().size() + " przystanków, import " + r.stops);

        // wynik zgodny z AsmCli (czyli z wersją w C)
        File ref = File.createTempFile("ref", ".json");
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-cp", System.getProperty("java.class.path"), "AsmCli", a[0], version, poles, ref.getPath()).inheritIO().start();
        check(p.waitFor() == 0, "AsmCli");
        Object x = Json.parse(new String(Files.readAllBytes(new File(folder, "rozklad.json").toPath()), "UTF-8"));
        Object y = Json.parse(new String(Files.readAllBytes(ref.toPath()), "UTF-8"));
        check(x.equals(y), "wynik zgodny z AsmCli");

        // 404
        ImportReq bad = ImportReq.parse("rozklad://import?u=http%3A%2F%2F127.0.0.1%3A" + port + "%2Frozklad&v=999&p=1.1");
        Importer.Result r2 = new Importer().run(bad, Files.createTempDirectory("rzimp2").toFile(), null);
        check(!r2.ok && r2.error.contains("404"), "404: " + r2.error);

        // brak serwera
        srv.stop(0);
        long t0 = System.currentTimeMillis();
        Importer.Result r3 = new Importer().run(req, Files.createTempDirectory("rzimp3").toFile(), null);
        check(!r3.ok && r3.error.length() > 0, "brak serwera daje błąd: " + r3.error);

        if (fails > 0) { System.out.println("\n" + fails + " błędów"); System.exit(1); }
        System.out.println("importer_test: OK");
    }
}
