package com.nazar0vsky.rozklad;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import java.io.InputStream;

/**
 * Wygląd aplikacji: motywy, kolory, skórzane paski, tła i rysowane kodem ikony.
 * Odpowiednik RZViews z wersji na iOS.
 */
final class Skin {
    private Skin() {}

    static final class Def {
        String name;
        boolean leather;
        int navTop, navBot, tint, tabTop, tabBot, tabOff, tabOn;
        int bg;                 // 0 papier, 1 szare prążki iOS, 2 szary len
        int ink, footer, subtle, cellText, cellBg, sep;
        int ticketBg, ticketCaption, tear, accent;
        boolean darkShadow;     // motyw ciemny: jasne napisy z czarnym cieniem
        boolean selLight;       // zaznaczona zakładka jaśniejsza
    }

    private static int rgb(int r, int g, int b) { return Color.rgb(r, g, b); }

    static final Def[] THEMES = new Def[4];

    static {
        Def d = new Def();
        d.name = "Skóra"; d.leather = true; d.tint = rgb(66, 38, 18);
        d.tabOff = rgb(214, 196, 164); d.tabOn = rgb(255, 232, 160);
        d.bg = 0; d.ink = rgb(74, 46, 24); d.footer = rgb(104, 84, 62); d.subtle = rgb(128, 128, 128);
        d.cellText = rgb(20, 20, 20); d.cellBg = rgb(255, 255, 255); d.sep = rgb(205, 200, 190);
        d.ticketBg = rgb(247, 240, 219); d.ticketCaption = rgb(138, 130, 112); d.tear = rgb(199, 191, 168);
        d.accent = rgb(166, 15, 37);
        THEMES[0] = d;

        d = new Def();
        d.name = "Czerwony"; d.navTop = rgb(238, 78, 92); d.navBot = rgb(128, 5, 20); d.tint = rgb(118, 8, 22);
        d.tabTop = rgb(228, 66, 82); d.tabBot = rgb(112, 4, 18); d.tabOff = rgb(255, 214, 218); d.tabOn = rgb(255, 255, 255);
        d.bg = 2; d.ink = rgb(246, 246, 250); d.footer = rgb(196, 200, 210); d.subtle = rgb(172, 178, 192);
        d.cellText = rgb(244, 244, 248); d.cellBg = rgb(54, 58, 66); d.sep = rgb(30, 33, 39);
        d.ticketBg = rgb(46, 50, 58); d.ticketCaption = rgb(172, 178, 192); d.tear = rgb(92, 98, 110);
        d.accent = rgb(255, 112, 112); d.darkShadow = true;
        THEMES[1] = d;

        d = new Def();
        d.name = "Klasyczny"; d.navTop = rgb(180, 200, 228); d.navBot = rgb(90, 122, 178); d.tint = rgb(76, 108, 160);
        d.tabTop = rgb(72, 72, 76); d.tabBot = rgb(4, 4, 6); d.tabOff = rgb(172, 172, 178); d.tabOn = rgb(96, 170, 255);
        d.bg = 1; d.ink = rgb(76, 86, 108); d.footer = rgb(84, 94, 116); d.subtle = rgb(120, 126, 140);
        d.cellText = rgb(20, 20, 20); d.cellBg = rgb(255, 255, 255); d.sep = rgb(196, 200, 208);
        d.ticketBg = rgb(255, 255, 255); d.ticketCaption = rgb(120, 126, 140); d.tear = rgb(206, 210, 218);
        d.accent = rgb(170, 20, 40); d.selLight = true;
        THEMES[2] = d;

        d = new Def();
        d.name = "Grafit"; d.navTop = rgb(100, 104, 116); d.navBot = rgb(26, 28, 34); d.tint = rgb(24, 26, 30);
        d.tabTop = rgb(78, 82, 92); d.tabBot = rgb(12, 13, 16); d.tabOff = rgb(170, 174, 186); d.tabOn = rgb(255, 196, 87);
        d.bg = 2; d.ink = rgb(246, 246, 250); d.footer = rgb(196, 200, 210); d.subtle = rgb(172, 178, 192);
        d.cellText = rgb(244, 244, 248); d.cellBg = rgb(54, 58, 66); d.sep = rgb(30, 33, 39);
        d.ticketBg = rgb(46, 50, 58); d.ticketCaption = rgb(172, 178, 192); d.tear = rgb(92, 98, 110);
        d.accent = rgb(255, 118, 118); d.darkShadow = true; d.selLight = true;
        THEMES[3] = d;
    }

