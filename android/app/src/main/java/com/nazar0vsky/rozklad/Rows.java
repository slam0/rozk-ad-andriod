package com.nazar0vsky.rozklad;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Wiersze list w stylu iOS 6 (grupy z zaokrąglonymi rogami, nagłówki i stopki sekcji). */
final class Rows {
    private Rows() {}

    static final int T_HEADER = 0, T_FOOTER = 1, T_SPACE = 2, T_MSG = 3, T_STOP = 4, T_DEP = 5,
        T_TICKET = 6, T_SETTING = 7, T_PICK = 8, T_NOTE = 9, TYPES = 10;

    abstract static class Row {
        final int type;
        final boolean cell;      // wiersz w grupie (z tłem), a nie nagłówek/stopka
        int pos = Skin.POS_ONLY;

        Row(int type, boolean cell) {
            this.type = type;
            this.cell = cell;
        }

        abstract View create(Context c);

        abstract void bind(View v);

        boolean clickable() { return false; }

        void click() { }
    }

    /** Wiersz w grupie: ramka z tłem zależnym od położenia w grupie. */
    static final class Cell extends FrameLayout {
        private int pos = -1;
        private int themeIndex = -1;
        private boolean ticket;

        Cell(Context c, boolean ticket) {
            super(c);
            this.ticket = ticket;
            setPos(Skin.POS_ONLY);
        }

        void setPos(int p) {
            if (p == pos && themeIndex == Skin.index()) return;
            pos = p;
            themeIndex = Skin.index();
            Skin.background(this, ticket ? Skin.ticketBackground() : Skin.groupBackground(p));
        }
    }

