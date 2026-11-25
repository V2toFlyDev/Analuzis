package com.analizis.app;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Los precios que la ficha de la CNMC trae como texto libre («Término Energía Punta:
 * 0,208952 €/kWh», «Término Potencia Punta: 33,00 €/kW año»…). Es una ayuda para rellenar el
 * comparador manual: no es un dato estructurado y cada comercializadora lo escribe a su manera,
 * así que devuelve lo que pueda y el usuario lo revisa.
 */
public final class OfferPrices {

    public double energy = 0;         // €/kWh si hay un precio único
    public double e1 = 0, e2 = 0, e3 = 0;  // €/kWh por tramo (Punta/Llano/Valle o P1/P2/P3)
    public double power1 = 0, power2 = 0;  // €/kW·día

    public boolean hasEnergy() {
        return energy > 0 || (e1 > 0 && e2 > 0 && e3 > 0);
    }

    public boolean hasPower() {
        return power1 > 0 || power2 > 0;
    }

    public static OfferPrices parse(String raw) {
        OfferPrices p = new OfferPrices();
        if (raw == null || raw.trim().isEmpty()) return p;
        String t = norm(raw);
        // Cada línea o frase es un bloque: así la potencia no se mezcla con la energía y, si
        // hay «sin descuento» y «con descuento», el segundo bloque (el último) manda.
        for (String chunk : t.split("\\r?\\n|(?<=\\.)\\s+")) {
            if (chunk == null || chunk.trim().isEmpty()) continue;
            if (chunk.contains("energia")) parseEnergy(chunk, p);
            if (chunk.contains("potencia") || chunk.contains("termino fijo")
                    || KW_NOT_KWH.matcher(chunk).find()) {
                parsePower(chunk, p);
            }
        }
        return p;
    }

    private static void parseEnergy(String c, OfferPrices p) {
        Matcher m = E_LABEL.matcher(c);
        boolean any = false;
        double first = 0;
        while (m.find()) {
            any = true;
            double v = num(m.group(2));
            if (v <= 0) continue;
            if (first == 0) first = v;
            String lab = m.group(1);
            if ("punta".equals(lab) || "p1".equals(lab)) p.e1 = v;
            else if ("llano".equals(lab) || "p2".equals(lab)) p.e2 = v;
            else if ("valle".equals(lab) || "p3".equals(lab)) p.e3 = v;
        }
        if (any) {
            // Si solo venía un tramo, se ofrece como precio único en vez de dejar un hueco.
            if (!(p.e1 > 0 && p.e2 > 0 && p.e3 > 0) && p.energy == 0) p.energy = first;
            return;
        }
        Matcher s = SINGLE_E.matcher(c);
        double v = 0;
        while (s.find()) v = num(s.group(1));
        if (v > 0) p.energy = v;
    }

    private static void parsePower(String c, OfferPrices p) {
        double f = c.contains("dia") ? 1.0 : (c.contains("ano") ? 1.0 / 365.0 : 1.0);
        Matcher m = P_LABEL.matcher(c);
        boolean any = false;
        while (m.find()) {
            any = true;
            double v = num(m.group(2)) * f;
            if (v <= 0) continue;
            if ("punta".equals(m.group(1)) || "p1".equals(m.group(1))) p.power1 = v;
            else p.power2 = v;
        }
        if (any) return;
        Matcher s = SINGLE_P.matcher(c);
        double v = 0;
        while (s.find()) v = num(s.group(1)) * f;
        if (v > 0) p.power1 = v;
    }

    private static final Pattern KW_NOT_KWH = Pattern.compile("kw(?!h)");
    private static final Pattern E_LABEL = Pattern.compile(
            "(?i)(punta|llano|valle|p1|p2|p3)\\s*:?\\s*([0-9]+[.,][0-9]+)\\s*€?\\s*/?\\s*kwh");
    private static final Pattern SINGLE_E = Pattern.compile(
            "(?i)([0-9]+[.,][0-9]+)\\s*€?\\s*/\\s*kwh");
    private static final Pattern P_LABEL = Pattern.compile(
            "(?i)(punta|valle|p1|p2)\\s*:?\\s*([0-9]+[.,][0-9]+)");
    private static final Pattern SINGLE_P = Pattern.compile(
            "(?i)([0-9]+[.,][0-9]+)\\s*€?\\s*/?\\s*kw");

    /** Minúsculas y sin acentos, para no depender de cómo escriba cada web «año» o «día». */
    private static String norm(String s) {
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        t = t.replace('\u00A0', ' ').toLowerCase(Locale.ROOT);
        return t.replaceAll("[ \\t]+", " ");
    }

    static double num(String s) {
        if (s == null) return 0;
        try {
            return Double.parseDouble(s.trim().replace(",", "."));
        } catch (Exception e) {
            return 0;
        }
    }
}
