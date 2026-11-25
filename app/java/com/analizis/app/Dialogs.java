package com.analizis.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Piezas para construir los diálogos con el mismo lenguaje visual que las tarjetas:
 * etiquetas en mayúsculas, filas etiqueta/valor, notas al pie. Así la información
 * se lee de un vistazo en vez de un bloque de texto.
 */
public final class Dialogs {

    private Dialogs() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static LinearLayout body(Context c) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        // Márgenes laterales: sin esto el contenido va pegado al borde de la tarjeta del
        // diálogo (se veía en el de Datadis) y los pies largos se cortaban por la derecha.
        b.setPadding(dp(c, 18), dp(c, 4), dp(c, 18), dp(c, 6));
        return b;
    }

    /** Diálogo al 90 % del ancho: ni pegado a los bordes ni recortado por la derecha. */
    public static void width(android.app.AlertDialog dlg) {
        if (dlg == null || dlg.getWindow() == null) return;
        int w = Math.round(dlg.getContext().getResources().getDisplayMetrics().widthPixels * 0.90f);
        dlg.getWindow().setLayout(w, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
    }

    /** Cabecera de bloque: «CONSUMO», «IMPORTE»… */
    public static void section(Context c, LinearLayout parent, String text) {
        TextView t = new TextView(c);
        t.setText(text.toUpperCase(new Locale("es", "ES")));
        t.setTextSize(10);
        t.setLetterSpacing(0.14f);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        t.setTextColor(c.getColor(R.color.muted));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, parent.getChildCount() == 0 ? 2 : 18);
        lp.bottomMargin = dp(c, 4);
        parent.addView(t, lp);
    }

    public static void row(Context c, LinearLayout parent, String label, String value) {
        row(c, parent, label, value, c.getColor(R.color.text));
    }

    /** Etiqueta a la izquierda, valor a la derecha. Es lo que hace legible un detalle. */
    public static void row(Context c, LinearLayout parent, String label, String value, int color) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(c, 6), 0, dp(c, 6));

        TextView l = new TextView(c);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(c.getColor(R.color.muted));
        l.setMaxLines(3);
        row.addView(l, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(13);
        v.setTextColor(color);
        v.setGravity(Gravity.END);
        v.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        // Un valor muy largo («Válida sólo para consumidores domésticos») ensanchaba la fila
        // más que la pantalla y el diálogo salía recortado por los dos lados: aquí se parte.
        // Con el 48 % y hasta 4 líneas, entran dos columnas holgadas dentro del diálogo.
        v.setMaxWidth(Math.round(c.getResources().getDisplayMetrics().widthPixels * 0.48f));
        v.setMaxLines(4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(c, 12);
        row.addView(v, lp);

        parent.addView(row);
    }

    /**
     * Ancho del contenedor. Un `TextView` añadido sin LayoutParams mide su texto en una sola
     * línea y estira el diálogo entero más allá de la pantalla (era el recorte del detalle de
     * oferta); con esto parte dentro. Es lo primero que hay que mirar si un diálogo se sale.
     */
    public static void fill(View v) {
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** Etiqueta arriba y valor debajo, a la izquierda: para textos largos que no caben al lado. */
    public static void wideRow(Context c, LinearLayout parent, String label, String value, int color) {
        TextView l = new TextView(c);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(c.getColor(R.color.muted));
        l.setPadding(0, dp(c, 10), 0, dp(c, 2));
        parent.addView(l, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(13);
        v.setTextColor(color);
        v.setLineSpacing(dp(c, 3), 1f);
        parent.addView(v, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** Nota al pie o aclaración: pequeña y en gris. */
    public static void note(Context c, LinearLayout parent, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(c.getColor(R.color.muted));
        t.setLineSpacing(dp(c, 3), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        parent.addView(t, lp);
    }

    /** Aviso con fondo tenue, para lo que no se puede pasar por alto. */
    public static TextView banner(Context c, String text, int bg, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(color);
        t.setLineSpacing(dp(c, 3), 1f);
        t.setBackgroundResource(bg);
        t.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 12);
        t.setLayoutParams(lp);
        return t;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(c.getColor(R.color.line));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1)));
        lp.topMargin = dp(c, 10);
        v.setLayoutParams(lp);
        return v;
    }

    /** Opción de un menú: fila ancha, con su flecha, para tocar con comodidad. */
    public static LinearLayout option(Context c, String text) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setPadding(0, dp(c, 13), dp(c, 2), dp(c, 13));

        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(c.getColor(R.color.text));
        row.addView(t, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = new TextView(c);
        chevron.setText("›");
        chevron.setTextSize(16);
        chevron.setTextColor(c.getColor(R.color.muted));
        chevron.setPadding(dp(c, 10), 0, 0, 0);
        row.addView(chevron);
        return row;
    }

    /**
     * Scroll con altura máxima: si el contenido es corto ocupa lo justo y, si es largo,
     * se desplaza en vez de salirse de la pantalla.
     */
    public static ScrollView scroller(Context c, View child, int maxDp) {
        final int max = dp(c, maxDp);
        ScrollView sc = new ScrollView(c) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec,
                        MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
            }
        };
        sc.setClipToPadding(false);
        sc.addView(child, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sc;
    }
}
