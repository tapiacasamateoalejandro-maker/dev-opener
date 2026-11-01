package com.example.devopener;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Launcher minimalista para TV: reloj, rejilla de apps y entradas de TV.
 * El fondo puede ser una imagen o un video que se coloca en una carpeta
 * llamada "Fondos" (en una memoria USB o en el almacenamiento de la TV).
 * Si no hay ninguno, usa un degradado animado.
 */
public class LauncherActivity extends Activity {

    private static final String[] VIDEO_EXT = {"mp4", "webm", "mkv", "3gp", "mov"};
    private static final String[] IMAGE_EXT = {"jpg", "jpeg", "png", "webp"};
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    private static final int KIND_APP = 0;
    private static final int KIND_SPECIAL = 1;
    private static final int KIND_UNINSTALL_ON = 2;
    private static final int KIND_UNINSTALL_OFF = 3;

    private static class Entry {
        String label;
        String detail;
        Drawable icon;
        Intent intent;
        int kind = KIND_SPECIAL;
        String pkg;
        boolean removable;
    }

    private static class Holder {
        ImageView icon;
        TextView label;
    }

    private FrameLayout bgFrame;
    private VideoView videoView;
    private GridView grid;
    private TextView banner;
    private TextView infoText;
    private EntryAdapter adapter;
    private boolean selectFirstNext = true;
    private boolean uninstallMode = false;
    private volatile String currentBgKey = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        bgFrame = new FrameLayout(this);
        root.addView(bgFrame, new FrameLayout.LayoutParams(MATCH, MATCH));
        showDefaultBackground();

