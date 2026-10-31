package com.example.devopener;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Muestra la IP de la TV y el estado de la depuración, y tiene botones que
 * intentan abrir las opciones de desarrollador por distintas rutas.
 * Funciona con control remoto (los botones son enfocables).
 */
public class MainActivity extends Activity {

    private TextView info;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Dev Opener v2");
        title.setTextSize(26);
        root.addView(title);

        info = new TextView(this);
        info.setTextSize(22);
        info.setPadding(0, dp(12), 0, dp(4));
        root.addView(info);

        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0, dp(8), 0, dp(12));
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

        addButton(root, "6. Actualizar IP y estado", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshInfo();
                status.setText("Datos actualizados.");
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        refreshInfo();
        first.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (info != null) {
            refreshInfo();
        }
    }

    private void refreshInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("IP de esta TV (wlan0 = Wi-Fi, eth0 = cable):\n");
        sb.append(findIps());
        int adb = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0);
        sb.append("\nDepuración USB: ").append(adb == 1 ? "ACTIVADA" : "desactivada").append("\n");
        sb.append("Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Para conectar desde la PC: adb connect IP:5555");
        info.setText(sb.toString());
    }

    private String findIps() {
        StringBuilder sb = new StringBuilder();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        sb.append("  ").append(ni.getName()).append(": ")
                                .append(a.getHostAddress()).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            sb.append("  Error al leer la red (").append(e.getClass().getSimpleName()).append(")\n");
        }
        if (sb.length() == 0) {
            return "  No se encontró IP. Revisa que la TV esté conectada a la red.\n";
        }
        return sb.toString();
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
            refreshInfo();
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
