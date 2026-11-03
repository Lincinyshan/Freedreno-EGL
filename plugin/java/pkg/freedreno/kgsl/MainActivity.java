package pkg.freedreno.kgsl;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Freedreno-EGL（OpenGL 4.6）设置界面。
 *
 * 不依赖任何第三方库：纯 android.widget + 系统调色板
 * （API 31+ 用系统动态色，低版本回退固定色）。
 * 极简扁平列表：分区标题 + 行（标题/副标题 + 开关），整行可点。
 * 设置写到 Android/media/<包名>/fcl_env.txt，改完 force-stop FCL 生效。
 */
public class MainActivity extends Activity {
    private static final String FILE_NAME = "fcl_env.txt";
    private static final String PREFS = "fcl_ui";

    private int accent, accentDark, onSurface, onSurfaceVariant, background, trackOff;
    private LinearLayout content, bottomBar;
    private TextView titleView;
    private boolean settingsPage;
    private int lang;

    private boolean flush, notile, noubwc, sysmem, nobin, noFastClear, forceCore, forceCompat;
    private boolean noShaderCache, noOutFence, importCache, glthread;
    private boolean noBarrier, noReadClear, noSameBatchFlush, noUncompress, dropDeferredClear, trustBoFlags;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private boolean zh() {
        int l = lang;
        if (l == 0) {
            String code = Locale.getDefault().getLanguage();
            l = (code != null && code.startsWith("zh")) ? 1 : 2;
        }
        return l == 1;
    }

    private String t(String z, String e) { return zh() ? z : e; }

    private int sysColor(String name, int fallback) {
        if (Build.VERSION.SDK_INT >= 31) {
            int id = getResources().getIdentifier(name, "color", "android");
            if (id != 0) {
                try { return getColor(id); } catch (Throwable ignored) { }
            }
        }
        return fallback;
    }

