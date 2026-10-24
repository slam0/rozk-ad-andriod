package com.nazar0vsky.rozklad;

import android.content.Context;
import android.content.SharedPreferences;

/** Ustawienia aplikacji zapisane w telefonie (SharedPreferences). */
final class Store implements Kv {
    private final SharedPreferences sp;

    Store(Context c) { sp = c.getSharedPreferences("rozklad", Context.MODE_PRIVATE); }

    public String get(String key) {
        try {
            return sp.getString(key, null);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public void put(String key, String value) { sp.edit().putString(key, value).apply(); }

    public void remove(String key) { sp.edit().remove(key).apply(); }

    int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key));
        } catch (RuntimeException e) {
            return def;
        }
    }

    void putInt(String key, int v) { put(key, Integer.toString(v)); }
}
