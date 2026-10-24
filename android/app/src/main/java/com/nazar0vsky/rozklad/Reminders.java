package com.nazar0vsky.rozklad;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/** Przypomnienia o odjazdach: budziki systemowe + powiadomienia. */
final class Reminders {
    private Reminders() {}

    static final String CHANNEL = "rozklad_przypomnienia";
    static final String KEY = "przypomnienia";

    /** Jedno przypomnienie: numer, czas (ms) i tekst. */
    static final class Item {
        int id;
        long fire;
        String text;
    }

    static List<Item> load(Store s) {
        List<Item> r = new ArrayList<Item>();
        String v = s.get(KEY);
        if (v == null) return r;
        try {
            List<Object> a = Json.arr(Json.parse(v));
            if (a == null) return r;
            for (Object o : a) {
                List<Object> e = Json.arr(o);
                if (e == null || e.size() < 3) continue;
                Item it = new Item();
                it.id = Json.intOf(e.get(0));
                it.fire = Long.parseLong(Json.str(e.get(1)));
                it.text = Json.str(e.get(2));
                r.add(it);
            }
        } catch (Json.ParseException e) {
            // uszkodzony zapis: zaczynamy od pustej listy
        } catch (NumberFormatException e) {
            // j.w.
        }
        return r;
    }

    static void save(Store s, List<Item> items) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (i > 0) b.append(',');
            b.append('[').append(it.id).append(',').append(Json.quote(Long.toString(it.fire))).append(',').append(Json.quote(it.text)).append(']');
        }
        b.append(']');
        s.put(KEY, b.toString());
    }

    private static PendingIntent pending(Context c, int id, String text, int flags) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction("com.nazar0vsky.rozklad.PRZYPOMNIENIE");
        i.putExtra("id", id);
        i.putExtra("text", text);
        int f = flags;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, id, i, f);
    }

    private static void arm(Context c, Item it) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(c, it.id, it.text, PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.fire, pi);
        } else if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.fire, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, it.fire, pi);
        }
    }

    /** Ustawia przypomnienie na podany czas. */
    static void schedule(Context c, Store s, long fire, String text) {
        List<Item> items = prune(load(s));
        int id = (int) (System.currentTimeMillis() % 1000000000L);
        for (Item x : items) if (x.id == id) id++;
        Item it = new Item();
        it.id = id;
        it.fire = fire;
        it.text = text;
        items.add(it);
        save(s, items);
        arm(c, it);
    }

    private static List<Item> prune(List<Item> items) {
        long now = System.currentTimeMillis();
        List<Item> r = new ArrayList<Item>();
        for (Item x : items) if (x.fire > now) r.add(x);
        return r;
    }

    static int count(Store s) {
        List<Item> all = load(s);
        List<Item> live = prune(all);
        if (live.size() != all.size()) save(s, live);
        return live.size();
    }

    static void cancelAll(Context c, Store s) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        for (Item it : load(s)) {
            if (am != null) am.cancel(pending(c, it.id, it.text, PendingIntent.FLAG_UPDATE_CURRENT));
        }
        s.remove(KEY);
    }

    /** Po ponownym uruchomieniu telefonu budziki znikają: ustawiamy je od nowa. */
    static void rearm(Context c, Store s) {
        List<Item> live = prune(load(s));
        save(s, live);
        for (Item it : live) arm(c, it);
    }

    static void show(Context c, int id, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, Lang.t("Przypomnienia o odjazdach"), NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription(Lang.t("Powiadomienia o odjazdach autobusów, tramwajów, pociągów i metra"));
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat);
        b.setContentTitle(Lang.t("Przypomnienie o odjeździe"));
        b.setContentText(text);
        b.setStyle(new Notification.BigTextStyle().bigText(text));
        b.setAutoCancel(true);
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        b.setContentIntent(PendingIntent.getActivity(c, 0, open, f));
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_HIGH);
            b.setDefaults(Notification.DEFAULT_ALL);
        }
        nm.notify(id, b.build());
    }
}
