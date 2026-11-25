package com.analizis.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee la web de una comercializadora desde el propio móvil, sin servidor ni servicio externo:
 * descarga la página de la oferta, la deja en texto plano y busca el €/kWh de los excedentes
 * con el mismo patrón que usa la ficha de la CNMC (`CnmcClient.scanSurplus`).
 *
 * Muchas webs cargan los precios con JavaScript, así que no siempre habrá suerte; cuando no la
 * hay se dice y manda el campo manual, como antes.
 */
final class Web {

    private Web() {
    }

    /** Tope de lectura: con la mitad de una web de marketing hay de sobra. */
    private static final int MAX_BYTES = 1200 * 1024;

    /**
     * Texto plano de la página, o null si no se pudo leer (sin red, 403, un PDF, una web que
     * necesita JavaScript…). Puede devolver cadena vacía si la página no tiene texto.
     */
    static String page(String url) {
        if (url == null || url.isEmpty()) return null;
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) return null;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(12000);
            c.setInstanceFollowRedirects(true);
            // Sin un agente de navegador, muchas webs devuelven 403 a lo primero que no conocen.
            c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) Analuzis/1.0 (uso personal)");
            c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            c.setRequestProperty("Accept-Language", "es-ES,es;q=0.9");
            if (c.getResponseCode() != 200) return null;
            String type = String.valueOf(c.getContentType()).toLowerCase(Locale.ROOT);
            // Un PDF de condiciones no se intenta: aquí solo se lee HTML.
            if (!type.contains("html") && !type.contains("text")) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total >= MAX_BYTES) break;
            }
            in.close();
            byte[] data = out.toByteArray();
            String s = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            if (s.indexOf('\uFFFD') >= 0) s = new String(data, "ISO-8859-1");
            return text(s);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /**
     * Prueba la web de la oferta y, si ahí no lo dice, la de las condiciones. Rellena
     * `webSurplus`, `webQuote` y `battery` del detalle, y deja en `page` la que se leyó.
     * Devuelve true si llegó a leer alguna página (aunque no encontrara el precio).
     */
    static boolean scanOffer(CnmcClient.Detail d) {
        if (d == null) return false;
        boolean read = false;
        String[] urls = {d.web, d.contract, d.particular};
        for (String url : urls) {
            if (url == null || url.isEmpty() || url.equals(d.page)) continue;
            String t = page(url);
            if (t == null) continue;
            read = true;
            d.page = url;
            if (d.webSurplus <= 0) {
                CnmcClient.Detail found = new CnmcClient.Detail();
                CnmcClient.scanSurplus(t, found);
                if (found.surplus > 0) {
                    d.webSurplus = found.surplus;
                    d.webQuote = found.surplusQuote;
                }
            }
            if (!d.battery) d.battery = CnmcClient.mentionsBattery(t);
            if (d.webSurplus > 0) break;   // ya está: no hace falta seguir con otra página
        }
        return read;
    }

    /** HTML a texto: fuera script/style, etiquetas a espacios y entidades resueltas. */
    static String text(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>", " ");
        s = s.replaceAll("(?s)<!--.*?-->", " ");
        s = s.replaceAll("(?i)<(br|/p|/div|/li|/h[1-6]|/tr|/td|/section)[^>]*>", "\n");
        s = s.replaceAll("<[^>]{0,400}>", " ");
        s = entities(s);
        s = s.replace('\u00a0', ' ');
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        s = s.replaceAll(" ?\\n ?", "\n");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    private static final Pattern NUM = Pattern.compile("&#(x?[0-9a-fA-F]{1,6});");

    private static String entities(String s) {
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&#39;", "'").replace("&euro;", "€").replace("&aacute;", "á")
                .replace("&eacute;", "é").replace("&iacute;", "í").replace("&oacute;", "ó")
                .replace("&uacute;", "ú").replace("&ntilde;", "ñ").replace("&ordf;", "ª")
                .replace("&middot;", "·").replace("&ndash;", "–").replace("&mdash;", "—")
                .replace("&#160;", " ").replace("&#8364;", "€");
        Matcher m = NUM.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String g = m.group(1);
            int cp;
            try {
                cp = g.charAt(0) == 'x' || g.charAt(0) == 'X'
                        ? Integer.parseInt(g.substring(1), 16) : Integer.parseInt(g);
            } catch (Exception e) {
                cp = -1;
            }
            m.appendReplacement(sb, cp > 0 && cp <= 0x10FFFF
                    ? Matcher.quoteReplacement(new String(Character.toChars(cp))) : "");
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
