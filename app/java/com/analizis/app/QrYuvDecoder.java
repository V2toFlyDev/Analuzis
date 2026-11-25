package com.analizis.app;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Convierte el plano de luminancia de la cámara y busca un QR.
 * Probado con imágenes sintéticas (incluidas giradas 90 y 270 grados).
 */
public final class QrYuvDecoder {

    private QrYuvDecoder() {
    }

    public static String decode(byte[] y, int rowStride, int width, int height) {
        byte[] p = pack(y, rowStride, width, height);
        String t = quick(p, width, height);
        if (t != null) return t;
        t = quick(rotate90(p, width, height), height, width);
        if (t != null) return t;
        t = quick(rotate270(p, width, height), height, width);
        if (t != null) return t;

        // Último intento: zona central más pequeña y búsqueda a fondo.
        int s = ((int) (Math.min(width, height) * 0.7)) & ~1;
        return one(p, width, height, (width - s) / 2, (height - s) / 2, s, s, true);
    }

    /** Deja el plano Y sin relleno (rowStride puede ser mayor que el ancho). */
    static byte[] pack(byte[] y, int rowStride, int width, int height) {
        byte[] out = new byte[width * height];
        for (int r = 0; r < height; r++) {
            int src = r * rowStride;
            if (src >= y.length) break;
            int n = Math.min(width, y.length - src);
            System.arraycopy(y, src, out, r * width, n);
        }
        return out;
    }

    private static String quick(byte[] p, int w, int h) {
        String t = one(p, w, h, 0, 0, w, h, false);
        if (t != null) return t;
        int s = Math.min(w, h) & ~1;
        return one(p, w, h, (w - s) / 2, (h - s) / 2, s, s, false);
    }

    private static String one(byte[] p, int w, int h, int left, int top, int cw, int ch, boolean hard) {
        try {
            PlanarYUVLuminanceSource src = new PlanarYUVLuminanceSource(p, w, h, left, top, cw, ch, false);
            BinaryBitmap bmp = new BinaryBitmap(new HybridBinarizer(src));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(BarcodeFormat.QR_CODE));
            if (hard) hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            Result r = new MultiFormatReader().decode(bmp, hints);
            return r == null ? null : r.getText();
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] rotate90(byte[] src, int w, int h) {
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++) {
            int base = y * w;
            for (int x = 0; x < w; x++) {
                out[x * h + (h - 1 - y)] = src[base + x];
            }
        }
        return out;
    }

    static byte[] rotate270(byte[] src, int w, int h) {
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++) {
            int base = y * w;
            for (int x = 0; x < w; x++) {
                out[(w - 1 - x) * h + y] = src[base + x];
            }
        }
        return out;
    }
}
