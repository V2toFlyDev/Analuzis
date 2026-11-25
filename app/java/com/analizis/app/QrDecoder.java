package com.analizis.app;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Lectura del QR desde una foto, en el propio móvil y sin internet. */
public class QrDecoder {

    public static String decode(Bitmap original) {
        if (original == null) return null;
        Bitmap b = original;
        int max = 1600;
        int side = Math.max(b.getWidth(), b.getHeight());
        if (side > max) {
            float s = max / (float) side;
            b = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)),
                    Math.max(1, Math.round(b.getHeight() * s)), true);
        }

        String text = tryDecode(b);
        if (text != null) return text;

        // Segundo intento: recorte central (el QR suele estar en medio de la factura).
        int w = b.getWidth(), h = b.getHeight();
        int cw = (int) (w * 0.7), ch = (int) (h * 0.7);
        if (cw > 40 && ch > 40) {
            Bitmap crop = Bitmap.createBitmap(b, (w - cw) / 2, (h - ch) / 2, cw, ch);
            text = tryDecode(crop);
        }
        return text;
    }

    private static String tryDecode(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] pixels = new int[w * h];
        b.getPixels(pixels, 0, w, 0, 0, w, h);
        LuminanceSource source = new RGBLuminanceSource(w, h, pixels);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(BarcodeFormat.QR_CODE));
        try {
            Result r = new MultiFormatReader().decode(bitmap, hints);
            return r.getText();
        } catch (Exception e) {
            return null;
        }
    }
}
