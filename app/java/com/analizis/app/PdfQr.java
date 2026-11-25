package com.analizis.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Busca el enlace del QR oficial de la CNMC dentro de un PDF de factura.
 * La estructura del PDF la resuelve PdfReader: aquí solo queda lo de Android,
 * que es convertir las imágenes incrustadas en Bitmap y descodificar el QR.
 */
public class PdfQr {

    /** El texto ya viene extraído por PdfReader: no se lee el PDF dos veces. */
    public static List<String> find(byte[] data, String text) {
        List<String> out = new ArrayList<>();
        if (data == null || data.length < 200 || data.length > 25 * 1024 * 1024) return out;

        // 1) el enlace, en el texto decodificado o en el propio archivo
        addUrls(text, out);
        if (out.isEmpty()) addUrls(new String(data, java.nio.charset.StandardCharsets.ISO_8859_1), out);
        if (!out.isEmpty()) return out;

        // 2) el QR como imagen incrustada
        for (PdfReader.Img img : PdfReader.images(data)) {
            Bitmap bmp = build(img.dict, img.data);
            String t = decodeQr(bmp);
            if (t != null && t.contains("comparador") && !out.contains(t)) out.add(t);
        }
        return out;
    }

    private static void addUrls(String s, List<String> out) {
        if (s == null) return;
        int i = 0;
        int guard = 0;
        while (guard++ < 4000) {
            i = s.indexOf("comparador.cnmc", i);
            if (i < 0) return;
            int start = s.lastIndexOf("http", i);
            if (start < 0) {
                i += 15;
                continue;
            }
            int end = start;
            while (end < s.length()) {
                char c = s.charAt(end);
                if (c == ')' || c == '<' || c == '>' || c == ']' || c == '\\' || c == '\n'
                        || c == '\r' || c == ' ' || c == '"' || c == '\'' || c == '\t') break;
                end++;
            }
            String u = s.substring(start, end);
            if (u.length() > 40 && !out.contains(u)) out.add(u);
            i = end > i ? end : i + 15;
        }
    }

    private static Bitmap build(String dict, byte[] raw) {
        int w = num(dict, "/Width");
        int h = num(dict, "/Height");
        int bpc = num(dict, "/BitsPerComponent");
        if (bpc <= 0) bpc = 8;
        if (w < 12 || h < 12 || w > 2500 || h > 2500) return null;

        if (dict.contains("/DCTDecode")) {
            try {
                return BitmapFactory.decodeByteArray(raw, 0, raw.length);
            } catch (Exception e) {
                return null;
            }
        }
        if (!dict.contains("FlateDecode")) return null;

        byte[] px = PdfReader.inflate(raw);
        if (px == null) return null;

        if (bpc == 1) {
            int rowBytes = (w + 7) / 8;
            if (px.length < rowBytes * h) return null;
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int bit = (px[y * rowBytes + (x >> 3)] >> (7 - (x & 7))) & 1;
                    // En DeviceGray 1 bit: 0 = negro, 1 = blanco.
                    pixels[y * w + x] = bit != 0 ? Color.WHITE : Color.BLACK;
                }
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, w, 0, 0, w, h);
            return bmp;
        }
        if (bpc == 8) {
            int ch = dict.contains("/DeviceRGB") ? 3 : (dict.contains("/DeviceCMYK") ? 4 : 1);
            if (px.length < w * h * ch) return null;
            int[] pixels = new int[w * h];
            for (int i = 0; i < w * h; i++) {
                int o = i * ch;
                int r = px[o] & 0xFF, g = r, b = r;
                if (ch == 3) {
                    g = px[o + 1] & 0xFF;
                    b = px[o + 2] & 0xFF;
                } else if (ch == 4) {
                    r = 255 - r;
                    g = 255 - (px[o + 1] & 0xFF);
                    b = 255 - (px[o + 2] & 0xFF);
                }
                pixels[i] = Color.rgb(r, g, b);
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, w, 0, 0, w, h);
            return bmp;
        }
        return null;
    }

    private static String decodeQr(Bitmap bmp) {
        if (bmp == null) return null;
        String t = QrDecoder.decode(bmp);
        if (t != null) return t;
        for (int k = 2; k <= 6; k += 2) {
            if (Math.max(bmp.getWidth(), bmp.getHeight()) * k > 2600) break;
            Bitmap up = Bitmap.createScaledBitmap(bmp, bmp.getWidth() * k, bmp.getHeight() * k, false);
            t = QrDecoder.decode(up);
            if (t != null) return t;
        }
        return null;
    }

    private static int num(String dict, String key) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile(java.util.regex.Pattern.quote(key) + "\\s+(\\d+)").matcher(dict);
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Exception ignored) {
        }
        return 0;
    }
}
