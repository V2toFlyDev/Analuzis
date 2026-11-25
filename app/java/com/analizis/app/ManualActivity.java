package com.analizis.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Una oferta escrita a mano con los precios de su web. Sirve para las tres cosas: crear una
 * nueva, editar una guardada (se le pasa su id) y partir de una oferta de la CNMC (se le pasan
 * sus datos y su id para leer la ficha).
 *
 * No tiene fórmula propia: `ManualOffer` copia tu perfil, cambia energía y potencia y usa las
 * mismas cuentas que el comparador (`BillData.pricesBreakdown` + `netCostWith`).
 */
public class ManualActivity extends Activity {

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_OFFER_ID = "offerId";
    public static final String EXTRA_SUPPLIER = "supplier";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_WEB = "web";
    public static final String EXTRA_GREEN = "green";
    public static final String EXTRA_PENALTY = "penalty";
    public static final String EXTRA_SELF = "selfUse";
    public static final String EXTRA_SURPLUS = "surplus";

    private Store store;
    private BillData bill;
    private ManualOffer m = new ManualOffer();
    private boolean binding = false;
    private int offerId = 0;

    private EditText inName, inSupplier, inWeb, inEnergy, inE1, inE2, inE3, inP1, inP2, inSurplus;
    private Switch swTramos;
    private LinearLayout boxTramos, resultBox;
    private CheckBox cbCompensates, cbBattery, cbGreen, cbPenalty;
    private TextView fichaNote, btnDelete, subtitle;
    private Button btnSave;

    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_manual);
        Insets.apply(findViewById(R.id.root), dp(20), dp(28));
        store = new Store(this);
        bill = new History(store.invoices()).apply(store.load().copy());
        bindViews();
        wire();
        load();
        syncFields();
        recalc();
    }

    private void bindViews() {
        inName = findViewById(R.id.inName);
        inSupplier = findViewById(R.id.inSupplier);
        inWeb = findViewById(R.id.inWeb);
        inEnergy = findViewById(R.id.inEnergy);
        inE1 = findViewById(R.id.inE1);
        inE2 = findViewById(R.id.inE2);
        inE3 = findViewById(R.id.inE3);
        inP1 = findViewById(R.id.inP1);
        inP2 = findViewById(R.id.inP2);
        inSurplus = findViewById(R.id.inSurplus);
        swTramos = findViewById(R.id.swTramos);
        boxTramos = findViewById(R.id.boxTramos);
        resultBox = findViewById(R.id.resultBox);
        cbCompensates = findViewById(R.id.cbCompensates);
        cbBattery = findViewById(R.id.cbBattery);
        cbGreen = findViewById(R.id.cbGreen);
        cbPenalty = findViewById(R.id.cbPenalty);
        fichaNote = findViewById(R.id.fichaNote);
        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);
        subtitle = findViewById(R.id.subtitle);

        // En los campos de números valen la coma y el punto: se escribe como se quiera.
        DigitsKeyListener numeric = DigitsKeyListener.getInstance("0123456789.,");
        for (EditText e : new EditText[]{inEnergy, inE1, inE2, inE3, inP1, inP2, inSurplus}) {
            e.setKeyListener(numeric);
        }
    }

    private void wire() {
        findViewById(R.id.back).setOnClickListener(v -> finish());
        findViewById(R.id.btnWeb).setOnClickListener(v -> abrirWeb());

        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (!binding) recalc();
            }
        };
        EditText[] fields = {inName, inSupplier, inWeb, inEnergy, inE1, inE2, inE3, inP1, inP2, inSurplus};
        for (EditText e : fields) e.addTextChangedListener(w);

        swTramos.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            boxTramos.setVisibility(on ? View.VISIBLE : View.GONE);
            recalc();
        });
        cbCompensates.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            inSurplus.setEnabled(on);
            recalc();
        });
        cbBattery.setOnCheckedChangeListener((b, on) -> { if (!binding) recalc(); });
        cbGreen.setOnCheckedChangeListener((b, on) -> { if (!binding) recalc(); });
        cbPenalty.setOnCheckedChangeListener((b, on) -> { if (!binding) recalc(); });

        btnSave.setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> confirmDelete());
    }

    /** Nueva, editada o desde una oferta de la CNMC. */
    private void load() {
        Intent i = getIntent();
        long id = i.getLongExtra(EXTRA_ID, 0);
        if (id > 0) {
            ManualOffer saved = store.manualOffer(id);
            if (saved != null) {
                m = saved;
                btnDelete.setVisibility(View.VISIBLE);
                subtitle.setText("Estás editando una oferta guardada. Los cambios se aplican al "
                        + "volver al comparador.");
                return;
            }
        }

        // Desde una oferta automática: nombre, comercializadora y lo que ya se sepa de ella.
        m.name = safe(i.getStringExtra(EXTRA_NAME));
        m.supplier = safe(i.getStringExtra(EXTRA_SUPPLIER));
        m.web = safe(i.getStringExtra(EXTRA_WEB));
        m.green = i.getBooleanExtra(EXTRA_GREEN, false);
        m.penalty = i.getBooleanExtra(EXTRA_PENALTY, false);
        m.compensates = i.getBooleanExtra(EXTRA_SELF, false);
        m.surplusPrice = i.getDoubleExtra(EXTRA_SURPLUS, 0);
        offerId = i.getIntExtra(EXTRA_OFFER_ID, 0);
    }

    private void syncFields() {
        binding = true;
        inName.setText(safe(m.name));
        inSupplier.setText(safe(m.supplier));
        inWeb.setText(safe(m.web));
        boolean tramos = m.byTramos();
        swTramos.setChecked(tramos);
        boxTramos.setVisibility(tramos ? View.VISIBLE : View.GONE);
        inEnergy.setText(m.energy > 0 ? Fmt.dec(m.energy, 5) : "");
        inE1.setText(m.e1 > 0 ? Fmt.dec(m.e1, 5) : "");
        inE2.setText(m.e2 > 0 ? Fmt.dec(m.e2, 5) : "");
        inE3.setText(m.e3 > 0 ? Fmt.dec(m.e3, 5) : "");
        inP1.setText(m.power1 > 0 ? Fmt.dec(m.power1, 6) : "");
        inP2.setText(m.power2 > 0 ? Fmt.dec(m.power2, 6) : "");
        cbCompensates.setChecked(m.compensates);
        inSurplus.setEnabled(m.compensates);
        inSurplus.setText(m.surplusPrice > 0 ? Fmt.dec(m.surplusPrice, 5) : "");
        cbBattery.setChecked(m.battery);
        cbGreen.setChecked(m.green);
        cbPenalty.setChecked(m.penalty);
        binding = false;
        if (offerId > 0 && m.id <= 0) pedirFicha();
    }

    /** Lo que la ficha de la CNMC diga de energía y potencia, solo para rellenar huecos. */
    private void pedirFicha() {
        fichaNote.setVisibility(View.VISIBLE);
        fichaNote.setText("Leyendo los precios de su ficha en la CNMC…");
        pool.execute(() -> {
            CnmcClient.Detail d = CnmcClient.offerDetail(bill, offerId);
            final OfferPrices p = d == null ? new OfferPrices() : OfferPrices.parse(d.text);
            final String web = d == null ? "" : d.web;
            runOnUiThread(() -> aplicarFicha(p, web));
        });
    }

    private void aplicarFicha(OfferPrices p, String web) {
        binding = true;
        if (text(inWeb).isEmpty() && web != null && !web.isEmpty()) inWeb.setText(web);
        boolean tramos = p.e1 > 0 && p.e2 > 0 && p.e3 > 0;
        if (tramos && text(inEnergy).isEmpty() && text(inE1).isEmpty()
                && text(inE2).isEmpty() && text(inE3).isEmpty()) {
            swTramos.setChecked(true);
            boxTramos.setVisibility(View.VISIBLE);
            inE1.setText(Fmt.dec(p.e1, 5));
            inE2.setText(Fmt.dec(p.e2, 5));
            inE3.setText(Fmt.dec(p.e3, 5));
        } else if (p.energy > 0 && text(inEnergy).isEmpty()) {
            inEnergy.setText(Fmt.dec(p.energy, 5));
        }
        if (p.power1 > 0 && text(inP1).isEmpty()) inP1.setText(Fmt.dec(p.power1, 6));
        if (p.power2 > 0 && text(inP2).isEmpty()) inP2.setText(Fmt.dec(p.power2, 6));
        binding = false;
        recalc();

        boolean algo = p.hasEnergy() || p.hasPower();
        StringBuilder n = new StringBuilder();
        if (!algo) {
            n.append("Su ficha en la CNMC no trae los precios en un formato que pueda leer: "
                    + "escríbelos desde su web.");
        } else {
            n.append("Leído de su ficha en la CNMC: ");
            if (p.hasEnergy()) {
                n.append("energía ");
                if (p.energy > 0) n.append(Fmt.dec(p.energy, 5)).append(" €/kWh");
                if (p.e1 > 0 && p.e2 > 0 && p.e3 > 0) {
                    n.append(p.energy > 0 ? " y por tramos " : "por tramos ")
                            .append(Fmt.dec(p.e1, 5)).append(" / ")
                            .append(Fmt.dec(p.e2, 5)).append(" / ")
                            .append(Fmt.dec(p.e3, 5)).append(" €/kWh");
                }
            }
            if (p.hasPower()) {
                n.append(p.hasEnergy() ? " · potencia " : "potencia ")
                        .append(Fmt.dec(p.power1, 6));
                if (p.power2 > 0 && Math.abs(p.power2 - p.power1) > 0.000001) {
                    n.append(" / ").append(Fmt.dec(p.power2, 6));
                }
                n.append(" €/kW·día");
            }
            n.append(". Revisa lo que falte antes de guardar.");
        }
        fichaNote.setText(n.toString());
    }

    /** Lee los campos y pinta el resultado, sin guardar nada. */
    private void recalc() {
        readFields();
        resultBox.removeAllViews();

        if (!bill.complete()) {
            resultBox.addView(noteText("Para calcular hace falta tu factura y tu consumo anual. "
                    + "Añádela en la pantalla principal: esta oferta se compara con tus kWh, tu "
                    + "potencia y tus impuestos, y sin ellos no hay cuenta que hacer."));
            setSaveEnabled(false);
            return;
        }

        if (!m.hasPrices()) {
            resultBox.addView(noteText(m.byTramos() || swTramos.isChecked()
                    ? "Faltan precios: con tramos hacen falta P1, P2 y P3, y la potencia."
                    : "Faltan precios: escribe la energía (€/kWh) y la potencia (€/kW·día)."));
            setSaveEnabled(false);
            return;
        }

        setSaveEnabled(!m.name.isEmpty());
        BillData.Breakdown d = m.breakdown(bill);
        double net = m.net(bill);

        Dialogs.section(this, resultBox, "Coste al año");
        Dialogs.row(this, resultBox, "Con esta oferta", Fmt.eur2(net), getColor(R.color.accent));
        if (m.compensates && net < d.gross - 0.5) {
            Dialogs.row(this, resultBox, "Sin descontar excedentes", Fmt.eur2(d.gross));
        }
        double mine = bill.baselineAnnual();
        if (mine > 0) {
            Dialogs.row(this, resultBox, "Tu factura actual", Fmt.eur2(mine) + " al año");
            double diff = mine - net;
            Dialogs.row(this, resultBox,
                    diff >= 0 ? "Ahorro al año" : "Te costaría más al año",
                    Fmt.eur2(Math.abs(diff)),
                    getColor(diff >= 0 ? R.color.accent : R.color.warn));
        }

        Dialogs.section(this, resultBox, "De dónde sale");
        Dialogs.row(this, resultBox, "Energía", Fmt.eur2(d.energy));
        Dialogs.row(this, resultBox, "Potencia", Fmt.eur2(d.power));
        if (d.bono > 0) Dialogs.row(this, resultBox, "Bono social", Fmt.eur2(d.bono));
        if (d.tax > 0) Dialogs.row(this, resultBox, "Mínimo comunitario", Fmt.eur2(d.tax));
        if (d.rent > 0) Dialogs.row(this, resultBox, "Contador", Fmt.eur2(d.rent));
        Dialogs.row(this, resultBox, "Impuesto eléctrico", Fmt.eur2(d.iee));
        Dialogs.row(this, resultBox, "IVA", Fmt.eur2(d.iva));
        Dialogs.row(this, resultBox, "Total", Fmt.eur2(d.gross));

        if (m.compensates) {
            Dialogs.section(this, resultBox, "Excedentes");
            double price = m.usedSurplus(bill);
            String from = m.surplusPrice > 0 ? "el de esta oferta"
                    : (bill.surplusFromBill() ? "el de tu factura" : "el tuyo");
            Dialogs.row(this, resultBox, "Aplico",
                    price > 0 ? Fmt.dec(price, 4) + " €/kWh · " + from : "sin precio");
            boolean algo = bill.surplusKwhYear > 0 || bill.annualSurplus() > 0;
            if (!algo) {
                Dialogs.note(this, resultBox, "No hay kWh vertidos guardados ni importe en tu "
                        + "factura, así que esta compensación no descuenta nada.");
            } else {
                double credit = m.credit(bill);
                Dialogs.row(this, resultBox, "Al año",
                        "−" + Fmt.eur2(credit) + " con impuestos");
                if (bill.annualSurplus() > 0) {
                    double factura = bill.annualSurplusTaxed();
                    Dialogs.row(this, resultBox, "Tu factura a precio de hoy", "−" + Fmt.eur2(factura));
                    double diff = credit - factura;
                    if (Math.abs(diff) >= 0.01) {
                        Dialogs.row(this, resultBox, "Diferencia por excedentes",
                                Fmt.eur2(diff), getColor(R.color.warn));
                    }
                }
                if (bill.surplusKwhYear <= 0) {
                    Dialogs.note(this, resultBox, "Sin kWh vertidos guardados no puedo cambiar el "
                            + "precio por kWh: aplico la compensación de tu factura tal cual.");
                }
            }
        }

        StringBuilder extra = new StringBuilder();
        if (m.battery) extra.append("Con batería virtual (solo como nota). ");
        if (m.green) extra.append("Energía verde. ");
        if (m.penalty) extra.append("Con permanencia. ");
        if (extra.length() > 0) Dialogs.note(this, resultBox, extra.toString().trim());

        if (m.createdAt > 0) {
            String s = "Creada el " + day(m.createdAt);
            if (m.updatedAt > 0 && m.updatedAt != m.createdAt) s += " · editada el " + day(m.updatedAt);
            Dialogs.note(this, resultBox, s + ".");
        }
        if (m.name.isEmpty()) {
            Dialogs.note(this, resultBox, "Ponle un nombre para poder identificarla y guardarla.");
        }
    }

    private void readFields() {
        m.name = text(inName);
        m.supplier = text(inSupplier);
        m.web = text(inWeb);
        boolean tramos = swTramos.isChecked();
        m.energy = tramos ? 0 : num(text(inEnergy));
        m.e1 = tramos ? num(text(inE1)) : 0;
        m.e2 = tramos ? num(text(inE2)) : 0;
        m.e3 = tramos ? num(text(inE3)) : 0;
        m.power1 = num(text(inP1));
        m.power2 = num(text(inP2));
        m.compensates = cbCompensates.isChecked();
        m.surplusPrice = m.compensates ? num(text(inSurplus)) : 0;
        m.battery = cbBattery.isChecked();
        m.green = cbGreen.isChecked();
        m.penalty = cbPenalty.isChecked();
    }

    private void save() {
        readFields();
        if (m.name.isEmpty()) {
            toast("Ponle un nombre a la oferta.");
            return;
        }
        if (!bill.complete()) {
            toast("Falta tu factura: sin ella no hay con qué comparar.");
            return;
        }
        if (swTramos.isChecked() && !(m.e1 > 0 && m.e2 > 0 && m.e3 > 0)) {
            toast("Con tramos hacen falta los tres precios, P1, P2 y P3.");
            return;
        }
        if (!swTramos.isChecked() && m.energy <= 0) {
            toast("Escribe el precio de la energía (€/kWh).");
            return;
        }
        if (m.power1 <= 0 && m.power2 <= 0) {
            toast("Escribe la potencia (€/kW·día).");
            return;
        }
        store.saveManualOffer(m);
        toast("Oferta guardada");
        finish();
    }

    private void confirmDelete() {
        if (m.id <= 0) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("¿Borrar esta oferta a mano?")
                .setMessage("«" + m.name + "» se quita del listado y del comparador. Si la CNMC "
                        + "tiene una oferta con el mismo nombre, vuelve a salir con sus datos.")
                .setPositiveButton("Borrar", (d, w) -> {
                    store.deleteManualOffer(m.id);
                    toast("Oferta borrada");
                    finish();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void setSaveEnabled(boolean on) {
        btnSave.setEnabled(on);
        btnSave.setAlpha(on ? 1f : 0.45f);
    }

    private void abrirWeb() {
        String u = text(inWeb);
        if (u.isEmpty()) {
            toast("Escribe primero la web de la oferta.");
            return;
        }
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        } catch (Exception e) {
            toast("No he podido abrir el navegador.");
        }
    }

    private TextView noteText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(getColor(R.color.muted));
        t.setTextSize(12);
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(0, dp(8), 0, 0);
        return t;
    }

    private static String text(EditText e) {
        return e.getText().toString().trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static double num(String s) {
        return Fmt.parse(s);
    }

    private static String day(long at) {
        return new java.text.SimpleDateFormat("dd/MM/yyyy", new Locale("es", "ES"))
                .format(new java.util.Date(at));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
    }
}
