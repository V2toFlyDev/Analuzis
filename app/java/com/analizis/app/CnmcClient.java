package com.analizis.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

/** Consulta las ofertas reales en el comparador oficial de la CNMC. */
public class CnmcClient {

    private static final String API = "https://comparador.cnmc.gob.es/api/";
    private static final String ENDPOINT = API + "publico/ofertas/electricidad";

    /** Lo que la CNMC avisa de su propia respuesta en la última consulta. */
    public static final List<String> lastAvisos = new ArrayList<>();

    /**
     * El comparador ha contestado, pero con un error (500, 403…). Es distinto de no tener
     * internet: aquí el móvil sí ha llegado al servidor y el fallo es suyo.
     */
    public static class ApiException extends IOException {
        public final int status;
        public final String detail;

        ApiException(int status, String detail) {
            super("HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
            this.status = status;
            this.detail = detail;
        }
    }

    /**
     * Nombre de la comercializadora a partir del código que trae el QR (p. ej. R2-001).
     * Devuelve "" si el código no se reconoce o no hay conexión.
     */
    public static String supplierName(String code) {
        if (code == null || code.trim().isEmpty()) return "";
        HttpURLConnection c = null;
        try {
            String url = API + "publico/nombrecodigo/"
                    + java.net.URLEncoder.encode(code.trim(), "UTF-8");
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "text/plain, application/json, */*");
            c.setRequestProperty("User-Agent", "Analuzis (uso personal)");
            if (c.getResponseCode() != 200) return "";
            return clean(read(c.getInputStream()));
        } catch (Exception e) {
            return "";
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static List<Offer> fetch(BillData b) throws IOException {
        String url = buildUrl(b);
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "Analuzis/1.1 (uso personal)");
            int code = c.getResponseCode();
            if (code != 200) {
                // El error viene en JSON (title/detail), pero puede ser HTML o vacío.
                throw new ApiException(code, detail(c));
            }
            String body = read(c.getInputStream());

            JSONObject root = new JSONObject(body);
            // La CNMC avisa aquí de lo que ha tenido que suponer (por ejemplo, que no tiene
            // tu perfil horario y calcula las tarifas flexibles con uno estándar).
            lastAvisos.clear();
            JSONArray av = root.optJSONArray("avisos");
            if (av != null) {
                for (int i = 0; i < av.length(); i++) {
                    String a = clean(av.optString(i, ""));
                    if (!a.isEmpty()) lastAvisos.add(a);
                }
            }
            JSONArray arr = root.optJSONArray("resultadoComparador");
            if (arr == null || arr.length() == 0) {
                arr = root.optJSONArray("resultadoComparadorSinAjustePrecio");
            }
            if (arr == null) throw new IOException("Respuesta sin ofertas");

            List<Offer> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Offer f = new Offer();
                f.id = o.optInt("id");
                f.supplier = clean(o.optString("comercializadora", ""));
                f.name = clean(o.optString("oferta", ""));
                f.year1 = o.optDouble("importePrimerAnio", 0);
                f.year2 = o.optDouble("importeSegundoAnio", 0);
                f.green = o.optBoolean("verde", false);
                f.penalty = o.optBoolean("penalizacion", false);
                f.extras = o.optBoolean("serviciosAdicionales", false);
                // La CNMC sí dice, oferta por oferta, si admite compensación de excedentes.
                f.selfUse = o.optBoolean("autoconsumo", false);
                f.kind = o.optString("tipoElectricidad", "");
                f.validity = clean(o.optString("validez", ""));
                f.penaltyAmount = o.optDouble("importeEstimadoPenalizacion", 0);
                if (f.year1 > 0) out.add(f);
            }
            // Ya vienen ordenadas, pero nos aseguramos.
            out.sort((a, z) -> Double.compare(a.year1, z.year1));
            return out;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage() == null ? "respuesta no válida" : e.getMessage());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Lo que la CNMC publica del detalle de una oferta: webs, condiciones y lo que se pueda sacar. */
    public static class Detail {
        public String web = "";        // página de la oferta en la comercializadora
        public String contract = "";   // condiciones del contrato
        public String particular = ""; // contratación
        public String text = "";       // características + información adicional
        public double surplus = 0;      // €/kWh que dice pagar por excedentes (0 si no lo dice)
        public String surplusQuote = ""; // la frase concreta, para poder comprobarla
        public boolean battery = false;  // menciona batería virtual, monedero o similar
        public double webSurplus = 0;    // €/kWh que dice su propia web (0 si no lo dice)
        public String webQuote = "";     // la frase de su web, para poder comprobarla
        public String page = "";         // la web que se leyó (vacío si no se miró)
    }

