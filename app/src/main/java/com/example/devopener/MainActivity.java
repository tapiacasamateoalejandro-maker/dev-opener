package com.example.devopener;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Pantalla simple con botones que intentan abrir las opciones de desarrollador
 * por distintas rutas. Funciona con control remoto (los botones son enfocables).
 */
public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Abrir opciones de desarrollador");
        title.setTextSize(26);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0, dp(12), 0, dp(12));
        status.setText("Prueba los botones de arriba hacia abajo. "
                + "Si uno no abre nada o se cierra solo, pasa al siguiente.");
        root.addView(status);

        Button first = addButton(root, "1. Opciones de desarrollador", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(new Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"), "Ruta 1");
            }
        });

        addButton(root, "2. Ruta alterna (DevelopmentSettings)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent();
                i.setComponent(new ComponentName("com.android.settings",
                        "com.android.settings.DevelopmentSettings"));
                open(i, "Ruta 2");
            }
        });

        addButton(root, "3. Ruta alterna (panel moderno)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent();
                i.setComponent(new ComponentName("com.android.settings",
                        "com.android.settings.Settings$DevelopmentSettingsDashboardActivity"));
                open(i, "Ruta 3");
            }
        });

        addButton(root, "4. Información del dispositivo", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(new Intent("android.settings.DEVICE_INFO_SETTINGS"), "Información del dispositivo");
            }
        });

        addButton(root, "5. Activar modo desarrollador y depuración USB", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableDeveloperMode();
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        first.requestFocus();
    }

    private Button addButton(LinearLayout parent, String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(20);
        b.setFocusable(true);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        parent.addView(b, lp);
        return b;
    }

    private void open(Intent intent, String name) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            status.setText(name + ": enviado. Si no se abrió nada o se cerró solo, prueba el siguiente.");
        } catch (Exception e) {
            status.setText(name + ": no disponible en esta TV (" + e.getClass().getSimpleName() + ").");
        }
    }

    private void enableDeveloperMode() {
        try {
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.ADB_ENABLED, 1);
            status.setText("Listo: modo desarrollador y depuración USB activados. "
                    + "Ahora prueba el botón 1.");
        } catch (SecurityException e) {
            status.setText("Sin permiso: Android solo deja hacer esto si se concede "
                    + "WRITE_SECURE_SETTINGS por ADB. Usa los botones 1 a 3.");
        } catch (Exception e) {
            status.setText("No se pudo activar (" + e.getClass().getSimpleName() + ").");
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