    private int themeColor(int attr, int fallback) {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                    && tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
            if (tv.resourceId != 0) {
                try { return getColor(tv.resourceId); } catch (Throwable ignored) { }
            }
        }
        return fallback;
    }

    private void initColors() {
        // 文本/背景用主题色，保证任何 ROM/深浅色下都可读；强调色尽量用系统动态色
        accent = sysColor("system_accent1_500", themeColor(android.R.attr.colorAccent, 0xFF6750A4));
        accentDark = sysColor("system_accent1_600", themeColor(android.R.attr.colorAccent, 0xFF4F378B));
        onSurface = themeColor(android.R.attr.textColorPrimary, 0xFF1C1B1F);
        onSurfaceVariant = themeColor(android.R.attr.textColorSecondary, 0xFF49454F);
        background = themeColor(android.R.attr.colorBackground, 0xFFF7F2FA);
        trackOff = sysColor("system_neutral2_300", 0xFFCAC4D0);
    }

    private File envFile() {
        return new File(android.os.Environment.getExternalStorageDirectory(),
                "Android/media/" + getPackageName() + "/" + FILE_NAME);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initColors();
        // 兼容旧版界面写入的 String（"SYSTEM"/"ZH"/"EN"）
        Object savedLang = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getAll().get("lang");
        if (savedLang instanceof Integer) {
            lang = (Integer) savedLang;
        } else if (savedLang instanceof String) {
            String v = (String) savedLang;
            lang = v.equals("ZH") ? 1 : (v.equals("EN") ? 2 : 0);
        } else {
            lang = 0;
        }
        load();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);
        root.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(16), dp(8), dp(8));
        titleView = new TextView(this);
        titleView.setTextSize(28f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(onSurface);
        header.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextSize(22f);
        gear.setTextColor(onSurfaceVariant);
        gear.setPadding(dp(12), dp(8), dp(12), dp(8));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { settingsPage = !settingsPage; render(); }
        });
        header.addView(gear);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomBar = new LinearLayout(this);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(20), dp(8), dp(20), dp(16));
        root.addView(bottomBar);

        setContentView(root);
        render();
    }

    private interface Setter { void set(boolean v); }

    private void render() {
        titleView.setText(settingsPage ? t("设置", "Settings") : t("配置", "Config"));
        content.removeAllViews();
        bottomBar.removeAllViews();
        bottomBar.setVisibility(settingsPage ? View.GONE : View.VISIBLE);

        if (!settingsPage) {
            section(t("驱动调试", "Driver debug"));
            toggle("flush", t("每次 draw 后强制 flush（排查同步问题，很慢）", "Force flush after each draw (slow)"), flush,
                    new Setter() { public void set(boolean v) { flush = v; } });
            toggle("notile", t("禁用 tiling", "Disable tiling"), notile, new Setter() { public void set(boolean v) { notile = v; } });
            toggle("noubwc", t("禁用 UBWC 压缩（排查花屏首选）", "Disable UBWC (try first for artifacts)"), noubwc,
                    new Setter() { public void set(boolean v) { noubwc = v; } });
            toggle("sysmem", t("绕过 GMEM，全部渲染到系统内存（很慢）", "Bypass GMEM (very slow)"), sysmem,
                    new Setter() { public void set(boolean v) { sysmem = v; } });
            toggle("nobin", t("禁用硬件 binning", "Disable hardware binning"), nobin,
                    new Setter() { public void set(boolean v) { nobin = v; } });
            section(t("其他", "Other"));
            toggle("FCL_NO_FAST_CLEAR", t("关闭 a7xx GMEM fast clear", "Disable a7xx GMEM fast clear"), noFastClear,
                    new Setter() { public void set(boolean v) { noFastClear = v; } });
            toggle(t("关闭着色器缓存", "Disable shader cache"),
                    t("FCL_NO_SHADER_CACHE=1：每次启动重新编译 shader（仅用于对照）",
                      "FCL_NO_SHADER_CACHE=1: recompile shaders every launch (for A/B only)"),
                    noShaderCache, new Setter() { public void set(boolean v) { noShaderCache = v; } });
            toggle(t("关闭 out-fence", "Disable out-fence"),
                    t("FCL_NO_OUT_FENCE=1：swap 不带 fence（可能撕裂，仅用于性能对照）",
                      "FCL_NO_OUT_FENCE=1: swap without fence (may tear, perf A/B only)"),
                    noOutFence, new Setter() { public void set(boolean v) { noOutFence = v; } });
            toggle(t("窗口导入缓存（实验）", "Window import cache (experimental)"),
                    t("FCL_IMPORT_CACHE=1：缓存窗口 dmabuf 的 __DRIimage，省掉每帧 import",
                      "FCL_IMPORT_CACHE=1: cache window dmabuf __DRIimage, skip per-frame import"),
                    importCache, new Setter() { public void set(boolean v) { importCache = v; } });
            toggle(t("线程化 GL（glthread）", "Threaded GL (glthread)"),
                    t("mesa_glthread=true：GL 调用攒批到独立线程（需重启，可能不兼容）",
                      "mesa_glthread=true: batch GL calls on a worker thread (restart; may break)"),
                    glthread, new Setter() { public void set(boolean v) { glthread = v; } });
            section(t("UBWC 精细调整（实验）", "UBWC tuning (experimental)"));
            toggle(t("跳过 UCHE 屏障配对", "Skip UCHE barrier pair"),
                    t("FCL_UBWC_NO_BARRIER=1：CCU clean 后不补 UCHE invalidate / WFI",
                      "FCL_UBWC_NO_BARRIER=1: no UCHE invalidate/WFI after CCU clean"),
                    noBarrier, new Setter() { public void set(boolean v) { noBarrier = v; } });
            toggle(t("跳过读前清 flag", "Skip read-before clear"),
                    t("FCL_UBWC_NO_READ_CLEAR=1：采样前不检查/清除 UBWC flag",
                      "FCL_UBWC_NO_READ_CLEAR=1: no flag check before sampling"),
                    noReadClear, new Setter() { public void set(boolean v) { noReadClear = v; } });
            toggle(t("跳过同批写后读 flush", "Skip same-batch flush"),
                    t("FCL_UBWC_NO_SAMEBATCH_FLUSH=1：写后读不再 flush 整批",
                      "FCL_UBWC_NO_SAMEBATCH_FLUSH=1: keep write+read in one batch"),
                    noSameBatchFlush, new Setter() { public void set(boolean v) { noSameBatchFlush = v; } });
            toggle(t("跳过 uncompress（危险）", "Skip uncompress (dangerous)"),
                    t("FCL_UBWC_NO_UNCOMPRESS=1：格式重解释不做 shadow，几乎必花屏",
                      "FCL_UBWC_NO_UNCOMPRESS=1: no shadow on reinterpret, likely corrupt"),
                    noUncompress, new Setter() { public void set(boolean v) { noUncompress = v; } });
            toggle(t("丢弃延迟 clear", "Drop deferred clear"),
                    t("FCL_UBWC_DROP_DEFERRED_CLEAR=1：batch 已提交时直接丢掉待清 flag",
                      "FCL_UBWC_DROP_DEFERRED_CLEAR=1: drop pending flag on flushed batch"),
                    dropDeferredClear, new Setter() { public void set(boolean v) { dropDeferredClear = v; } });
            toggle(t("信任 BO 旧 flag", "Trust recycled BO flags"),
                    t("FCL_UBWC_TRUST_BO_FLAGS=1：BO cache 复用时不清 flag",
                      "FCL_UBWC_TRUST_BO_FLAGS=1: don't clear flags on BO reuse"),
                    trustBoFlags, new Setter() { public void set(boolean v) { trustBoFlags = v; } });
            section("GL Profile");
            toggle(t("强制 Core Profile", "Force Core Profile"), t("严格现代 GL；部分光影包会编译失败", "Modern GL only; some packs fail"), forceCore,
                    new Setter() { public void set(boolean v) { forceCore = v; if (v) forceCompat = false; } });
            toggle(t("强制 Compatibility Profile", "Force Compatibility Profile"), t("保留旧特性；兼容性更好", "Legacy features; better compatibility"), forceCompat,
                    new Setter() { public void set(boolean v) { forceCompat = v; if (v) forceCore = false; } });
            section(t("状态", "Status"));
            TextView status = new TextView(this);
            status.setTextSize(12f);
            status.setTextColor(onSurfaceVariant);
            status.setPadding(dp(20), dp(4), dp(20), dp(8));
            status.setText(envFile().getAbsolutePath() + "\n" + readText());
            content.addView(status);

            Button clear = new Button(this);
            clear.setText(t("清空", "Clear"));
            clear.setBackgroundColor(Color.TRANSPARENT);
            clear.setTextColor(accentDark);
            clear.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { clearAll(); }
            });
            bottomBar.addView(clear);
            bottomBar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            Button save = new Button(this);
            save.setText(t("保存", "Save"));
            save.setTextColor(Color.WHITE);
            save.setBackgroundTintList(ColorStateList.valueOf(accent));
            save.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { save(); }
            });
            bottomBar.addView(save);
        } else {
            section(t("常规", "General"));
            radio(t("跟随系统", "Follow system"), 0);
            radio(t("简体中文", "简体中文"), 1);
            radio("English", 2);
            section(t("关于", "About"));
            TextView about = new TextView(this);
            about.setTextSize(14f);
            about.setTextColor(onSurfaceVariant);
            about.setPadding(dp(20), dp(4), dp(20), dp(8));
            about.setText("Freedreno-EGL · 1.0.3");
            content.addView(about);
        }
    }

    private void section(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(accentDark);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(20), dp(20), dp(20), dp(4));
        content.addView(tv);
    }

    private void toggle(String title, String subtitle, boolean checked, final Setter setter) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(10), dp(20), dp(10));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(16f);
        t1.setTextColor(onSurface);
        texts.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(subtitle);
        t2.setTextSize(13f);
        t2.setTextColor(onSurfaceVariant);
        texts.addView(t2);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setThumbTintList(new ColorStateList(
                new int[][] { { android.R.attr.state_checked }, {} },
                new int[] { Color.WHITE, trackOff }));
        sw.setTrackTintList(new ColorStateList(
                new int[][] { { android.R.attr.state_checked }, {} },
                new int[] { accent, trackOff }));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean v) { setter.set(v); }
        });
        row.addView(sw);

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sw.setChecked(!sw.isChecked()); }
        });
        content.addView(row);
    }

    private void radio(String label, final int value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(10), dp(20), dp(10));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16f);
        tv.setTextColor(onSurface);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final RadioButton rb = new RadioButton(this);
        rb.setChecked(lang == value);
        rb.setClickable(false);
        rb.setFocusable(false);
        row.addView(rb);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                lang = value;
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("lang", value).apply();
                render();
            }
        });
        content.addView(row);
    }

    private void load() {
        for (String line : readText().split("\n")) {
            line = line.trim();
            if (line.startsWith("FD_MESA_DEBUG=")) {
                String v = line.substring("FD_MESA_DEBUG=".length());
                flush = v.contains("flush");
                notile = v.contains("notile");
                noubwc = v.contains("noubwc");
                sysmem = v.contains("sysmem");
                nobin = v.contains("nobin");
            } else if (line.startsWith("FCL_NO_FAST_CLEAR=")) {
                noFastClear = true;
            } else if (line.startsWith("FCL_GL_FORCE_CORE=")) {
                forceCore = true;
            } else if (line.startsWith("FCL_GL_FORCE_COMPAT=")) {
                forceCompat = true;
            } else if (line.startsWith("FCL_NO_SHADER_CACHE=")) {
                noShaderCache = !line.endsWith("=0");
            } else if (line.startsWith("FCL_NO_OUT_FENCE=")) {
                noOutFence = !line.endsWith("=0");
            } else if (line.startsWith("FCL_IMPORT_CACHE=")) {
                importCache = !line.endsWith("=0");
            } else if (line.startsWith("mesa_glthread=")) {
                glthread = !line.endsWith("=0");
            } else if (line.startsWith("FCL_UBWC_NO_BARRIER=")) {
                noBarrier = !line.endsWith("=0");
            } else if (line.startsWith("FCL_UBWC_NO_READ_CLEAR=")) {
                noReadClear = !line.endsWith("=0");
            } else if (line.startsWith("FCL_UBWC_NO_SAMEBATCH_FLUSH=")) {
                noSameBatchFlush = !line.endsWith("=0");
            } else if (line.startsWith("FCL_UBWC_NO_UNCOMPRESS=")) {
                noUncompress = !line.endsWith("=0");
            } else if (line.startsWith("FCL_UBWC_DROP_DEFERRED_CLEAR=")) {
                dropDeferredClear = !line.endsWith("=0");
            } else if (line.startsWith("FCL_UBWC_TRUST_BO_FLAGS=")) {
                trustBoFlags = !line.endsWith("=0");
            }
        }
        if (forceCore && forceCompat) { forceCore = false; forceCompat = false; }
    }

    private String readText() {
        File f = envFile();
        if (!f.exists()) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    private void save() {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> dbg = new ArrayList<>();
        if (flush) dbg.add("flush");
        if (notile) dbg.add("notile");
        if (noubwc) dbg.add("noubwc");
        if (sysmem) dbg.add("sysmem");
        if (nobin) dbg.add("nobin");
        if (!dbg.isEmpty()) values.put("FD_MESA_DEBUG", String.join(",", dbg));
        if (noFastClear) values.put("FCL_NO_FAST_CLEAR", "1");
        if (noShaderCache) values.put("FCL_NO_SHADER_CACHE", "1");
        if (noOutFence) values.put("FCL_NO_OUT_FENCE", "1");
        if (importCache) values.put("FCL_IMPORT_CACHE", "1");
        if (glthread) values.put("mesa_glthread", "true");
        if (noBarrier) values.put("FCL_UBWC_NO_BARRIER", "1");
        if (noReadClear) values.put("FCL_UBWC_NO_READ_CLEAR", "1");
        if (noSameBatchFlush) values.put("FCL_UBWC_NO_SAMEBATCH_FLUSH", "1");
        if (noUncompress) values.put("FCL_UBWC_NO_UNCOMPRESS", "1");
        if (dropDeferredClear) values.put("FCL_UBWC_DROP_DEFERRED_CLEAR", "1");
        if (trustBoFlags) values.put("FCL_UBWC_TRUST_BO_FLAGS", "1");
        if (forceCore != forceCompat) {
            values.put(forceCore ? "FCL_GL_FORCE_CORE" : "FCL_GL_FORCE_COMPAT", "1");
        }
        write(values);
        Toast.makeText(this, t("已保存", "Saved"), Toast.LENGTH_SHORT).show();
        render();
    }

    private void clearAll() {
        flush = notile = noubwc = sysmem = nobin = noFastClear = forceCore = forceCompat = false;
        noShaderCache = noOutFence = importCache = glthread = false;
        noBarrier = noReadClear = noSameBatchFlush = noUncompress = dropDeferredClear = trustBoFlags = false;
        write(new LinkedHashMap<String, String>());
        render();
    }

    private void write(Map<String, String> values) {
        File f = envFile();
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write("# Freedreno-EGL (OpenGL 4.6) settings, written by MainActivity\n".getBytes("UTF-8"));
            out.write("# takes effect after force-stopping FCL\n".getBytes("UTF-8"));
            for (Map.Entry<String, String> e : values.entrySet()) {
                out.write((e.getKey() + "=" + e.getValue() + "\n").getBytes("UTF-8"));
            }
        } catch (Throwable e) {
            Toast.makeText(this, "write failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
