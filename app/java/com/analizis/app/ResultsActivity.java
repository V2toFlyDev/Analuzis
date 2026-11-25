package com.analizis.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ranking de ofertas y análisis de la factura. */
public class ResultsActivity extends Activity {

    private static List<Offer> cached;
    private static String cachedSupplier = "";
    private static long cachedAt = 0;
    /** Lo que el PVPC cobra por potencia y contador, pedido al comparador. 0 si no se pudo. */
    private static double cachedPvpcFixed = 0;
    /** Si el PVPC compensa excedentes, según el comparador. La fila propia hereda ese dato. */
    private static boolean cachedPvpcSelfUse = false;

    // Cuánto puede pesar la parte fija dentro del PVPC que da la CNMC. Es una horquilla ancha
    // (con 7,392/8,048 kW pesa ~19 %), solo para cazar una respuesta que ya no signifique lo
    // mismo: por debajo del 10 % o por encima del 60 % no se usa.
    private static final double PVPC_FIXED_MIN = 0.10;
    private static final double PVPC_FIXED_MAX = 0.60;

    private String mySupplier = "";
    /** Aviso de que lo que se enseña es la comparativa guardada, no una consulta de ahora. */
    private String staleNote = "";
    /** Las fichas se piden una vez por pantalla; lo que dicen se guarda una semana en Store. */
    private boolean fichasPedidas = false;
    private boolean fichasCargando = false;
    private Store store;
    private LinearLayout container;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    /** Para repintar solo cuando se ha guardado o borrado una oferta a mano (onResume). */
    private long seenManualStamp = -1;
    /** Las entradas a mano de la última pintada, por id, para calcular sus filas. */
    private final java.util.Map<Long, ManualOffer> manualRows = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new Store(this);
        seenManualStamp = store.manualStamp();
        View root = buildShell();
        setContentView(root);
        Insets.apply(root, 0, 0);
        loadOffers();
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

    private void loadOffers() {
        BillData b = new History(store.invoices()).apply(store.load().copy());
        if (!b.complete()) {
            container.addView(msg("Primero añade tu factura en la pantalla principal.", true));
            return;
        }
        if (cached != null && System.currentTimeMillis() - cachedAt < 10 * 60 * 1000) {
            mySupplier = cachedSupplier;
            render(cached);
            return;
        }

        TextView loading = msg("Consultando las ofertas del mercado…", false);
        container.addView(loading);

        pool.execute(() -> {
            try {
                List<Offer> all = CnmcClient.fetch(b);
                // Solo hace falta si hay PVPC guardado: es para ponerlo en el ranking con la
                // potencia dentro, como el resto de ofertas.
                final double fixed = validatedPvpcFixed(b, all);
                final boolean pvpcSelf = cachedPvpcSelfUse;
                final List<String> avisos = new ArrayList<>(CnmcClient.lastAvisos);
                String sup = CnmcClient.supplierName(b.supplierCode);
                if (sup != null && !sup.isEmpty()) new Store(this).setSupplierName(sup);
                runOnUiThread(() -> {
                    container.removeAllViews();
                    cached = all;
                    cachedSupplier = sup;
                    mySupplier = sup;
                    cachedPvpcFixed = fixed;
                    cachedPvpcSelfUse = pvpcSelf;
                    cachedAt = System.currentTimeMillis();
                    staleNote = "";
                    // Guardada para poder enseñarla si la CNMC vuelve a fallar.
                    new Store(this).saveOffers(all, fixed, pvpcSelf, avisos);
                    render(all);
                });
            } catch (Exception e) {
                // Un 500 del comparador no es falta de internet: el móvil ha llegado a su
                // servidor y el fallo es suyo. Se dice así para no mandar a nadie a mirar el wifi.
                final CnmcClient.ApiException api = e instanceof CnmcClient.ApiException
                        ? (CnmcClient.ApiException) e : null;
                runOnUiThread(() -> {
                    // Antes de dar un error, la última comparativa buena que tengamos guardada.
                    Store.SavedOffers saved = store.savedOffers();
                    if (saved != null) {
                        container.removeAllViews();
                        cached = saved.list;
                        cachedAt = saved.at;
                        cachedPvpcFixed = saved.pvpcFixed;
                        cachedPvpcSelfUse = saved.pvpcSelfUse;
                        CnmcClient.lastAvisos.clear();
                        for (String s : saved.avisos) CnmcClient.lastAvisos.add(s);
                        cachedSupplier = store.supplierName();
                        mySupplier = cachedSupplier;
                        staleNote = "No he podido consultar la CNMC ahora mismo"
                                + (api != null ? " (error " + api.status + " en su servidor)" : "")
                                + ". Te enseño la última comparativa que salió bien, del "
                                + when(saved.at) + ". Las ofertas cambian a menudo, así que "
                                + "tómalo como orientación y vuelve a intentarlo más tarde.";
                        render(saved.list);
                        return;
                    }
                    container.removeAllViews();
                    String text;
                    if (api != null) {
                        text = "El comparador de la CNMC ha contestado con un error " + api.status
                                + ", así que el fallo está en su servidor, no en tu conexión. "
                                + "Suele durar poco: prueba otra vez dentro de un rato.";
                        if (!api.detail.isEmpty()) text += "\n\n" + api.detail;
                    } else {
                        text = "No he podido llegar al comparador de la CNMC. Comprueba que tienes "
                                + "internet e inténtalo otra vez.";
                    }
                    container.addView(msg(text, true));
                    Button retry = new Button(this);
                    retry.setText("Volver a intentar");
                    retry.setBackground(getDrawable(R.drawable.bg_button_primary));
                    retry.setTextColor(getColor(R.color.bg));
                    retry.setAllCaps(false);
                    container.addView(retry);
                    retry.setOnClickListener(v -> {
                        cached = null;
                        cachedPvpcFixed = 0;
                        cachedPvpcSelfUse = false;
                        container.removeAllViews();
                        loadOffers();
                    });
                });
            }
        });
    }