    static void assignPositions(List<Row> rows) {
        int n = rows.size();
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            if (!r.cell) continue;
            boolean prev = i > 0 && rows.get(i - 1).cell && rows.get(i - 1).type != T_TICKET && r.type != T_TICKET;
            boolean next = i + 1 < n && rows.get(i + 1).cell && rows.get(i + 1).type != T_TICKET && r.type != T_TICKET;
            if (prev && next) r.pos = Skin.POS_MID;
            else if (prev) r.pos = Skin.POS_LAST;
            else if (next) r.pos = Skin.POS_FIRST;
            else r.pos = Skin.POS_ONLY;
        }
    }

    static final class Adapter extends BaseAdapter {
        private final Context ctx;
        private List<Row> rows = new ArrayList<Row>();

        Adapter(Context c) { ctx = c; }

        void set(List<Row> r) {
            assignPositions(r);
            rows = r;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return TYPES; }
        @Override public int getItemViewType(int i) { return rows.get(i).type; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int i) { return rows.get(i).clickable(); }

        @Override public View getView(int i, View convert, ViewGroup parent) {
            Row r = rows.get(i);
            View v = convert != null ? convert : r.create(ctx);
            r.bind(v);
            if (v instanceof Cell) ((Cell) v).setPos(r.pos);
            return v;
        }
    }

    /** Lista bez linii i podświetlenia systemowego (tło rysują same wiersze). */
    static ListView list(Context c, final Adapter a) {
        ListView lv = new ListView(c);
        lv.setAdapter(a);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        lv.setCacheColorHint(Color.TRANSPARENT);
        lv.setHeaderDividersEnabled(false);
        lv.setFooterDividersEnabled(false);
        lv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int position, long id) {
                int i = position - ((ListView) p).getHeaderViewsCount();
                if (i >= 0 && i < a.getCount()) {
                    Row r = (Row) a.getItem(i);
                    if (r.clickable()) r.click();
                }
            }
        });
        return lv;
    }

    // ------------------------------------------------------------------ nagłówek, stopka, odstęp

    static final class Header extends Row {
        final String text;

        Header(String text) { super(T_HEADER, false); this.text = text; }

        View create(Context c) {
            TextView t = new TextView(c);
            Skin.style(t, 17, Skin.ink(), true);
            t.setPadding(Skin.dp(20), Skin.dp(10), Skin.dp(20), Skin.dp(3));
            t.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return t;
        }

        void bind(View v) {
            TextView t = (TextView) v;
            t.setText(text);
            t.setTextColor(Skin.ink());
            Skin.shadow(t, Skin.headerShadow(), 1);
        }
    }

    static final class Footer extends Row {
        final String text;

        Footer(String text) { super(T_FOOTER, false); this.text = text; }

        View create(Context c) {
            TextView t = new TextView(c);
            Skin.style(t, 12, Skin.t().footer, false);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.setPadding(Skin.dp(20), Skin.dp(8), Skin.dp(20), Skin.dp(14));
            t.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return t;
        }

        void bind(View v) {
            TextView t = (TextView) v;
            t.setText(text);
            t.setTextColor(Skin.t().footer);
            Skin.shadow(t, Skin.headerShadow(), 1);
        }
    }

    static final class Space extends Row {
        final int dp;

        Space(int dp) { super(T_SPACE, false); this.dp = dp; }

        View create(Context c) {
            View v = new View(c);
            v.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(dp)));
            return v;
        }

        void bind(View v) { }
    }

    // ------------------------------------------------------------------ komunikat w komórce

    static final class Msg extends Row {
        final String text;

        Msg(String text) { super(T_MSG, true); this.text = text; }

        View create(Context c) {
            Cell cell = new Cell(c, false);
            TextView t = new TextView(c);
            Skin.style(t, 15, Skin.cellText(), false);
            t.setPadding(Skin.dp(24), Skin.dp(14), Skin.dp(24), Skin.dp(14));
            cell.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return cell;
        }

        void bind(View v) {
            TextView t = (TextView) ((Cell) v).getChildAt(0);
            t.setText(text);
            t.setTextColor(Skin.cellText());
        }
    }

    // ------------------------------------------------------------------ przystanek na liście

    abstract static class StopRow extends Row {
        final String title;
        final Schedule.Departure dep;
        final int now;
        final boolean fav;

        StopRow(String title, Schedule.Departure dep, int now, boolean fav) {
            super(T_STOP, true);
            this.title = title;
            this.dep = dep;
            this.now = now;
            this.fav = fav;
        }

        abstract void star();

        boolean clickable() { return true; }

        View create(Context c) {
            Cell cell = new Cell(c, false);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setTag("row");
            Widgets.Badge badge = new Widgets.Badge(c, 16);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Skin.dp(54), Skin.dp(28));
            bl.leftMargin = Skin.dp(14);
            row.addView(badge, bl);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(c);
            Skin.style(title, 17, Skin.cellText(), true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            TextView sub = new TextView(c);
            Skin.style(sub, 13, Skin.subtle(), false);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(title);
            col.addView(sub);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cl.leftMargin = Skin.dp(10);
            row.addView(col, cl);
            ImageView star = new ImageView(c);
            star.setScaleType(ImageView.ScaleType.CENTER);
            star.setClickable(true);
            row.addView(star, new LinearLayout.LayoutParams(Skin.dp(50), Skin.dp(58)));
            cell.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(58)));
            cell.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(58)));
            return cell;
        }

        void bind(View v) {
            LinearLayout row = (LinearLayout) ((Cell) v).getChildAt(0);
            Widgets.Badge badge = (Widgets.Badge) row.getChildAt(0);
            LinearLayout col = (LinearLayout) row.getChildAt(1);
            TextView t = (TextView) col.getChildAt(0);
            TextView sub = (TextView) col.getChildAt(1);
            ImageView star = (ImageView) row.getChildAt(2);
            t.setText(title);
            t.setTextColor(Skin.cellText());
            if (dep != null) {
                badge.setVisibility(View.VISIBLE);
                badge.set(dep.line, dep.kind);
                int left = dep.time - now;
                sub.setText(dep.head + " · " + Core.eta(left));
                sub.setTextColor(left <= 5 ? Skin.accent() : Skin.subtle());
                ((LinearLayout.LayoutParams) col.getLayoutParams()).leftMargin = Skin.dp(10);
            } else {
                badge.setVisibility(View.GONE);
                sub.setText(Lang.t("brak odjazdów w najbliższym czasie"));
                sub.setTextColor(Skin.subtle());
                ((LinearLayout.LayoutParams) col.getLayoutParams()).leftMargin = Skin.dp(14);
            }
            star.setImageBitmap(Icons.star(fav));
            star.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) { star(); }
            });
        }
    }

    // ------------------------------------------------------------------ odjazd na ekranie przystanku

    abstract static class DepRow extends Row {
        final Schedule.Departure dep;
        final int now;

        DepRow(Schedule.Departure dep, int now) {
            super(T_DEP, true);
            this.dep = dep;
            this.now = now;
        }

        boolean clickable() { return true; }

        View create(Context c) {
            Cell cell = new Cell(c, false);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Widgets.Badge badge = new Widgets.Badge(c, 16);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Skin.dp(58), Skin.dp(28));
            bl.leftMargin = Skin.dp(14);
            row.addView(badge, bl);
            TextView head = new TextView(c);
            Skin.style(head, 15, Skin.cellText(), true);
            head.setMaxLines(2);
            head.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            hl.leftMargin = Skin.dp(10);
            row.addView(head, hl);
            LinearLayout right = new LinearLayout(c);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.RIGHT);
            TextView time = new TextView(c);
            Skin.style(time, 17, Skin.cellText(), true);
            time.setGravity(Gravity.RIGHT);
            TextView eta = new TextView(c);
            Skin.style(eta, 12, Skin.subtle(), false);
            eta.setGravity(Gravity.RIGHT);
            eta.setSingleLine(true);
            right.addView(time);
            right.addView(eta);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.leftMargin = Skin.dp(8);
            rl.rightMargin = Skin.dp(14);
            row.addView(right, rl);
            cell.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(56)));
            cell.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(56)));
            return cell;
        }

        void bind(View v) {
            LinearLayout row = (LinearLayout) ((Cell) v).getChildAt(0);
            Widgets.Badge badge = (Widgets.Badge) row.getChildAt(0);
            TextView head = (TextView) row.getChildAt(1);
            LinearLayout right = (LinearLayout) row.getChildAt(2);
            TextView time = (TextView) right.getChildAt(0);
            TextView eta = (TextView) right.getChildAt(1);
            badge.set(dep.line, dep.kind);
            head.setText(dep.head);
            head.setTextColor(Skin.cellText());
            Skin.shadow(head, Skin.textShadow(), 1);
            time.setText(Core.clock(dep.time));
            time.setTextColor(Skin.cellText());
            int left = dep.time - now;
            eta.setText(Core.eta(left));
            if (left <= 5) {
                eta.setTextColor(Skin.accent());
                eta.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            } else {
                eta.setTextColor(Skin.subtle());
                eta.setTypeface(android.graphics.Typeface.DEFAULT);
            }
        }
    }

    // ------------------------------------------------------------------ "bilet" z najbliższym odjazdem

    abstract static class TicketRow extends Row {
        final Schedule.Departure dep;
        final String stopName;

        TicketRow(Schedule.Departure dep, String stopName) {
            super(T_TICKET, true);
            this.dep = dep;
            this.stopName = stopName;
        }

        boolean clickable() { return true; }

        /** Wywoływane po przypisaniu napisu odliczania, żeby ekran mógł go odświeżać co sekundę. */
        abstract void etaView(TextView eta);

        View create(Context c) {
            Cell cell = new Cell(c, true);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView caption = new TextView(c);
            Skin.style(caption, 13, Skin.t().ticketCaption, false);
            caption.setSingleLine(true);
            caption.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams cap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cap.setMargins(Skin.dp(24), Skin.dp(8), Skin.dp(24), 0);
            col.addView(caption, cap);

            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.HORIZONTAL);
            mid.setGravity(Gravity.CENTER_VERTICAL);
            Widgets.Badge badge = new Widgets.Badge(c, 26);
            mid.addView(badge, new LinearLayout.LayoutParams(Skin.dp(76), Skin.dp(44)));
            TextView head = new TextView(c);
            Skin.style(head, 20, Skin.cellText(), true);
            head.setMaxLines(2);
            head.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            hl.leftMargin = Skin.dp(10);
            mid.addView(head, hl);
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(48));
            ml.setMargins(Skin.dp(24), Skin.dp(4), Skin.dp(24), 0);
            col.addView(mid, ml);

            Widgets.Tear tear = new Widgets.Tear(c);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(6));
            tl.setMargins(Skin.dp(16), Skin.dp(2), Skin.dp(16), 0);
            col.addView(tear, tl);

            LinearLayout bot = new LinearLayout(c);
            bot.setOrientation(LinearLayout.HORIZONTAL);
            bot.setGravity(Gravity.CENTER_VERTICAL);
            TextView eta = new TextView(c);
            Skin.style(eta, 24, Skin.accent(), true);
            eta.setSingleLine(true);
            eta.setEllipsize(TextUtils.TruncateAt.END);
            bot.addView(eta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView clock = new TextView(c);
            Skin.style(clock, 15, Skin.t().ticketCaption, false);
            clock.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            kl.leftMargin = Skin.dp(8);
            bot.addView(clock, kl);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(34));
            bl.setMargins(Skin.dp(24), 0, Skin.dp(24), Skin.dp(2));
            col.addView(bot, bl);
            cell.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return cell;
        }

        void bind(View v) {
            LinearLayout col = (LinearLayout) ((Cell) v).getChildAt(0);
            TextView caption = (TextView) col.getChildAt(0);
            LinearLayout mid = (LinearLayout) col.getChildAt(1);
            Widgets.Badge badge = (Widgets.Badge) mid.getChildAt(0);
            TextView head = (TextView) mid.getChildAt(1);
            LinearLayout bot = (LinearLayout) col.getChildAt(3);
            TextView eta = (TextView) bot.getChildAt(0);
            TextView clock = (TextView) bot.getChildAt(1);
            Skin.Def t = Skin.t();
            caption.setText(Lang.f("Najbliższy odjazd · %s", stopName));
            caption.setTextColor(t.ticketCaption);
            Skin.shadow(caption, Skin.textShadow(), 1);
            badge.set(dep.line, dep.kind);
            head.setText(dep.head);
            head.setTextColor(Skin.cellText());
            Skin.shadow(head, Skin.textShadow(), 1);
            eta.setTextColor(Skin.accent());
            Skin.shadow(eta, Skin.textShadow(), 1);
            clock.setText("o " + Core.clock(dep.time));
            clock.setTextColor(t.ticketCaption);
            etaView(eta);
        }
    }

    // ------------------------------------------------------------------ ustawienia i wybór przystanków

    abstract static class SettingRow extends Row {
        String title = "";
        String detail;        // druga linia (mniejsza)
        String value;         // wartość po prawej
        boolean check;
        boolean arrow;
        boolean accentTitle;
        Bitmap icon;
        boolean enabled = true;
        int tall = 46;

        SettingRow(int type) { super(type, true); }

        boolean clickable() { return enabled; }

        View create(Context c) {
            Cell cell = new Cell(c, false);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView ic = new ImageView(c);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Skin.dp(36), Skin.dp(36));
            il.leftMargin = Skin.dp(14);
            row.addView(ic, il);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t = new TextView(c);
            Skin.style(t, 16, Skin.cellText(), true);
            TextView d = new TextView(c);
            Skin.style(d, 12, Skin.subtle(), false);
            d.setSingleLine(true);
            d.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(t);
            col.addView(d);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cl.leftMargin = Skin.dp(14);
            row.addView(col, cl);
            TextView val = new TextView(c);
            Skin.style(val, 16, Skin.t().darkShadow ? Skin.subtle() : Color.rgb(56, 84, 135), true);
            LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vl.leftMargin = Skin.dp(8);
            row.addView(val, vl);
            TextView chk = new TextView(c);
            Skin.style(chk, 20, Color.rgb(56, 84, 135), true);
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            kl.leftMargin = Skin.dp(8);
            kl.rightMargin = Skin.dp(16);
            row.addView(chk, kl);
            cell.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            cell.setMinimumHeight(Skin.dp(tall));
            cell.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return cell;
        }

        void bind(View v) {
            Cell cell = (Cell) v;
            cell.setMinimumHeight(Skin.dp(tall));
            LinearLayout row = (LinearLayout) cell.getChildAt(0);
            ImageView ic = (ImageView) row.getChildAt(0);
            LinearLayout col = (LinearLayout) row.getChildAt(1);
            TextView t = (TextView) col.getChildAt(0);
            TextView d = (TextView) col.getChildAt(1);
            TextView val = (TextView) row.getChildAt(2);
            TextView chk = (TextView) row.getChildAt(3);
            if (icon != null) {
                ic.setVisibility(View.VISIBLE);
                ic.setImageBitmap(icon);
            } else {
                ic.setVisibility(View.GONE);
            }
            ((LinearLayout.LayoutParams) col.getLayoutParams()).leftMargin = Skin.dp(icon != null ? 12 : 14);
            t.setText(title);
            t.setTextColor(accentTitle ? Skin.accent() : Skin.cellText());
            Skin.shadow(t, Skin.textShadow(), 1);
            if (detail != null) {
                d.setVisibility(View.VISIBLE);
                d.setText(detail);
                d.setTextColor(Skin.subtle());
            } else {
                d.setVisibility(View.GONE);
            }
            if (value != null) {
                val.setVisibility(View.VISIBLE);
                val.setText(value);
            } else {
                val.setVisibility(View.GONE);
            }
            if (check || arrow) {
                chk.setVisibility(View.VISIBLE);
                chk.setText(check ? "✓" : "›");
                chk.setTextColor(check ? Color.rgb(56, 84, 135) : Skin.subtle());
                if (Skin.t().darkShadow && check) chk.setTextColor(Color.rgb(140, 180, 255));
            } else {
                chk.setVisibility(View.GONE);
            }
            if (!check && !arrow && value == null) {
                // brak elementów po prawej: prawy margines zapewnia kolumna
                ((LinearLayout.LayoutParams) col.getLayoutParams()).rightMargin = Skin.dp(14);
            } else {
                ((LinearLayout.LayoutParams) col.getLayoutParams()).rightMargin = 0;
            }
        }
    }

}
