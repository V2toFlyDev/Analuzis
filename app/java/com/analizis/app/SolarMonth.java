package com.analizis.app;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lo que produjo el inversor en un mes: energía de las placas usada en casa
 * (autoconsumo) y energía vertida a la red. Se guarda por meses para poder sumar
 * el año con datos reales en vez de con una estimación.
 */
public class SolarMonth {

    /** Los kWh se guardan con decimales: el inversor da 317,65, no 318. */
    public static final int YEAR_MONTHS = 12;

    public String month = "";    // yyyy-MM
    public double selfKwh;       // autoconsumo: de las placas a la casa
    public double exportKwh;     // vertido a la red
    public double productionKwh; // producción total (autoconsumo + vertido)
    public double gridKwh;       // tomado de la red (informativo)

    public static SolarMonth of(String month, double self, double export, double production, double grid) {
        SolarMonth m = new SolarMonth();
        m.month = month;
        m.selfKwh = round2(self);
        m.exportKwh = round2(export);
        m.productionKwh = round2(production > 0 ? production : self + export);
        m.gridKwh = round2(grid);
        return m;
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static final String[] MONTHS = {"Ene", "Feb", "Mar", "Abr", "May", "Jun",
            "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"};

    /** «Nov2026» a partir del mes guardado como 2026-11. */
    public static String monthLabel(String month) {
        if (month == null || month.length() < 7) return month == null ? "" : month;
        int m;
        try {
            m = Integer.parseInt(month.substring(5, 7));
        } catch (Exception e) {
            return month;
        }
        if (m < 1 || m > 12) return month;
        return MONTHS[m - 1] + month.substring(0, 4);
    }

    /** «Ene»…«Dic» del mes 1..12. */
    public static String monthName(int m) {
        return m >= 1 && m <= 12 ? MONTHS[m - 1] : "";
    }

    /** El mes siguiente: 2026-11 → 2026-12. */
    public static String nextMonth(String month) {
        if (month == null || month.length() < 7) return month;
        try {
            int y = Integer.parseInt(month.substring(0, 4));
            int m = Integer.parseInt(month.substring(5, 7));
            if (m < 1 || m > 12) return month;
            return m == 12
                    ? String.format(Locale.ROOT, "%04d-01", y + 1)
                    : String.format(Locale.ROOT, "%04d-%02d", y, m + 1);
        } catch (Exception e) {
            return month;
        }
    }

    /** «Nov2026» para enseñarlo. */
    public String label() {
        return monthLabel(month);
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("m", month);
        o.put("self", selfKwh);
        o.put("exp", exportKwh);
        o.put("prod", productionKwh);
        o.put("grid", gridKwh);
        return o;
    }

    public static SolarMonth fromJson(JSONObject o) {
        SolarMonth m = new SolarMonth();
        m.month = o.optString("m", "");
        m.selfKwh = o.optDouble("self", 0);
        m.exportKwh = o.optDouble("exp", 0);
        m.productionKwh = o.optDouble("prod", 0);
        m.gridKwh = o.optDouble("grid", 0);
        return m;
    }

    /**
     * Ventana móvil: de todo lo guardado manda solo lo de los 12 meses más
     * recientes. Con más de un año guardado, sumarlo todo inflaría el año.
     */
    public static List<SolarMonth> lastYear(List<SolarMonth> months) {
        List<SolarMonth> out = new ArrayList<>();
        if (months == null) return out;
        out.addAll(months);
        out.sort((x, y) -> y.month.compareTo(x.month));
        if (out.size() > YEAR_MONTHS) out = new ArrayList<>(out.subList(0, YEAR_MONTHS));
        return out;
    }

    public static double sumSelf(List<SolarMonth> months) {
        double v = 0;
        for (SolarMonth m : lastYear(months)) v += m.selfKwh;
        return round2(v);
    }

    public static double sumExport(List<SolarMonth> months) {
        double v = 0;
        for (SolarMonth m : lastYear(months)) v += m.exportKwh;
        return round2(v);
    }

    public static double sumGrid(List<SolarMonth> months) {
        double v = 0;
        for (SolarMonth m : lastYear(months)) v += m.gridKwh;
        return round2(v);
    }

    /**
     * Acepta Nov2026, noviembre 2026, Nov-2026, 2026-10, 10/2026, 2026/10, 10-2026
     * y 202610. Devuelve yyyy-MM, o cadena vacía si no es un mes válido.
     */
    public static String parseMonth(String s) {
        if (s == null) return "";
        String raw = s.trim();
        if (raw.isEmpty()) return "";

        // Con el mes escrito: Nov2026, Nov 2026, noviembre de 2026.
        java.util.regex.Matcher w = java.util.regex.Pattern
                .compile("([A-Za-zÁÉÍÓÚÜáéíóúüñÑ]+)[\\s\\-]*(?:de[\\s\\-]+)?([0-9]{4})")
                .matcher(raw);
        if (w.find()) {
            int mon = monthFromWord(w.group(1));
            int year = Integer.parseInt(w.group(2));
            if (mon > 0 && year >= 2000 && year <= 2100) {
                return String.format(Locale.ROOT, "%04d-%02d", year, mon);
            }
        }

        int year = 0, mon = 0;
        try {
            String digits = raw.replaceAll("[^0-9]", "");
            List<Integer> nums = new ArrayList<>();
            for (String p : raw.split("[^0-9]+")) {
                if (!p.isEmpty()) nums.add(Integer.parseInt(p));
            }
            if (nums.size() == 1 && digits.length() == 6) {
                year = Integer.parseInt(digits.substring(0, 4));
                mon = Integer.parseInt(digits.substring(4));
            } else if (nums.size() >= 2) {
                int a = nums.get(0), b = nums.get(1);
                if (a > 31) {
                    year = a;
                    mon = b;
                } else {
                    mon = a;
                    year = b;
                }
            }
        } catch (Exception e) {
            return "";
        }
        if (year < 2000 || year > 2100 || mon < 1 || mon > 12) return "";
        return String.format(Locale.ROOT, "%04d-%02d", year, mon);
    }

    /** «nov», «noviembre», «sept»… a número de mes; 0 si no se reconoce. */
    private static int monthFromWord(String word) {
        String t = word.toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ü", "u");
        if (t.startsWith("ene")) return 1;
        if (t.startsWith("feb")) return 2;
        if (t.startsWith("mar")) return 3;
        if (t.startsWith("abr")) return 4;
        if (t.startsWith("may")) return 5;
        if (t.startsWith("jun")) return 6;
        if (t.startsWith("jul")) return 7;
        if (t.startsWith("ago")) return 8;
        if (t.startsWith("sep") || t.startsWith("set")) return 9;
        if (t.startsWith("oct")) return 10;
        if (t.startsWith("nov")) return 11;
        if (t.startsWith("dic")) return 12;
        return 0;
    }
}
