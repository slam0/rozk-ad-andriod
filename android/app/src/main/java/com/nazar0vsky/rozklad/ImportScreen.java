package com.nazar0vsky.rozklad;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** Okno pobierania rozkładu po dotknięciu „Otwórz w aplikacji Rozkład” na stronie. */
final class ImportScreen extends Screen {
    private final String url;
    private ImportReq req;
    private String parseError;
    private LinearLayout root;
    private Widgets.NavBar nav;
    private TextView head, detail, status;
    private ProgressBar bar, spin;
    private TextView cancel, start, done;
    private boolean working, finished;
    private Importer importer;

    ImportScreen(MainActivity a, String url) {
        super(a);
        this.url = url;
        try {
            req = ImportReq.parse(url);
        } catch (ImportReq.Err e) {
            parseError = Importer.errText(e.getMessage());
        }
    }

    private TextView text(float size, boolean bold) {
        TextView t = new TextView(act);
        Skin.style(t, size, Skin.ink(), bold);
        Skin.shadow(t, Skin.headerShadow(), 1);
        return t;
    }

    @Override View build() {
        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        Skin.background(root, Skin.pageBackground());
        root.setClickable(true);       // okno modalne nie przepuszcza dotyku do ekranów pod spodem
        nav = new Widgets.NavBar(act);
        nav.setTitle(Lang.t("Pobieranie"));
        root.addView(nav);

        ScrollView sv = new ScrollView(act);
        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Skin.dp(20), Skin.dp(20), Skin.dp(20), Skin.dp(20));
        sv.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        head = text(19, true);
        body.addView(head);
        detail = text(16, false);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = Skin.dp(10);
        body.addView(detail, dl);
        bar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setVisibility(View.GONE);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Skin.dp(12));
        bl.topMargin = Skin.dp(24);
        body.addView(bar, bl);
        spin = new ProgressBar(act);
        spin.setVisibility(View.GONE);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Skin.dp(36), Skin.dp(36));
        sl.gravity = Gravity.CENTER_HORIZONTAL;
        sl.topMargin = Skin.dp(16);
        body.addView(spin, sl);
        status = text(16, true);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stl.topMargin = Skin.dp(12);
        body.addView(status, stl);

        cancel = Widgets.barButton(act, Lang.t("Anuluj"), false, false);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { cancelTapped(); }
        });
        start = Widgets.barButton(act, Lang.t("Pobierz"), true, false);
        start.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startTapped(); }
        });
        done = Widgets.barButton(act, Lang.t("Gotowe"), true, false);
        done.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { leave(); }
        });

        if (req == null) {
            showFailure(parseError != null ? parseError : Lang.t("Nie rozumiem tego linku."));
        } else {
            head.setText(Lang.t("Pobrać rozkład?"));
            detail.setText(Lang.f("Aplikacja pobierze rozkład wybranych przystanków (słupków: %d) ze strony:\n%s\n\nTo zastąpi rozkład, który masz teraz w telefonie.", req.count(), req.host));
            nav.setLeft(cancel);
            nav.setRight(start);
        }
        return root;
    }

    private void leave() {
        act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        act.closeModal(this);
    }

    private void cancelTapped() {
        if (importer != null) importer.cancel();
        leave();
    }

    @Override boolean onBack() {
        if (working && importer != null) importer.cancel();
        return false;
    }

    private void swapButtons(View left, View right) {
        nav.removeView(cancel);
        nav.removeView(start);
        nav.removeView(done);
        if (left != null) nav.setLeft(left);
        if (right != null) nav.setRight(right);
    }

    private void startTapped() {
        if (working || req == null) return;
        working = true;
        head.setText(Lang.t("Pobieram rozkład…"));
        detail.setText(Lang.t("Nie zamykaj aplikacji, dopóki trwa pobieranie. Przy wolnym internecie może to potrwać kilka minut."));
        status.setText(Lang.t("Łączę…"));
        bar.setProgress(0);
        bar.setVisibility(View.VISIBLE);
        spin.setVisibility(View.VISIBLE);
        swapButtons(cancel, null);
        act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        importer = new Importer();
        final Importer imp = importer;
        final ImportReq rq = req;
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                final Importer.Result r = imp.run(rq, act.app.schedule.folder(), new Importer.Listener() {
                    public void progress(final float p, final String t) {
                        h.post(new Runnable() {
                            public void run() {
                                if (finished || !working) return;
                                bar.setProgress((int) (p * 1000));
                                status.setText(t);
                            }
                        });
                    }
                });
                if (r.ok) {
                    act.app.schedule.reload();
                    act.app.schedule.selectAllStops();    // plik zawiera tylko przystanki wybrane na stronie
                    act.app.store.put("site_base", r.base);
                }
                h.post(new Runnable() {
                    public void run() {
                        if (r.cancelled) return;
                        if (r.ok) showSuccess(r);
                        else showFailure(r.error.length() > 0 ? r.error : Lang.t("Pobieranie przerwano."));
                    }
                });
            }
        }).start();
    }

    private void showFailure(String message) {
        working = false;
        finished = true;
        spin.setVisibility(View.GONE);
        bar.setVisibility(View.GONE);
        head.setText(Lang.t("Nie udało się"));
        detail.setText(message);
        status.setText("");
        act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        swapButtons(null, done);
    }

    private void showSuccess(Importer.Result r) {
        working = false;
        finished = true;
        spin.setVisibility(View.GONE);
        bar.setVisibility(View.GONE);
        act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        head.setText(Lang.t("Gotowe!"));
        detail.setText(Lang.f("Rozkład jest już w telefonie.\nSłupków: %d, rozmiar pliku: %d KB.", r.stops, (r.bytes + 512) / 1024));
        status.setText("");
        swapButtons(null, done);
        act.dataChanged();
    }
}
