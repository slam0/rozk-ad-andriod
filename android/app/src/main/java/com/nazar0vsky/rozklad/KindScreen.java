package com.nazar0vsky.rozklad;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Lista przystanków jednego rodzaju (autobus, tramwaj, pociąg, metro). */
final class KindScreen extends Screen {
    private final char kind;
    private final String title;
    private LinearLayout root;
    private Widgets.NavBar nav;
    private ListView list;
    private Rows.Adapter adapter;
    private Widgets.Marquee marquee;
    private TextView pickButton;

    private List<Dataset.Stop> fav = new ArrayList<Dataset.Stop>();
    private List<Dataset.Stop> rest = new ArrayList<Dataset.Stop>();
    private Map<String, Schedule.Departure> next = new HashMap<String, Schedule.Departure>();
    private int nowMinutes = -1;
    private int allCount;
    private boolean prompted;

    KindScreen(MainActivity a, char kind, String title) {
        super(a);
        this.kind = kind;
        this.title = title;
    }

    @Override String backTitle() { return title; }

    private Schedule sc() { return act.app.schedule; }

    @Override View build() {
        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        Skin.background(root, Skin.pageBackground());

        nav = new Widgets.NavBar(act);
        nav.setTitle(title);
        pickButton = Widgets.barButton(act, Lang.t("Przystanki"), false, false);
        pickButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openPicker(); }
        });
        nav.setLeft(pickButton);
        TextView load = Widgets.barButton(act, Lang.t("Wczytaj"), false, false);
        load.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { loadTapped(); }
        });
        nav.setRight(load);
        root.addView(nav);

        adapter = new Rows.Adapter(act);
        list = Rows.list(act, adapter);
        marquee = new Widgets.Marquee(act);
        list.addHeaderView(marquee, null, false);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rebuild();
        return root;
    }

    @Override void onShow() {
        super.onShow();
        rebuild();
        marquee.restart();
        promptIfNeeded();
    }

    @Override void refresh() {
        prompted = false;
        rebuild();
    }

    @Override void tick() {
        if (Schedule.minutesOfDay(System.currentTimeMillis(), TimeZone.getDefault()) != nowMinutes) rebuild();
    }

    // ------------------------------------------------------------------ wczytywanie rozkładu

    private void loadTapped() {
        act.sheet(null, new String[] {
            Lang.t("Wybierz przystanki na stronie"),
            Lang.t("Wybierz plik z telefonu"),
            Lang.t("Odśwież z plików w telefonie") }, new MainActivity.Pick() {
            public void picked(int i) {
                if (i == 0) act.openSite();
                else if (i == 1) act.pickFile();
                else {
                    act.reloadAsync(null);
                    marquee.restart();
                }
            }
        });
    }

    // ------------------------------------------------------------------ wybór przystanków

    private void openPicker() {
        if (act.hasModal()) return;
        act.showModal(new PickerScreen(act, kind, new Runnable() {
            public void run() { act.dataChanged(); }
        }));
    }

    /** Gdy w pliku są przystanki tego rodzaju, a nic jeszcze nie wybrano, okno wyboru wyskakuje samo. */
    private void promptIfNeeded() {
        if (prompted || act.hasModal() || act.visible() != this) return;
        if (sc().needsSelectionForKind(kind)) {
            prompted = true;
            openPicker();
        }
    }

    // ------------------------------------------------------------------ dane

    private void rebuild() {
        if (root == null) return;
        Schedule s = sc();
        long now = System.currentTimeMillis();
        TimeZone tz = TimeZone.getDefault();
        allCount = s.stopsOfKind(kind).size();
        fav = new ArrayList<Dataset.Stop>();
        rest = new ArrayList<Dataset.Stop>();
        for (Dataset.Stop st : s.selectedStopsOfKind(kind)) {
            if (s.isFavorite(st, kind)) fav.add(st); else rest.add(st);
        }
        pickButton.setVisibility(allCount > 0 ? View.VISIBLE : View.GONE);
        nowMinutes = Schedule.minutesOfDay(now, tz);
        next = new HashMap<String, Schedule.Departure>();
        for (List<Dataset.Stop> group : java.util.Arrays.asList(fav, rest)) {
            for (Dataset.Stop st : group) {
                List<Schedule.Departure> deps = s.nextDepartures(st, kind, now, tz, 1);
                if (!deps.isEmpty()) next.put(st.key, deps.get(0));
            }
        }
        Skin.background(root, Skin.pageBackground());
        List<Rows.Row> rows = new ArrayList<Rows.Row>();
        if (fav.isEmpty() && rest.isEmpty()) {
            rows.add(new Rows.Space(6));
            rows.add(new Rows.Msg(emptyMessage()));
        } else {
            if (!fav.isEmpty()) {
                rows.add(new Rows.Header(Lang.t("Ulubione")));
                for (Dataset.Stop st : fav) rows.add(stopRow(st));
            }
            if (!rest.isEmpty()) {
                rows.add(new Rows.Header(fav.isEmpty() ? Lang.t("Przystanki") : Lang.t("Pozostałe przystanki")));
                for (Dataset.Stop st : rest) rows.add(stopRow(st));
            }
            rows.add(new Rows.Footer(Lang.f("Dane: %s", s.summary())));
        }
        adapter.set(rows);
        updateMarquee();
    }

    private Rows.Row stopRow(final Dataset.Stop st) {
        Schedule s = sc();
        return new Rows.StopRow(st.title, next.get(st.key), nowMinutes, s.isFavorite(st, kind)) {
            void star() {
                Schedule sch = sc();
                sch.setFavorite(!sch.isFavorite(st, kind), st, kind);
                rebuild();
            }

            void click() { act.push(new StopScreen(act, st.key, kind, st.title, title)); }
        };
    }

    private void updateMarquee() {
        Schedule s = sc();
        String text;
        boolean warning = false;
        if (s.datasets().isEmpty()) {
            text = Lang.t("UWAGA! Brak rozkładu. Dotknij „Wczytaj” i wybierz przystanki na stronie z rozkładem.");
            warning = true;
        } else {
            long expiry = s.expiryDate();
            if (expiry <= 0) {
                text = Lang.t("UWAGA! Nie znam daty ważności rozkładu. Jeśli odjazdy wyglądają na nieaktualne, odśwież rozkład.");
            } else {
                long left = (expiry - System.currentTimeMillis()) / 1000;
                if (left <= 0) {
                    text = Lang.t("UWAGA! Rozkład przeterminował się. Prosimy o odświeżenie rozkładu!");
                    warning = true;
                } else {
                    text = Lang.f("UWAGA! Rozkład przeterminuje się za: %s. Po tym czasie prosimy o odświeżenie rozkładu!", Core.duration(left));
                    warning = left < 7L * 24 * 3600;
                }
            }
        }
        marquee.setText(text, warning);
    }

    private String kindWord() {
        switch (kind) {
            case 't': return Lang.t("tramwajowych");
            case 'p': return Lang.t("kolejowych");
            case 'm': return Lang.t("stacji metra");
            default: return Lang.t("autobusowych");
        }
    }

    private String emptyMessage() {
        Schedule s = sc();
        if (s.datasets().isEmpty()) {
            String extra = s.loadError() != null ? "\n\n" + s.loadError() : "";
            return Lang.f("Brak rozkładu.\n\nDotknij „Wczytaj” u góry i wybierz „Wybierz przystanki na stronie”. Po wybraniu przystanków dotknij tam „Otwórz w aplikacji Rozkład”.\n\nMożesz też wskazać plik rozklad.json zapisany w telefonie („Wybierz plik z telefonu”).%s", extra);
        }
        if (allCount > 0) {
            return Lang.t("Nie wybrano żadnych przystanków.\n\nDotknij „Przystanki” w lewym górnym rogu i zaznacz te, które chcesz widzieć.");
        }
        return Lang.f("W wczytanym rozkładzie nie ma przystanków %s.\n\nWygeneruj rozkład z wybranymi przystankami tego rodzaju i skopiuj go na telefon.", kindWord());
    }
}
