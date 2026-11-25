package com.analizis.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * API privada de Datadis: de ahí sale la curva horaria que se guarda.
 *
 * Se entra con el NIF y la contraseña de datadis.es (los mismos de la web), se pide la
 * lista de suministros y luego el consumo horario. Las credenciales las guarda la app en el
 * móvil (`Store.datadisNif`/`datadisPass`) cuando Datadis las acepta; aquí solo se reciben.
 *
 * Datadis limita las llamadas: la misma consulta (mismo CUPS y mismas fechas) no se puede
 * repetir hasta que pasen 24 horas, y no perdona los errores de parámetros. Por eso se pide
 * de una vez el año entero y lo que llega se guarda como curva.
 */
public class Datadis {

    private static final String BASE = "https://datadis.es/";

    /** Un suministro tal y como lo da Datadis. */
    public static class Supply {
        public String cups = "";
        public String distributor = "";
        public String address = "";
        public int distributorCode = 0;
        public int pointType = 5;
    }

    /**
     * Lo que devuelve la consulta: la curva horaria y, si la distribuidora los publica,
     * los meses de autoconsumo. Si no los publica, vienen todos a cero y no se toca nada.
     */
    public static class Result {
        public Curve curve = new Curve();
        public final List<SolarMonth> months = new ArrayList<>();
        public double self = 0;        // autoconsumo del periodo, kWh
        public double exported = 0;    // vertido a la red, kWh
        public double production = 0;  // producción de las placas, kWh
        public double grid = 0;        // tomado de la red, kWh
        public long at = 0;            // cuándo se descargó (millis), para poder revisarlo luego

        public boolean hasSolar() {
            return self > 0 || exported > 0;
        }
    }

    private Datadis() {
    }

    /** Token de sesión. Devuelve IOException con un texto que se pueda enseñar. */
    public static String token(String nif, String pass) throws IOException {
        String body = "username=" + enc(nif.trim()) + "&password=" + enc(pass);
        HttpURLConnection c = open(BASE + "nikola-auth/tokens/login", "POST");
        try {
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(data.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(data);
            }
            int code = c.getResponseCode();
            if (code == 401 || code == 403) {
                throw new IOException("Datadis no acepta ese NIF y esa contraseña.");
            }
            if (code == 429) throw new IOException(busy());
            if (code != 200) throw new IOException("Datadis ha respondido " + code + " al entrar.");
            String t = read(c.getInputStream()).trim();
            // El token es una cadena larga; si llega un JSON o una página es que no hay sesión.
            if (t.length() < 20 || t.startsWith("{") || t.startsWith("<")) {
                throw new IOException("Datadis no ha devuelto un token válido.");
            }
            return t;
        } finally {
            c.disconnect();
        }
    }

