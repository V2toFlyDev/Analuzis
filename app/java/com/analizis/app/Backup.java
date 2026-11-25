package com.analizis.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Copia de seguridad local de Analuzis.
 *
 * Incluye todas las preferencias menos las credenciales, y los PDFs guardados. Las claves
 * de Datadis y ESIOS nunca se copian: después de restaurar hay que volver a introducirlas.
 */
final class Backup {

    static final int FORMAT = 1;

    private Backup() {
    }

    static JSONObject capture(Context context) throws Exception {
        Store store = new Store(context);
        JSONObject root = new JSONObject();
        root.put("app", "Analuzis");
        root.put("format", FORMAT);
        root.put("createdAt", System.currentTimeMillis());
        root.put("versionName", versionName(context));
        root.put("prefs", store.exportPrefs());
        root.put("pdfs", pdfs(context));
        return root;
    }

    static String suggestedName() {
        String date = new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(new Date());
        return "analizis-copia-" + date + ".json";
    }

    static Summary summary(JSONObject root) {
        Summary s = new Summary();
        JSONObject prefs = root == null ? null : root.optJSONObject("prefs");
        s.prefs = prefs == null ? 0 : prefs.length();
        s.invoices = arrayLength(pref(prefs, "invoices"));
        s.solarMonths = arrayLength(pref(prefs, "solarMonths"));
        s.datadisMonths = monthCount(pref(prefs, "datadisMonths"));
        String curve = pref(prefs, "curve");
        try {
            s.curve = !curve.isEmpty() && new JSONObject(curve).optInt("points", 0) > 0;
        } catch (Exception ignored) {
        }
        String pvpc = pref(prefs, "pvpc");
        try {
            s.pvpc = !pvpc.isEmpty() && new JSONObject(pvpc).optInt("hours", 0) > 0;
        } catch (Exception ignored) {
        }
        JSONArray pdfs = root == null ? null : root.optJSONArray("pdfs");
        s.pdfs = pdfs == null ? 0 : pdfs.length();
        return s;
    }

    static Summary restore(Context context, JSONObject root) throws Exception {
        if (root == null || !"Analuzis".equals(root.optString("app", ""))
                || root.optInt("format", 0) != FORMAT) {
            throw new Exception("El archivo no es una copia de Analuzis compatible.");
        }
        JSONObject prefs = root.optJSONObject("prefs");
        if (prefs == null) throw new Exception("La copia no trae datos de la app.");

        Store store = new Store(context);
        store.restorePrefs(prefs);

        JSONArray pdfs = root.optJSONArray("pdfs");
        for (int i = 0; pdfs != null && i < pdfs.length(); i++) {
            JSONObject p = pdfs.optJSONObject(i);
            if (p == null) continue;
            String name = p.optString("name", "");
            String data = p.optString("data", "");
            if (name.isEmpty() || data.isEmpty()) continue;
            try {
                Pdfs.save(context, name, Base64.decode(data, Base64.DEFAULT));
            } catch (Exception ignored) {
                // Un PDF corrupto no debe impedir restaurar el resto.
            }
        }
        return summary(root);
    }

    static String readText(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static JSONArray pdfs(Context context) throws Exception {
        JSONArray out = new JSONArray();
        File[] files = Pdfs.dir(context).listFiles();
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : files) {
            if (f == null || !f.isFile() || f.length() <= 0) continue;
            try (FileInputStream in = new FileInputStream(f)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) bytes.write(buf, 0, n);
                JSONObject p = new JSONObject();
                p.put("name", f.getName());
                p.put("data", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
                out.put(p);
            }
        }
        return out;
    }

    private static String pref(JSONObject prefs, String key) {
        if (prefs == null) return "";
        JSONObject w = prefs.optJSONObject(key);
        return w == null ? "" : w.optString("v", "");
    }

    private static int arrayLength(String json) {
        try {
            return json == null || json.isEmpty() ? 0 : new JSONArray(json).length();
        } catch (Exception e) {
            return 0;
        }
    }

    private static int monthCount(String json) {
        try {
            if (json == null || json.isEmpty()) return 0;
            JSONArray a = new JSONObject(json).optJSONArray("months");
            return a == null ? 0 : a.length();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String versionName(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    static class Summary {
        int prefs;
        int invoices;
        int solarMonths;
        int datadisMonths;
        int pdfs;
        boolean curve;
        boolean pvpc;

        String text() {
            return invoices + (invoices == 1 ? " factura" : " facturas")
                    + " · " + solarMonths + " meses del inversor"
                    + " · " + datadisMonths + " meses de Datadis"
                    + " · " + (curve ? "curva sí" : "sin curva")
                    + " · " + (pvpc ? "PVPC sí" : "sin PVPC")
                    + " · " + pdfs + (pdfs == 1 ? " PDF" : " PDFs");
        }
    }
}
