package com.nazar0vsky.rozklad;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Odbiera budzik z przypomnieniem (i ustawia budziki od nowa po restarcie telefonu). */
public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        App app = App.app;
        if (app == null) return;
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a)) {
            Reminders.rearm(c, app.store);
            return;
        }
        String text = i.getStringExtra("text");
        if (text != null) Reminders.show(c, i.getIntExtra("id", 1), text);
    }
}