    // «0,035€/kWh», «0.05 €/kWh»…
    private static final Pattern PRICE_KWH = Pattern.compile(
            "([0-9]{1,2}(?:[.,][0-9]{1,5})?)\\s*€\\s*/\\s*kwh", Pattern.CASE_INSENSITIVE);
    private static final Pattern BATTERY = Pattern.compile(
            "(?i)bater[ií]a\\s+virtual|wallet|monedero|bolsa\\s+de\\s+excedentes");

    /**
     * Busca en el texto de la ficha un €/kWh que hable de excedentes o compensación. Es lo que
     * muchas comercializadoras escriben en su información adicional («los excedentes se
     * compensan a 0,035€/kWh»), así que se ofrece como sugerencia, nunca como dato seguro.
     */
    static void scanSurplus(String text, Detail d) {
        if (text == null || text.isEmpty()) return;
        Matcher m = PRICE_KWH.matcher(text);
        while (m.find()) {
            double v = PdfPrices.num(m.group(1));
            if (v < 0.005 || v > 0.90) continue;
            // El precio tiene que estar en la misma frase o celda que la palabra: así no se
            // coge un «Punta: 0,207 €/kWh» que solo tiene «compensación» al lado.
            String before = text.substring(chunkStart(text, m.start()), m.start())
                    .toLowerCase(new Locale("es", "ES"));
            boolean says = saysSurplus(before);
            if (!says) {
                // En tablas el rótulo queda en la línea de arriba (o en un salto de más):
                // se admite si la palabra está pegada al número.
                String near = text.substring(Math.max(0, m.start() - 45), m.start())
                        .toLowerCase(new Locale("es", "ES"));
                says = saysSurplus(near);
            }
            if (!says) continue;
            String tail = before.length() > 42 ? before.substring(before.length() - 42) : before;
            if (TARIFF_WORD.matcher(tail).find()) continue;
            d.surplus = v;
            d.surplusQuote = sentence(text, m.start(), m.end());
            return;
        }
    }

    private static boolean saysSurplus(String s) {
        return s.contains("excedent") || s.contains("compensaci") || s.contains("compensamos");
    }

    /** Desde dónde empieza la frase, la línea o la celda donde está el precio. */
    private static int chunkStart(String t, int start) {
        int a = start;
        while (a > 0) {
            char c = t.charAt(a - 1);
            if (c == '.' || c == '\n' || c == '\r' || c == ';' || c == '•' || c == '|') break;
            a--;
        }
        return a;
    }

    /** Palabras de una tabla de precios: si están delante, ese €/kWh no es el de excedentes. */
    private static final Pattern TARIFF_WORD = Pattern.compile(
            "(?i)punta|valle|llano|per[ií]odo|\\bp[1-6]\\b|t[eé]rmino|potencia|consumo|"
            + "energ[ií]a|kwh\\s*/\\s*d[ií]a|€\\s*/\\s*kw");

    static boolean mentionsBattery(String text) {
        return text != null && BATTERY.matcher(text).find();
    }

    /**
     * La frase (hasta el punto o el salto de línea) donde aparece el precio. La ficha de la
     * CNMC suele venir sin puntos, así que si el trozo es enorme se recorta alrededor del
     * precio: antes salía media ficha de Naturgy con los precios de potencia delante.
     */
    private static String sentence(String t, int start, int end) {
        int a = chunkStart(t, start);
        int z = end;
        while (z < t.length() && t.charAt(z) != '.' && t.charAt(z) != '\n') z++;
        if (z < t.length()) z++;
        if (z - a > 170) {
            a = Math.max(a, start - 120);
            z = Math.min(z, end + 80);
            // Sin cortar palabras por la mitad.
            while (a > 0 && a < start && !Character.isWhitespace(t.charAt(a - 1))) a--;
            while (z < t.length() && z > end && !Character.isWhitespace(t.charAt(z))) z++;
        }
        String s = clean(t.substring(a, z));
        return s.length() > 220 ? s.substring(0, 220) + "…" : s;
    }