    private void render(List<Offer> all) {
        BillData b = new History(store.invoices()).apply(store.load().copy());
        // Una entrada a mano manda sobre la oferta de la CNMC con la misma comercializadora y
        // nombre: así el ranking enseña tus precios, no los suyos. Las que no casan con
        // ninguna oferta entran como fila propia.
        List<ManualOffer> mine = store.manualOffers();
        manualRows.clear();
        java.util.Map<String, ManualOffer> byKey = new java.util.HashMap<>();
        for (ManualOffer m : mine) {
            manualRows.put(m.id, m);
            if (m.hasPrices()) byKey.put(m.key(), m);
        }
        java.util.Set<Long> usadas = new java.util.HashSet<>();
        List<Offer> list = new ArrayList<>();
        for (Offer o : all) {
            ManualOffer m = o.isRegulated() ? null : byKey.get(keyOf(o));
            if (m == null) {
                list.add(o);
            } else if (usadas.add(m.id)) {
                list.add(manualRow(m, b));
            }
        }
        for (ManualOffer m : mine) {
            if (m.hasPrices() && usadas.add(m.id)) list.add(manualRow(m, b));
        }
        boolean hayManual = !manualRows.isEmpty();
        Offer pvpc = pvpcWithCurve(b);
        if (pvpc != null) {
            // El PVPC del comparador se calcula con un perfil estándar (lo avisa la CNMC).
            // Si están los precios reales de ESIOS, se cambia por el tuyo, que va con tu
            // reparto P1/P2/P3 y con la misma potencia y contador.
            for (java.util.Iterator<Offer> it = list.iterator(); it.hasNext(); ) {
                if (it.next().isRegulated()) it.remove();
            }
            list.add(pvpc);
        }
        // Los filtros se aplican al final, con el PVPC dentro: si no, «sin PVPC» lo dejaba pasar
        // (se añadía después de filtrar) y los otros dos tampoco lo tocaban.
        list = CnmcClient.applyFilters(list, store);
        // Si los filtros han quitado el PVPC, la nota del pie no debe hablar de esa fila.
        boolean pvpcEnLista = false;
        for (Offer o : list) {
            if (o.isRegulated()) {
                pvpcEnLista = true;
                break;
            }
        }
        // Cada oferta lleva su descuento de excedentes (o ninguno, si no los compensa), así que
        // el orden es por lo que costaría de verdad, no por el importe que da el comparador.
        list.sort((a, z) -> Double.compare(net(a, b), net(z, b)));
        if (!staleNote.isEmpty()) {
            TextView stale = new TextView(this);
            stale.setText(staleNote);
            stale.setTextColor(getColor(R.color.warn));
            stale.setTextSize(12);
            stale.setLineSpacing(dp(3), 1f);
            stale.setBackground(getDrawable(R.drawable.bg_banner));
            stale.setPadding(dp(12), dp(10), dp(12), dp(10));
            container.addView(stale);
        }
        if (list.isEmpty()) {
            container.addView(msg("Ninguna oferta cumple tus filtros. Prueba a quitar alguno.", true));
            return;
        }

        // El precio de excedentes de la CNMC cambia el orden (va en el coste), así que se pide
        // antes de dar la lista por buena; mientras tanto se pinta en segundo plano.
        if (!fichasPedidas && hayFichasQueMirar(list)) {
            fichasPedidas = true;
            fichasCargando = true;
            pedirFichas(new ArrayList<>(list), b);
        }

        double current = b.baselineAnnual();
        double credit = b.annualSurplusTaxed();

        if (!CnmcClient.lastAvisos.isEmpty()) {
            StringBuilder w = new StringBuilder();
            for (String a : CnmcClient.lastAvisos) {
                if (w.length() > 0) w.append(' ');
                w.append(a);
            }
            TextView av = new TextView(this);
            av.setText("Aviso del comparador: " + w);
            av.setTextColor(getColor(R.color.warn));
            av.setTextSize(11);
            av.setLineSpacing(dp(3), 1f);
            av.setPadding(dp(4), dp(8), dp(4), 0);
            container.addView(av);
        }

        if (mySupplier != null && !mySupplier.isEmpty()) {
            container.addView(supplierCard(b, all));
        }

        TextView title = new TextView(this);
        title.setText(list.size() + " OFERTAS · DE MÁS BARATA A MÁS CARA");
        title.setTextColor(getColor(R.color.muted));
        title.setTextSize(11);
        title.setLetterSpacing(0.12f);
        title.setPadding(dp(4), dp(22), 0, dp(10));
        container.addView(title);

        if (fichasCargando) {
            TextView f = new TextView(this);
            f.setText("Mirando en las fichas de la CNMC y en sus webs cuánto paga cada oferta "
                    + "por tus excedentes…");
            f.setTextColor(getColor(R.color.muted));
            f.setTextSize(11);
            f.setLineSpacing(dp(3), 1f);
            f.setPadding(dp(4), 0, dp(4), dp(6));
            container.addView(f);
        }

        int max = Math.min(list.size(), 20);
        for (int i = 0; i < max; i++) {
            container.addView(offerRow(list.get(i), i + 1, current, b));
        }
        if (list.size() > max) {
            TextView more = new TextView(this);
            more.setText("Mostrando las " + max + " más baratas de " + list.size() + ".");
            more.setTextColor(getColor(R.color.muted));
            more.setTextSize(12);
            more.setPadding(dp(4), dp(6), 0, 0);
            container.addView(more);
        }

        // La compensación de excedentes, al pie y sin que parezca parte de la mejor oferta.
        if (credit > 0) {
            TextView foot = new TextView(this);
            foot.setText("A las ofertas que compensan excedentes les resto lo que pagan por ellos. "
                    + "Si su ficha en la CNMC lo publica, verás «Excedentes X €/kWh» en su fila y se "
                    + "usa ese precio; si no lo dice, uso el tuyo (" + Fmt.eur(credit) + " al año, ya "
                    + "con impuestos, la misma compensación que lleva tu factura) y puedes poner el "
                    + "suyo en su detalle. A las que no compensan no les resto nada.");
            foot.setTextColor(getColor(R.color.muted));
            foot.setTextSize(11);
            foot.setLineSpacing(dp(3), 1f);
            foot.setPadding(dp(4), dp(16), dp(4), 0);
            container.addView(foot);
        }

        TextView note = new TextView(this);
        note.setText("Importes anuales con impuestos, según el comparador oficial de la CNMC. "
                + "El comparador valora solo los kWh que compras de la red (los de tus facturas), "
                + "y a las ofertas que compensan excedentes les descuento tus excedentes: con el "
                + "precio que publica su ficha en la CNMC cuando lo dice («Excedentes X €/kWh» en "
                + "su fila) y, si no, con el tuyo. A las que no constan como compensadoras no les "
                + "descuento nada. Así tu factura y las ofertas se comparan en los mismos términos."
                + (pvpc != null && pvpcEnLista
                ? "\n\nLa fila «PVPC con tu curva» no es del comparador: lleva los precios "
                + "reales de ESIOS de los últimos 12 meses cruzados con tu reparto, porque el "
                + "comparador calcula el PVPC con un perfil estándar. Se actualiza al consultar "
                + "ESIOS en la pantalla principal."
                : (pvpcEnLista
                ? "\n\nEl PVPC de la lista es el del comparador, que lo calcula con un perfil "
                + "estándar. Solo lo cambio por el tuyo (con los precios reales de ESIOS y tu "
                + "reparto) cuando cuadra la comprobación de su parte fija; si no, se queda "
                + "esta fila tal cual."
                : ""))
                + (hayManual ? "\n\nLas filas MANUAL son ofertas que has escrito tú con los "
                + "precios de su web. Se calculan con tu consumo, tu potencia y tus impuestos, "
                + "y sustituyen a la oferta de la CNMC con el mismo nombre. La batería virtual y "
                + "la permanencia van solo como nota: no cambian la cuenta." : ""));
        note.setTextColor(getColor(R.color.muted));
        note.setTextSize(11);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        container.addView(note);
    }

