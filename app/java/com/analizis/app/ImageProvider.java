package com.analizis.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Muy pequeño proveedor de ficheros para pasar la foto a la app de cámara
 * sin pedir permisos y sin librerías externas.
 */
public class ImageProvider extends ContentProvider {

    public static final String AUTHORITY = "com.analizis.app.files";

    public static Uri uriFor(Context c, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context c = getContext();
        if (c == null) throw new FileNotFoundException();
        // Los PDFs solo se leen: content://…files/pdf/<nombre>
        List<String> parts = uri.getPathSegments();
        if (parts.size() == 2 && "pdf".equals(parts.get(0))) {
            File pdf = Pdfs.file(c, parts.get(1));
            if (pdf == null || !pdf.isFile()) throw new FileNotFoundException();
            return ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        File dir = new File(c.getCacheDir(), "images");
        if (!dir.exists()) dir.mkdirs();
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) throw new FileNotFoundException();
        File f = new File(dir, name);
        int flags = ParcelFileDescriptor.MODE_CREATE;
        flags |= (mode != null && mode.startsWith("r") && !mode.contains("w"))
                ? ParcelFileDescriptor.MODE_READ_ONLY
                : ParcelFileDescriptor.MODE_READ_WRITE;
        return ParcelFileDescriptor.open(f, flags);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        List<String> parts = uri.getPathSegments();
        return parts.size() == 2 && "pdf".equals(parts.get(0)) ? "application/pdf" : "image/jpeg";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
