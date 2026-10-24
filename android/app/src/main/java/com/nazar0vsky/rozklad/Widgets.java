package com.nazar0vsky.rozklad;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Własne widoki w stylu iOS 6: znaczek linii, ruchomy pasek, paski nawigacji i zakładek. */
final class Widgets {
    private Widgets() {}

    // ------------------------------------------------------------------ znaczek z numerem linii

    static final class Badge extends View {
        private String line = "";
        private char kind = 'b';
        private float textDp = 16;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path clip = new Path();

        Badge(Context c, float textDp) {
            super(c);
            this.textDp = textDp;
            stroke.setStyle(Paint.Style.STROKE);
            gloss.setColor(Color.argb(51, 255, 255, 255));
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setColor(Color.WHITE);
        }

        void set(String line, char kind) {
            this.line = line == null ? "" : line;
            this.kind = kind;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            int[] col = Skin.kindColors(kind, line);
            float rad = Skin.dpf(6), sw = Skin.dpf(1);
            r.set(sw / 2, sw / 2, w - sw / 2, h - sw / 2);
            fill.setShader(new LinearGradient(0, 0, 0, h, col[0], col[1], Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, fill);
            c.save();
            clip.reset();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.clipPath(clip);
            c.drawRect(0, 0, w, h / 2f, gloss);
            c.restore();
            stroke.setStrokeWidth(sw);
            stroke.setColor(col[2]);
            c.drawRoundRect(r, rad, rad, stroke);

            float size = Skin.dpf(textDp);
            text.setTextSize(size);
            float avail = w - Skin.dpf(8);
            float tw = text.measureText(line);
            if (tw > avail && tw > 0) {
                size = Math.max(size * 0.5f, size * avail / tw);
                text.setTextSize(size);
            }
            float y = h / 2f - (text.ascent() + text.descent()) / 2f;
            text.setColor(Color.argb(128, 0, 0, 0));
            c.drawText(line, w / 2f, y - Skin.dpf(1), text);
            text.setColor(Color.WHITE);
            c.drawText(line, w / 2f, y, text);
        }
    }

    // ------------------------------------------------------------------ ruchomy pasek z ostrzeżeniem

    static final class Marquee extends View {
        private String message = "";
        private boolean warning;
        private long start = SystemClock.uptimeMillis();
        private final Paint bg = new Paint();
        private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float textW;

        Marquee(Context c) {
            super(c);
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setTextSize(Skin.dpf(15));
            setMinimumHeight(Skin.dp(34));
        }

        void setText(String t, boolean warn) {
            boolean grew = t.length() > message.length();
            message = t;
            warning = warn;
            textW = tp.measureText(t) + Skin.dpf(24);
            if (grew) restart();
            invalidate();
        }

        void restart() {
            start = SystemClock.uptimeMillis();
            invalidate();
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(MeasureSpec.getSize(wSpec), Skin.dp(34));
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            int top = warning ? Color.rgb(232, 80, 95) : Color.rgb(255, 229, 138);
            int bot = warning ? Color.rgb(166, 15, 37) : Color.rgb(239, 181, 52);
            bg.setShader(new LinearGradient(0, 0, 0, h, top, bot, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, bg);
            float speed = Skin.dpf(55);
            float span = w + textW;
            float elapsed = (SystemClock.uptimeMillis() - start) / 1000f;
            float x = w - ((elapsed * speed) % span);
            float y = h / 2f - (tp.ascent() + tp.descent()) / 2f;
            if (warning) {
                tp.setColor(Color.argb(140, 0, 0, 0));
                c.drawText(message, x, y - Skin.dpf(1), tp);
                tp.setColor(Color.WHITE);
            } else {
                tp.setColor(Color.argb(153, 255, 255, 255));
                c.drawText(message, x, y + Skin.dpf(1), tp);
                tp.setColor(Color.rgb(74, 50, 0));
            }
            c.drawText(message, x, y, tp);
            postInvalidateOnAnimation();
        }
    }

    // ------------------------------------------------------------------ kreskowana linia biletu

    static final class Tear extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Tear(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Skin.dpf(2));
            p.setPathEffect(new DashPathEffect(new float[] { Skin.dpf(6), Skin.dpf(4) }, 0));
        }

        @Override protected void onDraw(Canvas c) {
            p.setColor(Skin.t().tear);
            float y = getHeight() / 2f;
            c.drawLine(0, y, getWidth(), y, p);
        }
    }

    // ------------------------------------------------------------------ przyciski paska