    public static List<Supply> supplies(String token) throws IOException {
        String body = get(BASE + "api-private/api/get-supplies", token);
        List<Supply> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(body);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Supply s = new Supply();
                s.cups = o.optString("cups", "").trim();
                s.distributor = o.optString("distributor", "").trim();
                s.address = o.optString("address", "").trim();
                s.distributorCode = o.optInt("distributorCode", 0);
                s.pointType = o.optInt("pointType", 5);
                if (!s.cups.isEmpty() && s.distributorCode > 0) out.add(s);
            }
        } catch (Exception e) {
            throw new IOException("No entiendo la lista de suministros de Datadis: " + messageOf(body));
        }
        if (out.isEmpty()) {
            throw new IOException("En Datadis no hay ningún suministro a tu nombre.");
        }
        return out;
    }

    /**
     * Curva horaria de los últimos meses ya cerrados (el actual todavía no está completo).
     * Se pide de una vez porque Datadis no deja repetir la misma consulta en 24 horas.
     * De la misma respuesta salen los kWh de autoconsumo, vertido y producción de cada mes.
     */
    public static Result curve(String token, Supply s, int months) throws IOException {
        YearMonth end = YearMonth.now().minusMonths(1);
        YearMonth start = end.minusMonths(months - 1);
        String url = BASE + "api-private/api/get-consumption-data"
                + "?cups=" + enc(s.cups)
                + "&distributorCode=" + s.distributorCode
                + "&startDate=" + ym(start)
                + "&endDate=" + ym(end)
                + "&measurementType=0"
                + "&pointType=" + s.pointType;
        String body = get(url, token);

        Curve c = new Curve();
        // Por mes: autoconsumo, vertido, producción y red (en ese orden).
        Map<String, double[]> byMonth = new TreeMap<>();
        try {
            JSONArray a = new JSONArray(body);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                LocalDate d = Curve.date(o.optString("date", ""));
                double h = Curve.hour(o.optString("time", ""));
                if (d == null || h < 0) continue;
                double kwh = o.optDouble("consumptionKWh", 0);
                // Datadis da la hora en la que acaba el intervalo: "01:00" es de 00:00 a 01:00.
                c.add(d, h >= 1 ? h - 1 : h, kwh, 1);

                String key = d.toString().substring(0, 7);
                double[] v = byMonth.get(key);
                if (v == null) {
                    v = new double[4];
                    byMonth.put(key, v);
                }
                v[0] += o.optDouble("selfConsumptionEnergyKWh", 0);
                v[1] += o.optDouble("surplusEnergyKWh", 0);
                v[2] += o.optDouble("generationEnergyKWh", 0);
                v[3] += kwh;
            }
        } catch (Exception e) {
            throw new IOException("No entiendo la curva que ha devuelto Datadis: " + messageOf(body));
        }
        c.finish();
        if (c.points == 0) {
            throw new IOException("Datadis no tiene lecturas de ese suministro entre "
                    + ym(start) + " y " + ym(end) + ".");
        }

        Result r = new Result();
        r.curve = c;
        for (Map.Entry<String, double[]> e : byMonth.entrySet()) {
            double[] v = e.getValue();
            SolarMonth m = SolarMonth.of(e.getKey(), v[0], v[1], v[2], v[3]);
            if (m.selfKwh <= 0 && m.exportKwh <= 0 && m.gridKwh <= 0) continue;
            r.months.add(m);
            r.self += m.selfKwh;
            r.exported += m.exportKwh;
            r.production += m.productionKwh;
            r.grid += m.gridKwh;
        }
        return r;
    }

    private static String get(String url, String token) throws IOException {
        HttpURLConnection c = open(url, "GET");
        try {
            c.setRequestProperty("Authorization", "Bearer " + token);
            int code = c.getResponseCode();
            if (code == 401 || code == 403) {
                throw new IOException("Datadis ha rechazado la sesión. Vuelve a entrar.");
            }
            if (code == 429) throw new IOException(busy());
            if (code == 404) throw new IOException("Datadis no encuentra ese suministro.");
            if (code != 200) {
                String err = "";
                try {
                    err = read(c.getErrorStream());
                } catch (Exception ignored) {
                }
                throw new IOException("Datadis ha respondido " + code
                        + (err.isEmpty() ? "." : ": " + messageOf(err)));
            }
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        // Un año de lecturas puede tardar; Datadis es lento de por sí.
        c.setReadTimeout(180000);
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        c.setRequestProperty("User-Agent", "Analuzis/1.1 (uso personal)");
        return c;
    }

    private static String busy() {
        return "Datadis está limitando las consultas. Espera un rato y vuelve a probar.";
    }

    private static String ym(YearMonth m) {
        return String.format(Locale.ROOT, "%04d/%02d", m.getYear(), m.getMonthValue());
    }

    private static String enc(String v) throws IOException {
        return URLEncoder.encode(v == null ? "" : v, "UTF-8");
    }

    /** Texto de error que traiga el cuerpo, si es JSON; si no, un recorte del cuerpo. */
    private static String messageOf(String body) {
        if (body == null) return "";
        try {
            JSONObject o = new JSONObject(body.trim());
            for (String k : new String[]{"message", "error", "errorDescription", "detail"}) {
                String s = o.optString(k, "");
                if (!s.isEmpty()) return s;
            }
            JSONArray err = o.optJSONArray("distributorError");
            if (err != null && err.length() > 0) {
                String s = err.optJSONObject(0) == null ? "" : err.optJSONObject(0).optString("errorDescription", "");
                if (!s.isEmpty()) return s;
            }
        } catch (Exception ignored) {
        }
        String t = body.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
