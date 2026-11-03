package com.hyundai.gaminglauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int BG_PICKER = 101;
    private LinearLayout root;
    private LinearLayout content;
    private TextView status;
    private final List<AppEntry> apps = new ArrayList<>();
    private boolean showingApps = true;

    private static class AppEntry {
        String label;
        String packageName;
        android.graphics.drawable.Drawable icon;
        boolean system;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        buildHome();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable panel(int color, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(12));
        d.setStroke(dp(2), strokeColor);
        return d;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setFocusable(true);
        b.setBackground(panel(Color.rgb(28, 28, 32), Color.rgb(220, 35, 48)));
        b.setOnClickListener(v -> action.run());
        b.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(panel(focused ? Color.rgb(120, 15, 25) : Color.rgb(28, 28, 32),
                    focused ? Color.WHITE : Color.rgb(220, 35, 48)));
        });
        return b;
    }

    private void buildHome() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(18), dp(28), dp(18));
        root.setBackgroundColor(Color.rgb(8, 8, 11));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView brand = text("HYUNDAI  /  GAMING", 27, Color.WHITE);
        brand.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1));

        TextView accent = text("LAUNCHER  //  0.1", 14, Color.rgb(255, 55, 65));
        accent.setGravity(Gravity.CENTER);
        header.addView(accent, new LinearLayout.LayoutParams(-2, dp(48)));
        root.addView(header);

        TextView subtitle = text("TU CENTRO DE CONTROL", 12, Color.rgb(180, 180, 188));
        subtitle.setPadding(0, 0, 0, dp(12));
        root.addView(subtitle);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button all = button("APLICACIONES", () -> showApps());
        Button customize = button("FONDO / PERSONALIZAR", () -> chooseBackground());
        Button settings = button("AJUSTES DEL SISTEMA", () -> openSettings());
        nav.addView(all, new LinearLayout.LayoutParams(0, dp(48), 1));
        nav.addView(customize, new LinearLayout.LayoutParams(0, dp(48), 1));
        nav.addView(settings, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(nav);

        status = text("Selecciona una aplicación con las flechas y pulsa ENTER.", 13,
                Color.rgb(220, 55, 65));
        status.setPadding(0, dp(10), 0, dp(8));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView footer = text("FLECHAS: NAVEGAR     ENTER: ABRIR     ESC: VOLVER", 11,
                Color.rgb(125, 125, 135));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(8), 0, 0);
        root.addView(footer);

        setContentView(root);
        showApps();
    }

    private void showApps() {
        showingApps = true;
        content.removeAllViews();
        status.setText("APLICACIONES INSTALADAS  •  Selecciona para abrir");
        loadApps();
        if (apps.isEmpty()) {
            content.addView(text("No se encontraron aplicaciones con acceso directo.", 16, Color.WHITE));
            return;
        }

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        for (AppEntry app : apps) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(8), dp(10), dp(8), dp(10));
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setBackground(panel(Color.rgb(22, 22, 27), Color.rgb(180, 25, 40)));
            if (app.icon != null) {
                android.widget.ImageView icon = new android.widget.ImageView(this);
                icon.setImageDrawable(app.icon);
                tile.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
            }
            TextView name = text(app.label, 14, Color.WHITE);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            tile.addView(name, new LinearLayout.LayoutParams(-1, dp(42)));
            tile.setOnFocusChangeListener((v, focused) -> {
                v.setBackground(panel(focused ? Color.rgb(100, 15, 25) : Color.rgb(22, 22, 27),
                        focused ? Color.WHITE : Color.rgb(220, 35, 48)));
            });
            tile.setOnClickListener(v -> launchApp(app));
            tile.setOnLongClickListener(v -> {
                showAppActions(app);
                return true;
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(112);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(5), dp(5), dp(5), dp(5));
            grid.addView(tile, lp);
        }
        content.addView(grid);
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN, null);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> results = pm.queryIntentActivities(query, 0);
        for (ResolveInfo ri : results) {
            if (ri.activityInfo == null) continue;
            AppEntry e = new AppEntry();
            e.label = ri.loadLabel(pm).toString();
            e.packageName = ri.activityInfo.packageName;
            e.icon = ri.loadIcon(pm);
            try {
                ApplicationInfo ai = pm.getApplicationInfo(e.packageName, 0);
                e.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            } catch (Exception ignored) {}
            if (!e.packageName.equals(getPackageName())) apps.add(e);
        }
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase()));
    }

    private void launchApp(AppEntry app) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (launch != null) startActivity(launch);
            else Toast.makeText(this, "Esta aplicación no ofrece un acceso directo.", Toast.LENGTH_SHORT).show();
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No se pudo abrir la aplicación.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppActions(AppEntry app) {
        List<String> choices = new ArrayList<>();
        choices.add("Abrir");
        if (!app.system) choices.add("Desinstalar aplicación");
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) launchApp(app);
                    else if (!app.system) confirmUninstall(app);
                }).show();
    }

    private void confirmUninstall(AppEntry app) {
        new AlertDialog.Builder(this)
                .setTitle("Desinstalar aplicación")
                .setMessage("¿Quieres solicitar la desinstalación de \"" + app.label + "\"? Android mostrará su confirmación.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar", (d, w) -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                        i.setData(Uri.parse("package:" + app.packageName));
                        startActivity(i);
                    } catch (Exception ex) {
                        Toast.makeText(this, "Android no permitió abrir el desinstalador.", Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void chooseBackground() {
        final String[] options = {"Elegir imagen", "Elegir vídeo MP4", "Cancelar"};
        new AlertDialog.Builder(this).setTitle("Personalizar fondo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0 || which == 1) {
                        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                        i.setType(which == 0 ? "image/*" : "video/mp4");
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        try {
                            startActivityForResult(Intent.createChooser(i, "Selecciona tu fondo"), BG_PICKER + which);
                        } catch (Exception e) {
                            Toast.makeText(this, "Este sistema no ofrece un selector compatible. Probamos otra opción después.", Toast.LENGTH_LONG).show();
                        }
                    }
                }).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            String kind = requestCode == BG_PICKER ? "imagen" : "vídeo";
            Toast.makeText(this, "Fondo seleccionado (" + kind + "). En la siguiente iteración añadiremos la vista previa y el guardado.", Toast.LENGTH_LONG).show();
            status.setText("Fondo seleccionado. Aplicación del fondo: siguiente iteración.");
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "No se pudieron abrir los ajustes del sistema.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK) {
            if (showingApps) {
                new AlertDialog.Builder(this)
                        .setTitle("HYUNDAI GAMING LAUNCHER")
                        .setMessage("Escape vuelve atrás. ¿Quieres salir del launcher?")
                        .setNegativeButton("Seguir aquí", null)
                        .setPositiveButton("Salir", (d, w) -> finish()).show();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
