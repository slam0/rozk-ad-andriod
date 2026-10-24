package com.nazar0vsky.rozklad;

import android.app.Application;

import java.io.File;
import java.util.Locale;

/** Aplikacja: wspólne ustawienia i wczytany rozkład. */
public class App extends Application {
    static App app;
    Store store;
    Schedule schedule;
    boolean loaded;

    @Override public void onCreate() {
        super.onCreate();
        app = this;
        Skin.init(this, getResources().getDisplayMetrics().density);
        store = new Store(this);
        schedule = new Schedule(new File(getFilesDir(), "Rozklady"), store);
        Skin.setIndex(store.getInt("motyw", 0));
        applyLanguage();
    }

    /** 0 automatycznie, 1 polski, 2 angielski, 3 rosyjski */
    int langChoice() {
        int c = store.getInt("jezyk", 0);
        return (c >= 0 && c <= 3) ? c : 0;
    }

    void applyLanguage() {
        int c = langChoice();
        if (c >= 1) Lang.setCurrent(c - 1);
        else Lang.setCurrent(Lang.fromLocale(Locale.getDefault().getLanguage()));
    }

    void setLanguageChoice(int c) {
        store.putInt("jezyk", c);
        applyLanguage();
    }

    void setThemeIndex(int i) {
        Skin.setIndex(i);
        store.putInt("motyw", Skin.index());
    }
}
