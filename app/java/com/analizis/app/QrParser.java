package com.analizis.app;

import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lee el QR normalizado de la factura eléctrica (Resolución CNMC de 24/06/2021).
 * El QR lleva una URL al comparador con los datos del suministro.
 */
public class QrParser {

    public static BillData parse(String raw) {
        if (raw == null) return null;
        String text = raw.trim().replace("&amp;", "&");
        int q = text.indexOf('?');
        if (q < 0) return null;

        String base = text.substring(0, q).toLowerCase(Locale.ROOT);
        if (!base.contains("comparador.cnmc") && !base.contains("cnmc.gob.es")) return null;

        Map<String, String> p = query(text.substring(q + 1));
        String cp = p.get("cp");
        if (cp == null) return null;
        cp = cp.trim();
        while (cp.length() > 1 && cp.startsWith("0")) cp = cp.substring(1);
        if (cp.isEmpty() || cp.length() > 5) return null;

        BillData b = new BillData();
        b.cp = cp;
        b.cups = nz(p.get("cups"));
        b.supplierCode = nz(p.get("com"));

        double p1 = num(p.get("pP1"));
        double p2 = num(p.get("pP2"));
        if (p1 <= 0) p1 = p2;
        if (p2 <= 0) p2 = p1;
        b.p1 = p1;
        b.p2 = p2;

        int ca1 = (int) num(p.get("caP1"));
        int ca2 = (int) num(p.get("caP2"));
        int ca3 = (int) num(p.get("caP3"));

        int cf1 = (int) num(p.get("cfP1"));
        int cf2 = (int) num(p.get("cfP2"));
        int cf3 = (int) num(p.get("cfP3"));
        b.billKwh1 = cf1;
        b.billKwh2 = cf2;
        b.billKwh3 = cf3;

        b.invoiceTotal = num(p.get("imp"));
        b.surplus = num(p.get("exc"));

        b.from = nz(p.get("iniF"));
        b.to = nz(p.get("finF"));
        int days = daysBetween(p.get("iniF"), p.get("finF"));
        if (days <= 0) days = 30;
        b.invoiceDays = days;

        if (ca1 + ca2 + ca3 > 0) {
            b.kwh1 = ca1;
            b.kwh2 = ca2;
            b.kwh3 = ca3;
            b.kwhYear = ca1 + ca2 + ca3;
        } else {
            int c1 = cf1, c2 = cf2, c3 = cf3;
            int total = c1 + c2 + c3;
            if (total > 0) {
                double factor = 365.0 / days;
                b.kwh1 = (int) Math.round(c1 * factor);
                b.kwh2 = (int) Math.round(c2 * factor);
                b.kwh3 = (int) Math.round(c3 * factor);
                b.kwhYear = b.kwh1 + b.kwh2 + b.kwh3;
            }
        }
        return b.complete() ? b : null;
    }

    private static Map<String, String> query(String s) {
        Map<String, String> m = new HashMap<>();
        for (String part : s.split("&")) {
            int i = part.indexOf('=');
            if (i <= 0) continue;
            try {
                String k = URLDecoder.decode(part.substring(0, i), "UTF-8");
                String v = URLDecoder.decode(part.substring(i + 1), "UTF-8");
                m.put(k, v);
            } catch (Exception ignored) {
            }
        }
        return m;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    static double num(String v) {
        if (v == null) return 0;
        StringBuilder s = new StringBuilder();
        for (char c : v.toCharArray()) {
            if ((c >= '0' && c <= '9') || c == '.' || c == ',') s.append(c);
            if (c == '-' && s.length() == 0) s.append(c);
        }
        String r = s.toString().replace(",", ".");
        if (r.isEmpty()) return 0;
        try {
            return Double.parseDouble(r);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int daysBetween(String from, String to) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            f.setLenient(false);
            Date a = f.parse(from);
            Date b = f.parse(to);
            long d = (b.getTime() - a.getTime()) / 86400000L;
            if (d > 0 && d < 400) return (int) d;
        } catch (Exception ignored) {
        }
        return 0;
    }
}
