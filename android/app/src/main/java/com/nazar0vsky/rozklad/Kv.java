package com.nazar0vsky.rozklad;

/** Prosty magazyn ustawień (na Androidzie: SharedPreferences; w testach: w pamięci). */
public interface Kv {
    String get(String key);          // null, gdy brak
    void put(String key, String value);
    void remove(String key);
}
