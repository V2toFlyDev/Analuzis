package com.analizis.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Precios del PVPC desde ESIOS (Red Eléctrica).
 *
 * - Indicador 1001: término de energía del PVPC 2.0TD, hora a hora, en €/MWh. Incluye los
 *   peajes y cargos de energía, pero no el término de potencia ni el alquiler del contador.
 * - Indicador 1739: precio de la energía excedentaria del autoconsumo (compensación
 *   simplificada del PVPC), hora a hora, en €/MWh.
 *
 * El token personal lo guarda la app en el móvil (SharedPreferences, sin nube) la primera
 * vez que ESIOS lo acepta, y se reutiliza en las siguientes consultas. Aquí no se toca:
 * esta clase solo lo recibe. Los valores llegan en €/MWh y se pasan a €/kWh.
 *
 * Los precios se agrupan por los periodos del 2.0TD (los mismos de la curva) para poder
 * cruzarlos con el reparto real del consumo en vez de con una media plana.
 */
public class Esios {

    private static final String BASE = "https://api.esios.ree.es/indicators/";
    private static final int GEO_PENINSULA = 8741;

    /** Lo que hace falta para comparar: precio medio del periodo y de cada tramo. */
    public static class Prices {
        public double p1, p2, p3;   // €/kWh medios de los periodos punta, llano y valle
        public double all;          // €/kWh medio del periodo completo
        public double surplus;      // €/kWh medio que paga el PVPC por los excedentes
        public int hours;           // lecturas de PVPC leídas
        public String from = "";
        public String to = "";

        public boolean ok() {
            return hours > 0 && all > 0;
        }
    }

    private Esios() {
    }

    /** Precios entre dos fechas (ambas incluidas). Lanza IOException con el motivo. */
    public static Prices fetch(String token, LocalDate from, LocalDate to) throws IOException {
        Prices p = new Prices();
        p.from = from.toString();
        p.to = to.toString();

        double s1 = 0, s2 = 0, s3 = 0, sAll = 0;
        int n1 = 0, n2 = 0, n3 = 0, nAll = 0;
        for (Object[] row : values(token, 1001, from, to)) {
            LocalDate day = (LocalDate) row[0];
            int hour = (Integer) row[1];
            double eurKwh = (Double) row[2];
            sAll += eurKwh;
            nAll++;
            int period = Curve.period(day, hour);
            if (period == 1) {
                s1 += eurKwh;
                n1++;
            } else if (period == 2) {
                s2 += eurKwh;
                n2++;
            } else {
                s3 += eurKwh;
                n3++;
            }
        }
        if (nAll == 0) {
            throw new IOException("ESIOS no devuelve precios del PVPC para ese periodo.");
        }
        p.all = sAll / nAll;
        p.hours = nAll;
        p.p1 = n1 > 0 ? s1 / n1 : p.all;
        p.p2 = n2 > 0 ? s2 / n2 : p.all;
        p.p3 = n3 > 0 ? s3 / n3 : p.all;

        // El precio de excedentes es un extra: si falla, el PVPC se compara igual.
        double sExc = 0;
        int nExc = 0;
        try {
            for (Object[] row : values(token, 1739, from, to)) {
                sExc += (Double) row[2];
                nExc++;
            }
        } catch (Exception ignored) {
        }
        p.surplus = nExc > 0 ? sExc / nExc : 0;
        return p;
    }

    /** Filas (fecha local, hora, €/kWh) del indicador en ese rango. */
    private static List<Object[]> values(String token, int indicator,
                                         LocalDate from, LocalDate to) throws IOException {
        String url = String.format(Locale.ROOT,
                "%s%d?start_date=%sT00:00:00Z&end_date=%sT23:59:59Z&time_trunc=hour&locale=es&geo_ids[]=%d",
                BASE, indicator, from, to, GEO_PENINSULA);
        String body = get(url, token);
        List<Object[]> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(body);
            JSONObject ind = root.optJSONObject("indicator");
            JSONArray a = ind == null ? null : ind.optJSONArray("values");
            if (a == null) a = root.optJSONArray("values");
            if (a == null) return out;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                double raw = o.optDouble("value", Double.NaN);
                if (Double.isNaN(raw)) continue;
                LocalDate day;
                int hour;
                try {
                    OffsetDateTime t = OffsetDateTime.parse(o.optString("datetime"));
                    day = t.toLocalDate();
                    hour = t.getHour();
                } catch (Exception e) {
                    continue;
                }
                // ESIOS da €/MWh; en casa se piensa en €/kWh.
                out.add(new Object[]{day, hour, raw / 1000.0});
            }
        } catch (Exception e) {
            throw new IOException("No entiendo la respuesta de ESIOS: " + messageOf(body));
        }
        return out;
    }

    private static String get(String url, String token) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(20000);
            c.setReadTimeout(90000);
            c.setRequestProperty("Accept", "application/json; application/vnd.esios-api-v2+json");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("x-api-key", token == null ? "" : token.trim());
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            String body = read(in);
            if (code < 200 || code >= 300) {
                throw new IOException(hint(code) + " " + messageOf(body));
            }
            return body;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String hint(int code) {
        if (code == 401 || code == 403) return "ESIOS no acepta el token:";
        if (code == 429) return "ESIOS está limitando las consultas, espera un rato:";
        return "ESIOS ha respondido " + code + ":";
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            return new String(o.toByteArray(), "UTF-8");
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Los errores de ESIOS a veces vienen en HTML: se dejan en una línea legible. */
    static String messageOf(String body) {
        if (body == null) return "";
        String t = body.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return t.length() > 220 ? t.substring(0, 220) + "…" : t;
    }
}
