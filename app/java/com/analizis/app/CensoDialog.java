package com.analizis.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * «¿Quién falta en el comparador?»: el censo de la CNMC con la web de cada comercializadora
 * que no publica oferta para este perfil. Antes vivía al pie de la comparativa; ahora se abre
 * desde la pantalla principal y cada fila abre su web en el navegador externo.
 */
final class CensoDialog {

    private static final long CENSO_MS = 30L * 24 * 60 * 60 * 1000;

    private CensoDialog() {
    }

    static void show(final Activity a, final Store store) {
        final LinearLayout box = Dialogs.body(a);
        AlertDialog dlg = new AlertDialog.Builder(a)
                .setTitle("Quién falta en el comparador")
                .setView(Dialogs.scroller(a, box, 440))
                .setPositiveButton("Cerrar", null)
                .show();
        Dialogs.width(dlg);

        final List<String> sups = suppliers(store);
        final Censo.Result saved = store.censo();
        if (saved != null && System.currentTimeMillis() - saved.at < CENSO_MS) {
            paint(a, box, saved, sups);
            return;
        }
        box.addView(small(a, "Mirando el censo de la CNMC…"));
        new Thread(() -> {
            final Censo.Result got = Censo.fetch();
            if (got != null) store.saveCenso(got);
            a.runOnUiThread(() -> {
                if (a.isFinishing()) return;
                if (got == null) {
                    box.removeAllViews();
                    box.addView(small(a, "No he podido leer el censo de la CNMC ahora mismo. La "
                            + "comparativa no cambia: solo falta este dato."));
                    if (saved != null) paint(a, box, saved, sups);
                    return;
                }
                paint(a, box, got, sups);
            });
        }).start();
    }

    private static void paint(Activity a, LinearLayout box, Censo.Result r, List<String> sups) {
        box.removeAllViews();
        List<Censo.Item> falta = Censo.faltan(r, sups);
        Dialogs.section(a, box, "Censo de la CNMC");
        Dialogs.row(a, box, "Filas del censo", String.valueOf(r.total));
        Dialogs.row(a, box, "Comercializadoras activas", String.valueOf(r.activas));
        Dialogs.row(a, box, "Con ámbito nacional", String.valueOf(r.nacionales));
        if (sups.isEmpty()) {
            Dialogs.note(a, box, "Todavía no hay una comparativa guardada: abre «Comparar ofertas» "
                    + "una vez y aquí se verá cuáles de estas faltan de verdad.");
        } else {
            Dialogs.row(a, box, "Con oferta en la app", String.valueOf(sups.size()),
                    a.getColor(R.color.accent));
        }
        Dialogs.section(a, box, "No aparecen con ese nombre (" + falta.size()
                + " de " + r.nacionales + ")");
        if (falta.isEmpty()) {
            box.addView(small(a, "No falta ninguna: todas las nacionales tienen oferta hoy."));
        } else {
            for (Censo.Item i : falta) addItem(a, box, i);
        }
        Dialogs.note(a, box, "Toca una para abrir su web en el navegador. El comparador solo "
                + "publica las ofertas que cada comercializadora le manda y que encajan con tu "
                + "consumo y potencia. Aquí se compara por el nombre del censo, y algunas pueden "
                + "estar con otra razón social (por ejemplo, Imagina es Hanwha), así que míralas "
                + "en su web antes de descartarlas. Muchas de las que faltan son locales o no "
                + "venden a particulares. Leído el " + when(r.at) + ".");
    }

    /** Una fila por comercializadora: nombre y, debajo, su web. Toda la fila abre el enlace. */
    private static void addItem(final Activity a, LinearLayout box, Censo.Item i) {
        final String url = url(i.web);
        String shown = i.web == null ? "" : i.web.trim();
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(a, 10), 0, dp(a, 10));
        if (!url.isEmpty()) {
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setOnClickListener(v -> open(a, url));
        }

        TextView name = new TextView(a);
        name.setText(pretty(i.name));
        name.setTextColor(a.getColor(R.color.text));
        name.setTextSize(13);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView web = new TextView(a);
        web.setText(shown.isEmpty() ? "sin web publicada"
                : shown.replaceFirst("(?i)^https?://", "") + "  ›");
        web.setTextColor(a.getColor(shown.isEmpty() ? R.color.muted : R.color.accent));
        web.setTextSize(11);
        web.setPadding(0, dp(a, 3), 0, 0);
        row.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        box.addView(row, lp);
    }

    private static void open(Activity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(a, "No he podido abrir el navegador.", Toast.LENGTH_SHORT).show();
        }
    }

    /** El enlace del censo tal cual o, si solo viene el texto, con https:// delante. */
    private static String url(String web) {
        String s = web == null ? "" : web.trim();
        if (s.isEmpty()) return "";
        if (s.startsWith("http://") || s.startsWith("https://")) return s;
        return "https://" + s;
    }

    /** Comercializadoras distintas con oferta, de la última comparativa guardada. */
    private static List<String> suppliers(Store store) {
        List<String> out = new ArrayList<>();
        Store.SavedOffers saved = store.savedOffers();
        if (saved == null) return out;
        for (Offer o : saved.list) {
            if (o == null || o.pvpcMine) continue;
            if (o.supplier == null || o.supplier.isEmpty()) continue;
            boolean seen = false;
            for (String s : out) {
                if (sameSupplier(s, o.supplier)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) out.add(o.supplier);
        }
        return out;
    }

    private static boolean sameSupplier(String a, String b) {
        String x = norm(a), y = norm(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        if (x.equals(y)) return true;
        String fx = x.split(" ")[0], fy = y.split(" ")[0];
        return fx.length() >= 5 && fx.equals(fy);
    }

    private static String norm(String s) {
        if (s == null) return "";
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        t = t.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    private static String pretty(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (String w : s.trim().split(" ")) {
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            if (w.indexOf('.') >= 0) {
                out.append(w.toUpperCase(Locale.ROOT));
            } else if (w.equalsIgnoreCase("de") || w.equalsIgnoreCase("y") || w.equalsIgnoreCase("del")) {
                out.append(w.toLowerCase(Locale.ROOT));
            } else {
                out.append(w.substring(0, 1).toUpperCase(Locale.ROOT))
                        .append(w.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.toString();
    }

    private static TextView small(Activity a, String s) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextColor(a.getColor(R.color.muted));
        t.setTextSize(13);
        t.setPadding(0, dp(a, 4), 0, 0);
        Dialogs.fill(t);
        return t;
    }

    private static String when(long at) {
        if (at <= 0) return "la última consulta";
        return new java.text.SimpleDateFormat("d 'de' MMMM 'a las' HH:mm", new Locale("es", "ES"))
                .format(new java.util.Date(at));
    }

    private static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