    /**
     * Pide el detalle de una oferta (`publico/oferta`). Es donde vienen las webs de la
     * comercializadora y el texto con las condiciones, que el listado no trae.
     */
    public static Detail offerDetail(BillData b, int id) {
        HttpURLConnection c = null;
        try {
            // buildUrl deja un `idOferta=` vacío y el detalle lo rechaza (HTTP 400 «el
            // identificador no puede estar vacío»): se quita antes de poner el id de verdad.
            String url = buildUrl(b).replace("&idOferta=&", "&")
                    .replace("/ofertas/electricidad", "/oferta") + "&idOferta=" + id;
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "Analuzis/1.1 (uso personal)");
            if (c.getResponseCode() != 200) return null;
            JSONObject root = new JSONObject(read(c.getInputStream()));
            JSONObject car = root.optJSONObject("caracteristicas");
            Detail d = new Detail();
            if (car == null) return d;
            d.web = clean(car.optString("webOferta", ""));
            d.contract = clean(car.optString("webContrato", ""));
            d.particular = clean(car.optString("webParticular", ""));
            if (d.web.isEmpty()) {
                // Respaldo: la web general de la comercializadora, si la oferta no trae una.
                JSONObject of = root.optJSONObject("datosOferta");
                if (of != null) d.web = clean(of.optString("web", ""));
            }
            String chars = car.optString("caracteristicas", "").trim();
            String info = car.optString("infoAdicional", "").trim();
            d.text = (chars + (info.isEmpty() ? "" : "\n\n" + info)).trim();
            scanSurplus(d.text, d);
            d.battery = mentionsBattery(d.text);
            return d;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /**
     * Los tres filtros de la pantalla, sobre la lista final (PVPC incluido). «Con autoconsumo»
     * usa el mismo criterio que la fila: la CNMC o un precio puesto a mano; si no, se filtraba
     * distinto de lo que se pintaba.
     */
    public static List<Offer> applyFilters(List<Offer> all, Store st) {
        List<Offer> out = new ArrayList<>();
        for (Offer o : all) {
            // El PVPC no es una oferta de mercado: solo lo quita su casilla («sin PVPC»). Las
            // otras dos filtran ofertas, y la CNMC marca el PVPC con autoconsumo=false y
            // verde=false, así que lo escondían sin querer al quitar «sin PVPC».
            if (o.isRegulated()) {
                if (!st.filterFixed()) out.add(o);
                continue;
            }
            if (st.filterSelfUse() && !o.compensates() && st.offerSurplus(o) <= 0) continue;
            if (st.filterGreen() && !o.green) continue;
            out.add(o);
        }
        return out;
    }

    /**
     * Lo que cuestan la potencia y el alquiler del contador en el PVPC, con impuestos. Se le
     * pide al comparador la misma casa con 1 kWh de consumo: lo que devuelve el PVPC es su parte
     * fija. Hace falta para poder enseñar el PVPC con los precios reales de ESIOS junto a las
     * ofertas, que sí llevan la potencia dentro. Devuelve 0 si no se puede.
     */
    public static double pvpcFixed(BillData b) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(buildUrl(b, 1, 1, 0, 0)).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "Analuzis/1.1 (uso personal)");
            if (c.getResponseCode() != 200) return 0;
            JSONObject root = new JSONObject(read(c.getInputStream()));
            JSONArray arr = root.optJSONArray("resultadoComparador");
            if (arr == null) arr = root.optJSONArray("resultadoComparadorSinAjustePrecio");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && "PVPC".equalsIgnoreCase(o.optString("tipoElectricidad", ""))) {
                    return o.optDouble("importePrimerAnio", 0);
                }
            }
            return 0;
        } catch (Exception e) {
            return 0;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static String buildUrl(BillData b) {
        return buildUrl(b, b.kwhYear, b.kwh1, b.kwh2, b.kwh3);
    }

    /** La misma consulta con otro consumo: sirve para aislar la parte fija del PVPC. */
    public static String buildUrl(BillData b, int kwhYear, int kwh1, int kwh2, int kwh3) {
        int p1 = (int) Math.round(b.p1);
        int p2 = (int) Math.round(b.p2);

        StringBuilder s = new StringBuilder(ENDPOINT);
        s.append("?tipoSuministro=E");
        s.append("&codigoPostal=").append(enc(b.cp));
        s.append("&potencia=").append(fmt(b.p1));
        s.append("&potenciaPrimeraFranja=").append(fmt(b.p1));
        s.append("&potenciaSegundaFranja=").append(fmt(b.p2));
        s.append("&potenciaTerceraFranja=").append(fmt(b.p2));
        s.append("&potenciaCuartaFranja=0&potenciaQuintaFranja=0&potenciaSextaFranja=0");
        s.append("&consumoAnualE=").append(kwhYear);
        s.append("&consumoAnualEOrig=").append(kwhYear);
        s.append("&consumoPrimeraFranja=").append(kwh1);
        s.append("&consumoSegundaFranja=").append(kwh2);
        s.append("&consumoTerceraFranja=").append(kwh3);
        s.append("&consumoCuartaFranja=0&consumoQuintaFranja=0&consumoSextaFranja=0");
        s.append("&tarifa=4");
        s.append("&consumoAnualG=6000&consumoAnualGOrig=6000");
        // Los filtros de servicios y permanencia se quitaron de la interfaz; en la API, 1
        // significa «excluir» y 2 «incluir todo». Con 1/1 el comparador devolvía 43 ofertas de
        // 18 comercializadoras con el perfil de esta casa; con 2/2, 65 de 25. No se filtra por
        // servicios ni permanencia: se enseñan todas y cada una lleva su píldora.
        s.append("&serviciosAdicionales=2&permanencia=2&idOferta=&vivienda=true&factura=true");
        // El consumo anual que pedimos al comparador es el que compras de la red (el de
        // tus facturas) y los excedentes los descuento yo, igual para todas las ofertas.
        // Si le paso el autoconsumo, la CNMC resta esa energía a unas ofertas (PVPC) y a
        // las de precio fijo no, y deja de ser una comparación en los mismos términos.
        s.append("&energiaAutoconsumo=0");
        s.append("&idAuditoriaQR=0");
        s.append("&potenciaAutoconsumo=0");
        s.append("&revisionPrecios=2");
        s.append("&autoconsumo=false");
        // El comparador nuevo (SICOMP) exige el perfil de consumo y los campos del QR a cero:
        // sin ellos responde HTTP 500 aunque los datos de la casa estén bien. Comprobado el
        // 24/11/2026 con la propia web: con estos campos vuelve a dar las ~86 ofertas.
        s.append("&perfilConsumo=13");
        s.append("&consumoAnualEQr=0&consumoPrimeraFranjaQr=0&consumoSegundaFranjaQr=0");
        s.append("&consumoTerceraFranjaQr=0&consumoCuartaFranjaQr=0&consumoQuintaFranjaQr=0");
        s.append("&consumoSextaFranjaQr=0");
        s.append("&consumoAnualEPQr=0&consumoPrimeraFranjaPQr=0&consumoSegundaFranjaPQr=0");
        s.append("&consumoTerceraFranjaPQr=0&consumoCuartaFranjaPQr=0&consumoQuintaFranjaPQr=0");
        s.append("&consumoSextaFranjaPQr=0");
        return s.toString();
    }

    private static String fmt(double v) {
        // El comparador admite decimales en potencia; usamos punto.
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    private static String enc(String v) {
        return v == null ? "" : v.replace(" ", "%20");
    }

    /** El «detail» que explica el error, o el cuerpo recortado si no es JSON. */
    private static String detail(HttpURLConnection c) {
        String body = "";
        try {
            InputStream err = c.getErrorStream();
            if (err != null) body = read(err).trim();
        } catch (Exception ignored) {
        }
        if (body.isEmpty()) return "";
        try {
            JSONObject o = new JSONObject(body);
            String d = clean(o.optString("detail", ""));
            String t = clean(o.optString("title", ""));
            if (!d.isEmpty() && !t.isEmpty()) return t + ": " + d;
            if (!d.isEmpty()) return d;
            if (!t.isEmpty()) return t;
        } catch (Exception ignored) {
        }
        String flat = clean(body.replace('\n', ' '));
        return flat.length() > 180 ? flat.substring(0, 180) + "…" : flat;
    }

    private static String clean(String s) {
        if (s == null) return "";
        s = s.replace("  ", " ").trim();
        return s;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        byte[] data = out.toByteArray();
        String s = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        if (s.indexOf('\uFFFD') >= 0) {
            s = new String(data, java.nio.charset.StandardCharsets.ISO_8859_1);
        }
        return s;
    }
}
