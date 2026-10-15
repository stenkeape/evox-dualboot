package com.evox.dualboot;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** HyperOS-style "Dual system" page. Evolution X lives in a DSU image; switching = one-shot DSU boot. */
public class DualSystemActivity extends Activity {

    private static final int CURRENT = 0; // the app always runs inside HyperOS (the host system)

    private boolean night;
    private int bg, card, tx, sub, accent;

    private final String[] name = new String[2];
    private final String[] ver = new String[2];
    private final String[] and = new String[2];
    private final String[] sto = new String[2];

    private int selected = 1;
    private final RadioView[] radios = new RadioView[2];
    private final LinearLayout[] rows = new LinearLayout[2];
    private TextView vVer, vAnd, vSto, btn, footer;
    private LinearLayout detailCard;
    private FrameLayout overlay;
    private TextView overlayText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Dual system");
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            bg = 0xFF0B0C0F; card = 0xFF1A1C21; tx = 0xFFFFFFFF; sub = 0xFF8A8F98;
        } else {
            bg = 0xFFF3F4F6; card = 0xFFFFFFFF; tx = 0xFF111111; sub = 0xFF7A7F87;
        }
        accent = 0xFF3482FF;
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        if (!night) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        loadInfo();
        buildUi();
    }

    // ------------------------------------------------------------------ data

    private void loadInfo() {
        String inc = prop("ro.mi.os.version.incremental");
        if (inc.isEmpty()) inc = prop("ro.build.version.incremental");
        String clean = inc.replaceFirst("^OS", "");
        StringBuilder v = new StringBuilder();
        String major = "";
        for (String seg : clean.split("\\.")) {
            if (!seg.matches("\\d+")) break;
            if (major.isEmpty()) major = seg;
            if (v.length() > 0) v.append('.');
            v.append(seg);
        }
        name[0] = major.isEmpty() ? "HyperOS" : "HyperOS " + major;
        ver[0] = v.length() > 0 ? v.toString() : inc;
        and[0] = "Android " + Build.VERSION.RELEASE;
        sto[0] = totalStorage();

        name[1] = "Evolution X";
        ver[1] = "11.8";
        and[1] = "Android 16 QPR2";
        sto[1] = "64 GB";

        for (int i = 0; i < 2; i++) {
            ver[i] = prefs.getString("v" + i, ver[i]);
            and[i] = prefs.getString("a" + i, and[i]);
            sto[i] = prefs.getString("s" + i, sto[i]);
        }
    }

    private static String prop(String k) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", k});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String s = r.readLine();
            p.waitFor();
            return s == null ? "" : s.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String totalStorage() {
        try {
            StatFs st = new StatFs(Environment.getDataDirectory().getPath());
            double gb = st.getTotalBytes() / 1e9;
            int p = 32;
            while (p < gb) p *= 2;
            return p + " GB";
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ UI

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(8), dp(18), dp(32));
        sv.addView(col, new ViewGroup.LayoutParams(-1, -2));

        TextView back = text("‹", 40, tx, false);
        back.setPadding(dp(6), 0, dp(16), 0);
        back.setOnClickListener(v -> finish());
        col.addView(back, new LinearLayout.LayoutParams(-2, -2));

        TextView title = text("Dual system", 34, tx, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = dp(18);
        tl.leftMargin = dp(8);
        col.addView(title, tl);

        TextView subtitle = text("Select which system to start when your device restarts.", 14, sub, false);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.leftMargin = dp(8);
        sl.topMargin = dp(8);
        col.addView(subtitle, sl);

        col.addView(sectionLabel("System"));
        LinearLayout sysCard = card();
        sysCard.setPadding(dp(6), dp(6), dp(6), dp(6));
        sysCard.addView(systemRow(0));
        sysCard.addView(systemRow(1));
        col.addView(sysCard, cardLp());

        col.addView(sectionLabel("Details"));
        detailCard = card();
        vVer = detailRow(detailCard, "Version");
        vAnd = detailRow(detailCard, "Android version");
        vSto = detailRow(detailCard, "Allocated storage");
        detailCard.setOnLongClickListener(v -> {
            editDialog();
            return true;
        });
        col.addView(detailCard, cardLp());

        btn = text("Switch system", 17, 0xFFFFFFFF, true);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(round(accent, 28));
        btn.setOnClickListener(v -> onSwitch());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(56));
        bl.topMargin = dp(28);
        bl.leftMargin = dp(6);
        bl.rightMargin = dp(6);
        col.addView(btn, bl);

        footer = text("", 13, sub, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
        fl.topMargin = dp(14);
        col.addView(footer, fl);

        TextView note = text("Evolution X starts once. Restarting from it brings you back to " + name[0] + ".",
                12, sub, false);
        note.setGravity(Gravity.CENTER);
        note.setAlpha(0.8f);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = dp(6);
        col.addView(note, nl);

        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        root.addView(buildOverlay(), new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        updateSelection(false);
        fillDetails(false);
        updateButton();

        for (int i = 0; i < col.getChildCount(); i++) {
            View c = col.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(dp(18));
            c.animate().alpha(1f).translationY(0).setStartDelay(i * 55L).setDuration(340)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(card, 26));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s, 13, sub, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(28);
        lp.bottomMargin = dp(8);
        lp.leftMargin = dp(12);
        t.setLayoutParams(lp);
        return t;
    }

    private View systemRow(final int i) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(name[i], 17, tx, true));
        if (i == CURRENT) {
            TextView chip = text("Active", 11, accent, true);
            chip.setPadding(dp(8), dp(2), dp(8), dp(2));
            chip.setBackground(round((accent & 0x00FFFFFF) | 0x26000000, 10));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.leftMargin = dp(8);
            top.addView(chip, cl);
        }
        texts.addView(top);
        TextView s = text(i == CURRENT ? "Primary system" : "Secondary system", 13, sub, false);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(-2, -2);
        l2.topMargin = dp(2);
        texts.addView(s, l2);

        radios[i] = new RadioView();
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(radios[i], new LinearLayout.LayoutParams(dp(24), dp(24)));
        row.setOnClickListener(v -> select(i));
        rows[i] = row;
        return row;
    }

    private TextView detailRow(LinearLayout parent, String label) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(20), dp(17), dp(20), dp(17));
        r.addView(text(label, 16, tx, false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = text("", 16, sub, false);
        v.setGravity(Gravity.END);
        r.addView(v, new LinearLayout.LayoutParams(-2, -2));
        parent.addView(r);
        return v;
    }

    private View buildOverlay() {
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor((bg & 0x00FFFFFF) | 0xF2000000);
        overlay.setClickable(true);
        overlay.setVisibility(View.GONE);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.addView(new ProgressBar(this), new LinearLayout.LayoutParams(dp(48), dp(48)));
        overlayText = text("", 17, tx, true);
        overlayText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(20);
        c.addView(overlayText, lp);
        overlay.addView(c, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        return overlay;
    }

    // ------------------------------------------------------------------ state

    private void select(int i) {
        if (selected == i) return;
        selected = i;
        updateSelection(true);
        fillDetails(true);
        updateButton();
    }

    private void updateSelection(boolean animate) {
        for (int i = 0; i < 2; i++) {
            radios[i].set(selected == i, animate);
            rows[i].setBackground(selected == i
                    ? round((accent & 0x00FFFFFF) | 0x1F000000, 20)
                    : round(0x00000000, 20));
        }
    }

    private void fillDetails(boolean animate) {
        vVer.setText(ver[selected]);
        vAnd.setText(and[selected]);
        vSto.setText(sto[selected]);
        if (animate) {
            for (TextView v : new TextView[]{vVer, vAnd, vSto}) {
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(240).start();
            }
        }
    }

    private void updateButton() {
        boolean on = selected != CURRENT;
        btn.setEnabled(on);
        btn.animate().alpha(on ? 1f : 0.38f).setDuration(200).start();
        footer.setText(on ? "Your device will restart into " + name[selected] + "."
                : name[CURRENT] + " is the current system.");
    }

    // ------------------------------------------------------------------ actions

    private void onSwitch() {
        if (selected == CURRENT) return;
        final String target = name[selected];
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Switch to " + target + "?")
                .setMessage("Your device will restart now. Your apps and data in each system stay separate.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restart", (dd, w) -> startSwitch(target))
                .create();
        d.show();
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(round(card, 28));
    }

    private void startSwitch(final String target) {
        overlayText.setText("Restarting into " + target + "…");
        overlay.setAlpha(0f);
        overlay.setVisibility(View.VISIBLE);
        overlay.animate().alpha(1f).setDuration(250).start();
        new Thread(() -> {
            boolean ok = su("gsi_tool enable -s");
            if (!ok) {
                runOnUiThread(() -> {
                    overlay.setVisibility(View.GONE);
                    Toast.makeText(this,
                            "Couldn't start " + target + ". Allow root for this app in ReSukiSU and check that it's installed.",
                            Toast.LENGTH_LONG).show();
                });
                return;
            }
            try { Thread.sleep(1200); } catch (InterruptedException ignored) { }
            su("reboot");
        }).start();
    }

    private static boolean su(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void editDialog() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), dp(8));
        sv.addView(box);
        final EditText[][] f = new EditText[2][3];
        String[] labels = {"version", "Android version", "storage"};
        for (int i = 0; i < 2; i++) {
            String[] vals = {ver[i], and[i], sto[i]};
            for (int k = 0; k < 3; k++) {
                EditText e = new EditText(this);
                e.setInputType(InputType.TYPE_CLASS_TEXT);
                e.setSingleLine(true);
                e.setHint(name[i] + " " + labels[k]);
                e.setText(vals[k]);
                f[i][k] = e;
                box.addView(e);
            }
        }
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Edit details")
                .setView(sv)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Reset", (dd, w) -> {
                    prefs.edit().clear().apply();
                    loadInfo();
                    fillDetails(true);
                })
                .setPositiveButton("Save", (dd, w) -> {
                    SharedPreferences.Editor ed = prefs.edit();
                    for (int i = 0; i < 2; i++) {
                        ed.putString("v" + i, f[i][0].getText().toString());
                        ed.putString("a" + i, f[i][1].getText().toString());
                        ed.putString("s" + i, f[i][2].getText().toString());
                    }
                    ed.apply();
                    loadInfo();
                    fillDetails(true);
                })
                .create();
        d.show();
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(round(card, 28));
    }

    // ------------------------------------------------------------------ radio

    private class RadioView extends View {
        private float t = 0f;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        RadioView() {
            super(DualSystemActivity.this);
        }

        void set(boolean checked, boolean animate) {
            float to = checked ? 1f : 0f;
            if (!animate) {
                t = to;
                invalidate();
                return;
            }
            ValueAnimator a = ValueAnimator.ofFloat(t, to);
            a.setDuration(200);
            a.addUpdateListener(v -> {
                t = (float) v.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - dp(2);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor((Integer) new ArgbEvaluator().evaluate(t, sub, accent));
            c.drawCircle(cx, cy, r, p);
            if (t > 0f) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(accent);
                c.drawCircle(cx, cy, r * 0.55f * t, p);
            }
        }
    }
}