        View dim = new View(this);
        dim.setBackgroundColor(0x66000000);
        root.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(48), dp(32), dp(48), dp(24));
        root.addView(content, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(56);
        time.setTextColor(Color.WHITE);
        content.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(20);
        date.setTextColor(0xCCFFFFFF);
        content.addView(date);

        banner = new TextView(this);
        banner.setTextSize(18);
        banner.setTextColor(Color.WHITE);
        banner.setBackgroundColor(0xCCB71C1C);
        banner.setPadding(dp(16), dp(8), dp(16), dp(8));
        banner.setText("MODO DESINSTALAR: elige una app para quitarla. "
                + "Para salir elige \"Salir\" o pulsa Atrás.");
        banner.setVisibility(View.GONE);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(8);
        content.addView(banner, bp);

        infoText = new TextView(this);
        infoText.setTextSize(14);
        infoText.setTextColor(0x99FFFFFF);
        infoText.setSingleLine(true);

        grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(150));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(12));
        grid.setHorizontalSpacing(dp(12));
        grid.setVerticalScrollBarEnabled(false);
        grid.setPadding(0, dp(24), 0, dp(8));
        grid.setSelector(makeSelector());
        adapter = new EntryAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onEntryClicked(adapter.getItem(position));
            }
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Entry e = adapter.getItem(position);
                String extra = e.detail != null ? "  ·  " + e.detail : "";
                infoText.setText(e.label + extra);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                infoText.setText("");
            }
        });
        content.addView(grid, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        content.addView(infoText);

        setContentView(root);
        grid.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null) {
            videoView.start();
        }
        loadEntries();
        loadBackground();
    }

    @Override
    protected void onPause() {
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (videoView != null) {
            videoView.stopPlayback();
        }
        super.onDestroy();
    }

    /** Un launcher no debe cerrarse con Atras; solo sirve para salir del modo desinstalar. */
    @Override
    public void onBackPressed() {
        if (uninstallMode) {
            setUninstallMode(false);
        }
    }

    // ------------------------------------------------------------------
    // Lista de apps y entradas
    // ------------------------------------------------------------------

    private void setUninstallMode(boolean on) {
        uninstallMode = on;
        selectFirstNext = true;
        banner.setVisibility(on ? View.VISIBLE : View.GONE);
        infoText.setText("");
        loadEntries();
    }

    private void onEntryClicked(Entry e) {
        if (e == null) {
            return;
        }
        if (e.kind == KIND_UNINSTALL_ON) {
            setUninstallMode(true);
        } else if (e.kind == KIND_UNINSTALL_OFF) {
            setUninstallMode(false);
        } else if (uninstallMode && e.kind == KIND_APP) {
            uninstall(e);
        } else {
            launch(e);
        }
    }

    /** Abre el aviso del sistema para desinstalar; ahi se confirma. */
    private void uninstall(Entry e) {
        if (e.pkg == null) {
            return;
        }
        Uri uri = Uri.parse("package:" + e.pkg);
        try {
            Intent i = new Intent(Intent.ACTION_DELETE, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        } catch (Exception ignored) {
            // se prueba la pantalla de informacion de la app
        }
        try {
            Intent i = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Toast.makeText(this, "Pulsa Desinstalar en esta pantalla", Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, "No se pudo desinstalar " + e.label
                    + " desde esta TV (" + ex.getClass().getSimpleName() + ")",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void loadEntries() {
        final boolean mode = uninstallMode;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<Entry> list = buildEntries(mode);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mode != uninstallMode) {
                            return; // el modo cambio mientras cargaba; ya hay otra carga en curso
                        }
                        adapter.setItems(list);
                        if (selectFirstNext) {
                            selectFirstNext = false;
                            grid.setSelection(0);
                            grid.requestFocus();
                        }
                    }
                });
            }
        }).start();
    }

    private ArrayList<Entry> buildEntries(boolean mode) {
        PackageManager pm = getPackageManager();
        ArrayList<Entry> out = new ArrayList<Entry>();

        if (!mode) {
            addTvInputs(out);
        }

        LinkedHashMap<String, Entry> apps = new LinkedHashMap<String, Entry>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(cat);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            for (ResolveInfo ri : list) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null) {
                    continue;
                }
                if (ai.packageName.equals(getPackageName()) && ai.name.endsWith("LauncherActivity")) {
                    continue;
                }
                if (apps.containsKey(ai.packageName)) {
                    continue;
                }
                Entry e = new Entry();
                e.label = String.valueOf(ri.loadLabel(pm));
                e.detail = ai.packageName;
                e.icon = ri.loadIcon(pm);
                e.kind = KIND_APP;
                e.pkg = ai.packageName;
                e.removable = ai.applicationInfo != null
                        && (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                        && !ai.packageName.equals(getPackageName());
                Intent li = new Intent(Intent.ACTION_MAIN);
                li.addCategory(Intent.CATEGORY_LAUNCHER);
                li.setComponent(new ComponentName(ai.packageName, ai.name));
                li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                e.intent = li;
                apps.put(ai.packageName, e);
            }
        }

        ArrayList<Entry> sorted = new ArrayList<Entry>(apps.values());
        Collections.sort(sorted, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        if (mode) {
            out.add(plain("Salir", "salir del modo desinstalar",
                    android.R.drawable.ic_menu_revert, KIND_UNINSTALL_OFF));
            for (Entry e : sorted) {
                if (e.removable) {
                    out.add(e);
                }
            }
            return out;
        }
        out.addAll(sorted);

        out.add(plain("Desinstalar apps", "quitar apps instaladas",
                android.R.drawable.ic_menu_delete, KIND_UNINSTALL_ON));
        out.add(special("Ajustes", "android.settings.SETTINGS", android.R.drawable.ic_menu_preferences));
        out.add(special("Cambiar launcher", "android.settings.HOME_SETTINGS", android.R.drawable.ic_menu_revert));
        return out;
    }

    private Entry special(String label, String action, int iconRes) {
        Entry e = new Entry();
        e.label = label;
        e.detail = action;
        e.icon = getResources().getDrawable(iconRes, getTheme());
        Intent i = new Intent(action);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        e.intent = i;
        return e;
    }

    /** Entrada del launcher que no abre nada por si misma (la maneja onEntryClicked). */
    private Entry plain(String label, String detail, int iconRes, int kind) {
        Entry e = new Entry();
        e.label = label;
        e.detail = detail;
        e.icon = getResources().getDrawable(iconRes, getTheme());
        e.kind = kind;
        return e;
    }

    private static String shortPkg(String pkg) {
        int i = pkg.lastIndexOf('.');
        return i >= 0 ? pkg.substring(i + 1) : pkg;
    }

    /** Agrega las entradas tipo HDMI/AV si la TV las expone al sistema. */
    private void addTvInputs(ArrayList<Entry> out) {
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) {
                return;
            }
            List<TvInputInfo> inputs = tim.getTvInputList();
            for (TvInputInfo ti : inputs) {
                if (!ti.isPassthroughInput()) {
                    continue;
                }
                Entry e = new Entry();
                String label = String.valueOf(ti.loadLabel(this));
                e.label = label.length() > 0 ? label : "Entrada";
                e.detail = "entrada " + ti.getId();
                Drawable ic = null;
                try {
                    ic = ti.loadIcon(this);
                } catch (Exception ignored) {
                    // se usa un icono generico
                }
                if (ic == null) {
                    ic = getResources().getDrawable(android.R.drawable.ic_menu_slideshow, getTheme());
                }
                e.icon = ic;
                Intent i = new Intent(Intent.ACTION_VIEW,
                        TvContract.buildChannelUriForPassthroughInput(ti.getId()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                e.intent = i;
                out.add(e);
            }
        } catch (Throwable ignored) {
            // la TV no expone entradas al sistema: se usan las apps de la lista
        }
    }

    private void launch(Entry e) {
        if (e == null || e.intent == null) {
            return;
        }
        try {
            startActivity(e.intent);
        } catch (Exception ex) {
            Toast.makeText(this, "No se pudo abrir " + e.label
                    + " (" + ex.getClass().getSimpleName() + ")", Toast.LENGTH_LONG).show();
        }
    }

    private class EntryAdapter extends BaseAdapter {
        private ArrayList<Entry> items = new ArrayList<Entry>();

        void setItems(ArrayList<Entry> list) {
            items = list;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Entry getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            View row = convertView;
            if (row == null) {
                LinearLayout ll = new LinearLayout(LauncherActivity.this);
                ll.setOrientation(LinearLayout.VERTICAL);
                ll.setGravity(Gravity.CENTER);
                ll.setPadding(dp(8), dp(10), dp(8), dp(10));
                ll.setLayoutParams(new AbsListView.LayoutParams(MATCH, dp(156)));

                h = new Holder();
                h.icon = new ImageView(LauncherActivity.this);
                h.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ll.addView(h.icon, new LinearLayout.LayoutParams(dp(84), dp(84)));

                h.label = new TextView(LauncherActivity.this);
                h.label.setTextSize(15);
                h.label.setTextColor(Color.WHITE);
                h.label.setGravity(Gravity.CENTER);
                h.label.setMaxLines(1);
                h.label.setEllipsize(TextUtils.TruncateAt.END);
                h.label.setPadding(0, dp(8), 0, 0);
                ll.addView(h.label, new LinearLayout.LayoutParams(MATCH,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                ll.setTag(h);
                row = ll;
            } else {
                h = (Holder) row.getTag();
            }
            Entry e = items.get(position);
            h.icon.setImageDrawable(e.icon);
            if (uninstallMode && e.kind == KIND_APP) {
                h.label.setMaxLines(2);
                h.label.setTextSize(13);
                h.label.setText(e.label + "\n" + shortPkg(e.pkg));
            } else {
                h.label.setMaxLines(1);
                h.label.setTextSize(15);
                h.label.setText(e.label);
            }
            return row;
        }
    }

    private Drawable makeSelector() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x33FFFFFF);
        g.setCornerRadius(dp(14));
        g.setStroke(dp(2), 0xCCFFFFFF);
        return g;
    }

    // ------------------------------------------------------------------
    // Fondo (degradado animado, imagen o video)
    // ------------------------------------------------------------------

    private void showDefaultBackground() {
        bgFrame.removeAllViews();
        videoView = null;
        bgFrame.addView(new GradientBackground(this), new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    private void showImageBackground(Bitmap bm) {
        bgFrame.removeAllViews();
        videoView = null;
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(bm);
        bgFrame.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    private void showVideoBackground(File f) {
        bgFrame.removeAllViews();
        VideoView vv = new VideoView(this);
        videoView = vv;
        vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(true);
                mp.setVolume(0f, 0f);
            }
        });
        vv.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                showDefaultBackground();
                return true;
            }
        });
        bgFrame.addView(vv, new FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER));
        vv.setVideoPath(f.getAbsolutePath());
        vv.start();
    }

    private void loadBackground() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final File f = resolveBackground();
                if (f == null) {
                    return;
                }
                final String key = f.getAbsolutePath() + "|" + f.lastModified() + "|" + f.length();
                if (key.equals(currentBgKey)) {
                    return;
                }
                if (hasExt(f.getName(), VIDEO_EXT)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            currentBgKey = key;
                            showVideoBackground(f);
                        }
                    });
                } else {
                    DisplayMetrics dm = getResources().getDisplayMetrics();
                    final Bitmap bm = decodeScaled(f, dm.widthPixels, dm.heightPixels);
                    if (bm != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                currentBgKey = key;
                                showImageBackground(bm);
                            }
                        });
                    }
                }
            }
        }).start();
    }

    /**
     * Busca un archivo de fondo en carpetas llamadas "Fondos". Si lo encuentra,
     * lo copia al almacenamiento interno de la app para que siga funcionando
     * aunque se quite la memoria USB.
     */
    private File resolveBackground() {
        File dir = getFilesDir();
        File found = findBackgroundSource();
        if (found != null) {
            File dest = new File(dir, "fondo." + extensionOf(found.getName()));
            boolean same = dest.exists()
                    && dest.length() == found.length()
                    && dest.lastModified() / 1000 == found.lastModified() / 1000;
            if (!same) {
                deleteCachedBackgrounds(dir);
                File tmp = new File(dir, "fondo.tmp");
                if (copyFile(found, tmp) && tmp.renameTo(dest)) {
                    dest.setLastModified(found.lastModified());
                } else {
                    tmp.delete();
                    return found;
                }
            }
            return dest;
        }
        for (String e : VIDEO_EXT) {
            File f = new File(dir, "fondo." + e);
            if (f.exists()) {
                return f;
            }
        }
        for (String e : IMAGE_EXT) {
            File f = new File(dir, "fondo." + e);
            if (f.exists()) {
                return f;
            }
        }
        return null;
    }

    private void deleteCachedBackgrounds(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.getName().startsWith("fondo.")) {
                f.delete();
            }
        }
    }

    private File findBackgroundSource() {
        ArrayList<File> dirs = new ArrayList<File>();
        try {
            File ext = Environment.getExternalStorageDirectory();
            if (ext != null) {
                searchFondos(ext, 1, dirs);
            }
            searchFondos(new File("/storage"), 3, dirs);
            searchFondos(new File("/mnt"), 4, dirs);
        } catch (Exception ignored) {
            // se usa lo que se haya encontrado
        }
        for (File d : dirs) {
            File[] files = d.listFiles();
            if (files == null) {
                continue;
            }
            Arrays.sort(files);
            for (File f : files) {
                if (f.isFile() && (hasExt(f.getName(), VIDEO_EXT) || hasExt(f.getName(), IMAGE_EXT))) {
                    return f;
                }
            }
        }
        return null;
    }

    private void searchFondos(File dir, int depth, ArrayList<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            if (!k.isDirectory()) {
                continue;
            }
            if (k.getName().equalsIgnoreCase("Fondos")) {
                out.add(k);
            } else if (depth > 1) {
                searchFondos(k, depth - 1, out);
            }
        }
    }

    private static boolean hasExt(String name, String[] exts) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String e : exts) {
            if (n.endsWith("." + e)) {
                return true;
            }
        }
        return false;
    }

    private static String extensionOf(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "bin";
    }

    private static boolean copyFile(File from, File to) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(from);
            out = new FileOutputStream(to);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
                // nada que hacer
            }
        }
    }

    private static Bitmap decodeScaled(File f, int reqW, int reqH) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= reqW && o.outHeight / (s * 2) >= reqH) {
                s *= 2;
            }
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        } catch (Throwable t) {
            return null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Degradado oscuro que cambia de color muy despacio. */
    private static class GradientBackground extends View {
        private final GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{0xFF101830, 0xFF281040});
        private float hue = 220f;
        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                hue = (hue + 0.9f) % 360f;
                int c1 = Color.HSVToColor(new float[]{hue, 0.55f, 0.28f});
                int c2 = Color.HSVToColor(new float[]{(hue + 70f) % 360f, 0.65f, 0.16f});
                drawable.setColors(new int[]{c1, c2});
                invalidate();
                postDelayed(this, 100);
            }
        };

        GradientBackground(Context c) {
            super(c);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            post(tick);
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(tick);
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawable.setBounds(0, 0, getWidth(), getHeight());
            drawable.draw(canvas);
        }
    }
}
