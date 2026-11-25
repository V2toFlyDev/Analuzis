package com.analizis.app;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formato de números y fechas en español. */
public class Fmt {

    private static final DecimalFormatSymbols SYM = DecimalFormatSymbols.getInstance(new Locale("es", "ES"));

    /** Importes siempre con céntimos: nada de "70 €" cuando son 69,99 €. */
    public static String eur(double v) {
        return dec(v, 2) + " €";
    }

    public static String eur2(double v) {
        return dec(v, 2) + " €";
    }

    public static String dec(double v, int decimals) {
        StringBuilder p = new StringBuilder("#,##0");
        if (decimals > 0) {
            p.append('.');
            for (int i = 0; i < decimals; i++) p.append('0');
        }
        DecimalFormat f = new DecimalFormat(p.toString(), SYM);
        return f.format(v);
    }

    public static String kwh(double v) {
        return dec(v, 0) + " kWh";
    }

    /**
     * Número escrito a mano: el punto y la coma valen los dos como decimales. Si vienen
     * los dos, el último manda como decimal y el otro se toma como separador de miles.
     */
    public static double parse(String s) {
        if (s == null) return 0;
        String t = s.replace("\u00A0", "").replace("\u202F", "").replace(" ", "")
                .replace("€", "").replaceAll("[^0-9.,\\-]", "");
        if (t.isEmpty()) return 0;
        int comma = t.lastIndexOf(',');
        int dot = t.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            if (comma > dot) t = t.replace(".", "").replace(',', '.');
            else t = t.replace(",", "");
        } else if (comma >= 0) {
            t = t.replace(',', '.');
        }
        try {
            return Double.parseDouble(t);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Número con decimales solo si los tiene: 318 · 317,65. */
    public static String num(double v) {
        return Math.abs(v - Math.rint(v)) < 0.005 ? dec(v, 0) : dec(v, 2);
    }

    /** kWh con sus decimales: 318 kWh · 317,65 kWh. */
    public static String kwhDec(double v) {
        return num(v) + " kWh";
    }
}
