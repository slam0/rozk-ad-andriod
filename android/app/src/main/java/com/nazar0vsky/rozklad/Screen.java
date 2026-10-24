package com.nazar0vsky.rozklad;

import android.view.View;

/** Jeden ekran aplikacji (jak UIViewController na iOS). */
abstract class Screen {
    final MainActivity act;
    private View root;
    boolean dirty;

    Screen(MainActivity a) { act = a; }

    final View view() {
        if (root == null) root = build();
        return root;
    }

    abstract View build();

    /** Ekran pojawił się na wierzchu. */
    void onShow() {
        if (dirty) {
            dirty = false;
            refresh();
        }
    }

    void onHide() { }

    /** Dane (rozkład, język, motyw) się zmieniły: odśwież treść. */
    void refresh() { }

    /** Wołane co sekundę, gdy ekran jest widoczny. */
    void tick() { }

    /** Przycisk Wstecz: true = obsłużono. */
    boolean onBack() { return false; }

    String backTitle() { return Lang.t("Wstecz"); }
}
