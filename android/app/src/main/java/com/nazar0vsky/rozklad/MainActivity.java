package com.nazar0vsky.rozklad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/** Jedyna Activity: zakładki, stos ekranów, okna modalne, ekran powitalny i adres rozklad://import. */
public class MainActivity extends Activity {
    static final char[] KINDS = { 'b', 't', 'p', 'm', 's' };
    private static final int REQ_FILE = 88;
    private static final int REQ_NOTIF = 77;

    App app;
    private FrameLayout rootFrame, content, modalLayer;
    private LinearLayout main;
    private Widgets.TabBar tabs;
    private ImageView splash;
    @SuppressWarnings("unchecked")
    private final ArrayList<Screen>[] stacks = new ArrayList[5];
    private final ArrayList<Screen> modals = new ArrayList<Screen>();
    private int tab = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean resumed, ready, loading;
    private String pendingUrl;
    private Runnable pendingNotif;
    private long startTime;

    private final Runnable ticker = new Runnable() {
        public void run() {
            if (!resumed) return;
            Screen s = visible();
            if (s != null) s.tick();
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        app = (App) getApplication();
        startTime = SystemClock.uptimeMillis();
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(Color.rgb(170, 8, 24));
        setContentView(rootFrame);
        splash = new ImageView(this);
        splash.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap sp = Skin.splash();
        if (sp != null) splash.setImageBitmap(sp);
        splash.setClickable(true);
        rootFrame.addView(splash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setBars(Color.rgb(170, 8, 24), Color.rgb(170, 8, 24));
        remember(getIntent());
        startLoading();
    }

    private void startLoading() {
        if (app.loaded) {
            onLoaded();
            return;
        }
        if (loading) return;
        loading = true;
        new Thread(new Runnable() {
            public void run() {
                app.schedule.reload();
                app.loaded = true;
                handler.post(new Runnable() {
                    public void run() {
                        loading = false;
                        onLoaded();
                    }
                });
            }
        }).start();
    }

    private void onLoaded() {
        ready = true;
        buildUi(-1);
        long wait = 1400 - (SystemClock.uptimeMillis() - startTime);
        handler.postDelayed(new Runnable() {
            public void run() { fadeSplash(); }
        }, Math.max(0, wait));
        String url = pendingUrl;
        pendingUrl = null;
        if (url != null) showImport(url);
        if (resumed) handler.post(ticker);
    }

    private void fadeSplash() {
        if (splash == null) return;
        splash.animate().alpha(0f).setDuration(450).withEndAction(new Runnable() {
            public void run() {
                if (splash != null) rootFrame.removeView(splash);
                splash = null;
                setBars(Skin.statusColor(), Skin.navigationColor());
                Screen s = visible();
                if (s != null) s.onShow();
            }
        }).start();
    }

    // ------------------------------------------------------------------ pasek stanu

    private void setBars(int status, int nav) {
        if (Build.VERSION.SDK_INT >= 21) {
            Window w = getWindow();
            w.setStatusBarColor(status);
            w.setNavigationBarColor(nav);
        }
    }

    // ------------------------------------------------------------------ budowa interfejsu

    /** Buduje wszystkie ekrany od nowa (start, zmiana motywu lub języka). */
    void buildUi(int selectTab) {
        if (main != null) rootFrame.removeView(main);
        if (modalLayer != null) rootFrame.removeView(modalLayer);
        modals.clear();
        String[] names = { Lang.t("Autobus"), Lang.t("Tramwaj"), Lang.t("Pociąg"), Lang.t("Metro"), Lang.t("Ustawienia") };
        String[] titles = { Lang.t("Autobusy"), Lang.t("Tramwaje"), Lang.t("Pociągi"), Lang.t("Metro"), Lang.t("Ustawienia") };

        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        content = new FrameLayout(this);
        main.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        tabs = new Widgets.TabBar(this, names, KINDS, new Widgets.TabBar.Listener() {
            public void onTab(int index) { showTab(index, true); }
        });
        main.addView(tabs);

        int first = -1;
        for (int i = 0; i < 5; i++) {
            stacks[i] = new ArrayList<Screen>();
            if (i < 4) {
                stacks[i].add(new KindScreen(this, KINDS[i], titles[i]));
                if (first < 0 && !app.schedule.stopsOfKind(KINDS[i]).isEmpty()) first = i;
            } else {
                stacks[i].add(new SettingsScreen(this));
            }
        }
        modalLayer = new FrameLayout(this);
        modalLayer.setVisibility(View.GONE);
        rootFrame.addView(main, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootFrame.addView(modalLayer, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (splash == null) setBars(Skin.statusColor(), Skin.navigationColor());
        int t = selectTab >= 0 ? selectTab : (first >= 0 ? first : 0);
        showTab(t, false);
    }

    /** Zmiana motywu lub języka: ekrany budowane od nowa, zostajemy na tej samej zakładce. */
    void rebuildKeepingTab() { buildUi(tab); }

    private void showTab(int i, boolean tapped) {
        if (tapped && i == tab && stacks[i].size() > 1) {
            // dotknięcie aktywnej zakładki wraca do jej początku
            while (stacks[i].size() > 1) stacks[i].remove(stacks[i].size() - 1);
        }
        Screen old = visible();
        if (old != null && content.getChildCount() > 0) old.onHide();
        tab = i;
        tabs.select(i);
        showTop();
    }

    private void showTop() {
        content.removeAllViews();
        Screen s = top();
        content.addView(s.view(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (splash == null) s.onShow();
    }

    private Screen top() {
        ArrayList<Screen> st = stacks[tab];
        return st.get(st.size() - 1);
    }

    /** Ekran, który użytkownik widzi teraz. */
    Screen visible() {
        if (!ready || stacks[tab] == null) return null;
        if (!modals.isEmpty()) return modals.get(modals.size() - 1);
        return top();
    }

    void push(Screen s) {
        top().onHide();
        stacks[tab].add(s);
        showTop();
    }

    void pop() {
        ArrayList<Screen> st = stacks[tab];
        if (st.size() <= 1) return;
        top().onHide();
        st.remove(st.size() - 1);
        showTop();
    }

    /** Tytuł ekranu pod bieżącym (do przycisku Wstecz). */
    String previousTitle() {
        ArrayList<Screen> st = stacks[tab];
        return st.size() >= 2 ? st.get(st.size() - 2).backTitle() : Lang.t("Wstecz");
    }

    boolean hasModal() { return !modals.isEmpty(); }

    void showModal(Screen s) {
        Screen below = visible();
        if (below != null) below.onHide();
        modals.add(s);
        modalLayer.setVisibility(View.VISIBLE);
        modalLayer.addView(s.view(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        s.onShow();
    }

    void closeModal(Screen s) {
        if (!modals.remove(s)) return;
        s.onHide();
        modalLayer.removeView(s.view());
        if (modals.isEmpty()) modalLayer.setVisibility(View.GONE);
        Screen v = visible();
        if (v != null) v.onShow();
    }

    /** Rozkład, język lub ustawienia się zmieniły: odśwież wszystkie ekrany. */
    void dataChanged() {
        if (!ready) return;
        for (ArrayList<Screen> st : stacks) if (st != null) for (Screen s : st) s.dirty = true;
        for (Screen s : modals) s.dirty = true;
        Screen v = visible();
        if (v != null) v.onShow();
    }

    int currentTab() { return tab; }

    // ------------------------------------------------------------------ cykl życia

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (ready) {
            if (app.schedule.fileCount() >= 0) reloadIfChangedAsync();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        remember(i);
        if (ready && pendingUrl != null) {
            String url = pendingUrl;
            pendingUrl = null;
            showImport(url);
        }
    }

    private void remember(Intent i) {
        if (i == null) return;
        Uri u = i.getData();
        if (u != null && "rozklad".equalsIgnoreCase(u.getScheme())) {
            String s = u.toString();
            if (s.length() >= 16) s = "rozklad://import" + s.substring(16);
            pendingUrl = s;
            i.setData(null);
        }
    }

    private void showImport(String url) {
        while (!modals.isEmpty()) closeModal(modals.get(modals.size() - 1));
        showModal(new ImportScreen(this, url));
    }

    /** Wykonuje pracę w tle (np. kasowanie plików), a potem odświeża ekrany. */
    void background(final Runnable work) {
        new Thread(new Runnable() {
            public void run() {
                work.run();
                handler.post(new Runnable() {
                    public void run() { dataChanged(); }
                });
            }
        }).start();
    }

    private void reloadIfChangedAsync() {
        new Thread(new Runnable() {
            public void run() {
                final boolean changed = app.schedule.reloadIfChanged();
                if (changed) handler.post(new Runnable() {
                    public void run() { dataChanged(); }
                });
            }
        }).start();
    }

    /** Wczytuje od nowa pliki z folderu aplikacji (w tle) i odświeża ekrany. */
    void reloadAsync(final Runnable then) {
        new Thread(new Runnable() {
            public void run() {
                app.schedule.reload();
                handler.post(new Runnable() {
                    public void run() {
                        dataChanged();
                        if (then != null) then.run();
                    }
                });
            }
        }).start();
    }

    @Override public void onBackPressed() {
        if (!ready) { super.onBackPressed(); return; }
        Screen v = visible();
        if (v != null && v.onBack()) return;
        if (!modals.isEmpty()) {
            closeModal(modals.get(modals.size() - 1));
            return;
        }
        if (stacks[tab].size() > 1) {
            pop();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ okna dialogowe

    void alert(String title, String text) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK", null).show();
    }

    interface Pick { void picked(int index); }

    /** Okno z listą wyboru (jak arkusz akcji na iOS). */
    void sheet(String title, String[] items, final Pick p) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        if (title != null) b.setTitle(title);
        b.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) { p.picked(which); }
        });
        b.setNegativeButton(Lang.t("Anuluj"), null);
        b.show();
    }

    /** Potwierdzenie czynności, której nie da się cofnąć. */
    void confirm(String title, String button, final Runnable yes) {
        new AlertDialog.Builder(this).setMessage(title)
            .setPositiveButton(button, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { yes.run(); }
            })
            .setNegativeButton(Lang.t("Anuluj"), null).show();
    }

    void toast(String t) { Toast.makeText(this, t, Toast.LENGTH_LONG).show(); }

    // ------------------------------------------------------------------ strona z rozkładem

    /** Otwiera w przeglądarce stronę, na której wybiera się przystanki. */
    void openSite() {
        String site = Config.SITE_URL;
        if (!site.startsWith("http")) site = app.store.get("site_base");
        if (site == null || !site.startsWith("http")) {
            alert(Lang.t("Brak adresu strony"), Lang.t("Otwórz w przeglądarce stronę z rozkładem, wybierz przystanki i dotknij „Otwórz w aplikacji Rozkład”. Potem aplikacja zapamięta adres."));
            return;
        }
        if (site.endsWith("/")) site = site + "kreator/";
        site = site + (site.indexOf('?') < 0 ? "?" : "&") + "lang=" + Lang.code();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(site)));
        } catch (RuntimeException e) {
            alert(Lang.t("Brak adresu strony"), site);
        }
    }

    // ------------------------------------------------------------------ plik z telefonu

    void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_FILE);
        } catch (RuntimeException e) {
            alert(Lang.t("Nie udało się"), Lang.t("Nie udało się wczytać pliku."));
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && res == RESULT_OK && data != null && data.getData() != null) importFile(data.getData());
    }

    private String displayName(Uri u) {
        Cursor c = null;
        try {
            c = getContentResolver().query(u, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException e) {
            // brak nazwy: użyjemy rozklad.json
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    private static String safeName(String n) {
        if (n == null) return "rozklad.json";
        String s = n.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!s.toLowerCase(java.util.Locale.ROOT).endsWith(".json") || s.startsWith(".")) return "rozklad.json";
        return s;
    }

    private void importFile(final Uri uri) {
        final AlertDialog wait = new AlertDialog.Builder(this).setMessage(Lang.t("Wczytuję plik…")).setCancelable(false).create();
        wait.show();
        final String name = safeName(displayName(uri));
        new Thread(new Runnable() {
            public void run() {
                String error = null;
                File folder = app.schedule.folder();
                folder.mkdirs();
                File part = new File(folder, "import.part");
                try {
                    ContentResolver cr = getContentResolver();
                    InputStream in = cr.openInputStream(uri);
                    if (in == null) throw new IOException("brak pliku");
                    OutputStream out = new FileOutputStream(part);
                    try {
                        byte[] buf = new byte[65536];
                        long total = 0;
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            total += n;
                            if (total > 80L * 1024 * 1024) throw new IOException("za duży");
                            out.write(buf, 0, n);
                        }
                    } finally {
                        out.close();
                        in.close();
                    }
                    Schedule.check(part);
                    File dest = new File(folder, name);
                    dest.delete();
                    if (!part.renameTo(dest)) throw new IOException("zapis");
                    app.schedule.reload();
                } catch (Scan.Bad e) {
                    error = Lang.f("Nie mogę odczytać pliku %s (to nie jest rozklad.json z generatora).", name);
                } catch (IOException e) {
                    error = Lang.t("Nie udało się wczytać pliku.");
                } catch (OutOfMemoryError e) {
                    error = Lang.t("Brak pamięci w telefonie, żeby złożyć rozkład. Wybierz mniej przystanków.");
                } catch (RuntimeException e) {
                    error = Lang.t("Nie udało się wczytać pliku.");
                } finally {
                    part.delete();
                }
                final String err = error;
                handler.post(new Runnable() {
                    public void run() {
                        wait.dismiss();
                        dataChanged();
                        if (err != null) alert(Lang.t("Nie udało się"), err);
                        else alert(Lang.t("Wczytano plik"), Lang.t("Plik został wczytany i zapisany w aplikacji."));
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ powiadomienia (Android 13+)

    /** Prosi o zgodę na powiadomienia (jeśli trzeba) i potem wykonuje akcję. */
    void withNotifications(Runnable go) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            pendingNotif = go;
            requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, REQ_NOTIF);
            return;
        }
        go.run();
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_NOTIF && pendingNotif != null) {
            Runnable r = pendingNotif;
            pendingNotif = null;
            r.run();
            if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
                toast(Lang.t("Aby przypomnienia działały, zezwól aplikacji na powiadomienia w ustawieniach telefonu."));
            }
        }
    }
}
