import com.nazar0vsky.rozklad.*;
import java.io.*;
import java.util.*;

public class LangAuditTest {
    public static void main(String[] a) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(a[0]), "UTF-8"));
        String l; while ((l = r.readLine()) != null) sb.append(l);
        Map<String,Object> m = Json.obj(Json.parse(sb.toString()));
        int missing = 0;
        List<String> keys = new ArrayList<String>(m.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            if (!Lang.has(k)) { missing++; System.out.println("BRAK: " + k + "  <- " + m.get(k)); }
        }
        System.out.println("sprawdzono " + keys.size() + ", brakuje " + missing);
        System.exit(missing == 0 ? 0 : 1);
    }
}
