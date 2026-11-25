package com.analizis.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El censo de comercializadoras de la CNMC (sede electrónica): quién existe de verdad, con su
 * web y si está activa. El comparador solo enseña las ofertas que cada empresa le manda, así que
 * este listado es la forma de ver el hueco: «de 59 comercializadoras nacionales, 25 publican
 * oferta para tu perfil» (65 ofertas con el filtro actual de la CNMC).
 *
 * Se pide solo cuando se pulsa el enlace y se guarda un mes; si la sede cambia el HTML o falla,
 * la comparativa sigue igual.
 */
final class Censo {

    static final String URL = "https://sede.cnmc.gob.es/listado/censo/2";

    static class Item {
        String name = "";
        String web = "";
    }

    static class Result {
        long at = 0;
        int total = 0;       // filas del censo, incluidas las de baja
        int activas = 0;
        int nacionales = 0;  // activas con ámbito «N»
        final List<Item> items = new ArrayList<>();

        boolean ok() {
            return activas > 0;
        }
    }

    private Censo() {
    }

    /** Descarga y lee el censo. Null si no se pudo (sin red, 403, HTML distinto…). */
    static Result fetch() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(URL).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) Analuzis/1.1 (uso personal)");
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            if (c.getResponseCode() != 200) return null;
            return parse(read(c.getInputStream()));
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Una fila por comercializadora; las columnas 1, 7, 11 y 12 son nombre, ámbito, web y estado. */
    static Result parse(String html) {
        if (html == null || html.isEmpty()) return null;
        Result r = new Result();
        r.at = System.currentTimeMillis();

        for (String row : html.split("(?i)<tr")) {
            List<String> cells = new ArrayList<>();
            List<String> raw = new ArrayList<>();
            Matcher m = Pattern.compile("(?is)<td[^>]*>(.*?)</td>").matcher(row);
            while (m.find()) {
                raw.add(m.group(1));
                cells.add(Web.text(m.group(1)).trim());
            }
            if (cells.size() < 13) continue;
            if (!cells.get(0).matches("R\\d+-\\d+")) continue;   // cabecera o fila rara

            r.total++;
            if (cells.get(12).toLowerCase(Locale.ROOT).contains("baja")) continue;
            r.activas++;

            // El ámbito viene como «N; INTER AND» o «N, INT FR»: las dos cuentan como nacional.
            boolean nacional = false;
            for (String a : cells.get(7).toUpperCase(Locale.ROOT).split("[;,]")) {
                if (a.trim().equals("N")) nacional = true;
            }
            if (!nacional) continue;
            r.nacionales++;

            Item i = new Item();
            i.name = cells.get(1);
            // El enlace real de la celda: el texto se ve («www…») y no siempre lleva esquema,
            // pero el href sí sirve tal cual para abrirlo en el navegador.
            i.web = href(raw.get(11));
            if (i.web.isEmpty()) i.web = cells.get(11).trim();
            r.items.add(i);
        }
        return r.ok() ? r : null;
    }

    /** El href de una celda (la de la web), o vacío si no lo tiene. */
    private static String href(String cellHtml) {
        if (cellHtml == null) return "";
        Matcher m = Pattern.compile("(?i)href\\s*=\\s*\"([^\"]*)\"").matcher(cellHtml);
        if (!m.find()) return "";
        String u = m.group(1).trim();
        if (u.equalsIgnoreCase("http://") || u.equalsIgnoreCase("https://")) return "";
        return u;
    }

    /** Las nacionales activas cuya oferta no aparece en el comparador para este perfil. */
    static List<Item> faltan(Result r, List<String> suppliers) {
        List<Item> out = new ArrayList<>();
        if (r == null) return out;
        for (Item i : r.items) {
            if (!cubierta(i.name, suppliers)) out.add(i);
        }
        return out;
    }

    private static boolean cubierta(String name, List<String> suppliers) {
        if (suppliers == null) return false;
        String[] a = tokens(name);
        for (String s : suppliers) {
            String[] b = tokens(s);
            for (String x : a) {
                for (String y : b) {
                    if (x.equals(y)) return true;
                }
            }
        }
        return false;
    }

    /** Palabras que no distinguen una empresa de otra. */
    private static final List<String> GENERIC = java.util.Arrays.asList(
            "ENERGIA", "ENERGY", "COMERCIALIZADORA", "COMERCIALIZADOR", "ELECTRICIDAD",
            "ELECTRICA", "GAS", "LUZ", "CLIENTES", "SOCIEDAD", "GRUPO", "ESPANA", "SPAIN",
            "LIMITADA", "ANONIMA", "UNIPERSONAL", "SERVICIOS", "GESTION", "IBERIA", "HOGAR",
            "SOLAR", "SL", "SA", "SLU", "SAU", "SLNE", "DE", "DEL", "LA", "EL", "LOS", "LAS",
            "Y", "E", "EN");

    private static String[] tokens(String s) {
        if (s == null) return new String[0];
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", " ");
        List<String> out = new ArrayList<>();
        for (String w : t.split("\\s+")) {
            if (w.length() < 4 || GENERIC.contains(w)) continue;
            out.add(w);
        }
        return out.toArray(new String[0]);
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        byte[] data = out.toByteArray();
        String s = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        if (s.indexOf('\uFFFD') >= 0) s = new String(data, "ISO-8859-1");
        return s;
    }
}