    static TextView barButton(Context ctx, String text, boolean done, boolean back) {
        TextView b = new TextView(ctx);
        b.setText(text);
        Skin.style(b, 13, Color.WHITE, true);
        Skin.shadow(b, Color.argb(160, 0, 0, 0), -1);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setMaxWidth(Skin.dp(120));
        b.setMinWidth(Skin.dp(36));
        int left = back ? Skin.dp(18) : Skin.dp(10);
        b.setPadding(left, 0, Skin.dp(10), 0);
        Skin.background(b, new Skin.BarButtonDrawable(done, back));
        b.setClickable(true);
        return b;
    }

    static TextView barIconButton(Context ctx, Bitmap icon) {
        TextView b = barButton(ctx, "", false, false);
        b.setMinWidth(Skin.dp(40));
        b.setCompoundDrawablesWithIntrinsicBounds(new BitmapDrawable(ctx.getResources(), icon), null, null, null);
        b.setCompoundDrawablePadding(0);
        b.setPadding(Skin.dp(8), 0, Skin.dp(8), 0);
        return b;
    }

    // ------------------------------------------------------------------ pasek nawigacji

    static final class NavBar extends FrameLayout {
        private final TextView title;

        NavBar(Context c) {
            super(c);
            Skin.background(this, new Skin.BarDrawable(false));
            title = new TextView(c);
            Skin.style(title, 20, Color.WHITE, true);
            title.setShadowLayer(1f, 0f, -Skin.dpf(1), Color.argb(191, 0, 0, 0));
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setGravity(Gravity.CENTER);
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.leftMargin = Skin.dp(96);
            lp.rightMargin = Skin.dp(96);
            addView(title, lp);
            setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(44)));
        }

        void setTitle(String t) { title.setText(t); }

        void setLeft(View v) { put(v, Gravity.LEFT | Gravity.CENTER_VERTICAL, Skin.dp(6), 0); }

        void setRight(View v) { put(v, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, Skin.dp(6)); }

        private void put(View v, int gravity, int left, int right) {
            if (v == null) return;
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Skin.dp(30), gravity);
            lp.leftMargin = left;
            lp.rightMargin = right;
            addView(v, lp);
        }
    }

    // ------------------------------------------------------------------ pasek zakładek

    static final class TabBar extends LinearLayout {
        interface Listener { void onTab(int index); }

        private final TabItem[] items = new TabItem[5];
        private int selected = -1;
        private Listener listener;

        TabBar(Context c, final String[] names, final char[] kinds, Listener l) {
            super(c);
            this.listener = l;
            setOrientation(HORIZONTAL);
            Skin.background(this, new Skin.BarDrawable(true));
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                items[i] = new TabItem(c, names[i], kinds[i]);
                items[i].setOnClickListener(new OnClickListener() {
                    public void onClick(View v) { if (listener != null) listener.onTab(idx); }
                });
                addView(items[i], new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            }
            setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(49)));
        }

        void select(int i) {
            selected = i;
            for (int k = 0; k < items.length; k++) items[k].setSel(k == i);
        }

        int selected() { return selected; }
    }

    static final class TabItem extends View {
        private final String name;
        private final char kind;
        private boolean sel;
        private Bitmap on, off;
        private int onColor, offColor;
        private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ip = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF r = new RectF();

        TabItem(Context c, String name, char kind) {
            super(c);
            this.name = name;
            this.kind = kind;
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(Skin.dpf(10));
            setClickable(true);
        }

        void setSel(boolean s) {
            sel = s;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            Skin.Def t = Skin.t();
            int size = Skin.dp(28);
            if (on == null || offColor != t.tabOff || onColor != t.tabOn) {
                onColor = t.tabOn;
                offColor = t.tabOff;
                on = Skin.tabIcon(kind, onColor, size);
                off = Skin.tabIcon(kind, offColor, size);
            }
            int w = getWidth(), h = getHeight();
            if (sel) {
                r.set(Skin.dpf(2), Skin.dpf(3), w - Skin.dpf(2), h - Skin.dpf(3));
                Skin.drawTabSelection(c, r);
            }
            Bitmap ic = sel ? on : off;
            c.drawBitmap(ic, (w - size) / 2f, Skin.dpf(3), ip);
            String label = TextUtils.ellipsize(name, new android.text.TextPaint(tp), w - Skin.dpf(4), TextUtils.TruncateAt.END).toString();
            float y = h - Skin.dpf(7);
            tp.setColor(Color.argb(204, 0, 0, 0));
            c.drawText(label, w / 2f, y - Skin.dpf(1), tp);
            tp.setColor(sel ? onColor : offColor);
            c.drawText(label, w / 2f, y, tp);
        }

        @Override protected void drawableStateChanged() {
            super.drawableStateChanged();
        }
    }

    // ------------------------------------------------------------------ pomoce

    static TextView label(Context c, float sizeDp, int color, boolean bold) {
        TextView t = new TextView(c);
        Skin.style(t, sizeDp, color, bold);
        return t;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }
}