    private static int index = 0;
    private static float density = 2f;
    private static Context appCtx;

    static void init(Context c, float dens) {
        appCtx = c.getApplicationContext();
        density = dens;
    }

    static int dp(float v) { return Math.round(v * density); }
    static float dpf(float v) { return v * density; }

    static int index() { return index; }
    static void setIndex(int i) { index = (i >= 0 && i < THEMES.length) ? i : 0; }
    static Def t() { return THEMES[index]; }
    static int count() { return THEMES.length; }
    static String name(int i) { return Lang.t(THEMES[i].name); }

    // ------------------------------------------------------------------ obrazy z folderu assets

    private static Bitmap navLeather, tabLeather, paper, linen, stripe;

    private static Bitmap asset(String name) {
        try {
            InputStream in = appCtx.getAssets().open(name);
            try {
                return BitmapFactory.decodeStream(in);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    static Bitmap splash() { return asset("splash.png"); }

    // ------------------------------------------------------------------ kolory pomocnicze

    static int mix(int a, int b, float t) {
        return Color.rgb(
            (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t + 0.5f),
            (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t + 0.5f),
            (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t + 0.5f));
    }

    static int alpha(int c, float a) { return Color.argb((int) (a * 255 + 0.5f), Color.red(c), Color.green(c), Color.blue(c)); }

    static int ink() { return t().ink; }
    static int subtle() { return t().subtle; }
    static int cellText() { return t().cellText; }
    static int cellBg() { return t().cellBg; }
    static int accent() { return t().accent; }
    static int headerShadow() { return t().darkShadow ? Color.argb(204, 0, 0, 0) : Color.argb(217, 255, 255, 255); }
    static int textShadow() { return t().darkShadow ? Color.TRANSPARENT : Color.WHITE; }

    // ------------------------------------------------------------------ znaczki z numerem linii

    /** Kolory znaczka: {góra, dół, ramka}. */
    static int[] kindColors(char kind, String line) {
        switch (kind) {
            case 't': return new int[] { rgb(90, 165, 245), rgb(27, 98, 200), rgb(15, 60, 130) };
            case 'p': return new int[] { rgb(110, 190, 110), rgb(36, 120, 48), rgb(20, 75, 28) };
            case 'm':
                if (line != null && line.startsWith("M2"))
                    return new int[] { rgb(232, 80, 95), rgb(166, 15, 37), rgb(109, 7, 21) };
                return new int[] { rgb(80, 110, 215), rgb(25, 45, 140), rgb(15, 25, 90) };
            default: return new int[] { rgb(232, 80, 95), rgb(166, 15, 37), rgb(109, 7, 21) };
        }
    }

    // ------------------------------------------------------------------ paski (nawigacja i zakładki)

    /** Błyszczący pasek jak w iOS 6 albo skóra z pliku. */
    static final class BarDrawable extends Drawable {
        private final boolean tab;
        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Rect src = new Rect();

        BarDrawable(boolean tab) { this.tab = tab; }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            Def t = t();
            if (t.leather) {
                Bitmap bm = tab ? tabLeather() : navLeather();
                if (bm != null) {
                    src.set(0, 0, bm.getWidth(), bm.getHeight());
                    c.drawBitmap(bm, src, b, p);
                    return;
                }
            }
            int top = tab ? t.tabTop : t.navTop;
            int bot = tab ? t.tabBot : t.navBot;
            if (t.leather) { top = rgb(98, 62, 36); bot = rgb(58, 33, 16); }
            int m1 = mix(top, bot, 0.40f), m2 = mix(top, bot, 0.62f);
            int half = b.top + b.height() / 2;
            Paint g = new Paint();
            g.setShader(new LinearGradient(0, b.top, 0, half, top, m1, Shader.TileMode.CLAMP));
            c.drawRect(b.left, b.top, b.right, half, g);
            g.setShader(new LinearGradient(0, half, 0, b.bottom, m2, bot, Shader.TileMode.CLAMP));
            c.drawRect(b.left, half, b.right, b.bottom, g);
            Paint l = new Paint();
            l.setColor(Color.argb(77, 255, 255, 255));
            c.drawRect(b.left, b.top, b.right, b.top + Math.max(1, dp(0.5f)), l);
            l.setColor(Color.argb(128, 0, 0, 0));
            c.drawRect(b.left, b.bottom - Math.max(1, dp(0.5f)), b.right, b.bottom, l);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    private static Bitmap navLeather() {
        if (navLeather == null) navLeather = asset("nav_leather.png");
        return navLeather;
    }

    private static Bitmap tabLeather() {
        if (tabLeather == null) tabLeather = asset("tab_leather.png");
        return tabLeather;
    }

    /** Kolor paska stanu telefonu pasujący do motywu. */
    static int statusColor() {
        Def t = t();
        return t.leather ? rgb(46, 26, 12) : mix(t.navBot, Color.BLACK, 0.35f);
    }

    static int navigationColor() {
        Def t = t();
        return t.leather ? rgb(40, 24, 12) : mix(t.tabBot, Color.BLACK, 0.2f);
    }

    /** Zaokrąglone podświetlenie zaznaczonej zakładki. */
    static void drawTabSelection(Canvas c, RectF r) {
        Def t = t();
        float rad = dpf(6);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (t.leather) {
            p.setShader(new LinearGradient(0, r.top, 0, r.bottom, Color.argb(127, 18, 8, 2), Color.argb(81, 18, 8, 2), Shader.TileMode.CLAMP));
        } else if (t.selLight) {
            p.setColor(Color.argb(61, 255, 255, 255));
        } else {
            p.setColor(Color.argb(77, 0, 0, 0));
        }
        c.drawRoundRect(r, rad, rad, p);
    }

    // ------------------------------------------------------------------ tła stron

    /** Płytki rysowane powtarzalnym wzorem (przesuwa się razem z oknem, nie z listą). */
    static final class TileDrawable extends Drawable {
        private final Paint p = new Paint();
        private final Bitmap bm;
        private final float tilePx;
        private final int fallback;

        TileDrawable(Bitmap bm, float tilePx, int fallback) {
            this.bm = bm;
            this.tilePx = tilePx;
            this.fallback = fallback;
            if (bm != null) {
                BitmapShader s = new BitmapShader(bm, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                Matrix m = new Matrix();
                float sc = tilePx / bm.getWidth();
                m.setScale(sc, sc);
                s.setLocalMatrix(m);
                p.setShader(s);
                p.setFilterBitmap(true);
            } else {
                p.setColor(fallback);
            }
        }

        @Override public void draw(Canvas c) { c.drawRect(getBounds(), p); }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    private static Bitmap stripeTile() {
        if (stripe == null) {
            Bitmap b = Bitmap.createBitmap(7, 8, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setColor(rgb(213, 219, 229)); c.drawRect(0, 0, 7, 8, p);
            p.setColor(rgb(197, 206, 220)); c.drawRect(0, 0, 3, 8, p);
            p.setColor(rgb(222, 227, 236)); c.drawRect(3, 0, 4, 8, p);
            stripe = b;
        }
        return stripe;
    }

    /** Tło listy w aktualnym motywie. */
    static Drawable pageBackground() {
        Def t = t();
        if (t.bg == 1) return new TileDrawable(stripeTile(), dpf(7), rgb(213, 219, 229));
        if (t.bg == 2) {
            if (linen == null) linen = asset("bg_linen.png");
            return new TileDrawable(linen, dpf(64), rgb(112, 116, 128));
        }
        if (paper == null) paper = asset("bg_paper.png");
        return new TileDrawable(paper, dpf(128), rgb(238, 230, 211));
    }

    // ------------------------------------------------------------------ komórki list (grupy jak w iOS 6)

    static final int POS_ONLY = 0, POS_FIRST = 1, POS_MID = 2, POS_LAST = 3;

    /** Tło wiersza w zaokrąglonej grupie. */
    static final class GroupDrawable extends Drawable {
        private final int pos;
        private final int fill, line, press;
        private final int inset;
        private boolean pressed;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF r = new RectF();

        GroupDrawable(int pos, int fill, int line, int press) {
            this.pos = pos;
            this.fill = fill;
            this.line = line;
            this.press = press;
            this.inset = dp(9);
        }

        @Override public boolean isStateful() { return true; }

        @Override protected boolean onStateChange(int[] state) {
            boolean pr = false;
            for (int s : state) if (s == android.R.attr.state_pressed) pr = true;
            if (pr != pressed) { pressed = pr; invalidateSelf(); return true; }
            return false;
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float sw = Math.max(1f, dpf(0.75f));
            float rad = dpf(9);
            boolean top = pos == POS_ONLY || pos == POS_FIRST;
            boolean bottom = pos == POS_ONLY || pos == POS_LAST;
            float l = b.left + inset, rt = b.right - inset;
            float tp = top ? b.top + sw / 2 : b.top - sw;
            float bt = bottom ? b.bottom - sw / 2 : b.bottom + sw;
            r.set(l + sw / 2, tp, rt - sw / 2, bt);
            float[] radii = {
                top ? rad : 0, top ? rad : 0, top ? rad : 0, top ? rad : 0,
                bottom ? rad : 0, bottom ? rad : 0, bottom ? rad : 0, bottom ? rad : 0 };
            path.reset();
            path.addRoundRect(r, radii, Path.Direction.CW);
            c.save();
            c.clipRect(b);
            p.setStyle(Paint.Style.FILL);
            p.setColor(pressed ? press : fill);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(line);
            c.drawPath(path, p);
            if (!bottom) {
                p.setStyle(Paint.Style.FILL);
                c.drawRect(l + sw, b.bottom - sw, rt - sw, b.bottom, p);
            }
            c.restore();
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static Drawable groupBackground(int pos) {
        Def t = t();
        int press = t.darkShadow ? mix(t.cellBg, Color.WHITE, 0.18f) : mix(t.cellBg, rgb(56, 121, 217), 0.28f);
        int line = t.darkShadow ? mix(t.sep, Color.BLACK, 0.2f) : mix(t.sep, Color.BLACK, 0.18f);
        return new GroupDrawable(pos, t.cellBg, line, press);
    }

    /** Tło "biletu" na ekranie przystanku (na całą szerokość grupy). */
    static Drawable ticketBackground() {
        Def t = t();
        int line = t.darkShadow ? mix(t.sep, Color.BLACK, 0.2f) : mix(t.sep, Color.BLACK, 0.18f);
        return new GroupDrawable(POS_ONLY, t.ticketBg, line, mix(t.ticketBg, rgb(56, 121, 217), 0.2f));
    }

    // ------------------------------------------------------------------ przyciski na pasku

    /** Przycisk na pasku w stylu iOS 6 (zaokrąglony, kolor motywu). */
    static final class BarButtonDrawable extends Drawable {
        private final boolean done, back;
        private boolean pressed;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path arrow = new Path();

        BarButtonDrawable(boolean done, boolean back) { this.done = done; this.back = back; }

        @Override public boolean isStateful() { return true; }

        @Override protected boolean onStateChange(int[] state) {
            boolean pr = false;
            for (int s : state) if (s == android.R.attr.state_pressed) pr = true;
            if (pr != pressed) { pressed = pr; invalidateSelf(); return true; }
            return false;
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            Def t = t();
            int base = done ? mix(t.leather ? rgb(60, 90, 160) : t.tint, rgb(40, 100, 220), 0.55f) : t.tint;
            if (t.leather && !done) base = rgb(98, 62, 36);
            int top = mix(base, Color.WHITE, 0.30f);
            int bot = mix(base, Color.BLACK, 0.10f);
            int edge = mix(base, Color.BLACK, 0.45f);
            if (pressed) { top = mix(top, Color.BLACK, 0.25f); bot = mix(bot, Color.BLACK, 0.25f); }
            float rad = dpf(5);
            r.set(b.left + dpf(0.5f), b.top + dpf(0.5f), b.right - dpf(0.5f), b.bottom - dpf(0.5f));
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, r.top, 0, r.bottom, top, bot, Shader.TileMode.CLAMP));
            if (back) {
                float mid = (r.top + r.bottom) / 2f;
                arrow.reset();
                arrow.moveTo(r.left, mid);
                arrow.lineTo(r.left + r.height() / 2f, r.top);
                arrow.lineTo(r.right, r.top);
                arrow.lineTo(r.right, r.bottom);
                arrow.lineTo(r.left + r.height() / 2f, r.bottom);
                arrow.close();
                p.setPathEffect(new android.graphics.CornerPathEffect(dpf(4)));
                c.drawPath(arrow, p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dpf(1));
                p.setColor(edge);
                c.drawPath(arrow, p);
                p.setPathEffect(null);
                return;
            }
            c.drawRoundRect(r, rad, rad, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dpf(1));
            p.setColor(edge);
            c.drawRoundRect(r, rad, rad, p);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ------------------------------------------------------------------ ikony rysowane kodem

    private static Paint fillPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.BLACK);
        return p;
    }

    private static Paint clearPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        return p;
    }

    private static void rr(Canvas c, float l, float t, float w, float h, float rad, Paint p) {
        c.drawRoundRect(new RectF(l, t, l + w, t + h), rad, rad, p);
    }

    private static void box(Canvas c, float l, float t, float w, float h, Paint p) {
        c.drawRect(l, t, l + w, t + h, p);
    }

    private static void oval(Canvas c, float l, float t, float w, float h, Paint p) {
        c.drawOval(new RectF(l, t, l + w, t + h), p);
    }

    /** Ikona zakładki (b t p m s = ustawienia) pomalowana jednym kolorem. */
    static Bitmap tabIcon(char kind, int color, int sizePx) {
        Bitmap bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        c.scale(sizePx / 30f, sizePx / 30f);
        Paint f = fillPaint(), clr = clearPaint();
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStyle(Paint.Style.STROKE);
        s.setColor(Color.BLACK);
        s.setStrokeWidth(2f);
        switch (kind) {
            case 't':
                rr(c, 3, 8, 24, 15, 4, f);
                box(c, 6, 11, 5, 5, clr); box(c, 12.5f, 11, 5, 5, clr); box(c, 19, 11, 5, 5, clr);
                c.drawLine(15, 8, 15, 4, s);
                c.drawLine(9, 3.5f, 21, 3.5f, s);
                oval(c, 7, 21, 5, 5, f); oval(c, 18, 21, 5, 5, f);
                box(c, 2, 27, 26, 1.5f, f);
                break;
            case 'p':
                rr(c, 7, 2, 16, 21, 5, f);
                box(c, 10, 6, 10, 7, clr);
                oval(c, 10, 16.5f, 3, 3, clr); oval(c, 17, 16.5f, 3, 3, clr);
                c.drawLine(11, 23, 8, 28, s);
                c.drawLine(19, 23, 22, 28, s);
                break;
            case 'm': {
                oval(c, 2, 2, 26, 26, f);
                Path m = new Path();
                m.moveTo(8, 21); m.lineTo(8, 9); m.lineTo(15, 17); m.lineTo(22, 9); m.lineTo(22, 21);
                Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
                mp.setStyle(Paint.Style.STROKE);
                mp.setStrokeWidth(3f);
                mp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                c.drawPath(m, mp);
                break;
            }
            case 's': {
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.translate(15, 15);
                    c.rotate(i * 45f);
                    box(c, -2.6f, -13f, 5.2f, 7f, f);
                    c.restore();
                }
                oval(c, 5, 5, 20, 20, f);
                oval(c, 15 - 4.2f, 15 - 4.2f, 8.4f, 8.4f, clr);
                break;
            }
            default:
                rr(c, 5, 3, 20, 20, 4, f);
                box(c, 8, 6, 14, 7, clr);
                oval(c, 8, 16, 3.5f, 3.5f, clr); oval(c, 18.5f, 16, 3.5f, 3.5f, clr);
                oval(c, 7, 21, 5, 6, f); oval(c, 18, 21, 5, 6, f);
        }
        // pomalowanie maski wybranym kolorem
        Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        Paint tp = new Paint(Paint.FILTER_BITMAP_FLAG);
        oc.drawBitmap(bm, 0, 0, tp);
        Paint cp = new Paint();
        cp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        cp.setColor(color);
        oc.drawRect(0, 0, sizePx, sizePx, cp);
        bm.recycle();
        return out;
    }

    private static final float[][] STAR = {
        {0f, -1f}, {0.5878f, -0.809f}, {0.9511f, -0.309f}, {0.9511f, 0.309f}, {0.5878f, 0.809f},
        {0f, 1f}, {-0.5878f, 0.809f}, {-0.9511f, 0.309f}, {-0.9511f, -0.309f}, {-0.5878f, -0.809f} };

    /** Gwiazdka ulubionych (pełna żółta albo sam szary obrys). */
    static Bitmap star(boolean filled, int sizePx) {
        Bitmap bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        c.scale(sizePx / 24f, sizePx / 24f);
        Path p = new Path();
        float cx = 12f, cy = 12.5f, ro = 10.5f, ri = 4.4f;
        for (int i = 0; i < 10; i++) {
            float r = (i % 2 == 0) ? ro : ri;
            float x = cx + r * STAR[i][0], y = cy + r * STAR[i][1];
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeJoin(Paint.Join.ROUND);
        if (filled) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(rgb(255, 205, 40));
            c.drawPath(p, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f);
            paint.setColor(rgb(176, 112, 0));
            c.drawPath(p, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f);
            paint.setColor(rgb(150, 150, 156));
            c.drawPath(p, paint);
        }
        return bm;
    }

    /** Miniaturka motywu do listy w ustawieniach. */
    static Bitmap swatch(int i, int sizePx) {
        Def t = THEMES[i];
        Bitmap bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        c.scale(sizePx / 40f, sizePx / 40f);
        Path clip = new Path();
        clip.addRoundRect(new RectF(1, 1, 39, 39), 6, 6, Path.Direction.CW);
        c.clipPath(clip);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int bg = t.bg == 1 ? rgb(205, 212, 224) : (t.bg == 2 ? rgb(112, 116, 128) : rgb(238, 230, 211));
        p.setColor(bg);
        c.drawRect(0, 0, 40, 40, p);
        int nt = t.leather ? rgb(98, 62, 36) : t.navTop;
        int nb = t.leather ? rgb(58, 33, 16) : t.navBot;
        int tt = t.leather ? rgb(98, 62, 36) : t.tabTop;
        int tb = t.leather ? rgb(58, 33, 16) : t.tabBot;
        Paint g = new Paint();
        g.setShader(new LinearGradient(0, 0, 0, 11, nt, nb, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 40, 11, g);
        g = new Paint();
        g.setShader(new LinearGradient(0, 31, 0, 40, tt, tb, Shader.TileMode.CLAMP));
        c.drawRect(0, 31, 40, 40, g);
        p.setColor(t.darkShadow ? rgb(54, 58, 66) : Color.WHITE);
        c.drawRect(6, 15, 34, 20, p);
        c.drawRect(6, 23, 34, 28, p);
        return bm;
    }

    // ------------------------------------------------------------------ drobne pomoce do widoków

    static void style(TextView v, float sizeDp, int color, boolean bold) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        v.setTextColor(color);
        v.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
    }

    static void shadow(TextView v, int color, float dy) {
        v.setShadowLayer(1f, 0f, dy * density, color);
    }

    static void background(View v, Drawable d) {
        if (android.os.Build.VERSION.SDK_INT >= 16) v.setBackground(d);
        else v.setBackgroundDrawable(d);
    }
}
