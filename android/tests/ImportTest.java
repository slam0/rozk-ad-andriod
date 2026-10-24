import com.nazar0vsky.rozklad.*;

/** Port tests/import_test.c. */
public class ImportTest {
    static int fails = 0;
    static void check(boolean c, String m) { if (!c) { System.out.println("BŁĄD: " + m); fails++; } }

    static boolean bad(String url) {
        try { ImportReq.parse(url); return false; } catch (ImportReq.Err e) { return e.getMessage().length() > 0; }
    }

    static ImportReq ok(String url) {
        try { return ImportReq.parse(url); } catch (ImportReq.Err e) { System.out.println("BŁĄD: nieoczekiwany błąd " + e.getMessage() + " dla " + url); fails++; return new ImportReq(); }
    }

    public static void main(String[] args) {
        ImportReq r = ok("rozklad://import?u=https%3A%2F%2Fuser.github.io%2Frozklad%2F&v=202610231820&p=12.1,13.3,400.15");
        check(r.base.equals("https://user.github.io/rozklad/"), "base");
        check(r.host.equals("user.github.io"), "host");
        check(r.version.equals("202610231820"), "version");
        check(r.count() == 3 && r.num.get(0) == 12 && r.mask.get(0) == 1 && r.num.get(1) == 13 && r.mask.get(1) == 3 && r.num.get(2) == 400 && r.mask.get(2) == 15, "poles");
        check("https://user.github.io/rozklad/d/202610231820/svc.json".equals(r.fileUrl(-1)), "svc url");
        check("https://user.github.io/rozklad/d/202610231820/s/400.json".equals(r.fileUrl(2)), "pole url");
        check(r.fileUrl(3) == null, "poza zakresem");

        r = ok("rozklad://import?p=1.8&v=7&u=http%3A%2F%2F192.168.0.5%3A8000%2Fx");
        check(r.base.equals("http://192.168.0.5:8000/x/") && r.host.equals("192.168.0.5:8000"), "port");

        check(bad("rozklad://other?u=a&v=1&p=1.1"), "inny host");
        check(bad("http://import?u=a&v=1&p=1.1"), "inny schemat");
        check(bad("rozklad://import"), "bez parametrów");
        check(bad("rozklad://import?v=1&p=1.1"), "brak u");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F&p=1.1"), "brak v");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F&v=1"), "brak p");
        check(bad("rozklad://import?u=ftp%3A%2F%2Fa.pl%2F&v=1&p=1.1"), "ftp");
        check(bad("rozklad://import?u=file%3A%2F%2F%2Fetc%2F&v=1&p=1.1"), "file");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F&v=1a&p=1.1"), "wersja nie z cyfr");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F&v=&p=1.1"), "pusta wersja");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F&v=1&p=1"), "brak maski");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F&v=1&p=1.0"), "maska 0");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F&v=1&p=1.16"), "maska 16");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F&v=1&p=1.1,,2.1"), "podwójny przecinek");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F&v=1&p=1.1,"), "końcowy przecinek");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F&v=1&p=12345678.1"), "za długi numer");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F%zz&v=1&p=1.1"), "zły %XX");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F%2&v=1&p=1.1"), "ucięty %X");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F%3Fx&v=1&p=1.1"), "? w adresie");
        check(bad("rozklad://import?u=https%3A%2F%2F%2F&v=1&p=1.1"), "pusty host");
        check(bad("rozklad://import?u=https%3A%2F%2Fa.pl%2F%20x&v=1&p=1.1"), "spacja");

        StringBuilder big = new StringBuilder("rozklad://import?u=https%3A%2F%2Fa.pl%2F&v=1&p=");
        for (int i = 0; i <= ImportReq.MAX_POLES; i++) big.append(i > 0 ? "," : "").append(i).append(".1");
        check(bad(big.toString()), "za dużo słupków");
        String cut = big.substring(0, big.length() - 6);
        r = ok(cut);
        check(r.count() == ImportReq.MAX_POLES, "400 słupków jest jeszcze ok");

        if (fails > 0) { System.out.println("\n" + fails + " błędów"); System.exit(1); }
        System.out.println("import_test: OK");
    }
}
