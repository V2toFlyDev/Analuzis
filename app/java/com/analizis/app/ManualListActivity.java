package com.analizis.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Las ofertas escritas a mano con el mismo aspecto que el ranking: una tarjeta por oferta con
 * sus píldoras (verde, excedentes, batería, permanencia) y ordenadas de más barata a más cara
 * con los datos del usuario. Al tocar una tarjeta se abre su editor.
 */
public class ManualListActivity extends Activity {

    private Store store;
    private BillData bill;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new Store(this);
        View root = buildShell();
        setContentView(root);
        Insets.apply(root, 0, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Se repinta al volver del editor: si se ha guardado o borrado, la lista cambia.
        render();
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(18), dp(16), dp(10));

        TextView back = new TextView(this);
        back.setText("‹  Volver");
        back.setTextColor(getColor(R.color.muted));
        back.setTextSize(15);
        back.setPadding(dp(4), dp(6), dp(10), dp(6));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0);
        slp.weight = 1;
        root.addView(scroll, slp);

        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), 0, dp(20), dp(36));
        scroll.addView(container);
        return root;
    }

    private void render() {
        bill = new History(store.invoices()).apply(store.load().copy());
        boolean cost = bill.complete();
        List<ManualOffer> all = new ArrayList<>(store.manualOffers());
        // De más barata a más cara, igual que el ranking. Sin factura no hay coste con el que
        // ordenar: se ordenan por nombre y se avisa de que falta la factura.
        if (cost) all.sort((a, z) -> Double.compare(a.net(bill), z.net(bill)));
        else all.sort((a, z) -> a.name.compareToIgnoreCase(z.name));

        container.removeAllViews();

        TextView title = new TextView(this);
        title.setText("OFERTAS A MANO" + (all.isEmpty() ? "" : " · " + all.size()));
        title.setTextColor(getColor(R.color.muted));
        title.setTextSize(11);
        title.setLetterSpacing(0.12f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(4), dp(6), 0, dp(6));
        container.addView(title);

        if (all.isEmpty()) {
            container.addView(msg("Todavía no hay ninguna oferta a mano. Con «Comparador manual», "
                    + "en la pantalla principal, escribes los precios que publica su web y se "
                    + "comparan con tus datos, igual que las de la CNMC."));
            return;
        }

        TextView sub = new TextView(this);
        sub.setText(cost
                ? "De más barata a más cara, con tu consumo, tu potencia y tus impuestos."
                : "Añade tu factura en la pantalla principal para ver el coste anual; ahora van "
                  + "por nombre.");
        sub.setTextColor(getColor(R.color.muted));
        sub.setTextSize(12);
        sub.setLineSpacing(dp(3), 1f);
        sub.setPadding(dp(4), 0, dp(4), dp(4));
        container.addView(sub);

        double current = cost ? bill.baselineAnnual() : 0;
        for (int i = 0; i < all.size(); i++) {
            container.addView(card(all.get(i), i + 1, current));
        }

        TextView note = new TextView(this);
        note.setText("El coste es con tu consumo, tu potencia y tus impuestos: el bono social, el "
                + "contador y los impuestos salen de tu última factura. Toca una oferta para "
                + "editarla o borrarla.");
        note.setTextColor(getColor(R.color.muted));
        note.setTextSize(11);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        container.addView(note);
    }

    /** Una tarjeta como las del comparador: puesto, nombre, coste al año y píldoras. */
    private View card(final ManualOffer m, int rank, double current) {
        LinearLayout card = card();

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView num = new TextView(this);
        num.setText(String.valueOf(rank));
        num.setTextColor(rank <= 3 ? getColor(R.color.accent) : getColor(R.color.muted));
        num.setTextSize(16);
        num.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        num.setWidth(dp(26));
        top.addView(num);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp.weight = 1;
        mid.setLayoutParams(mlp);

        TextView name = new TextView(this);
        name.setText(m.name);
        name.setTextColor(getColor(R.color.text));
        name.setTextSize(15);
        name.setMaxLines(2);
        mid.addView(name);

        TextView sup = new TextView(this);
        sup.setText(m.supplier == null || m.supplier.isEmpty() ? "Sin comercializadora" : m.supplier);
        sup.setTextColor(getColor(R.color.muted));
        sup.setTextSize(11);
        sup.setPadding(0, dp(2), 0, 0);
        mid.addView(sup);
        top.addView(mid);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView price = new TextView(this);
        price.setText(bill.complete() ? Fmt.eur(m.net(bill)) : "—");
        price.setTextColor(getColor(R.color.text));
        price.setTextSize(17);
        price.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        price.setGravity(Gravity.END);
        right.addView(price);
        TextView per = new TextView(this);
        per.setText("al año");
        per.setTextColor(getColor(R.color.muted));
        per.setTextSize(10);
        per.setGravity(Gravity.END);
        right.addView(per);
        top.addView(right);

        card.addView(top);

        List<TextView> pills = new ArrayList<>();
        pills.add(pill("MANUAL", true));
        if (m.compensates) {
            pills.add(m.surplusPrice > 0
                    ? pill("Excedentes " + Fmt.dec(m.surplusPrice, 3) + " €/kWh", true)
                    : pill(bill.surplusFromBill() ? "Excedentes · factura" : "Excedentes · precio × kWh",
                           false, bill.surplusFromBill() ? getColor(R.color.text) : getColor(R.color.warn)));
        }
        if (m.battery) pills.add(pill("Batería virtual", false));
        if (m.green) pills.add(pill("Verde", true));
        if (m.penalty) pills.add(pill("Con permanencia", false));
        addPills(card, pills);

        if (current > 0) {
            double save = current - m.net(bill);
            TextView s = new TextView(this);
            s.setText(save > 0
                    ? "Ahorras " + Fmt.eur(save) + " al año"
                    : "Te costaría " + Fmt.eur(-save) + " más al año");
            s.setTextColor(save > 0 ? getColor(R.color.accent) : getColor(R.color.warn));
            s.setTextSize(12);
            s.setPadding(dp(26), dp(8), 0, 0);
            card.addView(s);
        }

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(dp(26), dp(10), 0, 0);
        TextView edited = new TextView(this);
        edited.setText("Editada el " + day(m.updatedAt));
        edited.setTextColor(getColor(R.color.muted));
        edited.setTextSize(11);
        foot.addView(edited, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView open = new TextView(this);
        open.setText("Editar ›");
        open.setTextColor(getColor(R.color.accent));
        open.setTextSize(12);
        foot.addView(open);
        card.addView(foot);

        card.setOnClickListener(v -> startActivity(new Intent(this, ManualActivity.class)
                .putExtra(ManualActivity.EXTRA_ID, m.id)));
        return card;
    }

    // ---------------- piezas de interfaz (las mismas que el ranking) ----------------

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(getDrawable(R.drawable.bg_card));
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        l.setLayoutParams(lp);
        return l;
    }

    private TextView pill(String text, boolean accent) {
        return pill(text, accent, 0);
    }

    private TextView pill(String text, boolean accent, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(10);
        t.setTextColor(color != 0 ? color
                : (accent ? getColor(R.color.accent) : getColor(R.color.muted)));
        t.setBackground(getDrawable(accent ? R.drawable.bg_pill_green : R.drawable.bg_pill_mute));
        t.setPadding(dp(9), dp(4), dp(9), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    /** Reparte las píldoras en filas que quepan, midiéndolas antes (como en el ranking). */
    private void addPills(LinearLayout card, List<TextView> list) {
        int max = getResources().getDisplayMetrics().widthPixels - dp(20 * 2 + 14 * 2 + 26 + 2);
        LinearLayout row = null;
        int used = 0;
        for (TextView p : list) {
            p.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int w = p.getMeasuredWidth() + dp(6);
            if (row == null || used + w > max) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(26), dp(8), 0, 0);
                card.addView(row);
                used = 0;
            }
            row.addView(p);
            used += w;
        }
    }

    private TextView msg(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(getColor(R.color.muted));
        t.setTextSize(14);
        t.setLineSpacing(dp(4), 1f);
        t.setPadding(dp(4), dp(24), dp(4), dp(12));
        return t;
    }

    private static String day(long at) {
        if (at <= 0) return "—";
        return new java.text.SimpleDateFormat("dd/MM/yy", new Locale("es", "ES"))
                .format(new java.util.Date(at));
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
