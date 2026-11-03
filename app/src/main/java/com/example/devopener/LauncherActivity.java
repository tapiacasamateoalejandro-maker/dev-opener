package com.example.devopener;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
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
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Launcher estilo tvOS/Play, con tres pantallas (pestañas arriba): Apps,
 * Entradas y Ajustes. Fondo de imagen o video elegido desde la app o desde
 * una carpeta "Fondos" (USB o almacenamiento); sin fondo propio usa un
 * degradado animado.
 */
public class LauncherActivity extends Activity {

    private static final String[] VIDEO_EXT = {"mp4", "webm", "mkv", "3gp", "mov"};
    private static final String[] IMAGE_EXT = {"jpg", "jpeg", "png", "webp"};
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int REQ_PICK_BG = 501;
    private static final String PREFS = "launcher_prefs";
    private static final String KEY_ORDER = "orden_apps";

    private static final int TAB_APPS = 0;
    private static final int TAB_INPUTS = 1;
    private static final int TAB_SETTINGS = 2;
    private static final String[] TAB_LABELS = {"Aplicaciones", "Entradas", "Ajustes"};
    private static final int[] TAB_COLORS = {0xFF3DDC84, 0xFFFFA726, 0xFFBB86FC};

    private static final int KIND_APP = 0;
    private static final int KIND_SPECIAL = 1;
    private static final int KIND_UNINSTALL_ON = 2;
    private static final int KIND_UNINSTALL_OFF = 3;
    private static final int KIND_MOVE_ON = 4;
    private static final int KIND_MOVE_OFF = 5;
    private static final int KIND_PICK_BG = 6;
    private static final int KIND_INFO = 7;

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
        FrameLayout card;
        ImageView icon;
        TextView label;
    }

    private FrameLayout bgFrame;
    private VideoView videoView;
    private LinearLayout tabBar;
    private TextView[] tabViews = new TextView[TAB_LABELS.length];
    private View[] tabUnderlines = new View[TAB_LABELS.length];
    private GridView grid;
    private TextView banner;
    private TextView infoText;
    private EntryAdapter adapter;
    private int currentTab = TAB_APPS;
    private boolean selectFirstNext = true;
    private boolean uninstallMode = false;
    private boolean moveMode = false;
    private int pickedPosition = -1;
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

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(header, new LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(44);
        time.setTextColor(Color.WHITE);
        clockBox.addView(time);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(16);
        date.setTextColor(0xCCFFFFFF);
        clockBox.addView(date);
        header.addView(clockBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int idx = i;
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(dp(18), dp(6), dp(18), dp(4));

            TextView t = new TextView(this);
            t.setText(TAB_LABELS[i]);
            t.setTextSize(18);
            t.setTextColor(0xAAFFFFFF);
            t.setFocusable(true);
            t.setFocusableInTouchMode(true);
            t.setClickable(true);
            t.setPadding(dp(4), dp(4), dp(4), dp(4));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTab(idx);
                }
            });
            t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        switchTab(idx);
                    }
                }
            });
            box.addView(t);

            View underline = new View(this);
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(MATCH, dp(3));
            ulp.topMargin = dp(4);
            box.addView(underline, ulp);

            tabViews[i] = t;
            tabUnderlines[i] = underline;
            tabBar.addView(box);
        }
        header.addView(tabBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        banner = new TextView(this);
        banner.setTextSize(18);
        banner.setTextColor(Color.WHITE);
        banner.setPadding(dp(16), dp(8), dp(16), dp(8));
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
        grid.setColumnWidth(dp(170));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(18));
        grid.setHorizontalSpacing(dp(18));
        grid.setVerticalScrollBarEnabled(false);
        grid.setPadding(0, dp(28), 0, dp(8));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        adapter = new EntryAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onEntryClicked(position);
            }
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Entry e = adapter.getItem(position);
                String extra = e.detail != null && e.kind == KIND_APP ? "  ·  " + e.detail : "";
                infoText.setText(e.label + extra);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                infoText.setText("");
            }
        });
        content.addView(grid, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        content.addView(infoText);

        setContentView(root);
        updateTabsUi();
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

    /** Un launcher no debe cerrarse con Atras; sirve para salir de los modos especiales. */
    @Override
    public void onBackPressed() {
        if (pickedPosition != -1) {
            pickedPosition = -1;
            adapter.notifyDataSetChanged();
        } else if (uninstallMode) {
            setUninstallMode(false);
        } else if (moveMode) {
            setMoveMode(false);
        } else if (currentTab != TAB_APPS) {
            tabViews[TAB_APPS].requestFocus();
        }
    }

    /**
     * Mientras hay una app "levantada" para moverla, las flechas la desplazan
     * en vez de mover el foco a otra app.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (moveMode && pickedPosition != -1) {
            int delta = 0;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                delta = 1;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                delta = -1;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                delta = adapter.columnsEstimate();
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                delta = -adapter.columnsEstimate();
            }
            if (delta != 0) {
                movePicked(delta);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                dropPicked();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------------
    // Pestañas (pantallas)
    // ------------------------------------------------------------------

    private void switchTab(int tab) {
        boolean changed = tab != currentTab;
        currentTab = tab;
        if (changed) {
            uninstallMode = false;
            moveMode = false;
            pickedPosition = -1;
            banner.setVisibility(View.GONE);
        }
        updateTabsUi();
        if (changed) {
            selectFirstNext = true;
            grid.setAlpha(0.15f);
            loadEntries();
        }
    }

    private void updateTabsUi() {
        for (int i = 0; i < TAB_LABELS.length; i++) {
            boolean sel = i == currentTab;
            tabViews[i].setTextColor(sel ? Color.WHITE : 0x99FFFFFF);
            tabViews[i].setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tabUnderlines[i].setBackgroundColor(sel ? TAB_COLORS[i] : Color.TRANSPARENT);
        }
    }

    // ------------------------------------------------------------------
    // Modo desinstalar / modo mover (solo en la pantalla de Apps)
    // ------------------------------------------------------------------

    private void setUninstallMode(boolean on) {
        uninstallMode = on;
        moveMode = false;
        pickedPosition = -1;
        selectFirstNext = true;
        updateBanner();
        loadEntries();
    }

    private void setMoveMode(boolean on) {
        moveMode = on;
        uninstallMode = false;
        pickedPosition = -1;
        selectFirstNext = true;
        updateBanner();
        loadEntries();
    }

    private void updateBanner() {
        if (uninstallMode) {
            banner.setBackgroundColor(0xCCB71C1C);
            banner.setText("DESINSTALAR: elige una app para quitarla. \"Salir\" o Atrás para terminar.");
            banner.setVisibility(View.VISIBLE);
        } else if (moveMode) {
            banner.setBackgroundColor(0xCC1C4FB7);
            banner.setText(pickedPosition == -1
                    ? "MOVER APPS: elige una app, y con las flechas cámbiala de lugar."
                    : "Mueve con las flechas y pulsa OK para soltarla. \"Salir\" o Atrás para terminar.");
            banner.setVisibility(View.VISIBLE);
        } else {
            banner.setVisibility(View.GONE);
        }
    }

    private void onEntryClicked(int position) {
        Entry e = adapter.getItem(position);
        if (e == null) {
            return;
        }
        if (e.kind == KIND_UNINSTALL_ON) {
            switchTab(TAB_APPS);
            setUninstallMode(true);
        } else if (e.kind == KIND_UNINSTALL_OFF) {
            setUninstallMode(false);
        } else if (e.kind == KIND_MOVE_ON) {
            switchTab(TAB_APPS);
            setMoveMode(true);
        } else if (e.kind == KIND_MOVE_OFF) {
            setMoveMode(false);
        } else if (e.kind == KIND_PICK_BG) {
            pickBackground();
        } else if (e.kind == KIND_INFO) {
            // no hace nada: solo explica algo en pantalla
        } else if (uninstallMode && e.kind == KIND_APP) {
            uninstall(e);
        } else if (moveMode && e.kind == KIND_APP) {
            if (pickedPosition == position) {
                dropPicked();
            } else {
                pickedPosition = position;
                updateBanner();
                adapter.notifyDataSetChanged();
            }
        } else {
            launch(e);
        }
    }

    private void dropPicked() {
        pickedPosition = -1;
        updateBanner();
        adapter.notifyDataSetChanged();
        saveOrder();
    }

    private void movePicked(int delta) {
        int from = pickedPosition;
        int to = from + delta;
        ArrayList<Entry> items = adapter.items;
        if (to < 0 || to >= items.size()) {
            return;
        }
        // No se cruza con las entradas fijas del final (Salir, etc.).
        if (items.get(to).kind != KIND_APP) {
            return;
        }
        Collections.swap(items, from, to);
        pickedPosition = to;
        adapter.notifyDataSetChanged();
        grid.setSelection(to);
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

    // ------------------------------------------------------------------
    // Carga de cada pantalla
    // ------------------------------------------------------------------

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void saveOrder() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : adapter.items) {
            if (e.kind == KIND_APP && e.pkg != null) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(e.pkg);
            }
        }
        prefs().edit().putString(KEY_ORDER, sb.toString()).apply();
    }

    private List<String> savedOrder() {
        String s = prefs().getString(KEY_ORDER, "");
        if (TextUtils.isEmpty(s)) {
            return Collections.emptyList();
        }
        return Arrays.asList(s.split(","));
    }

    private void loadEntries() {
        final int tab = currentTab;
        final boolean uMode = uninstallMode;
        final boolean mMode = moveMode;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<Entry> list;
                if (tab == TAB_INPUTS) {
                    list = buildInputsEntries();
                } else if (tab == TAB_SETTINGS) {
                    list = buildSettingsEntries();
                } else {
                    list = buildAppsEntries(uMode, mMode);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (tab != currentTab || uMode != uninstallMode || mMode != moveMode) {
                            return; // la pantalla o el modo cambiaron mientras cargaba
                        }
                        adapter.setItems(list);
                        grid.animate().alpha(1f).setDuration(180).start();
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

    private ArrayList<Entry> buildAppsEntries(boolean uMode, boolean mMode) {
        PackageManager pm = getPackageManager();
        ArrayList<Entry> out = new ArrayList<Entry>();

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

        ArrayList<Entry> sorted = sortByCustomOrder(apps);

        if (uMode) {
            out.add(plain("Salir", "salir del modo desinstalar",
                    android.R.drawable.ic_menu_revert, KIND_UNINSTALL_OFF));
            for (Entry e : sorted) {
                if (e.removable) {
                    out.add(e);
                }
            }
            return out;
        }
        if (mMode) {
            out.add(plain("Salir", "salir del modo mover",
                    android.R.drawable.ic_menu_revert, KIND_MOVE_OFF));
            out.addAll(sorted);
            return out;
        }

        out.addAll(sorted);
        return out;
    }

    /** Entradas de video (HDMI, AV) que la TV avise al sistema, si las expone. */
    private ArrayList<Entry> buildInputsEntries() {
        ArrayList<Entry> out = new ArrayList<Entry>();
        addTvInputs(out);
        if (out.isEmpty()) {
            Entry e = plain("Sin entradas detectadas",
                    "Esta TV no le avisa a las apps sobre sus entradas HDMI o AV",
                    android.R.drawable.ic_dialog_info, KIND_INFO);
            out.add(e);
        }
        return out;
    }

    private ArrayList<Entry> buildSettingsEntries() {
        ArrayList<Entry> out = new ArrayList<Entry>();
        out.add(plain("Mover apps", "cambiar el orden",
                android.R.drawable.ic_menu_sort_alphabetically, KIND_MOVE_ON));
        out.add(plain("Desinstalar apps", "quitar apps instaladas",
                android.R.drawable.ic_menu_delete, KIND_UNINSTALL_ON));
        out.add(plain("Elegir fondo", "imagen o video de tu TV",
                android.R.drawable.ic_menu_gallery, KIND_PICK_BG));
        out.add(special("Ajustes del sistema", "android.settings.SETTINGS", android.R.drawable.ic_menu_preferences));
        out.add(special("Cambiar launcher", "android.settings.HOME_SETTINGS", android.R.drawable.ic_menu_revert));
        return out;
    }

    /** Pone primero las apps en el orden guardado; las nuevas van alfabeticas al final. */
    private ArrayList<Entry> sortByCustomOrder(LinkedHashMap<String, Entry> apps) {
        List<String> order = savedOrder();
        ArrayList<Entry> out = new ArrayList<Entry>();
        HashMap<String, Entry> pool = new HashMap<String, Entry>(apps);
        for (String pkg : order) {
            Entry e = pool.remove(pkg);
            if (e != null) {
                out.add(e);
            }
        }
        ArrayList<Entry> rest = new ArrayList<Entry>(pool.values());
        Collections.sort(rest, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        out.addAll(rest);
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
            // la TV no expone entradas al sistema: se muestra el aviso de "sin entradas"
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

    // ------------------------------------------------------------------
    // Posters (adaptador con animacion al enfocar, estilo tvOS)
    // ------------------------------------------------------------------

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

        /** Estimado de columnas visibles, para que Arriba/Abajo muevan filas enteras. */
        int columnsEstimate() {
            int cw = grid.getColumnWidth() + dp(18);
            int w = grid.getWidth();
            if (cw <= 0 || w <= 0) {
                return 4;
            }
            int n = w / cw;
            return Math.max(1, n);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Holder h;
            View cell = convertView;
            if (cell == null) {
                LinearLayout ll = new LinearLayout(LauncherActivity.this);
                ll.setOrientation(LinearLayout.VERTICAL);
                ll.setGravity(Gravity.CENTER);
                ll.setLayoutParams(new AbsListView.LayoutParams(MATCH, dp(176)));

                h = new Holder();
                h.card = new FrameLayout(LauncherActivity.this);
                h.card.setBackground(makeCard(false, false, TAB_COLORS[TAB_APPS]));
                FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(108), dp(108));
                ll.addView(h.card, clp);

                h.icon = new ImageView(LauncherActivity.this);
                h.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int m = dp(14);
                FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(MATCH, MATCH);
                ilp.setMargins(m, m, m, m);
                h.card.addView(h.icon, ilp);

                h.label = new TextView(LauncherActivity.this);
                h.label.setTextSize(14);
                h.label.setTextColor(Color.WHITE);
                h.label.setGravity(Gravity.CENTER);
                h.label.setMaxLines(2);
                h.label.setEllipsize(TextUtils.TruncateAt.END);
                h.label.setPadding(dp(4), dp(10), dp(4), 0);
                ll.addView(h.label, new LinearLayout.LayoutParams(MATCH,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                ll.setTag(h);
                cell = ll;
            } else {
                h = (Holder) cell.getTag();
            }

            Entry e = items.get(position);
            h.icon.setImageDrawable(e.icon);
            h.icon.setAlpha(e.kind == KIND_INFO ? 0.6f : 1f);

            boolean selected = position == grid.getSelectedItemPosition();
            boolean picked = position == pickedPosition;
            int accent = TAB_COLORS[currentTab];

            h.card.setBackground(makeCard(selected, picked, accent));
            h.card.setScaleX(selected ? 1.18f : 1f);
            h.card.setScaleY(selected ? 1.18f : 1f);
            h.card.setTranslationY(selected ? -dp(6) : 0);

            if (e.kind == KIND_APP && uninstallMode) {
                h.label.setText(e.label + "\n" + shortPkg(e.pkg));
            } else if (e.kind == KIND_INFO) {
                h.label.setText(e.label + "\n" + e.detail);
            } else {
                h.label.setText(e.label);
            }
            boolean alwaysShow = e.kind == KIND_INFO && items.size() == 1;
            h.label.setVisibility(selected || picked || alwaysShow ? View.VISIBLE : View.INVISIBLE);
            h.label.setTextColor(picked ? 0xFFFFD54F : Color.WHITE);

            return cell;
        }
    }

    private Drawable makeCard(boolean selected, boolean picked, int accent) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        if (picked) {
            g.setColor(0x552196F3);
            g.setStroke(dp(3), 0xFFFFD54F);
        } else if (selected) {
            g.setColor(0x33FFFFFF);
            g.setStroke(dp(3), accent);
        } else {
            g.setColor(0x1AFFFFFF);
            g.setStroke(dp(1), 0x33FFFFFF);
        }
        return g;
    }

    // ------------------------------------------------------------------
    // Fondo: elegido desde la app, carpeta "Fondos", o degradado animado
    // ------------------------------------------------------------------

    /** Abre el selector de archivos del sistema para elegir una imagen o video. */
    private void pickBackground() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "Elegir fondo"), REQ_PICK_BG);
        } catch (Exception e) {
            Toast.makeText(this, "Esta TV no tiene un selector de archivos "
                    + "(" + e.getClass().getSimpleName() + "). Usa una carpeta \"Fondos\" en una USB.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_BG || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        Toast.makeText(this, "Copiando fondo…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = saveCustomBackground(uri);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            currentBgKey = null;
                            loadBackground();
                            Toast.makeText(LauncherActivity.this, "Fondo actualizado", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(LauncherActivity.this,
                                    "No se pudo usar ese archivo como fondo", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private boolean saveCustomBackground(Uri uri) {
        String ext = guessExtension(uri);
        if (!hasExt("x." + ext, VIDEO_EXT) && !hasExt("x." + ext, IMAGE_EXT)) {
            ext = "jpg"; // formato no identificado: se intenta como imagen
        }
        File dir = getFilesDir();
        clearBackgroundFiles(dir);
        File tmp = new File(dir, "fondo_personalizado.tmp");
        InputStream in = null;
        OutputStream out = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) {
                return false;
            }
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } catch (Exception e) {
            tmp.delete();
            return false;
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
        File dest = new File(dir, "fondo_personalizado." + ext);
        return tmp.renameTo(dest);
    }

    private String guessExtension(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                if (name != null && name.contains(".")) {
                    return extensionOf(name);
                }
            }
        } catch (Exception ignored) {
            // se intenta por el tipo MIME
        } finally {
            if (c != null) {
                c.close();
            }
        }
        String type = getContentResolver().getType(uri);
        if (type != null) {
            if (type.startsWith("video/")) {
                return type.contains("webm") ? "webm" : "mp4";
            }
            if (type.contains("png")) {
                return "png";
            }
        }
        return "jpg";
    }

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
     * Orden de busqueda del fondo: 1) el elegido desde la app, 2) una carpeta
     * "Fondos" en USB o almacenamiento (se copia una vez al almacenamiento
     * interno), 3) ninguno (se usa el degradado).
     */
    private File resolveBackground() {
        File dir = getFilesDir();
        for (String e : VIDEO_EXT) {
            File f = new File(dir, "fondo_personalizado." + e);
            if (f.exists()) {
                return f;
            }
        }
        for (String e : IMAGE_EXT) {
            File f = new File(dir, "fondo_personalizado." + e);
            if (f.exists()) {
                return f;
            }
        }

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

    private void clearBackgroundFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.getName().startsWith("fondo_personalizado.")) {
                f.delete();
            }
        }
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
