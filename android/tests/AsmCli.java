import com.nazar0vsky.rozklad.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Narzędzie testowe: AsmCli katalog_strony wersja lista (np. "3.1,4.15") [plik_wyjściowy]. Odpowiednik tests/import_cli.c. */
public class AsmCli {
    public static void main(String[] a) throws Exception {
        String url = "rozklad://import?u=http%3A%2F%2Fexample.org%2Frozklad&v=" + a[1] + "&p=" + a[2];
        ImportReq r;
        try { r = ImportReq.parse(url); } catch (ImportReq.Err e) { System.err.println("parse: " + e.getMessage()); System.exit(3); return; }
        File site = new File(a[0]);
        File svc = new File(site, "d/" + a[1] + "/svc.json");
        if (!svc.exists()) { System.err.println("brak svc"); System.exit(4); }
        File tmp = File.createTempFile("asm", ".tmp");
        File out = a.length > 3 ? new File(a[3]) : File.createTempFile("asm", ".json");
        Assembler as = null;
        try {
            try {
                as = new Assembler(new String(Files.readAllBytes(svc.toPath()), StandardCharsets.UTF_8), tmp);
            } catch (Assembler.Err e) { System.err.println("svc: " + e.getMessage()); System.exit(5); return; }
            for (int i = 0; i < r.count(); i++) {
                File p = new File(site, "d/" + a[1] + "/s/" + r.num.get(i) + ".json");
                if (!p.exists()) { System.err.println("brak " + p); System.exit(6); }
                try {
                    Reader rd = new InputStreamReader(new FileInputStream(p), "UTF-8");
                    try { as.add(rd, r.mask.get(i)); } finally { rd.close(); }
                } catch (Assembler.Err e) { System.err.println("pole " + r.num.get(i) + ": " + e.getMessage()); System.exit(7); }
            }
            try { as.finish(out); } catch (Assembler.Err e) { System.err.println(e.getMessage()); System.exit(8); }
        } finally {
            if (as != null) as.close();
        }
        if (a.length <= 3) {
            System.out.write(Files.readAllBytes(out.toPath()));
            System.out.flush();
            out.delete();
        }
    }
}
