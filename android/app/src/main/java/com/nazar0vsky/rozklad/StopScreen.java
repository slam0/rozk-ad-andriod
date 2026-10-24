package com.nazar0vsky.rozklad;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Odjazdy z jednego przystanku: „bilet” z najbliższym odjazdem, lista kolejnych i przypomnienia. */
final class StopScreen extends Screen {
    private final String key;
    private final char kind;
    private String stopTitle;
    private final String parentTitle;
    private LinearLayout root;
    private Widgets.NavBar nav;
    private TextView starButton;
    private Rows.Adapter adapter;
    private ListView list;
    private List<Schedule.Departure> deps = new ArrayList<Schedule.Departure>();
    private int nowMinutes = -1;
    private boolean gone;
    private TextView ticketEta;

    StopScreen(MainActivity a, String key, char kind, String title, String parentTitle) {
        super(a);
        this.key = key;
        this.kind = kind;
        this.stopTitle = title;
        this.parentTitle = parentTitle;
    }

    @Override String backTitle() { return stopTitle; }

    private Schedule sc() { return act.app.schedule; }

    /** Szuka przystanku po kluczu w aktualnie wczytanym rozkładzie (obiekty zmieniają się przy każdym wczytaniu). */
    private Dataset.Stop currentStop() {
        for (Dataset.Stop s : sc().stopsOfKind(kind)) if (s.key.equals(key)) return s;
        return null;
    }

    @Override View build() {
        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        Skin.background(root, Skin.pageBackground());
        nav = new Widgets.NavBar(act);
        nav.setTitle(stopTitle);
        TextView back = Widgets.barButton(act, parentTitle, false, true);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { act.pop(); }
        });
        nav.setLeft(back);
        starButton = Widgets.barIconButton(act, Icons.star(false));
        starButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { starTapped(); }
        });
        nav.setRight(starButton);
        root.addView(nav);
        adapter = new Rows.Adapter(act);
        list = Rows.list(act, adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rebuild();
        return root;
    }

    @Override void onShow() {
        super.onShow();
        rebuild();
    }

    @Override void refresh() { rebuild(); }

    @Override boolean onBack() {
        return false;
    }

    private void updateStar() {
        Dataset.Stop s = currentStop();
        boolean fav = s != null && sc().isFavorite(s, kind);
        starButton.setCompoundDrawablesWithIntrinsicBounds(
            new android.graphics.drawable.BitmapDrawable(act.getResources(), Icons.star(fav)), null, null, null);
    }

    private void starTapped() {
        Dataset.Stop s = currentStop();
        if (s == null) return;
        sc().setFavorite(!sc().isFavorite(s, kind), s, kind);
        updateStar();
    }

    /** Co sekundę odświeżamy odliczanie na bilecie; listę liczymy od nowa, gdy zmieni się minuta. */
    @Override void tick() {
        long now = System.currentTimeMillis();
        TimeZone tz = TimeZone.getDefault();
        if (Schedule.minutesOfDay(now, tz) != nowMinutes) {
            rebuild();
            return;
        }
        if (!deps.isEmpty() && ticketEta != null) {
            ticketEta.setText(Core.countdown(deps.get(0).time * 60 - Schedule.secondsOfDay(now, tz)));
        }
    }

    private void rebuild() {
        if (root == null) return;
        Schedule s = sc();
        Dataset.Stop st = currentStop();
        long now = System.currentTimeMillis();
        TimeZone tz = TimeZone.getDefault();
        nowMinutes = Schedule.minutesOfDay(now, tz);
        gone = st == null;
        if (st != null) {
            stopTitle = st.title;
            deps = s.nextDepartures(st, kind, now, tz, s.departureLimit());
        } else {
            deps = new ArrayList<Schedule.Departure>();
        }
        nav.setTitle(stopTitle);
        Skin.background(root, Skin.pageBackground());
        updateStar();

        List<Rows.Row> rows = new ArrayList<Rows.Row>();
        rows.add(new Rows.Header(Lang.t("Najbliższy odjazd")));
        ticketEta = null;
        if (!deps.isEmpty()) {
            final Schedule.Departure d0 = deps.get(0);
            rows.add(new Rows.TicketRow(d0, stopTitle) {
                void etaView(TextView eta) {
                    ticketEta = eta;
                    eta.setText(Core.countdown(d0.time * 60 - Schedule.secondsOfDay(System.currentTimeMillis(), TimeZone.getDefault())));
                }

                void click() { remind(d0); }
            });
        } else {
            String hours = kind == 'p' ? Lang.t("24 godzinach") : Lang.t("12 godzinach");
            rows.add(new Rows.Msg(gone
                ? Lang.t("Tego przystanku nie ma w wczytanym rozkładzie. Wróć do listy i wybierz inny.")
                : Lang.f("Brak odjazdów w najbliższych %s.", hours)));
        }
        if (deps.size() > 1) {
            rows.add(new Rows.Header(Lang.t("Kolejne odjazdy")));
            for (int i = 1; i < deps.size(); i++) {
                final Schedule.Departure d = deps.get(i);
                rows.add(new Rows.DepRow(d, nowMinutes) {
                    void click() { remind(d); }
                });
            }
        }
        rows.add(new Rows.Footer(Lang.f("Dotknij odjazdu, aby ustawić przypomnienie.\nDane: %s", s.summary())));
        adapter.set(rows);
    }

    // ------------------------------------------------------------------ przypomnienia o odjeździe

    private void remind(final Schedule.Departure d) {
        String title = Lang.f("Przypomnienie: %s → %s, %s", d.line, d.head, Core.clock(d.time));
        act.sheet(title, new String[] {
            Lang.t("5 minut przed odjazdem"), Lang.t("10 minut przed odjazdem"), Lang.t("15 minut przed odjazdem") },
            new MainActivity.Pick() {
                public void picked(int i) { setReminder(d, i == 0 ? 5 : (i == 1 ? 10 : 15)); }
            });
    }

    private void setReminder(final Schedule.Departure d, final int before) {
        // odjazd to minuty od północy dzisiaj (po północy może być ponad 1440)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.MINUTE, d.time - before);
        final long fire = cal.getTimeInMillis();
        if (fire - System.currentTimeMillis() < 2000) {
            act.alert(Lang.t("Za późno"), Lang.t("Do odjazdu zostało mniej czasu niż wybrane przypomnienie. Wybierz krótszy czas albo późniejszy odjazd."));
            return;
        }
        final String text = Lang.f("%s → %s odjeżdża o %s z przystanku %s (za %d min)", d.line, d.head, Core.clock(d.time), stopTitle, before);
        act.withNotifications(new Runnable() {
            public void run() {
                Reminders.schedule(act, act.app.store, fire, text);
                act.alert(Lang.t("Przypomnienie ustawione"), Lang.f("Telefon zadzwoni o %s, %d min przed odjazdem.", Core.clock(d.time - before), before));
            }
        });
    }
}
