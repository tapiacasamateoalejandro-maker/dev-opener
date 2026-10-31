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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Muestra la IP de la TV, el estado de la depuración y si el ADB por red
 * (puerto 5555) esta activo, y tiene botones que intentan abrir las opciones
 * de desarrollador por distintas rutas. Funciona con control remoto.
 */
public class MainActivity extends Activity {

    private static final Pattern FIB_IP = Pattern.compile("\\|--\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)");
    private static final Pattern CMD_IP = Pattern.compile("inet(?: addr:)?\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)");

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
        title.setText("Dev Opener v3");
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
        ArrayList<String> ips = findIps();
        if (ips.isEmpty()) {
            sb.append("  No se pudo leer la IP.\n");
        } else {
            for (String s : ips) {
                sb.append("  ").append(s).append("\n");
            }
        }
        int adb = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0);
        sb.append("\nDepuración USB: ").append(adb == 1 ? "ACTIVADA" : "desactivada").append("\n");
        sb.append("ADB por red (puerto 5555): ").append(portStatus()).append("\n");
        sb.append("Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Para conectar desde la PC: adb connect IP:5555");
        info.setText(sb.toString());
    }

    /** Busca las IPv4 de la TV con varios metodos que no necesitan permisos. */
    private ArrayList<String> findIps() {
        LinkedHashSet<String> out = new LinkedHashSet<String>();

        // Metodo 1: interfaces de red de Java (cada interfaz por separado).
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                try {
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress a = addrs.nextElement();
                        if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                            out.add(ni.getName() + ": " + a.getHostAddress());
                        }
                    }
                } catch (Exception ignored) {
                    // se ignora esta interfaz y se sigue con la siguiente
                }
            }
        } catch (Exception ignored) {
            // se prueba el siguiente metodo
        }

        // Metodo 2: tabla de rutas del kernel.
        if (out.isEmpty()) {
            BufferedReader r = null;
            try {
                r = new BufferedReader(new FileReader("/proc/net/fib_trie"));
                String line;
                String last = null;
                while ((line = r.readLine()) != null) {
                    Matcher m = FIB_IP.matcher(line);
                    if (m.find()) {
                        last = m.group(1);
                    } else if (last != null && line.contains("/32 host LOCAL")) {
                        if (!last.startsWith("127.")) {
                            out.add(last);
                        }
                    }
                }
            } catch (Exception ignored) {
                // se prueba el siguiente metodo
            } finally {
                closeQuietly(r);
            }
        }

        // Metodo 3: comandos del sistema.
        if (out.isEmpty()) {
            addIpsFromText(out, runCmd("ip", "-4", "addr"));
        }
        if (out.isEmpty()) {
            addIpsFromText(out, runCmd("ifconfig"));
        }

        return new ArrayList<String>(out);
    }

    private void addIpsFromText(LinkedHashSet<String> out, String text) {
        Matcher m = CMD_IP.matcher(text);
        while (m.find()) {
            String ip = m.group(1);
            if (!ip.startsWith("127.")) {
                out.add(ip);
            }
        }
    }

    private String runCmd(String... cmd) {
        Process p = null;
        BufferedReader r = null;
        try {
            p = Runtime.getRuntime().exec(cmd);
            r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        } finally {
            closeQuietly(r);
            if (p != null) {
                p.destroy();
            }
        }
    }

    /** Mira si algun programa esta escuchando en el puerto 5555 (0x15B3). */
    private String portStatus() {
        boolean readable = false;
        boolean listening = false;
        String[] files = {"/proc/net/tcp", "/proc/net/tcp6"};
        for (String f : files) {
            BufferedReader r = null;
            try {
                r = new BufferedReader(new FileReader(f));
                readable = true;
                String line = r.readLine();
                while ((line = r.readLine()) != null) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length > 3 && "0A".equals(p[3])
                            && p[1].toUpperCase().endsWith(":15B3")) {
                        listening = true;
                    }
                }
            } catch (Exception ignored) {
                // no se pudo leer este archivo
            } finally {
                closeQuietly(r);
            }
        }
        if (!readable) {
            return "no se pudo comprobar";
        }
        return listening ? "ACTIVO" : "CERRADO";
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
