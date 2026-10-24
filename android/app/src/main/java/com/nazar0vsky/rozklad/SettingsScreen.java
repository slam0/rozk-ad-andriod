package com.nazar0vsky.rozklad;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Ustawienia: motyw, język, wyświetlanie, dane i informacje o aplikacji. */
final class SettingsScreen extends Screen {
    private LinearLayout root;
    private Widgets.NavBar nav;
    private Rows.Adapter adapter;

    SettingsScreen(MainActivity a) { super(a); }

    @Override String backTitle() { return Lang.t("Ustawienia"); }

    private Schedule sc() { return act.app.schedule; }

    @Override View build() {
        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        Skin.background(root, Skin.pageBackground());
        nav = new Widgets.NavBar(act);
        nav.setTitle(Lang.t("Ustawienia"));
        root.addView(nav);
        adapter = new Rows.Adapter(act);
        ListView list = Rows.list(act, adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rebuild();
        return root;
    }

    @Override void onShow() {
        super.onShow();
        rebuild();
    }

    @Override void refresh() { rebuild(); }

    private String version() {
        try {
            PackageInfo pi = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
            return pi.versionName != null ? pi.versionName : "?";
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private String dataSummary() {
        Schedule s = sc();
        if (s.datasets().isEmpty()) return Lang.t("brak wczytanego rozkładu");
        String when = "";
        long exp = s.expiryDate();
        if (exp > 0) {
            // expiryDate to początek następnego dnia, więc pokazujemy dzień wcześniej
            when = Lang.f(", ważny do %s", new SimpleDateFormat("d.MM.yyyy", java.util.Locale.US).format(new Date(exp - 3600L * 1000)));
        }
        return Lang.f("przystanków: %d%s", s.stops().size(), when);
    }

    private Rows.SettingRow row(String title, String detail) {
        Rows.SettingRow r = new Rows.SettingRow(Rows.T_SETTING) { };
        r.title = title;
        r.detail = detail;
        r.enabled = false;
        return r;
    }

    private void rebuild() {
        if (root == null) return;
        Skin.background(root, Skin.pageBackground());
        nav.setTitle(Lang.t("Ustawienia"));
        final Schedule s = sc();
        List<Rows.Row> rows = new ArrayList<Rows.Row>();

        // --- motyw
        rows.add(new Rows.Header(Lang.t("Motyw")));
        for (int i = 0; i < Skin.count(); i++) {
            final int idx = i;
            Rows.SettingRow r = new Rows.SettingRow(Rows.T_SETTING) {
                void click() {
                    act.app.setThemeIndex(idx);
                    act.rebuildKeepingTab();
                }
            };
            r.title = Skin.name(i);
            r.icon = Skin.swatch(i, Skin.dp(36));
            r.check = i == Skin.index();
            r.tall = 52;
            rows.add(r);
        }

        // --- język
        rows.add(new Rows.Header(Lang.t("Język")));
        String[] names = { Lang.t("Automatycznie"), "Polski", "English", "Русский" };
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Rows.SettingRow r = new Rows.SettingRow(Rows.T_SETTING) {
                void click() {
                    act.app.setLanguageChoice(idx);
                    act.rebuildKeepingTab();
                }
            };
            r.title = names[i];
            r.check = i == act.app.langChoice();
            rows.add(r);
        }

        // --- wyświetlanie
        rows.add(new Rows.Header(Lang.t("Wyświetlanie")));
        Rows.SettingRow lim = new Rows.SettingRow(Rows.T_SETTING) {
            void click() {
                int cur = s.departureLimit();
                s.setDepartureLimit(cur == 10 ? 20 : (cur == 20 ? 30 : 10));
                act.dataChanged();
            }
        };
        lim.title = Lang.t("Odjazdów na ekranie przystanku");
        lim.value = Integer.toString(s.departureLimit());
        rows.add(lim);
        rows.add(new Rows.Footer(Lang.t("Ulubione przystanki oznaczasz gwiazdką na liście lub na ekranie przystanku. Trafiają wtedy na górę.")));

        // --- dane
        rows.add(new Rows.Header(Lang.t("Dane")));
        Rows.SettingRow info = row(Lang.t("Wczytany rozkład"), dataSummary());
        rows.add(info);

        Rows.SettingRow site = new Rows.SettingRow(Rows.T_SETTING) {
            void click() { act.openSite(); }
        };
        site.title = Lang.t("Pobierz nowy rozkład ze strony");
        site.detail = Lang.t("otwiera stronę w przeglądarce");
        site.arrow = true;
        rows.add(site);

        final int expired = s.expiredFileCount(System.currentTimeMillis());
        Rows.SettingRow exp = new Rows.SettingRow(Rows.T_SETTING) {
            void click() {
                if (s.expiredFileCount(System.currentTimeMillis()) == 0) {
                    act.alert(Lang.t("Brak przeterminowanych rozkładów"), Lang.t("Wszystkie wczytane rozkłady są jeszcze ważne."));
                } else {
                    act.confirm(Lang.t("Usunąć przeterminowane rozkłady z telefonu?"), Lang.t("Usuń przeterminowane"), new Runnable() {
                        public void run() {
                            act.background(new Runnable() {
                                public void run() { s.removeExpiredFiles(System.currentTimeMillis()); }
                            });
                        }
                    });
                }
            }
        };
        exp.title = Lang.t("Usuń przeterminowane rozkłady");
        exp.detail = expired > 0 ? Lang.f("do usunięcia: %d", expired) : Lang.t("nie ma żadnych przeterminowanych");
        rows.add(exp);

        Rows.SettingRow all = new Rows.SettingRow(Rows.T_SETTING) {
            void click() {
                if (s.fileCount() == 0) {
                    act.alert(Lang.t("Brak plików"), Lang.t("Na telefonie nie ma żadnych plików z rozkładem."));
                } else {
                    act.confirm(Lang.t("Usunąć wszystkie rozkłady z telefonu? Nowy pobierzesz ze strony."), Lang.t("Usuń wszystko"), new Runnable() {
                        public void run() {
                            act.background(new Runnable() {
                                public void run() { s.removeAllFiles(); }
                            });
                        }
                    });
                }
            }
        };
        all.title = Lang.t("Usuń wszystkie rozkłady z telefonu");
        all.detail = Lang.f("plików na telefonie: %d", s.fileCount());
        all.accentTitle = true;
        rows.add(all);

        Rows.SettingRow favs = new Rows.SettingRow(Rows.T_SETTING) {
            void click() {
                if (s.favoriteCount() == 0) {
                    act.alert(Lang.t("Brak ulubionych"), Lang.t("Nie masz żadnych przystanków oznaczonych gwiazdką."));
                } else {
                    act.confirm(Lang.t("Usunąć wszystkie gwiazdki z przystanków?"), Lang.t("Wyczyść ulubione"), new Runnable() {
                        public void run() {
                            s.clearFavorites();
                            act.dataChanged();
                        }
                    });
                }
            }
        };
        favs.title = Lang.t("Wyczyść ulubione");
        favs.detail = Lang.f("gwiazdek: %d", s.favoriteCount());
        rows.add(favs);

        Rows.SettingRow rem = new Rows.SettingRow(Rows.T_SETTING) {
            void click() {
                if (Reminders.count(act.app.store) == 0) {
                    act.alert(Lang.t("Brak przypomnień"), Lang.t("Nie masz ustawionych żadnych przypomnień. Dotknij odjazdu na ekranie przystanku, aby je dodać."));
                } else {
                    act.confirm(Lang.t("Anulować wszystkie przypomnienia o odjazdach?"), Lang.t("Anuluj przypomnienia"), new Runnable() {
                        public void run() {
                            Reminders.cancelAll(act, act.app.store);
                            act.dataChanged();
                        }
                    });
                }
            }
        };
        rem.title = Lang.t("Anuluj wszystkie przypomnienia");
        rem.detail = Lang.f("ustawionych: %d", Reminders.count(act.app.store));
        rows.add(rem);

        // --- o aplikacji
        rows.add(new Rows.Header(Lang.t("O aplikacji")));
        rows.add(row("Rozkład", Lang.f("wersja %s", version())));
        rows.add(row(Lang.t("Autor"), "nazar0vsky-slam0"));
        rows.add(row(Lang.t("Strona z rozkładem"), Config.SITE_URL.startsWith("http") ? Config.SITE_URL : Lang.t("adres zapisze się po pierwszym pobraniu")));
        rows.add(row(Lang.t("Źródło danych"), Lang.t("ZTM Warszawa (GTFS), udostępnia mkuran.pl")));
        rows.add(new Rows.Space(16));
        adapter.set(rows);
    }
}
