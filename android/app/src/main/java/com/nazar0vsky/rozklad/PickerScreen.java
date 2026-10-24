package com.nazar0vsky.rozklad;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Okno wyboru przystanków do pokazania (osobno dla każdego rodzaju). */
final class PickerScreen extends Screen {
    private final char kind;
    private final Runnable done;
    private List<Dataset.Stop> stops;
    private List<String> details;
    private final Set<String> chosen = new HashSet<String>();
    private LinearLayout root;
    private Rows.Adapter adapter;
    private TextView toggle;

    PickerScreen(MainActivity a, char kind, Runnable done) {
        super(a);
        this.kind = kind;
        this.done = done;
        Schedule sc = a.app.schedule;
        stops = sc.sortedStopsOfKind(kind);
        details = new ArrayList<String>();
        for (Dataset.Stop s : stops) details.add(sc.descriptionForStop(s, kind));
        for (Dataset.Stop s : sc.selectedStopsOfKind(kind)) chosen.add(s.key);
    }

    @Override View build() {
        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        Skin.background(root, Skin.pageBackground());
        Widgets.NavBar nav = new Widgets.NavBar(act);
        nav.setTitle(Lang.t("Przystanki"));
        toggle = Widgets.barButton(act, "", false, false);
        toggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { toggleAll(); }
        });
        if (!stops.isEmpty()) nav.setLeft(toggle);
        TextView ok = Widgets.barButton(act, Lang.t("Gotowe"), true, false);
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { doneTapped(); }
        });
        nav.setRight(ok);
        root.addView(nav);
        adapter = new Rows.Adapter(act);
        ListView list = Rows.list(act, adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        reload();
        return root;
    }

    private boolean allChosen() { return !stops.isEmpty() && chosen.size() >= stops.size(); }

    private void toggleAll() {
        boolean clear = allChosen();
        chosen.clear();
        if (!clear) for (Dataset.Stop s : stops) chosen.add(s.key);
        reload();
    }

    private void doneTapped() {
        List<Dataset.Stop> picked = new ArrayList<Dataset.Stop>();
        for (Dataset.Stop s : stops) if (chosen.contains(s.key)) picked.add(s);
        act.app.schedule.setSelectedStops(picked, kind);
        act.closeModal(this);
        if (done != null) done.run();
    }

    private void reload() {
        toggle.setText(allChosen() ? Lang.t("Żadne") : Lang.t("Wszystkie"));
        List<Rows.Row> rows = new ArrayList<Rows.Row>();
        rows.add(new Rows.Header(stops.isEmpty() ? Lang.t("Brak przystanków") : Lang.t("Zaznacz przystanki, które chcesz widzieć:")));
        if (stops.isEmpty()) {
            rows.add(new Rows.Msg(Lang.t("W wczytanym rozkładzie nie ma tego rodzaju przystanków.")));
        } else {
            for (int i = 0; i < stops.size(); i++) {
                final Dataset.Stop s = stops.get(i);
                Rows.SettingRow r = new Rows.SettingRow(Rows.T_PICK) {
                    void click() {
                        if (chosen.contains(s.key)) chosen.remove(s.key); else chosen.add(s.key);
                        reload();
                    }
                };
                r.title = s.title;
                r.detail = details.get(i).length() > 0 ? details.get(i) : null;
                r.check = chosen.contains(s.key);
                r.tall = 56;
                rows.add(r);
            }
        }
        rows.add(new Rows.Space(16));
        // zachowanie pozycji przewijania: adapter odświeża się bez ustawiania nowej listy
        adapter.set(rows);
    }
}