    private void abrir(String url) {
        if (url == null || url.trim().isEmpty()) return;
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        } catch (Exception e) {
            Toast.makeText(this, "No he podido abrir el navegador.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * La parte fija del PVPC, pero solo si cuadra. Sale de una suposición (pedirle al comparador
     * la misma casa con 1 kWh), así que antes de usarla se comprueba que sea positiva y que pese
     * entre el 10 % y el 60 % del PVPC que la CNMC devuelve para esta casa. Si la CNMC cambia
     * cómo lo calcula, esto deja de cuadrar y se devuelve 0: la fila del comparador se queda
     * como estaba en vez de enseñar un PVPC inventado.
     */
    private double validatedPvpcFixed(BillData b, List<Offer> all) {
        cachedPvpcSelfUse = false;
        if (store.pvpc() == null) return 0;
        double cnmc = 0;
        for (Offer o : all) {
            if (o.isRegulated()) {
                cnmc = o.year1;
                // Si el PVPC compensa excedentes lo dice el comparador; la fila que monto yo
                // hereda ese dato en vez de darlo por supuesto.
                cachedPvpcSelfUse = o.selfUse;
                break;
            }
        }
        double fixed = CnmcClient.pvpcFixed(b);
        if (fixed <= 0 || cnmc <= 0) return 0;
        double share = fixed / cnmc;
        if (share < PVPC_FIXED_MIN || share > PVPC_FIXED_MAX) return 0;
        return fixed;
    }

    /**
     * El PVPC con tus precios: energía real de los últimos 12 meses (ESIOS) cruzada con tu
     * reparto P1/P2/P3, más la potencia y el contador que cobra el PVPC. Null si aún no hay
     * precios guardados o si el comparador no ha dado su parte fija.
     */
    private Offer pvpcWithCurve(BillData b) {
        Pvpc p = store.pvpc();
        if (p == null || !p.ok() || cachedPvpcFixed <= 0) return null;
        Offer o = new Offer();
        o.kind = "PVPC";
        o.name = "PVPC con tu curva";
        o.supplier = "Precios reales de los últimos 12 meses";
        o.year1 = cachedPvpcFixed + p.energyCostTaxed(b);
        o.pvpcMine = true;
        o.selfUse = cachedPvpcSelfUse;
        return o;
    }

    /** Tu comercializadora, tal y como la identifica el QR, y si sus números cuadran con tu factura. */
    private View supplierCard(BillData b, List<Offer> all) {
        LinearLayout card = card();
        card.addView(label("TU COMERCIALIZADORA SEGÚN TU FACTURA"));

        TextView name = new TextView(this);
        name.setText(pretty(mySupplier));
        name.setTextColor(getColor(R.color.text));
        name.setTextSize(19);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setPadding(0, dp(6), 0, 0);
        card.addView(name);

        List<Offer> mine = new ArrayList<>();
        for (Offer o : all) {
            if (sameSupplier(o.supplier, mySupplier)) mine.add(o);
        }

        if (mine.isEmpty()) {
            card.addView(small("Ahora mismo no tiene ninguna oferta en el comparador. Solo aparecen las que están a la venta: tu tarifa puede no estar entre ellas y eso no significa que los cálculos fallen."));
            return card;
        }

        Offer cheapest = mine.get(0);
        for (Offer o : mine) if (net(o, b) < net(cheapest, b)) cheapest = o;
        double mineNet = net(cheapest, b);
        double current = b.baselineAnnual();

        card.addView(small(mine.size() == 1
                ? "Tiene 1 oferta en el comparador hoy: «" + mine.get(0).name + "»."
                : "Tiene " + mine.size() + " ofertas en el comparador hoy; la más barata es «" + cheapest.name + "»."));

        if (current > 0) {
            addRow(card, b.hasYearData() ? "Tu factura, llevada a un año" : "Tu tarifa con tus precios, al año",
                    Fmt.eur(current));
            addRow(card, "Su oferta más barata, calculada", Fmt.eur(mineNet));

            double gap = Math.abs(mineNet - current);
            boolean ok = gap <= Math.max(80, current * 0.15);
            TextView verdict = new TextView(this);
            verdict.setText(ok
                    ? "Cuadra: tus números y su oferta más barata están cerca, así que los cálculos van bien."
                    : "La diferencia no viene de las cuentas: tu tarifa ya no está a la venta y esta es la oferta más parecida que esa comercializadora tiene hoy. Con tu consumo, tu tarifa sale "
                      + Fmt.eur(gap) + " al año " + (mineNet > current ? "más barata" : "más cara")
                      + " que esa oferta.");
            verdict.setTextColor(getColor(ok ? R.color.accent : R.color.warn));
            verdict.setTextSize(12);
            verdict.setLineSpacing(dp(3), 1f);
            verdict.setPadding(0, dp(10), 0, 0);
            card.addView(verdict);
        }
        return card;
    }

    private static boolean sameSupplier(String a, String b) {
        String x = norm(a), y = norm(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        if (x.equals(y)) return true;
        String fx = x.split(" ")[0], fy = y.split(" ")[0];
        return fx.length() >= 5 && fx.equals(fy);
    }

    private static String norm(String s) {
        if (s == null) return "";
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        t = t.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    private static String pretty(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (String w : s.trim().split(" ")) {
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            if (w.indexOf('.') >= 0) {
                out.append(w.toUpperCase(Locale.ROOT));
            } else if (w.equalsIgnoreCase("de") || w.equalsIgnoreCase("y") || w.equalsIgnoreCase("del")) {
                out.append(w.toLowerCase(Locale.ROOT));
            } else {
                out.append(w.substring(0, 1).toUpperCase(Locale.ROOT))
                        .append(w.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.toString();
    }

    private View offerRow(Offer o, int rank, double current, BillData b) {
        LinearLayout card = card();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

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
        name.setText(o.name);
        name.setTextColor(getColor(R.color.text));
        name.setTextSize(15);
        name.setMaxLines(2);
        mid.addView(name);

        TextView sup = new TextView(this);
        sup.setText(o.supplier);
        sup.setTextColor(getColor(R.color.muted));
        sup.setTextSize(11);
        sup.setPadding(0, dp(2), 0, 0);
        mid.addView(sup);
        top.addView(mid);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        double net = net(o, b);
        TextView price = new TextView(this);
        price.setText(Fmt.eur(net));
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

        // Las píldoras se parten en filas: un LinearLayout horizontal no las ajusta y la última
        // se quedaba aplastada a 30 px con el texto en vertical (la barra verde de Naturgy).
        List<TextView> pillList = new ArrayList<>();
        if (sameSupplier(o.supplier, mySupplier)) pillList.add(pill("Tu comercializadora", true));
        if (o.isRegulated()) pillList.add(pill("PVPC", false));
        if (o.pvpcMine) pillList.add(pill("Con tu curva", true));
        if (o.manual) pillList.add(pill("MANUAL", true));
        // Lo que paga por los excedentes, que es lo que se mira primero: si lo dice su ficha en
        // la CNMC va en la píldora con el precio; si no, se queda «Autoconsumo» y ya se ve en
        // el detalle que no hay dato.
                // De dónde sale el precio: CNMC (su ficha), WEB (su página), MANUAL (lo pusiste tú) o
        // SIN DATO (no lo publica y se usa tu precio, que puede no ser el suyo).
        if (paysSurplus(o)) {
            double sx = surplusPrice(o, b);
            String from = surplusFrom(o, b);
            String txt = sx > 0 ? "Excedentes " + Fmt.dec(sx, 3) + " €/kWh · " + from
                    : "Excedentes · " + from;
            pillList.add("SIN DATO".equals(from)
                    ? pill(txt, false, getColor(R.color.warn)) : pill(txt, true));
        }
        ManualOffer hm = manualOf(o);
        if (hm != null ? hm.battery : (store.cnmcBattery(o) || store.webBattery(o))) {
            pillList.add(pill("Batería virtual", false));
        }
        if (o.green) pillList.add(pill("Verde", true));
        if (o.penalty) pillList.add(pill("Con permanencia", false));
        if (o.extras) pillList.add(pill("Con servicios", false));
        if (!pillList.isEmpty()) addPills(card, pillList);

        if (net < o.year1 - 0.5) {
            TextView disc = new TextView(this);
            disc.setText("Sin descontar excedentes: " + Fmt.eur(o.year1) + " al año");
            disc.setTextColor(getColor(R.color.muted));
            disc.setTextSize(11);
            disc.setPadding(dp(26), dp(6), 0, 0);
            card.addView(disc);
        } else if (!paysSurplus(o)) {
            TextView no = new TextView(this);
            no.setText("No compensa excedentes: no lleva ese descuento");
            no.setTextColor(getColor(R.color.muted));
            no.setTextSize(11);
            no.setPadding(dp(26), dp(6), 0, 0);
            card.addView(no);
        }

        // Algunas ofertas suben el segundo año (descuento de bienvenida): se ve aquí para no
        // quedarse con el primer año, que es el que menos se parece al día a día.
        if (o.year2 > 0 && Math.abs(o.year2 - o.year1) >= 1) {
            TextView y2 = new TextView(this);
            y2.setText("Segundo año: " + Fmt.eur(net2(o, b)) + " al año");
            y2.setTextColor(getColor(R.color.muted));
            y2.setTextSize(11);
            y2.setPadding(dp(26), dp(6), 0, 0);
            card.addView(y2);
        }
        if (o.penaltyAmount > 0) {
            TextView pen = new TextView(this);
            pen.setText("Penalización estimada por irte antes: " + Fmt.eur(o.penaltyAmount));
            pen.setTextColor(getColor(R.color.warn));
            pen.setTextSize(11);
            pen.setPadding(dp(26), dp(6), 0, 0);
            card.addView(pen);
        }

        if (current > 0) {
            TextView save = new TextView(this);
            double s = current - net;
            save.setText(s > 0 ? "Ahorras " + Fmt.eur(s) + " al año" : "Te costaría " + Fmt.eur(-s) + " más al año");
            save.setTextColor(s > 0 ? getColor(R.color.accent) : getColor(R.color.warn));
            save.setTextSize(12);
            save.setPadding(dp(26), dp(8), 0, 0);
            card.addView(save);
        }

        // Abre el comparador manual con esta oferta ya cargada. Es una fila aparte, así que pulsar
        // aquí no abre el detalle de la tarjeta.
        if (!o.manual && !o.pvpcMine && !o.isRegulated()) {
            TextView hand = new TextView(this);
            hand.setText("Comparar a mano ›");
            hand.setTextColor(getColor(R.color.accent));
            hand.setTextSize(12);
            hand.setPadding(dp(26), dp(10), 0, 0);
            hand.setOnClickListener(v -> openManualFor(o, b));
            card.addView(hand);
        }

        card.setOnClickListener(v -> {
            ManualOffer manual = manualOf(o);
            if (manual != null) showManualDetail(manual, current, b);
            else showDetail(o, current, b);
        });
        return card;
    }

    private void showDetail(Offer o, double current, BillData b) {
        LinearLayout box = Dialogs.body(this);
        // El contenido iba pegado a los bordes de la tarjeta del diálogo.
        box.setPadding(dp(16), dp(2), dp(16), dp(6));

        double net = net(o, b);
        Dialogs.section(this, box, "Coste al año");
        Dialogs.row(this, box, "Primer año", Fmt.eur2(net), getColor(R.color.accent));
        if (net < o.year1 - 0.5) Dialogs.row(this, box, "Sin tus excedentes", Fmt.eur2(o.year1));
        if (o.year2 > 0) Dialogs.row(this, box, "Segundo año", Fmt.eur2(net2(o, b)));
        if (current > 0) Dialogs.row(this, box, "Tu factura actual", Fmt.eur(current) + " al año");

        if (o.pvpcMine) {
            Pvpc p = store.pvpc();
            if (p != null) {
                Dialogs.section(this, box, "Precios del PVPC");
                if (!p.from.isEmpty() && !p.to.isEmpty()) {
                    Dialogs.row(this, box, "Periodo", day(p.from) + " → " + day(p.to));
                }
                Dialogs.row(this, box, "Horas leídas", String.valueOf(p.hours));
                Dialogs.row(this, box, "Media P1", Fmt.dec(p.p1, 4) + " €/kWh");
                Dialogs.row(this, box, "Media P2", Fmt.dec(p.p2, 4) + " €/kWh");
                Dialogs.row(this, box, "Media P3", Fmt.dec(p.p3, 4) + " €/kWh");
                Dialogs.row(this, box, "Potencia y contador", Fmt.eur2(cachedPvpcFixed));
                Dialogs.note(this, box, "El PVPC no tiene precio fijo: esto es la media real de "
                        + "cada tramo de los últimos 12 meses cruzada con tu reparto. La potencia "
                        + "y el contador se piden al comparador, para que la cifra pueda ir al "
                        + "lado de las ofertas.");
            }
        } else {
            Dialogs.section(this, box, "La oferta");
            Dialogs.row(this, box, "Tipo de precio", o.kindLabel());
            Dialogs.row(this, box, "Energía verde", o.green ? "Sí" : "No");
            Dialogs.row(this, box, "Permanencia", o.penalty ? "Sí" : "No");
            if (o.penalty && o.penaltyAmount > 0) {
                Dialogs.row(this, box, "Penalización estimada", Fmt.eur2(o.penaltyAmount), getColor(R.color.warn));
            }
            Dialogs.row(this, box, "Servicios adicionales", o.extras ? "Sí" : "No");
            if (o.validity != null && !o.validity.isEmpty()) {
                // Texto largo y libre: debajo de la etiqueta, no en dos columnas.
                Dialogs.wideRow(this, box, "Condiciones", o.validity, getColor(R.color.text));
            }
        }

        // Compensación de excedentes: la CNMC no publica cuánto paga cada comercializadora, así
        // que por defecto se usa tu precio y aquí se puede poner el suyo, que manda sobre el tuyo.
        Dialogs.section(this, box, "Excedentes");
        boolean pays = paysSurplus(o);
        String comp = pays ? "Sí" : "No";
        if (pays && !o.compensates() && store.offerSurplus(o) > 0) comp = "Sí (manual)";
        Dialogs.row(this, box, "Compensa excedentes", comp);
        if (!pays) {
            Dialogs.note(this, box, "La CNMC no la marca como compensadora de excedentes: su "
                    + "precio es el del comparador, sin descontarte tus kWh vertidos.");
        }
        if (paysSurplus(o) && b.surplusKwhYear > 0) {
            double p = surplusPrice(o, b);
            String label = surplusFrom(o, b);
            Dialogs.row(this, box, "Aplico", Fmt.dec(p, 4) + " €/kWh · " + label,
                    "SIN DATO".equals(label) ? getColor(R.color.warn) : getColor(R.color.text));
            Dialogs.row(this, box, "Al año", "−" + Fmt.eur2(b.surplusCredit(p)) + " con impuestos");
            if ("SIN DATO".equals(label)) {
                Dialogs.note(this, box, "Ni su ficha en la CNMC ni su web publican el precio de los "
                        + "excedentes: uso " + (b.surplusFromBill() ? "el de tu factura" : "el tuyo")
                        + (b.surplusPrice() > 0 ? " (" + Fmt.dec(b.surplusPrice(), 5)
                        + " €/kWh)" : "") + ", así que el descuento de esta oferta puede no ser "
                        + "el que te daría de verdad.");
            }
            // Las dos cifras pueden no coincidir y no es un impuesto: son fechas distintas.
            double said = store.cnmcSurplus(o) > 0 ? store.cnmcSurplus(o) : store.webSurplus(o);
            String where = store.cnmcSurplus(o) > 0 ? "su ficha en la CNMC" : "su web";
            if (store.offerSurplus(o) <= 0 && said > 0 && b.surplusPrice() > 0
                    && Math.abs(said - b.surplusPrice()) >= 0.0005) {
                Dialogs.note(this, box, "Tu última factura compensa a "
                        + Fmt.dec(b.surplusPrice(), 5) + " €/kWh y " + where + " dice "
                        + Fmt.dec(said, 5) + " €/kWh: no son impuestos, son dos fechas. La factura es "
                        + "lo que te pagaron en su periodo; " + where + ", lo que publica hoy "
                        + "(y lo que uso para comparar).");
            }
        }

        final AlertDialog[] holder = new AlertDialog[1];
        // Sin compensación no hay precio que poner ni ficha que mirar: solo se explica.
        if (pays) {
            // Lo que la CNMC cuente de los excedentes en su ficha, con un «Usar» al lado. Es solo
            // una sugerencia: el campo de abajo sigue siendo manual y manda lo que se guarde.
            LinearLayout fichaBox = new LinearLayout(this);
            fichaBox.setOrientation(LinearLayout.VERTICAL);
            Dialogs.fill(fichaBox);
            box.addView(fichaBox);
            fichaBox.addView(small("Buscando el precio de los excedentes…"));

            TextView expl = new TextView(this);
            if (o.pvpcMine) {
                expl.setText("El PVPC no tiene ficha de comercializadora en la CNMC. Si no pongo nada, "
                        + "se usa tu precio (€/kWh sin impuestos); escríbelo aquí para mandar tú.");
            } else {
                expl.setText("Si no pongo nada, se usa lo que diga su ficha en la CNMC y, si no lo dice, "
                        + "su web (la lee este móvil, sin servidor) y, si tampoco, tu precio (€/kWh sin "
                        + "impuestos). Escríbelo aquí para mandar tú.");
            }
            expl.setTextColor(getColor(R.color.muted));
            expl.setTextSize(11);
            expl.setLineSpacing(dp(3), 1f);
            expl.setPadding(0, dp(8), 0, dp(6));
            Dialogs.fill(expl);
            box.addView(expl);

            final EditText et = new EditText(this);
            et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            et.setSingleLine(true);
            et.setKeyListener(DigitsKeyListener.getInstance("0123456789.,"));
            et.setText(store.offerSurplus(o) > 0 ? Fmt.dec(store.offerSurplus(o), 5) : "");
            if (store.cnmcSurplus(o) > 0) et.setHint(Fmt.dec(store.cnmcSurplus(o), 4) + " = el de la CNMC");
            else if (store.webSurplus(o) > 0) et.setHint(Fmt.dec(store.webSurplus(o), 4) + " = el de su web");
            else if (b.surplusPrice() > 0) et.setHint(Fmt.dec(b.surplusPrice(), 4) + " = el tuyo");
            Dialogs.fill(et);
            box.addView(et);
            cargarFicha(o, b, fichaBox, et);

            Button save = new Button(this);
            save.setText("Guardar");
            save.setAllCaps(false);
            save.setBackground(getDrawable(R.drawable.bg_button_primary));
            save.setTextColor(getColor(R.color.bg));
            save.setOnClickListener(v -> {
                store.setOfferSurplus(o, num(et.getText().toString()));
                if (holder[0] != null) holder[0].dismiss();
                if (cached != null) {
                    container.removeAllViews();
                    render(cached);
                }
            });
            box.addView(save);
        }

        // El listado de la CNMC no trae la web; se pide su detalle al pulsar. El PVPC con tu
        // curva no es una oferta de la CNMC, así que no tiene ficha ni web que abrir.
        if (!o.pvpcMine) {
            Button webBtn = new Button(this);
            webBtn.setText("Ver la oferta en su web");
            webBtn.setAllCaps(false);
            webBtn.setBackground(getDrawable(R.drawable.bg_button_primary));
            webBtn.setTextColor(getColor(R.color.bg));
            webBtn.setOnClickListener(v -> abrirWebOferta(o, b, webBtn));
            box.addView(webBtn);
        }

        if (!o.pvpcMine) {
            Dialogs.note(this, box, "El precio de los excedentes no siempre está en la ficha de "
                    + "la CNMC: cuando no lo dice, esta app descarga la web de la comercializadora "
                    + "y lo busca ahí, en el propio móvil. Si tampoco aparece, uso tu precio, y a "
                    + "las que no compensan no les resto nada.");
        }

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(o.name)
                .setView(Dialogs.scroller(this, box, 460))
                .setPositiveButton("Cerrar", null)
                .show();
        holder[0] = dlg;
        // El diálogo se ajustaba al contenido y con dos columnas se iba más allá de la
        // pantalla: se fija al 92 % del ancho para que las filas partan dentro.
        if (dlg.getWindow() != null) {
            int w = Math.round(getResources().getDisplayMetrics().widthPixels * 0.90f);
            dlg.getWindow().setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    /**
     * Lo que paga esa oferta por tus excedentes: lo que hayas puesto en su detalle o, si no
     * hay nada, tu propio precio. Solo se descuenta si la oferta compensa excedentes.
     */
    private double surplusPrice(Offer o, BillData b) {
        ManualOffer m = manualOf(o);
        if (m != null) return m.usedSurplus(b);
        double set = store.offerSurplus(o);
        if (set > 0) return set;                       // lo que hayas puesto tú manda
        double cnmc = store.cnmcSurplus(o);
        if (cnmc > 0) return cnmc;                     // luego su ficha en la CNMC
        double web = store.webSurplus(o);
        if (web > 0) return web;                       // luego su web
        return b.surplusPrice();                       // y si no, la tuya
    }

    /** De dónde sale el precio de excedentes que se aplica (para la píldora y el detalle). */
    private String surplusFrom(Offer o, BillData b) {
        ManualOffer m = manualOf(o);
        if (m != null) return m.surplusPrice > 0 ? "MANUAL"
                : (b.surplusFromBill() ? "TU FACTURA" : "SIN DATO");
        if (store.offerSurplus(o) > 0) return "MANUAL";
        if (store.cnmcSurplus(o) > 0) return "CNMC";
        if (store.webSurplus(o) > 0) return "WEB";
        return b.surplusFromBill() ? "TU FACTURA" : "SIN DATO";
    }

    /**
     * Si la oferta compensa excedentes. Lo decide la CNMC (`autoconsumo`) o un precio puesto a
     * mano: lo que la app encuentre sola en su ficha o en su web sirve para poner precio a una
     * oferta que ya compensa, pero no convierte en compensadora a una que la CNMC dice que no.
     */
    private boolean paysSurplus(Offer o) {
        ManualOffer m = manualOf(o);
        if (m != null) return m.compensates;
        return o.compensates() || store.offerSurplus(o) > 0;
    }

    /** Pide el detalle de la oferta a la CNMC y abre la web de la comercializadora. */
    private void abrirWebOferta(Offer o, BillData b, Button btn) {
        if (o.pvpcMine) {
            Toast.makeText(this, "El PVPC no tiene una web de oferta en la CNMC.", Toast.LENGTH_SHORT).show();
            return;
        }
        btn.setEnabled(false);
        btn.setText("Buscando el enlace…");
        pool.execute(() -> {
            CnmcClient.Detail d = ficha(o, b);
            // Su web de la oferta, la de contratación o la de las condiciones, en ese orden.
            String url = d == null ? ""
                    : (!d.web.isEmpty() ? d.web
                    : (!d.particular.isEmpty() ? d.particular : d.contract));
            runOnUiThread(() -> {
                btn.setEnabled(true);
                btn.setText("Ver la oferta en su web");
                if (url.isEmpty()) {
                    Toast.makeText(this, "La CNMC no publica una web para esta oferta: búscala "
                            + "por su nombre en internet.", Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Toast.makeText(this, "No he podido abrir el navegador.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /** Ficha de la CNMC ya descargada en esta pantalla (también los fallos, para no repetirlos). */
    private final java.util.HashMap<Integer, CnmcClient.Detail> fichas = new java.util.HashMap<>();

    private CnmcClient.Detail ficha(Offer o, BillData b) {
        if (o == null || o.id <= 0) return null;
        synchronized (fichas) {
            if (fichas.containsKey(o.id)) return fichas.get(o.id);
        }
        CnmcClient.Detail d = CnmcClient.offerDetail(b, o.id);
        synchronized (fichas) {
            fichas.put(o.id, d);
        }
        return d;
    }

    /**
     * Lee la ficha de la CNMC al abrir el detalle y ofrece lo que diga de los excedentes (con
     * «Usar», que solo rellena el campo) y de la batería virtual, que no se puede calcular.
     */
    private void cargarFicha(Offer o, BillData b, LinearLayout box, EditText et) {
        if (o.pvpcMine) {
            box.removeAllViews();
            box.addView(small("El PVPC no tiene ficha de comercializadora; se usa tu precio o el manual."));
            return;
        }
        // La CNMC ya dice que esta no compensa: no hay ficha ni web que buscar.
        if (!o.compensates()) {
            box.removeAllViews();
            box.addView(small(store.offerSurplus(o) > 0
                    ? "La CNMC no la marca como compensadora: el descuento sale del precio que has "
                    + "puesto a mano."
                    : "La CNMC no la marca como compensadora de excedentes: no hay precio que buscar."));
            return;
        }
        // Lo que ya se sepa (de esta pantalla o de la última consulta) sin esperar a la red.
        double cnmc = store.cnmcSurplus(o);
        if (cnmc > 0) {
            pintaFicha(o, box, et, cnmc, store.cnmcBattery(o), "", "la CNMC");
            return;
        }
        if (store.webSurplusAt(o) > 0 && store.webRead(o)) {
            pintaFicha(o, box, et, store.webSurplus(o), store.webBattery(o), "", "su web");
            return;
        }
        box.addView(small("Mirando su ficha en la CNMC y, si no lo dice, su web…"));
        pool.execute(() -> {
            CnmcClient.Detail d = ficha(o, b);
            if (d != null && d.surplus > 0) {
                store.setCnmcSurplus(o, d.surplus, d.battery);
                runOnUiThread(() -> pintaFicha(o, box, et, d.surplus, d.battery,
                        d.surplusQuote, "la CNMC"));
                return;
            }
            // Su ficha no lo dice: se mira su web, descargada desde el propio móvil.
            boolean read = d != null && Web.scanOffer(d);
            final double web = d == null ? 0 : d.webSurplus;
            final boolean bat = d != null && d.battery;
            final String quote = d == null ? "" : d.webQuote;
            if (d != null) store.setWebSurplus(o, web, bat, read);
            runOnUiThread(() -> {
                if (d == null) {
                    box.removeAllViews();
                    box.addView(small("No he podido leer su ficha en la CNMC."));
                } else if (web > 0) {
                    pintaFicha(o, box, et, web, bat, quote, "su web");
                } else {
                    pintaFicha(o, box, et, 0, bat, "", "");
                }
            });
        });
    }

    /**
     * Lo que dice la ficha: el precio (aplicado solo si no hay uno puesto a mano), la frase
     * literal y la batería virtual. No hay que pulsar nada para que cuente.
     */
    private void pintaFicha(Offer o, LinearLayout box, EditText et, double price, boolean battery,
                            String quote, String who) {
        boolean manual = store.offerSurplus(o) > 0;
        box.removeAllViews();
        boolean algo = false;
        if (price > 0) {
            algo = true;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, 0);
            TextView t = new TextView(this);
            t.setText(cap(who) + " dice " + Fmt.dec(price, 5) + " €/kWh");
            t.setTextColor(getColor(R.color.text));
            t.setTextSize(13);
            row.addView(t, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (manual) {
                // Ya hay un precio puesto a mano: se ofrece el de la CNMC para cambiarlo.
                TextView use = new TextView(this);
                use.setText("Usar");
                use.setTextColor(getColor(R.color.accent));
                use.setTextSize(13);
                use.setPadding(dp(14), dp(8), dp(4), dp(8));
                use.setOnClickListener(v -> {
                    et.setText(Fmt.dec(price, 5));
                    et.setSelection(et.getText().length());
                });
                row.addView(use);
            } else {
                // Sin nada a mano ya se aplica: se dice, para que no parezca que hay que pulsar.
                TextView ok = new TextView(this);
                ok.setText("ya se aplica");
                ok.setTextColor(getColor(R.color.accent));
                ok.setTextSize(11);
                ok.setPadding(dp(10), dp(8), 0, dp(8));
                row.addView(ok);
                et.setHint(Fmt.dec(price, 4) + " = el de " + who);
            }
            Dialogs.fill(row);
            box.addView(row);
            if (quote != null && !quote.isEmpty()) {
                TextView q = small("«" + quote + "»");
                q.setTextSize(11);
                box.addView(q);
            }
        }
        if (battery) {
            algo = true;
            TextView bt = small("Menciona batería virtual: pregúntale saldo y caducidad en su web.");
            bt.setTextSize(11);
            box.addView(bt);
        }
        if (!algo) {
            box.addView(small("Ni su ficha en la CNMC ni su web dicen el precio de los "
                    + "excedentes: se usa tu precio, que puede no ser el suyo."));
        }
        box.requestLayout();
    }

    /** Lo que dice una ficha (o una web) vale una semana; una web que no se pudo abrir, un día. */
    private static final long FICHA_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long WEB_FAIL_MS = 24L * 60 * 60 * 1000;

    /**
     * Si ya está mirado lo que hacía falta y sigue valiendo: la ficha de la CNMC y, cuando
     * esa no dice el precio, su web. Un fallo de red se vuelve a intentar al día siguiente.
     */
    private boolean fichaFresca(Offer o) {
        long now = System.currentTimeMillis();
        long at = store.cnmcSurplusAt(o);
        if (at == 0 || now - at > FICHA_MS) return false;
        if (store.cnmcSurplus(o) > 0) return true;      // ya lo dice la ficha
        long wat = store.webSurplusAt(o);
        if (wat == 0) return false;
        return now - wat <= (store.webRead(o) ? FICHA_MS : WEB_FAIL_MS);
    }

    private boolean hayFichasQueMirar(List<Offer> list) {
        for (Offer o : list) {
            if (o.pvpcMine || o.manual) continue;
            if (!o.compensates() && store.offerSurplus(o) <= 0) continue;
            if (!fichaFresca(o)) return true;
        }
        return false;
    }

    /** «la CNMC» → «La CNMC», para empezar la frase sin repetir el texto en cada sitio. */
    private static String cap(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Pide la ficha de las ofertas que compensan excedentes (de 3 en 3) para saber cuánto pagan
     * y vuelve a pintar la lista ya con ese precio dentro del coste y en la píldora. Va en su
     * propio hilo: el `pool` de la pantalla lo usa el detalle y no puede quedarse esperando.
     */
    private void pedirFichas(List<Offer> list, final BillData b) {
        final ExecutorService ex = Executors.newFixedThreadPool(3);
        for (Offer o : list) {
            if (o.pvpcMine || o.manual) continue;
            if (!o.compensates() && store.offerSurplus(o) <= 0) continue;
            if (fichaFresca(o)) continue;
            ex.execute(() -> {
                CnmcClient.Detail d = CnmcClient.offerDetail(b, o.id);
                // «No lo dice» también se guarda, para no repetir la consulta cada día.
                store.setCnmcSurplus(o, d == null ? 0 : d.surplus, d != null && d.battery);
                if (d == null || d.surplus > 0) return;
                // Su ficha no lo dice: se busca en su web, desde el móvil y sin servidor.
                boolean read = Web.scanOffer(d);
                store.setWebSurplus(o, d.webSurplus, d.battery, read);
            });
        }
        ex.shutdown();
        new Thread(() -> {
            try {
                ex.awaitTermination(3, java.util.concurrent.TimeUnit.MINUTES);
            } catch (InterruptedException ignored) {
            }
            runOnUiThread(() -> {
                fichasCargando = false;
                if (cached == null) return;
                container.removeAllViews();
                render(cached);
            });
        }).start();
    }

    private double net(Offer o, BillData b) {
        ManualOffer m = manualOf(o);
        if (m != null) return m.net(b);
        return paysSurplus(o) ? b.netCostWith(o.year1, surplusPrice(o, b)) : o.year1;
    }

    private double net2(Offer o, BillData b) {
        ManualOffer m = manualOf(o);
        if (m != null) return 0;
        return paysSurplus(o) ? b.netCostWith(o.year2, surplusPrice(o, b)) : o.year2;
    }

    // ---------------- ofertas a mano ----------------

    /** La entrada guardada detrás de una fila MANUAL (null si la fila es de la CNMC). */
    private ManualOffer manualOf(Offer o) {
        if (o == null || !o.manual || o.manualId <= 0) return null;
        return manualRows.get(o.manualId);
    }

    /** Misma clave que usa `ManualOffer`: comercializadora + nombre, sin mayúsculas. */
    private static String keyOf(Offer o) {
        String s = (o.supplier == null ? "" : o.supplier) + "|" + (o.name == null ? "" : o.name);
        return s.trim().toLowerCase(Locale.ROOT);
    }

    /** La fila virtual que representa una entrada a mano en el ranking. */
    private Offer manualRow(ManualOffer m, BillData b) {
        Offer o = new Offer();
        o.manual = true;
        o.manualId = m.id;
        o.supplier = m.supplier;
        o.name = m.name;
        o.green = m.green;
        o.penalty = m.penalty;
        o.selfUse = m.compensates;
        o.year1 = m.gross(b);
        return o;
    }

    /**
     * Comparador manual a partir de una oferta de la CNMC: si ya hay una entrada con esa
     * comercializadora y nombre se abre la que hay (no se duplica); si no, se precarga con sus
     * datos y se le pide la ficha para rellenar los precios.
     */
    private void openManualFor(Offer o, BillData b) {
        String key = keyOf(o);
        for (ManualOffer m : store.manualOffers()) {
            if (m.key().equals(key)) {
                openEditor(m.id);
                return;
            }
        }
        boolean pays = paysSurplus(o);
        startActivity(new Intent(this, ManualActivity.class)
                .putExtra(ManualActivity.EXTRA_OFFER_ID, o.id)
                .putExtra(ManualActivity.EXTRA_SUPPLIER, o.supplier)
                .putExtra(ManualActivity.EXTRA_NAME, o.name)
                .putExtra(ManualActivity.EXTRA_GREEN, o.green)
                .putExtra(ManualActivity.EXTRA_PENALTY, o.penalty)
                .putExtra(ManualActivity.EXTRA_SELF, pays)
                .putExtra(ManualActivity.EXTRA_SURPLUS, pays ? surplusPrice(o, b) : 0));
    }

    private void openEditor(long id) {
        startActivity(new Intent(this, ManualActivity.class)
                .putExtra(ManualActivity.EXTRA_ID, id));
    }

    /** Detalle de una oferta escrita a mano: desglose, fechas, Editar y Borrar. */
    private void showManualDetail(final ManualOffer m, double current, BillData b) {
        LinearLayout box = Dialogs.body(this);
        box.setPadding(dp(16), dp(2), dp(16), dp(6));

        BillData.Breakdown d = m.breakdown(b);
        Dialogs.section(this, box, "Coste al año");
        Dialogs.row(this, box, "Primer año", Fmt.eur2(m.net(b)), getColor(R.color.accent));
        if (m.compensates && m.net(b) < d.gross - 0.5) {
            Dialogs.row(this, box, "Sin descontar excedentes", Fmt.eur2(d.gross));
        }
        if (current > 0) Dialogs.row(this, box, "Tu factura actual", Fmt.eur(current) + " al año");

        Dialogs.section(this, box, "Precios que has puesto");
        if (m.byTramos()) {
            Dialogs.row(this, box, "Energía P1 · punta", Fmt.dec(m.e1, 5) + " €/kWh");
            Dialogs.row(this, box, "Energía P2 · llano", Fmt.dec(m.e2, 5) + " €/kWh");
            Dialogs.row(this, box, "Energía P3 · valle", Fmt.dec(m.e3, 5) + " €/kWh");
        } else {
            Dialogs.row(this, box, "Energía", Fmt.dec(m.energy, 5) + " €/kWh");
        }
        Dialogs.row(this, box, "Potencia P1", Fmt.dec(m.power1, 6) + " €/kW·día");
        if (m.power2 > 0 && Math.abs(m.power2 - m.power1) > 0.000001) {
            Dialogs.row(this, box, "Potencia P2", Fmt.dec(m.power2, 6) + " €/kW·día");
        }

        Dialogs.section(this, box, "Desglose con tus datos");
        Dialogs.row(this, box, "Energía", Fmt.eur2(d.energy));
        Dialogs.row(this, box, "Potencia", Fmt.eur2(d.power));
        if (d.bono > 0) Dialogs.row(this, box, "Bono social", Fmt.eur2(d.bono));
        if (d.tax > 0) Dialogs.row(this, box, "Mínimo comunitario", Fmt.eur2(d.tax));
        if (d.rent > 0) Dialogs.row(this, box, "Contador", Fmt.eur2(d.rent));
        Dialogs.row(this, box, "Impuesto eléctrico", Fmt.eur2(d.iee));
        Dialogs.row(this, box, "IVA", Fmt.eur2(d.iva));
        Dialogs.row(this, box, "Total", Fmt.eur2(d.gross));

        Dialogs.section(this, box, "Excedentes");
        Dialogs.row(this, box, "Compensa excedentes", m.compensates ? "Sí" : "No");
        if (m.compensates) {
            double p = m.usedSurplus(b);
            Dialogs.row(this, box, "Precio", p > 0
                    ? Fmt.dec(p, 4) + " €/kWh · " + (m.surplusPrice > 0 ? "el suyo"
                    : (b.surplusFromBill() ? "el de tu factura" : "el tuyo"))
                    : "sin dato");
            if (b.surplusKwhYear > 0 && p > 0) {
                Dialogs.row(this, box, "Al año", "−" + Fmt.eur2(m.credit(b)) + " con impuestos");
            }
        }

        Dialogs.section(this, box, "La oferta");
        Dialogs.row(this, box, "Energía verde", m.green ? "Sí" : "No");
        Dialogs.row(this, box, "Permanencia", m.penalty ? "Sí" : "No");
        Dialogs.row(this, box, "Batería virtual", m.battery ? "Sí (solo nota)" : "No");
        if (m.createdAt > 0) Dialogs.row(this, box, "Creada", when(m.createdAt));
        if (m.updatedAt > 0 && m.updatedAt != m.createdAt) {
            Dialogs.row(this, box, "Editada", when(m.updatedAt));
        }
        if (m.web != null && !m.web.isEmpty()) {
            Dialogs.wideRow(this, box, "Web", m.web, getColor(R.color.text));
        }
        Dialogs.note(this, box, "Los precios son los que has escrito, sin impuestos. El bono "
                + "social, el contador y los impuestos salen de tu última factura.");

        final AlertDialog[] holder = new AlertDialog[1];

        Button edit = new Button(this);
        edit.setText("Editar");
        edit.setAllCaps(false);
        edit.setBackground(getDrawable(R.drawable.bg_button_primary));
        edit.setTextColor(getColor(R.color.bg));
        edit.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            openEditor(m.id);
        });
        Dialogs.fill(edit);
        box.addView(edit);

        if (m.web != null && !m.web.isEmpty()) {
            Button web = new Button(this);
            web.setText("Ver su web");
            web.setAllCaps(false);
            web.setBackground(getDrawable(R.drawable.bg_button_secondary));
            web.setTextColor(getColor(R.color.text));
            web.setOnClickListener(v -> abrir(m.web));
            Dialogs.fill(web);
            box.addView(web);
        }

        TextView del = new TextView(this);
        del.setText("Borrar esta oferta");
        del.setTextColor(getColor(R.color.danger));
        del.setTextSize(13);
        del.setGravity(Gravity.CENTER);
        del.setPadding(0, dp(14), 0, dp(6));
        Dialogs.fill(del);
        del.setOnClickListener(v -> confirmDeleteManual(m, holder[0]));
        box.addView(del);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(m.name)
                .setView(Dialogs.scroller(this, box, 460))
                .setPositiveButton("Cerrar", null)
                .show();
        holder[0] = dlg;
        if (dlg.getWindow() != null) {
            int w = Math.round(getResources().getDisplayMetrics().widthPixels * 0.90f);
            dlg.getWindow().setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void confirmDeleteManual(final ManualOffer m, final AlertDialog detail) {
        new AlertDialog.Builder(this)
                .setTitle("¿Borrar esta oferta a mano?")
                .setMessage("«" + m.name + "» se quita del ranking. Si la CNMC tiene una oferta "
                        + "con el mismo nombre, vuelve a salir con sus datos.")
                .setPositiveButton("Borrar", (d, w) -> {
                    store.deleteManualOffer(m.id);
                    seenManualStamp = store.manualStamp();
                    if (detail != null) detail.dismiss();
                    repaint();
                    Toast.makeText(this, "Oferta borrada", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Vuelve a pintar con la lista ya descargada; solo se pide a la CNMC si no hay nada. */
    private void repaint() {
        if (cached == null) {
            container.removeAllViews();
            loadOffers();
            return;
        }
        container.removeAllViews();
        render(cached);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store == null) return;
        long stamp = store.manualStamp();
        if (seenManualStamp >= 0 && stamp != seenManualStamp) repaint();
        seenManualStamp = stamp;
    }

    /** «24/11/2026 a las 15:40», para decir de cuándo es lo guardado. */
    private static String when(long at) {
        if (at <= 0) return "la última consulta";
        return new java.text.SimpleDateFormat("d 'de' MMMM 'a las' HH:mm", new Locale("es", "ES"))
                .format(new java.util.Date(at));
    }

    private static double num(String s) {
        return Fmt.parse(s);
    }

    // ---------------- piezas de interfaz ----------------

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(getDrawable(R.drawable.bg_card));
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        l.setLayoutParams(lp);
        return l;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(getColor(R.color.muted));
        t.setTextSize(11);
        t.setLetterSpacing(0.12f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private TextView small(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(getColor(R.color.muted));
        t.setTextSize(13);
        t.setPadding(0, dp(4), 0, 0);
        Dialogs.fill(t);
        return t;
    }

    private void addRow(LinearLayout parent, String k, String v) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(9), 0, dp(9));

        TextView key = new TextView(this);
        key.setText(k);
        key.setTextColor(getColor(R.color.muted));
        key.setTextSize(13);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        klp.weight = 1;
        row.addView(key, klp);

        TextView val = new TextView(this);
        val.setText(v);
        val.setTextColor(getColor(R.color.text));
        val.setTextSize(13);
        val.setGravity(Gravity.END);
        row.addView(val);

        parent.addView(row);
    }

    private TextView pill(String text, boolean accent) {
        return pill(text, accent, 0);
    }

    /** `color` 0 = el del estilo; sirve para pintar de aviso un dato que no es de la oferta. */
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

    /**
     * Coloca las píldoras en filas de las que caben, midiendo cada una antes. Sin esto, la
     * última de la fila se aplasta (LinearLayout reparte el ancho que sobra en negativo).
     */
    private void addPills(LinearLayout card, List<TextView> list) {
        // Ancho útil: pantalla menos los márgenes de la lista, el relleno de la tarjeta y la
        // sangría de la fila (26 dp). Se deja un poco de holgura a propósito.
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

    private TextView msg(String s, boolean emphasize) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(emphasize ? getColor(R.color.text) : getColor(R.color.muted));
        t.setTextSize(14);
        t.setLineSpacing(dp(4), 1f);
        t.setPadding(dp(4), dp(24), dp(4), dp(12));
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** «2025-11-01» → «01/11/25», para el periodo del PVPC. */
    private static String day(String iso) {
        if (iso == null || iso.length() < 10) return iso == null ? "" : iso;
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(2, 4);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
    }
}
