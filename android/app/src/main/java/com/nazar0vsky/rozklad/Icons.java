package com.nazar0vsky.rozklad;

import android.graphics.Bitmap;

/** Mała pamięć podręczna rysowanych ikon. */
final class Icons {
    private Icons() {}

    private static Bitmap starOn, starOff;

    static Bitmap star(boolean filled) {
        if (filled) {
            if (starOn == null) starOn = Skin.star(true, Skin.dp(26));
            return starOn;
        }
        if (starOff == null) starOff = Skin.star(false, Skin.dp(26));
        return starOff;
    }
}
