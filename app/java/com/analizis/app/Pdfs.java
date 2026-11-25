package com.analizis.app;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Copia de los PDFs importados dentro de la app. Sin ella no se pueden volver a abrir:
 * al importar se lee el fichero y se tira, y el original puede estar en un correo o en
 * una nube a la que ya no llegas desde aquí.
 */
public final class Pdfs {

    private Pdfs() {
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "pdfs");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Nombre seguro a partir del periodo: «2026-10-03_2026-11-02.pdf». */
    public static String nameFor(String from, String to) {
        String a = from == null ? "" : from.replaceAll("[^0-9-]", "");
        String b = to == null ? "" : to.replaceAll("[^0-9-]", "");
        return (a.isEmpty() ? "factura" : a) + "_" + (b.isEmpty() ? "fin" : b) + ".pdf";
    }

    /** Null si el nombre no es un nombre de fichero simple. */
    public static File file(Context c, String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("..")) return null;
        return new File(dir(c), name);
    }

    public static void save(Context c, String name, byte[] bytes) {
        File f = file(c, name);
        if (f == null || bytes == null || bytes.length == 0) return;
        try {
            FileOutputStream o = new FileOutputStream(f);
            o.write(bytes);
            o.close();
        } catch (Exception ignored) {
        }
    }

    public static boolean has(Context c, String name) {
        File f = file(c, name);
        return f != null && f.isFile() && f.length() > 0;
    }

    public static void delete(Context c, String name) {
        File f = file(c, name);
        if (f != null && f.exists()) f.delete();
    }

    /** Lo sirve el proveedor de la propia app; se le da permiso de lectura al abrirlo. */
    public static Uri uri(Context c, String name) {
        return new Uri.Builder()
                .scheme("content")
                .authority(ImageProvider.AUTHORITY)
                .appendPath("pdf")
                .appendPath(name)
                .build();
    }
}
