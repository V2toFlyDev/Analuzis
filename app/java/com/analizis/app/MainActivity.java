package com.analizis.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pantalla principal. */
public class MainActivity extends Activity {

    private static final int REQ_CAM = 11;
    private static final int REQ_GAL = 12;
    private static final int REQ_LIVE = 13;
    private static final int REQ_PDF = 15;
    private static final int REQ_DIAG = 16;
    private static final int REQ_BACKUP = 17;
    private static final int REQ_CAMERA = 23;

    private Store store;
    private TextView btnClear, btnHelp;
    private Button btnAdd, btnCompare, btnManual, btnManualList;
    private Switch swAuto, swBattery;
    private EditText inAutoKwh, inSurplusKwh, inSurplusEur, inSurplusPrice;
    private EditText inPe, inPp1, inPp2, inPs, inBono, inRent;
    private Button btnSolarAdd;
    private TextView solarSummary;
    private TextView surplusSummary, priceSummary, historySummary, historyTitle, historyChevron;
    private TextView supplyDetails;
    private TextView monthlyLink, solarLink, datadisLink, pvpcLink, linkTramos, censoLink, backupLink;
    private LinearLayout historyBox, historyPanel, historyToggle, billInside;
    private CheckBox cbFijo, cbAuto, cbVerde;

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private File pendingPhoto;
    private boolean binding = false;
    // PDFs leídos pero sin confirmar: en memoria hasta que se guarda la factura.
    private final java.util.HashMap<String, byte[]> pendingPdfs = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        Insets.apply(findViewById(R.id.root), dp(28), dp(36));
        store = new Store(this);
        bindViews();
        showVersion();
        wire();
        render();
    }

    private void bindViews() {
        supplyDetails = findViewById(R.id.supplyDetails);
        datadisLink = findViewById(R.id.linkDatadis);
        pvpcLink = findViewById(R.id.linkPvpc);
        monthlyLink = findViewById(R.id.linkMonths);
        solarLink = findViewById(R.id.linkSolar);
        linkTramos = findViewById(R.id.linkTramos);
        censoLink = findViewById(R.id.linkCenso);
        backupLink = findViewById(R.id.linkBackup);
        billInside = findViewById(R.id.billInside);
        btnClear = findViewById(R.id.btnClear);
        btnHelp = findViewById(R.id.btnHelp);
        btnAdd = findViewById(R.id.btnAdd);
        btnCompare = findViewById(R.id.btnCompare);
        btnManual = findViewById(R.id.btnManual);
        btnManualList = findViewById(R.id.btnManualList);
        swAuto = findViewById(R.id.swAuto);
        inAutoKwh = findViewById(R.id.inAutoKwh);
        inSurplusKwh = findViewById(R.id.inSurplusKwh);
        inSurplusEur = findViewById(R.id.inSurplusEur);
        inSurplusPrice = findViewById(R.id.inSurplusPrice);
        inPe = findViewById(R.id.inPe);
        inPp1 = findViewById(R.id.inPp1);
        inPp2 = findViewById(R.id.inPp2);
        inPs = findViewById(R.id.inPs);
        inBono = findViewById(R.id.inBono);
        inRent = findViewById(R.id.inRent);
        surplusSummary = findViewById(R.id.surplusSummary);
        btnSolarAdd = findViewById(R.id.btnSolarAdd);
        solarSummary = findViewById(R.id.solarSummary);
        priceSummary = findViewById(R.id.priceSummary);
        historyBox = findViewById(R.id.historyBox);
        historySummary = findViewById(R.id.historySummary);
        historyTitle = findViewById(R.id.historyTitle);
        historyPanel = findViewById(R.id.historyPanel);
        historyToggle = findViewById(R.id.historyToggle);
        historyChevron = findViewById(R.id.historyChevron);
        swBattery = findViewById(R.id.swBattery);
        cbFijo = findViewById(R.id.cbFijo);
        cbAuto = findViewById(R.id.cbAuto);
        cbVerde = findViewById(R.id.cbVerde);
    }

    /** Ayuda breve: qué hace cada pantalla y de dónde sale cada dato. */
    private void showHelp() {
        LinearLayout body = Dialogs.body(this);

        Dialogs.section(this, body, "Qué hace");
        Dialogs.note(this, body, "Compara lo que pagas con las ofertas de la CNMC y con las que "
                + "escribas tú. Todo se queda en este móvil.");

        Dialogs.section(this, body, "Pantalla principal");
        Dialogs.note(this, body, "1 · Tu factura: añade el QR o los PDFs; sin factura no hay "
                + "comparación.\n"
                + "2 · Tu tarifa real: los precios de tu última factura.\n"
                + "3 · Tus placas y excedentes: autoconsumo y kWh vertidos.\n"
                + "4 · Qué quieres en la oferta: filtros.\n"
                + "Abajo están Comparador y Listado manual.");

        Dialogs.section(this, body, "Comparador y ofertas a mano");
        Dialogs.note(this, body, "El comparador ordena las ofertas de la CNMC por coste anual con "
                + "tus kWh, tu potencia y tus impuestos, descontando los excedentes que paga cada "
                + "una. Toca una oferta para ver el detalle. «Comparar a mano» crea una oferta con "
                + "los precios de su web; queda en Listado manual, donde puedes editarla o borrarla.");

        Dialogs.section(this, body, "QR y facturas");
        Dialogs.note(this, body, "El QR de tu factura da código postal, CUPS, comercializadora y "
                + "consumo anual. El PDF da precios de energía y potencia, periodo, kWh, impuestos "
                + "y excedentes. Puedes importar los PDFs o meter la factura a mano; las copias se "
                + "guardan aquí.");

        Dialogs.section(this, body, "Datadis");
        Dialogs.note(this, body, "Es la curva horaria de tu distribuidora: reparto P1/P2/P3 y "
                + "vertido a la red. Date de alta en datadis.es con tu NIF; la app pide ese NIF y "
                + "tu contraseña. No deja repetir la consulta en 24 horas y no ve el autoconsumo "
                + "de tus placas.");

        Dialogs.section(this, body, "ESIOS");
        Dialogs.note(this, body, "Da los precios reales del PVPC de los últimos 12 meses. Crea "
                + "una cuenta en esios.ree.es y copia tu token personal; la app lo pide una vez. "
                + "Solo compara el término de energía, no la potencia.");

        Dialogs.section(this, body, "Copia de seguridad");
        Dialogs.note(this, body, "Al final de la pantalla principal puedes crear un archivo con "
                + "tus facturas y PDFs, la curva de Datadis, los meses del inversor y el PVPC. "
                + "No incluye el NIF ni la contraseña de Datadis, ni el token de ESIOS: después "
                + "de restaurar hay que volver a introducirlos. Restaurar sustituye los datos "
                + "actuales y el archivo contiene datos personales; guárdalo en un sitio seguro.");

        Dialogs.section(this, body, "Consejo");
        Dialogs.note(this, body, "Cuantas más facturas y meses de placas metas, mejor sale la "
                + "comparación. Si algo no cuadra, el detalle de cada factura dice qué dato falla.");

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Cómo funciona Analuzis")
                .setView(Dialogs.scroller(this, body, 520))
                .setPositiveButton("Entendido", null)
                .show();
        Dialogs.width(dlg);
    }

    private void showVersion() {
        TextView ver = findViewById(R.id.verTop);
        String name = "";
        try {
            name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        ver.setText(name == null ? "" : name);
        ver.setVisibility(name == null || name.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void wire() {
        btnAdd.setOnClickListener(v -> showAddMenu());
        btnHelp.setOnClickListener(v -> showHelp());
        historyToggle.setOnClickListener(v -> {
            boolean open = historyPanel.getVisibility() != View.VISIBLE;
            store.setHistoryOpen(open);
            applyHistoryState(open);
        });
        btnCompare.setOnClickListener(v -> startActivity(new Intent(this, ResultsActivity.class)));
        // Siempre visibles: el comparador avisa si todavía falta la factura.
        btnManual.setOnClickListener(v -> startActivity(new Intent(this, ManualActivity.class)));
        btnManualList.setOnClickListener(v -> showManualList());
        btnClear.setOnClickListener(v -> confirmClear());
        btnSolarAdd.setOnClickListener(v -> addSolarMonth());
        solarSummary.setOnClickListener(v -> showSolarMonths());
        monthlyLink.setOnClickListener(v -> showMonths());
        datadisLink.setOnClickListener(v -> connectDatadis());
        pvpcLink.setOnClickListener(v -> showPvpc());
        linkTramos.setOnClickListener(v -> showTramos());
        solarLink.setOnClickListener(v -> showSolarMonths());
        censoLink.setOnClickListener(v -> CensoDialog.show(this, store));
        backupLink.setOnClickListener(v -> startActivityForResult(
                new Intent(this, BackupActivity.class), REQ_BACKUP));

        swAuto.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            // El interruptor se guarda aparte del número: al apagarlo no se borra el dato,
            // solo queda desactivado, y al volver a encenderlo sigue ahí.
            store.setAutoOn(on);
            applyAutoState(on);
        });

        inAutoKwh.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (binding) return;
                store.setAutoKwh(parseNumber(s.toString()));
            }
        });

        TextWatcher surplusWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (binding) return;
                BillData bd = store.load();
                double kwh = parseNumber(inSurplusKwh.getText().toString());
                double eur = parseNumber(inSurplusEur.getText().toString());
                double price = parseNumber(inSurplusPrice.getText().toString());
                if (Math.abs(kwh - bd.surplusKwhYear) < 0.005 && Math.abs(eur - bd.surplusEur) < 0.0005
                        && Math.abs(price - bd.surplusPriceManual) < 0.0005) return;
                bd.surplusKwhYear = kwh;
                bd.surplusEur = eur;
                bd.surplusPriceManual = price;
                store.save(bd);
                BillData p = new History(store.invoices()).apply(bd.copy());
                updateSurplus(p);
                renderBillInside(p);
            }
        };
        inSurplusKwh.addTextChangedListener(surplusWatcher);
        inSurplusEur.addTextChangedListener(surplusWatcher);
        inSurplusPrice.addTextChangedListener(surplusWatcher);

        TextWatcher priceWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (binding) return;
                BillData bd = store.load();
                double pe = parseNumber(inPe.getText().toString());
                double p1 = parseNumber(inPp1.getText().toString());
                double p2 = parseNumber(inPp2.getText().toString());
                double ps = parseNumber(inPs.getText().toString());
                double bono = parseNumber(inBono.getText().toString());
                double rent = parseNumber(inRent.getText().toString());
                if (Math.abs(pe - bd.nowEnergyPrice()) < 1e-9 && Math.abs(p1 - bd.pp1) < 1e-9
                        && Math.abs(p2 - bd.pp2) < 1e-9 && Math.abs(ps - bd.ps) < 1e-9
                        && Math.abs(bono - bd.bono) < 1e-9 && Math.abs(rent - bd.rent) < 1e-9) return;
                store.updatePrices(pe, p1, p2, ps, bono, rent);
                BillData fresh = store.load();
                updatePricesView(fresh);
                renderBillInside(new History(store.invoices()).apply(fresh.copy()));
            }
        };
        inPe.addTextChangedListener(priceWatcher);
        inPp1.addTextChangedListener(priceWatcher);
        inPp2.addTextChangedListener(priceWatcher);
        inPs.addTextChangedListener(priceWatcher);
        inBono.addTextChangedListener(priceWatcher);
        inRent.addTextChangedListener(priceWatcher);

        swBattery.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            BillData bd = store.load();
            bd.battery = on;
            store.save(bd);
        });

        cbFijo.setOnCheckedChangeListener((b, on) -> { if (!binding) store.setFilter("f_fixed", on); });
        cbAuto.setOnCheckedChangeListener((b, on) -> { if (!binding) store.setFilter("f_self", on); });
        cbVerde.setOnCheckedChangeListener((b, on) -> { if (!binding) store.setFilter("f_green", on); });

    }

    private void render() {
        binding = true;
        BillData raw = store.load();
        BillData b = new History(store.invoices()).apply(raw.copy());
        boolean has = b.complete();

        btnCompare.setVisibility(has ? View.VISIBLE : View.GONE);
        btnClear.setVisibility(has ? View.VISIBLE : View.GONE);
        // La tarjeta 1 lleva ya el desglose (el antiguo «Tu factura por dentro»): no hay otra
        // cabecera encima del botón de añadir la factura.
        renderBillInside(b);

        List<SolarMonth> solar = store.solarMonths();
        boolean fullYear = solar.size() >= SolarMonth.YEAR_MONTHS;
        swAuto.setChecked(b.autoOn);
        inAutoKwh.setText(b.autoKwh > 0 ? v(b.autoKwh) : "");
        applyAutoState(b.autoOn);
        renderSolar(solar);
        boolean hist = b.historyCount >= 2 && b.historyDays >= 60;
        inSurplusKwh.setText(b.surplusKwhYear > 0 ? v(b.surplusKwhYear) : "");
        inSurplusEur.setText(b.surplusEur > 0 ? Fmt.dec(b.surplusEur, 3) : "");
        inSurplusEur.setEnabled(!hist);
        inSurplusPrice.setText(b.surplusPriceManual > 0 ? Fmt.dec(b.surplusPriceManual, 3) : "");
        swBattery.setChecked(b.battery && b.autoOn);
        updateSurplus(b);

        renderSupply(b);
        double nowPe = b.nowEnergyPrice();
        inPe.setText(nowPe > 0 ? Fmt.dec(nowPe, 4) : "");
        inPp1.setText(b.pp1 > 0 ? Fmt.dec(b.pp1, 6) : "");
        inPp2.setText(b.pp2 > 0 ? Fmt.dec(b.pp2, 6) : "");
        inPs.setText(b.ps > 0 ? Fmt.dec(b.ps, 3) : "");
        renderTramos(b);
        inBono.setText(b.bono > 0 ? Fmt.dec(b.bono, 6) : "");
        inRent.setText(b.rent > 0 ? Fmt.dec(b.rent, 6) : "");
        updatePricesView(raw);

        cbFijo.setChecked(store.filterFixed());
        cbAuto.setChecked(store.filterSelfUse());
        cbVerde.setChecked(store.filterGreen());

        renderHistory();
        monthlyLink.setVisibility(store.invoices().isEmpty() && solar.isEmpty() ? View.GONE : View.VISIBLE);
        binding = false;
    }

    /**
     * «Tu factura por dentro»: el desglose con el que se hace la comparación. Antes salía en
     * la pantalla de ofertas; aquí se ve antes de elegir qué quieres en la oferta.
     */
    private void renderBillInside(BillData b) {
        if (billInside == null) return;
        billInside.removeAllViews();
        if (!b.complete()) {
            billInside.addView(insideNote("En cuanto tenga tu factura y tu consumo anual, aquí "
                    + "verás con qué números se hace la comparación."));
            return;
        }
        if (b.historyCount >= 2) {
            insideRow("Calculado con", b.historyCount + " facturas · " + b.historyDays + " días");
        }
        double mine = b.pricePerKwh();
        if (mine > 0 && b.historyCount < 2) {
            insideRow("Precio medio que pagas (con IVA)", Fmt.dec(mine, 3) + " €/kWh");
        }
        insideRow("Consumo del año", Fmt.kwh(b.kwhYear));
        if (b.kwh1 + b.kwh2 + b.kwh3 > 0) {
            insideRow("por tramos P1 · P2 · P3",
                    b.kwh1 + " · " + b.kwh2 + " · " + b.kwh3 + " kWh");
        }
        insideRow("Potencia P1 / P2", Fmt.dec(b.p1, 1) + " kW / "
                + Fmt.dec(b.p2 > 0 ? b.p2 : b.p1, 1) + " kW");

        double paidYear = b.paidAnnual();
        double atTariff = b.baselineAnnual();
        if (paidYear > 0) {
            insideRow(b.hasYearData() ? "Lo que has pagado al año (solo luz, con IVA)"
                    : "Tu tarifa con tus precios, al año", Fmt.eur(paidYear));
        }
        if (atTariff > 0 && Math.abs(atTariff - paidYear) >= 5) {
            insideRow("Lo mismo con tu última tarifa", Fmt.eur(atTariff));
        }
        if (b.autoKwh > 0) insideRow("Autoconsumo", Fmt.kwhDec(b.autoKwh) + " al año");
        double surplusPrice = b.annualSurplusPrice();
        if (b.surplusKwhYear > 0) {
            insideRow("Vertiste a la red (Datadis/inversor)",
                    Fmt.kwhDec(b.surplusKwhYear) + " al año");
        }
        if (b.surplusFromBill()) {
            insideRow("Cobrado en tus facturas",
                    Fmt.eur(b.surplusEur) + " en " + b.surplusDays() + " días");
        }
        if (surplusPrice > 0 && b.surplusKwhYear > 0) {
            insideRow("Al año a " + Fmt.dec(surplusPrice, 3) + " €/kWh",
                    Fmt.eur(b.annualSurplus()));
            // La cuenta va en una nota a todo el ancho: un valor tan largo en la fila dejaba
            // al rótulo con ancho 0 y estiraba la tarjeta con un hueco enorme.
            insideNote("= " + Fmt.kwhDec(b.surplusKwhYear) + " × "
                    + Fmt.dec(surplusPrice, 3) + " €/kWh"
                    + (b.ps > 0 ? " (precio de tu factura)" : " (a mano)"));
        } else if (b.surplusFromBill()) {
            insideRow("Al año", Fmt.eur(b.annualSurplus()) + " = "
                    + Fmt.eur(b.surplusEur) + " × 365/" + b.surplusDays() + " días");
        }
        if (b.annualSurplus() > 0) {
            insideRow("", Fmt.eur(b.annualSurplusTaxed()) + " con IVA");
            insideRow("Batería virtual", b.battery ? "Sí" : "No");
        }

        // Repaso de lo guardado: era la última línea de la vieja cabecera de «Tu factura».
        InvoiceCheck chk = InvoiceCheck.run(store.invoices());
        if (chk.total >= 2) {
            String check = "Facturas guardadas: " + chk.total
                    + " · con precios " + chk.withPrices
                    + " · cuadran " + chk.square;
            if (!chk.bad.isEmpty()) check += " · fallan " + chk.bad.size();
            billInside.addView(insideNote(check));
        }

        billInside.addView(insideNote("Tu gasto anual es solo luz: no cuenta fianzas, "
                + "devoluciones ni el saldo que tires de la batería virtual. Ya lleva descontado "
                + "lo que te compensan por excedentes; a cada oferta se le resta lo que paga ella "
                + "por tus kWh vertidos."));
    }

    private void insideRow(String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView k = new TextView(this);
        k.setText(key);
        k.setTextColor(getColor(R.color.muted));
        k.setTextSize(13);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        klp.weight = 1;
        row.addView(k, klp);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(getColor(R.color.text));
        v.setTextSize(13);
        row.addView(v);

        billInside.addView(row);
    }

    private TextView insideNote(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(getColor(R.color.muted));
        t.setTextSize(11);
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(0, dp(10), 0, 0);
        return t;
    }

    // ---------------- historial visible ----------------

    private void applyHistoryState(boolean open) {
        historyPanel.setVisibility(open ? View.VISIBLE : View.GONE);
        historyChevron.setText(open ? "▴" : "▾");
    }

    private void renderHistory() {
        List<Invoice> all = store.invoices();
        all.sort(Invoice.byDate());
        historyBox.removeAllViews();
        historyTitle.setText(all.isEmpty() ? "FACTURAS GUARDADAS" : "FACTURAS GUARDADAS · " + all.size());
        applyHistoryState(store.historyOpen());

        if (all.isEmpty()) {
            historySummary.setText("Aún no hay facturas guardadas. Importa tus PDFs: con dos o más, el cálculo usa tus datos reales en vez de multiplicar una factura por doce.");
            return;
        }

        int days = 0;
        for (Invoice i : all) days += i.days;
        String d = days >= 300
                ? " Mando el reparto por tramos de tu historial."
                : " Con 300 días o más usaré también el reparto por tramos del historial.";
        historySummary.setText(all.size() + (all.size() == 1 ? " factura" : " facturas")
                + " · " + days + " días." + d);

        for (Invoice i : all) historyBox.addView(historyRow(i));
    }

    private View historyRow(final Invoice inv) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setOnClickListener(v -> showInvoice(inv));

        TextView t = new TextView(this);
        t.setText(shortDate(inv.from) + " → " + shortDate(inv.to));
        t.append("\n" + Fmt.kwh(inv.kwh()) + " · " + Fmt.eur(inv.total));
        if (inv.surplus > 0) t.append(" · exc. " + Fmt.eur2(inv.surplus));
        t.setTextColor(getColor(R.color.text));
        t.setTextSize(13);
        t.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.weight = 1;
        row.addView(t, lp);

        if (inv.pdf != null && !inv.pdf.isEmpty() && Pdfs.has(this, inv.pdf)) {
            TextView tag = new TextView(this);
            tag.setText("PDF");
            tag.setTextSize(10);
            tag.setLetterSpacing(0.08f);
            tag.setTextColor(getColor(R.color.muted));
            tag.setBackgroundResource(R.drawable.bg_pill_mute);
            tag.setPadding(dp(7), dp(3), dp(7), dp(3));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tlp.rightMargin = dp(4);
            row.addView(tag, tlp);
        }

        TextView del = new TextView(this);
        del.setText("Borrar");
        del.setTextColor(getColor(R.color.danger));
        del.setTextSize(12);
        del.setPadding(dp(14), dp(10), dp(2), dp(10));
        del.setOnClickListener(v -> confirmDelete(inv));
        row.addView(del);
        return row;
    }

    private void confirmDelete(final Invoice inv) {
        LinearLayout body = Dialogs.body(this);
        Dialogs.row(this, body, "Periodo", shortDate(inv.from) + " → " + shortDate(inv.to));
        Dialogs.row(this, body, "Consumo", Fmt.kwh(inv.kwh()));
        if (inv.total > 0) Dialogs.row(this, body, "Total", Fmt.eur2(inv.total));
        if (inv.surplus > 0) Dialogs.row(this, body, "Excedentes", Fmt.eur2(inv.surplus));
        Dialogs.note(this, body, "El cálculo se rehace con las que queden.");

        new AlertDialog.Builder(this)
                .setTitle("¿Borrar esta factura?")
                .setView(body)
                .setPositiveButton("Borrar", (d, w) -> {
                    store.removeInvoice(inv);
                    render();
                    toast("Factura borrada; cálculo rehecho");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static String shortDate(String iso) {
        if (iso == null || iso.length() < 10) return "?";
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(2, 4);
    }

    /** Detalle de una factura guardada, con su PDF si se conservó. */
    private void showInvoice(final Invoice inv) {
        boolean saved = inv.pdf != null && !inv.pdf.isEmpty() && Pdfs.has(this, inv.pdf);

        LinearLayout body = Dialogs.body(this);

        Dialogs.section(this, body, "Consumo");
        Dialogs.row(this, body, "Periodo", shortDate(inv.from) + " → " + shortDate(inv.to));
        Dialogs.row(this, body, "Días", String.valueOf(inv.days));
        Dialogs.row(this, body, "Total", Fmt.kwh(inv.kwh()));
        if (inv.kwh1 + inv.kwh2 + inv.kwh3 > 0) {
            Dialogs.row(this, body, "Por tramos", "P1 " + inv.kwh1 + " · P2 " + inv.kwh2
                    + " · P3 " + inv.kwh3);
        }

        boolean paid = inv.elec > 0 && inv.total > 0 && Math.abs(inv.elec - inv.total) > 0.02;
        if (inv.total > 0 || inv.elec > 0) {
            Dialogs.section(this, body, "Importe");
            if (paid) {
                Dialogs.row(this, body, "Luz", Fmt.eur2(inv.elec));
                Dialogs.row(this, body, "Total pagado", Fmt.eur2(inv.total));
            } else {
                Dialogs.row(this, body, "Total", Fmt.eur2(inv.total > 0 ? inv.total : inv.elec));
            }
            if (inv.surplus > 0) {
                Dialogs.row(this, body, "Excedentes", Fmt.eur2(inv.surplus) + " sin IVA",
                        getColor(R.color.accent));
            }
        }

        if (inv.p1 > 0) {
            Dialogs.section(this, body, "Potencia");
            Dialogs.row(this, body, "P1", Fmt.dec(inv.p1, 2) + " kW");
            if (inv.p2 > 0 && Math.abs(inv.p2 - inv.p1) > 0.01) {
                Dialogs.row(this, body, "P2", Fmt.dec(inv.p2, 2) + " kW");
            }
        }

        if (inv.pe > 0) {
            boolean dosPrecios = inv.peNow > 0 && Math.abs(inv.peNow - inv.pe) > 0.00005;
            Dialogs.section(this, body, "Tu tarifa en esa factura");
            Dialogs.row(this, body, dosPrecios ? "Energía efectiva" : "Energía",
                    Fmt.dec(inv.pe, 4) + " €/kWh");
            if (dosPrecios) {
                Dialogs.row(this, body, "Energía vigente hoy", Fmt.dec(inv.peNow, 4) + " €/kWh",
                        getColor(R.color.accent));
            }
            if (inv.pe1 > 0) Dialogs.row(this, body, "Energía P1 · punta", Fmt.dec(inv.pe1, 5) + " €/kWh");
            if (inv.pe2 > 0) Dialogs.row(this, body, "Energía P2 · llano", Fmt.dec(inv.pe2, 5) + " €/kWh");
            if (inv.pe3 > 0) Dialogs.row(this, body, "Energía P3 · valle", Fmt.dec(inv.pe3, 5) + " €/kWh");
            if (inv.pp1 > 0) Dialogs.row(this, body, "Potencia P1", Fmt.dec(inv.pp1, 6) + " €/kW·día");
            if (inv.pp2 > 0) Dialogs.row(this, body, "Potencia P2", Fmt.dec(inv.pp2, 6) + " €/kW·día");
            if (inv.ps > 0) Dialogs.row(this, body, "Excedentes", Fmt.dec(inv.ps, 3) + " €/kWh");
            if (inv.bono > 0) Dialogs.row(this, body, "Bono social", Fmt.dec(inv.bono, 6) + " €/día");
            if (inv.rent > 0) Dialogs.row(this, body, "Contador", Fmt.dec(inv.rent, 6) + " €/día");
            if (dosPrecios) {
                Dialogs.note(this, body, "Esta factura tiene el mes partido por un cambio de "
                        + "precio: con el efectivo se reproduce su importe (la sección Cuadre), "
                        + "y con el vigente hoy se reprecia el año en el comparador.");
            }
            if (inv.priceFrom != null && !inv.priceFrom.isEmpty()) {
                Dialogs.note(this, body, "Los precios no venían en esta factura: son los de la del "
                        + shortDate(inv.priceFrom) + ".");
            }
        }

        // Cuadre de esta factura: lo que sale con sus precios frente a lo que dice ella.
        if (InvoiceCheck.hasPrices(inv) && InvoiceCheck.bill(inv) > 0) {
            double sim = InvoiceCheck.simTotal(inv);
            double bill = InvoiceCheck.bill(inv);
            double diff = BillData.round2(sim - bill);
            boolean ok = Math.abs(diff) <= InvoiceCheck.tolerance(bill);
            Dialogs.section(this, body, "Cuadre");
            Dialogs.row(this, body, "Con sus precios", Fmt.eur2(sim));
            Dialogs.row(this, body, inv.elec > 0 ? "Luz de la factura" : "Total de la factura",
                    Fmt.eur2(bill));
            Dialogs.row(this, body, "Diferencia", Fmt.eur2(diff),
                    getColor(ok ? R.color.accent : R.color.warn));
            if (!ok) {
                Dialogs.note(this, body, inv.elec <= 0
                        ? "De esta factura no se guardó la luz, solo el total a pagar (que puede "
                        + "llevar fianzas, depósitos o saldo de la batería), así que puede salir "
                        + "descuadrada aunque los precios sean buenos. Si vuelves a importar su "
                        + "PDF, se recalcula y solo se guarda si cuadra."
                        : "Esto es lo que sale con los precios que quedaron guardados. Suele ser "
                        + "que sus precios se arrastraron de otra factura o que la potencia y los "
                        + "días son los del QR y no los del PDF. Si vuelves a importar su PDF, se "
                        + "recalcula y solo se guarda si cuadra al céntimo.");
            }
        }

        Dialogs.section(this, body, "Archivo");
        Dialogs.row(this, body, "Origen", inv.origin == null || inv.origin.isEmpty() ? "—" : inv.origin);
        Dialogs.row(this, body, "PDF", saved ? "guardado en la app" : "no guardado",
                saved ? getColor(R.color.accent) : getColor(R.color.muted));
        if (!saved) {
            Dialogs.note(this, body, "Las facturas importadas antes de esta versión, y las de QR o a mano, "
                    + "no tienen PDF. Si vuelves a importar su PDF, queda enlazado.");
        }

        AlertDialog.Builder d = new AlertDialog.Builder(this)
                .setTitle("Factura del " + shortDate(inv.from))
                .setView(Dialogs.scroller(this, body, 440))
                .setNegativeButton("Cerrar", null);
        if (saved) {
            d.setPositiveButton("Abrir PDF", (x, w) -> openPdf(inv.pdf));
            d.setNeutralButton("Compartir", (x, w) -> sharePdf(inv.pdf));
        }
        d.show();
    }

    // ---------------- ofertas a mano ----------------

    /** El listado manual, con las mismas tarjetas y píldoras que el comparador. */
    private void showManualList() {
        startActivity(new Intent(this, ManualListActivity.class));
    }

    private void openPdf(String name) {
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Pdfs.uri(this, name), "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, "Abrir la factura"));
        } catch (Exception e) {
            toast("No hay ninguna app en el móvil para abrir PDFs.");
        }
    }

    private void sharePdf(String name) {
        Intent i = new Intent(Intent.ACTION_SEND)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, Pdfs.uri(this, name))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, "Compartir la factura"));
        } catch (Exception e) {
            toast("No he podido compartir el PDF.");
        }
    }

    /** La fila de precios por tramo: los enseña si están y deja ponerlos o quitarlos. */
    private void renderTramos(BillData b) {
        if (b.hasTramos()) {
            linkTramos.setText("Por tramos · P1 " + Fmt.dec(b.pe1, 4) + " · P2 " + Fmt.dec(b.pe2, 4)
                    + " · P3 " + Fmt.dec(b.pe3, 4) + " €/kWh ›");
            linkTramos.setTextColor(getColor(R.color.accent));
        } else if (b.pe > 0) {
            linkTramos.setText("Tu energía va a un solo precio. Si tiene tramos P1/P2/P3, ponlos aquí ›");
            linkTramos.setTextColor(getColor(R.color.muted));
        } else {
            linkTramos.setText("Precios por tramo P1/P2/P3 ›");
            linkTramos.setTextColor(getColor(R.color.muted));
        }
    }

    /**
     * Los tres precios de energía del 2.0TD. Se guardan en la última factura, como el resto
     * de precios; sin ellos la app usa el precio único de arriba, que es lo de siempre.
     */
    private void showTramos() {
        BillData b = store.load();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6), dp(4), dp(6), 0);

        int dec = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        final EditText e1 = field(box, "Energía P1 · punta (€/kWh)",
                b.pe1 > 0 ? Fmt.dec(b.pe1, 5) : "", dec);
        final EditText e2 = field(box, "Energía P2 · llano (€/kWh)",
                b.pe2 > 0 ? Fmt.dec(b.pe2, 5) : "", dec);
        final EditText e3 = field(box, "Energía P3 · valle (€/kWh)",
                b.pe3 > 0 ? Fmt.dec(b.pe3, 5) : "", dec);

        TextView note = new TextView(this);
        note.setText("Si tu tarifa cobra lo mismo a cualquier hora, deja los tres vacíos y se "
                + "usa el precio único. Si tiene tramos, hacen falta los tres: con ellos calculo "
                + "tu año cruzándolos con tu reparto P1/P2/P3 y el comparador sale más fino.");
        note.setTextColor(getColor(R.color.muted));
        note.setTextSize(12);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(dp(4), dp(12), dp(4), 0);
        Dialogs.fill(note);
        box.addView(note);

        new AlertDialog.Builder(this)
                .setTitle("Energía por tramos")
                .setView(Dialogs.scroller(this, box, 380))
                .setPositiveButton("Guardar", (d, w) -> {
                    double v1 = parseNumber(e1.getText().toString());
                    double v2 = parseNumber(e2.getText().toString());
                    double v3 = parseNumber(e3.getText().toString());
                    if (v1 <= 0 && v2 <= 0 && v3 <= 0) {
                        store.updateTramos(0, 0, 0);
                        render();
                        toast("Sin tramos: se usa el precio único.");
                        return;
                    }
                    if (v1 <= 0 || v2 <= 0 || v3 <= 0) {
                        toast("Hacen falta los tres precios, P1, P2 y P3.");
                        return;
                    }
                    store.updateTramos(v1, v2, v3);
                    render();
                    toast("Guardados los precios por tramo.");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Lo que aporta el QR: CP, CUPS, comercializadora y consumo anual. */
    private void renderSupply(BillData b) {
        Curve cv = store.curve();
        boolean any = !b.cp.isEmpty() || !b.cups.isEmpty() || b.kwhYear > 0 || cv.ok();
        if (!any) {
            supplyDetails.setText("Aún no los tengo: escanea el QR de una factura y los leo. El QR no se guarda como factura, solo aporta estos datos.");
            supplyDetails.setTextColor(getColor(R.color.muted));
            return;
        }
        StringBuilder s = new StringBuilder();
        if (!b.cp.isEmpty()) s.append("Código postal: ").append(b.cp).append('\n');
        if (!b.cups.isEmpty()) s.append("CUPS: ").append(b.cups).append('\n');
        String name = store.supplierName();
        if (name.isEmpty()) name = b.supplierCode;
        if (!name.isEmpty()) s.append("Comercializadora: ").append(name).append('\n');
        if (b.kwhYear > 0) {
            s.append("Consumo anual: ").append(Fmt.kwh(b.kwhYear));
            if (b.kwh1 + b.kwh2 + b.kwh3 > 0) {
                s.append(" (P1 ").append(Fmt.dec(b.kwh1, 0))
                 .append(" · P2 ").append(Fmt.dec(b.kwh2, 0))
                 .append(" · P3 ").append(Fmt.dec(b.kwh3, 0)).append(" kWh)");
            }
        }
        if (cv.ok()) {
            s.append('\n').append("Curva horaria: ").append(cv.days)
             .append(cv.days == 1 ? " día" : " días").append(" · P1 ")
             .append(pct(cv.share1())).append("% · P2 ").append(pct(cv.share2()))
             .append("% · P3 ").append(pct(cv.share3())).append('%');
            if (cv.maxP1 > 0) {
                s.append('\n').append("Máxima horaria: ").append(Fmt.dec(cv.maxP1, 2))
                 .append(" kW en punta · ").append(Fmt.dec(cv.maxP2, 2)).append(" kW en llano");
            }
        }
        supplyDetails.setText(s.toString().trim());
        supplyDetails.setTextColor(getColor(R.color.text));
    }

    /** Resumen de «Tu tarifa real»: reproduce (o no) el importe de tu última factura. */
    private void updatePricesView(BillData b) {
        boolean hasInvoice = b.from != null && !b.from.isEmpty();
        if (!b.hasPrices()) {
            priceSummary.setTextColor(getColor(R.color.muted));
            priceSummary.setText(hasInvoice
                    ? "Tu última factura (" + shortDate(b.from) + " → " + shortDate(b.to)
                      + ") no trae precios legibles. Importa su PDF otra vez o escríbelos aquí."
                    : "Aún no tengo los precios: importa el PDF de tu factura y los relleno solo. Si alguno no aparece, escríbelo aquí.");
            return;
        }

        BillData.Sim s = b.simulate();
        StringBuilder t = new StringBuilder();
        if (hasInvoice) {
            t.append("De tu factura del ").append(shortDate(b.from))
             .append(" al ").append(shortDate(b.to)).append('\n');
        }
        if (b.priceFrom != null && !b.priceFrom.isEmpty()) {
            t.append("Precios heredados: esta factura no los traía, son los de la del ")
             .append(shortDate(b.priceFrom)).append(" al ").append(shortDate(b.priceTo))
             .append(".\n");
        }
        if (b.peNow > 0 && b.pe > 0 && Math.abs(b.peNow - b.pe) > 0.00005) {
            t.append("Dos precios en esa factura: la reproduzco con el efectivo ")
             .append(Fmt.dec(b.pe, 4)).append(" y reprecia el año el vigente hoy ")
             .append(Fmt.dec(b.peNow, 4)).append(" €/kWh.\n");
        }
        t.append("Energía ").append(Fmt.eur2(s.energy))
         .append(" · Potencia ").append(Fmt.eur2(s.power));
        if (s.comp > 0) t.append(" · Excedentes −").append(Fmt.eur2(s.comp));
        t.append("\nImpuesto ").append(Fmt.eur2(s.iee));
        if (s.tax > 0) t.append(" · Mínimo comunitario ").append(Fmt.eur2(s.tax));
        if (s.rent > 0) t.append(" · Contador ").append(Fmt.eur2(s.rent));
        t.append(" · IVA ").append(Fmt.eur2(s.iva)).append('\n');

        double bill = b.invoiceElec > 0 ? b.invoiceElec : b.invoiceTotal;
        if (b.invoiceElec > 0 && b.invoiceTotal > 0
                && Math.abs(b.invoiceElec - b.invoiceTotal) > 0.02) {
            t.append("Luz ").append(Fmt.eur2(b.invoiceElec))
             .append("; con la batería virtual pagas ").append(Fmt.eur2(b.invoiceTotal))
             .append('\n');
        }

        if (bill > 0) {
            double diff = BillData.round2(s.total - bill);
            if (Math.abs(diff) <= 0.02) {
                t.append("Cuadra con tu factura: ").append(Fmt.eur2(s.total)).append('.');
                priceSummary.setTextColor(getColor(R.color.accent));
            } else {
                t.append("No cuadra: salen ").append(Fmt.eur2(s.total))
                 .append(" y la factura dice ").append(Fmt.eur2(bill))
                 .append(" (diferencia ").append(Fmt.eur2(diff))
                 .append("). Si la diferencia es pequeña, mira el bono social y el contador; si es grande, las potencias o los kWh del periodo.");
                priceSummary.setTextColor(getColor(R.color.danger));
            }
        } else {
            t.append("Con tus precios, ese periodo sale a ").append(Fmt.eur2(s.total)).append('.');
            priceSummary.setTextColor(getColor(R.color.accent));
        }
        priceSummary.setText(t.toString());
    }

    private void updateSurplus(BillData b) {
        double credit = b.annualSurplus();
        double price = b.surplusPrice();
        if (credit <= 0) {
            surplusSummary.setText("Sin excedentes: comparo solo lo que compras de la red.");
            surplusSummary.setTextColor(getColor(R.color.muted));
            return;
        }
        StringBuilder s = new StringBuilder();
        if (b.surplusFromBill()) {
            s.append("Cobrado: ").append(Fmt.eur(b.surplusEur)).append(" en ")
             .append(b.surplusDays()).append(" días\n");
        }
        if (price > 0) {
            s.append(Fmt.dec(price, 3)).append(" €/kWh");
            s.append(b.ps > 0 ? " (tu factura)" : " (a mano)");
            s.append('\n');
        }
        s.append(Fmt.eur(credit)).append(" al año sin IVA · ")
         .append(Fmt.eur(b.annualSurplusTaxed())).append(" con IVA");
        if (b.surplusKwhYear > 0) s.append(" (").append(Fmt.kwhDec(b.surplusKwhYear)).append(" al año)");
        if (store.solarMonths().size() >= SolarMonth.YEAR_MONTHS) {
            s.append("\nLos 12 meses de tus placas mandan sobre el kWh escrito a mano.");
        }
        surplusSummary.setText(s.toString());
        surplusSummary.setTextColor(getColor(R.color.accent));
    }

    // ---------------- facturas en PDF ----------------

    private void pickPdf() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(Intent.createChooser(i, "Elige las facturas en PDF"), REQ_PDF);
        } catch (Exception e) {
            toast("No he podido abrir el selector de archivos.");
        }
    }

    /** Diagnóstico: abre un PDF y enseña el texto que la app saca de él. No guarda nada. */
    private void pickPdfDiag() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/pdf");
            startActivityForResult(Intent.createChooser(i, "Elige un PDF"), REQ_DIAG);
        } catch (Exception e) {
            toast("No he podido abrir el selector de archivos.");
        }
    }

    private void inspectPdf(final Uri uri) {
        toast("Leyendo el PDF…");
        pool.execute(() -> {
            byte[] bytes = readAll(uri);
            final String text = bytes == null ? null : PdfReader.text(bytes);
            ui.post(() -> showPdfDiag(text));
        });
    }

    private void showPdfDiag(String raw) {
        final String txt = raw == null ? "" : raw;
        final String clean = txt.trim().replace("\r", "");
        BillData d = new BillData();
        if (!clean.isEmpty()) PdfPrices.fill(d, txt);

        StringBuilder head = new StringBuilder();
        if (clean.isEmpty()) {
            head.append("No hay texto que leer: o no he podido abrir el archivo, o el PDF es un escaneo ")
                .append("(una foto metida en un PDF) o está protegido.\n\n")
                .append("Si es un escaneo, el QR se puede leer de la imagen, pero los precios no: ")
                .append("hay que pedir la factura digital a la comercializadora.");
        } else {
            head.append("Texto leído: ").append(clean.length()).append(" caracteres.\n");
            head.append("Periodo: ").append(d.from.isEmpty() ? "no lo veo" : d.from)
                .append(" → ").append(d.to.isEmpty() ? "no lo veo" : d.to).append('\n');
            head.append("Consumo: ").append(d.billingKwh() > 0 ? Fmt.kwh(d.billingKwh()) : "no lo veo").append('\n');
            head.append("Total: ").append(d.invoiceTotal > 0 ? Fmt.eur(d.invoiceTotal) : "no lo veo").append('\n');
            head.append("Energía: ").append(d.pe > 0 ? Fmt.dec(d.pe, 6) + " €/kWh" : "no la encuentro").append('\n');
            head.append("Potencia: P1 ").append(d.pp1 > 0 ? Fmt.dec(d.pp1, 6) : "?")
                .append(" · P2 ").append(d.pp2 > 0 ? Fmt.dec(d.pp2, 6) : "?").append(" €/kW·día\n");
            head.append("Excedentes: ").append(d.ps > 0 ? Fmt.dec(d.ps, 6) + " €/kWh" : "no").append('\n');
            head.append("Bono social: ").append(d.bono > 0 ? Fmt.dec(d.bono, 6) : "no")
                .append(" · contador: ").append(d.rent > 0 ? Fmt.dec(d.rent, 6) : "no").append(" €/día\n");
            head.append("IEE ").append(Fmt.dec(d.iee, 6)).append(" % · IVA ").append(Fmt.dec(d.iva, 2)).append(" %");
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(10), dp(20), 0);
        TextView summary = new TextView(this);
        summary.setText(head.toString());
        summary.setTextSize(13);
        box.addView(summary);

        if (!clean.isEmpty()) {
            TextView body = new TextView(this);
            body.setText(clean.length() > 6000 ? clean.substring(0, 6000) + "\n…" : clean);
            body.setTextSize(11);
            body.setTypeface(Typeface.MONOSPACE);
            body.setTextIsSelectable(true);
            ScrollView sc = new ScrollView(this);
            sc.addView(body);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(300));
            lp.topMargin = dp(10);
            box.addView(sc, lp);
        }

        final String copy = clean.isEmpty() ? head.toString() : head + "\n---\n" + clean;
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Lo que leo del PDF")
                .setView(box)
                .setPositiveButton(clean.isEmpty() ? "Cerrar" : "Copiar texto", null)
                .setNeutralButton(clean.isEmpty() ? null : "Compartir", null)
                .setNegativeButton(clean.isEmpty() ? null : "Cerrar", null)
                .create();
        dlg.setOnShowListener(l -> {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (clean.isEmpty()) {
                    dlg.dismiss();
                    return;
                }
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Factura PDF", copy));
                    toast("Texto copiado: pégalo en el chat.");
                }
            });
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, "Texto leído del PDF");
                send.putExtra(Intent.EXTRA_TEXT, copy);
                startActivity(Intent.createChooser(send, "Compartir el texto"));
            });
        });
        dlg.show();
    }

    private void importPdfs(List<Uri> uris) {
        pendingPdfs.clear();
        toast(uris.size() == 1 ? "Leyendo el PDF…" : "Leyendo " + uris.size() + " PDFs…");
        pool.execute(() -> {
            final List<Invoice> found = new ArrayList<>();
            final List<String> mismatched = new ArrayList<>();
            BillData newestSupply = null;
            int failed = 0;
            for (Uri u : uris) {
                int mark = found.size();
                byte[] bytes = readAll(u);
                boolean ok = false;
                if (bytes != null) {
                    String pdfText = PdfReader.text(bytes);
                    for (String q : PdfQr.find(bytes, pdfText)) {
                        BillData bd = QrParser.parse(q);
                        if (bd != null && bd.billingKwh() > 0) {
                            PdfPrices.fill(bd, pdfText);
                            String why = keepPricesIfTheyMatch(bd, pdfText);
                            if (why != null) mismatched.add(label(bd) + " → " + why);
                            found.add(Invoice.of(bd, "PDF"));
                            if (bd.to != null && !bd.to.isEmpty()
                                    && (newestSupply == null || bd.to.compareTo(newestSupply.to) > 0)) {
                                newestSupply = bd;
                            }
                            ok = true;
                            break;
                        }
                    }
                    // Sin QR legible: si el texto del PDF trae periodo, consumo y precios, sirve igual.
                    if (!ok) {
                        BillData txt = new BillData();
                        boolean got = PdfPrices.fill(txt, pdfText);
                        if (got && !txt.from.isEmpty() && !txt.to.isEmpty() && txt.billingKwh() > 0) {
                            String why = keepPricesIfTheyMatch(txt, pdfText);
                            if (why != null) mismatched.add(label(txt) + " → " + why);
                            found.add(Invoice.of(txt, "PDF"));
                            if (newestSupply == null || txt.to.compareTo(newestSupply.to) > 0) {
                                newestSupply = txt;
                            }
                            ok = true;
                        }
                    }
                }
                if (!ok) failed++;
                // La copia se queda en memoria: al disco solo si se confirma la importación.
                if (ok && bytes != null) {
                    for (int i = mark; i < found.size(); i++) {
                        Invoice inv = found.get(i);
                        String name = Pdfs.nameFor(inv.from, inv.to);
                        inv.pdf = name;
                        pendingPdfs.put(name, bytes);
                    }
                }
            }
            final int nf = failed;
            // El suministro sale del QR que va dentro del PDF (si lo trae); la más reciente manda.
            if (newestSupply != null && !newestSupply.cp.isEmpty()) store.saveSupply(newestSupply);
            ui.post(() -> showImport(found, nf, mismatched));
        });
    }

    /**
     * Los precios del PDF solo valen si reproducen el total de esa factura. Si no
     * cuadran, se descartan (no se guardan) y se devuelve el motivo para avisar.
     *
     * La comprobación se hace con lo que dice el PDF. El QR describe el suministro, no
     * la factura: si uno de sus datos (potencia, kWh, días) no es el de este mes, la
     * simulación se dispara y se rechazarían unos precios que sí son buenos. Del QR
     * solo se toma lo que el PDF no traiga.
     */
    private static String keepPricesIfTheyMatch(BillData b, String text) {
        boolean any = b.pe > 0 || b.pp1 > 0 || b.pp2 > 0 || b.ps > 0 || b.bono > 0 || b.rent > 0;
        if (!any) return null;   // el PDF no traía precios: no hay nada que comprobar

        BillData c = new BillData();
        PdfPrices.fill(c, text);
        if (c.p1 <= 0) c.p1 = b.p1;
        if (c.p2 <= 0) c.p2 = b.p2;
        if (c.billed <= 0) c.billed = b.billed;
        if (c.billKwh1 + c.billKwh2 + c.billKwh3 <= 0) {
            c.billKwh1 = b.billKwh1;
            c.billKwh2 = b.billKwh2;
            c.billKwh3 = b.billKwh3;
        }
        if (c.invoiceDays <= 0) c.invoiceDays = b.invoiceDays;
        if (c.invoiceElec <= 0) c.invoiceElec = b.invoiceElec;
        if (c.invoiceTotal <= 0) c.invoiceTotal = b.invoiceTotal;
        if (c.surplusEur <= 0) c.surplusEur = b.surplus;

        // Se compara con la luz de la factura, antes de la batería virtual: el «Total a
        // pagar» puede ser menor porque tira del saldo acumulado y no es un precio.
        double bill = c.invoiceElec > 0 ? c.invoiceElec : c.invoiceTotal;
        String why;
        if (bill <= 0) {
            why = "la factura no trae el total";
        } else if (c.billingKwh() <= 0) {
            why = "no trae los kWh del periodo";
        } else if (c.invoiceDays <= 0) {
            why = "no trae el periodo";
        } else if (c.p1 <= 0 && c.p2 <= 0) {
            why = "no trae la potencia contratada";
        } else {
            BillData.Sim sim = c.simulate();
            double diff = Math.abs(sim.total - bill);
            double tol = Math.max(0.10, Math.abs(bill) * 0.01);
            if (diff <= tol) {
                // Los datos del PDF mandan también para lo que se guarda.
                if (c.billed > 0) b.billed = c.billed;
                if (c.p1 > 0) b.p1 = c.p1;
                if (c.p2 > 0) b.p2 = c.p2;
                if (c.invoiceDays > 0) b.invoiceDays = c.invoiceDays;
                if (c.invoiceElec > 0) b.invoiceElec = c.invoiceElec;
                if (c.surplus > 0) {
                    b.surplus = c.surplus;
                    b.surplusEur = c.surplus;
                }
                return null;
            }
            why = "con esos precios salen " + Fmt.eur(sim.total) + " y la factura dice "
                    + Fmt.eur(bill) + " (" + Fmt.kwh(c.billingKwh()) + " × "
                    + Fmt.dec(c.pe, 4) + " €/kWh, potencia " + Fmt.eur2(sim.power)
                    + " en " + sim.days + " días)";
        }
        b.pe = 0;
        b.pe1 = 0;
        b.pe2 = 0;
        b.pe3 = 0;
        b.peNow = 0;
        b.pp1 = 0;
        b.pp2 = 0;
        b.ps = 0;
        b.bono = 0;
        b.rent = 0;
        return why;
    }

    private static String label(BillData b) {
        String f = b.from == null || b.from.isEmpty() ? "?" : shortDate(b.from);
        String t = b.to == null || b.to.isEmpty() ? "?" : shortDate(b.to);
        return f + " → " + t;
    }

    private void showImport(final List<Invoice> found, final int failed, final List<String> mismatched) {
        if (found.isEmpty()) {
            LinearLayout body = Dialogs.body(this);
            body.addView(Dialogs.banner(this,
                    "En esos PDFs no está el QR de la CNMC, o está dibujado de una forma que no puedo leer.",
                    R.drawable.bg_banner, getColor(R.color.warn)));
            Dialogs.note(this, body, "Truco que siempre funciona: abre el PDF en el móvil y usa "
                    + "«Escanear otra» enfocando el QR de la pantalla; también vale una foto o una captura.");
            new AlertDialog.Builder(this)
                    .setTitle("No encuentro el QR oficial")
                    .setView(body)
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }
        // ¿Alguna de las nuevas pisa un periodo que ya tenemos guardado?
        List<Invoice> saved = store.invoices();
        final List<Invoice> clashes = new ArrayList<>();
        for (Invoice n : found) {
            for (Invoice s : saved) {
                if (overlaps(n, s) && !same(clashes, s)) clashes.add(s);
            }
        }

        LinearLayout body = Dialogs.body(this);
        Dialogs.section(this, body, "Facturas leídas");
        for (Invoice i : found) {
            String value = Fmt.kwh(i.kwh()) + " · " + Fmt.eur(i.total);
            if (i.surplus > 0) value += " · exc. " + Fmt.eur2(i.surplus);
            Dialogs.row(this, body, shortDate(i.from) + " → " + shortDate(i.to), value);
        }
        if (failed > 0) {
            Dialogs.note(this, body, failed == 1
                    ? "1 PDF sin QR legible."
                    : failed + " PDFs sin QR legible.");
        }

        Invoice withPrices = null;
        for (Invoice i : found) if (i.pe > 0) { withPrices = i; break; }
        if (withPrices != null) {
            Dialogs.section(this, body, "Tu tarifa");
            Dialogs.row(this, body, "Energía", Fmt.dec(withPrices.pe, 4) + " €/kWh");
            if (withPrices.pp1 > 0) {
                Dialogs.row(this, body, "Potencia P1", Fmt.dec(withPrices.pp1, 6) + " €/kW·día");
            }
            if (withPrices.pp2 > 0) {
                Dialogs.row(this, body, "Potencia P2", Fmt.dec(withPrices.pp2, 6) + " €/kW·día");
            }
            if (withPrices.ps > 0) {
                Dialogs.row(this, body, "Excedentes", Fmt.dec(withPrices.ps, 3) + " €/kWh");
            }
        } else {
            Dialogs.note(this, body, "No he podido leer los precios de la tarifa. Con «Añadir factura → "
                    + "Ver lo que leo de un PDF» ves y copias el texto de la factura. Mientras tanto, "
                    + "los precios se escriben en «Tu tarifa real».");
        }

        if (!mismatched.isEmpty()) {
            Dialogs.section(this, body, "No guardo los precios");
            Dialogs.note(this, body, "No cuadran con el total de su factura, así que la tarifa sigue "
                    + "como estaba.");
            for (String m : mismatched) Dialogs.note(this, body, "· " + m);
        }

        if (clashes.isEmpty()) {
            Dialogs.note(this, body, "Se guardan para calcular tu consumo real por tramos.");
            new AlertDialog.Builder(this)
                    .setTitle(title(found.size()))
                    .setView(Dialogs.scroller(this, body, 460))
                    .setPositiveButton("Guardar", (d, w) -> saveImport(found, new ArrayList<>()))
                    .setNegativeButton("Cancelar", (d, w) -> pendingPdfs.clear())
                    .setOnCancelListener(d -> pendingPdfs.clear())
                    .show();
            return;
        }

        Dialogs.section(this, body, "Se solapa con lo que ya tienes");
        for (Invoice s : clashes) {
            Dialogs.row(this, body, shortDate(s.from) + " → " + shortDate(s.to), Fmt.kwh(s.kwh()));
        }
        Dialogs.note(this, body, "Si la añado igual, esos días contarían dos veces y el año saldría "
                + "inflado. «Sustituir» quita la que ya tienes y deja esta.");

        new AlertDialog.Builder(this)
                .setTitle(title(found.size()))
                .setView(Dialogs.scroller(this, body, 460))
                .setPositiveButton("Sustituir", (d, w) -> saveImport(found, clashes))
                .setNeutralButton("Añadir igual", (d, w) -> saveImport(found, new ArrayList<>()))
                .setNegativeButton("Cancelar", (d, w) -> pendingPdfs.clear())
                .setOnCancelListener(d -> pendingPdfs.clear())
                .show();
    }

    private String title(int n) {
        return n == 1 ? "He leído 1 factura" : "He leído " + n + " facturas";
    }

    private void saveImport(List<Invoice> found, List<Invoice> clashes) {
        // Si el PDF no trae precios, no se pierden los que ya tenías: se heredan.
        BillData cur = store.load();
        for (Invoice n : found) {
            if (n.pe <= 0 && cur.pe > 0) {
                n.pe = cur.pe;
                n.peNow = cur.peNow;
                n.pp1 = cur.pp1;
                n.pp2 = cur.pp2;
                n.ps = cur.ps;
                n.bono = cur.bono;
                n.rent = cur.rent;
                n.iee = cur.iee;
                n.iva = cur.iva;
            }
        }
        // Primero se van las que se sustituyen (con su PDF) y luego se escribe la copia
        // nueva: si no, al borrar la vieja se llevaría por delante el mismo nombre.
        for (Invoice c : clashes) store.removeInvoice(c);
        for (Invoice n : found) {
            byte[] bytes = n.pdf == null || n.pdf.isEmpty() ? null : pendingPdfs.remove(n.pdf);
            if (bytes != null) Pdfs.save(this, n.pdf, bytes);
        }
        store.addInvoices(found);
        if (!clashes.isEmpty()) store.setHistoryOpen(true);
        render();
        toast(clashes.isEmpty()
                ? "Guardadas " + found.size() + (found.size() == 1 ? " factura" : " facturas")
                : "Sustituidas " + clashes.size() + " · guardadas " + found.size());
    }

    private static boolean same(List<Invoice> l, Invoice x) {
        for (Invoice i : l) if (i.key().equals(x.key())) return true;
        return false;
    }

    /**
     * ¿Se pisan los dos periodos? Las fechas van en yyyy-MM-dd, así que se comparan como texto.
     * Es estricto para no marcar como solape dos facturas seguidas que comparten el día de corte.
     */
    private static boolean overlaps(Invoice a, Invoice b) {
        if (a.key().equals(b.key())) return true;
        String af = a.from, at = a.to, bf = b.from, bt = b.to;
        if (af.isEmpty() || at.isEmpty() || bf.isEmpty() || bt.isEmpty()) return false;
        if (af.compareTo(at) > 0) { String t = af; af = at; at = t; }
        if (bf.compareTo(bt) > 0) { String t = bf; bf = bt; bt = t; }
        return af.compareTo(bt) < 0 && bf.compareTo(at) < 0;
    }

    private byte[] readAll(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                o.write(buf, 0, n);
                total += n;
                if (total > 25L * 1024 * 1024) break;
            }
            in.close();
            return o.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * El QR no es una factura: solo aporta CP, CUPS, comercializadora y consumo anual.
     * No entra en el historial.
     */
    private void applySupply(BillData bd) {
        if ((bd.cp == null || bd.cp.isEmpty()) && (bd.cups == null || bd.cups.isEmpty())) {
            toast("Ese QR no trae datos de suministro.");
            return;
        }
        store.saveSupply(bd);
        render();
        toast("Datos de suministro actualizados. Para la factura, importa el PDF.");
    }

    /**
     * Guarda la factura como actual y, si trae periodo y consumo, también en el historial.
     * Así escanear y subir el PDF acaban en el mismo sitio.
     */
    private void saveBill(final BillData bd, final String origin) {
        keepUserExtras(bd);
        if (bd.from.isEmpty() || bd.to.isEmpty()) {
            // Un QR sin fechas se guarda como los últimos N días que indique la factura.
            int days = bd.invoiceDays > 0 ? bd.invoiceDays : 30;
            java.time.LocalDate today = java.time.LocalDate.now();
            bd.from = today.minusDays(days).toString();
            bd.to = today.toString();
        }
        if (bd.billingKwh() <= 0 && bd.invoiceTotal <= 0) {
            // sin consumo ni importe no hay factura que guardar
            render();
            toast("No hay datos de factura que guardar");
            return;
        }
        final Invoice inv = Invoice.of(bd, origin);
        final List<Invoice> clashes = overlapsFor(inv, store.invoices());
        if (clashes.isEmpty()) {
            commitBill(bd, inv, new ArrayList<>());
            return;
        }
        LinearLayout body = Dialogs.body(this);
        Dialogs.section(this, body, "Se solapa con");
        for (Invoice s : clashes) {
            Dialogs.row(this, body, shortDate(s.from) + " → " + shortDate(s.to), Fmt.kwh(s.kwh()));
        }
        Dialogs.note(this, body, "Si la añado igual, esos días contarían dos veces y el año saldría "
                + "inflado. «Sustituir» quita la que ya tienes y deja esta.");
        new AlertDialog.Builder(this)
                .setTitle("Ya tienes una factura de ese periodo")
                .setView(body)
                .setPositiveButton("Sustituir", (d, w) -> commitBill(bd, inv, clashes))
                .setNeutralButton("Añadir igual", (d, w) -> commitBill(bd, inv, new ArrayList<>()))
                .setNegativeButton("Cancelar", (d, w) -> {
                    store.save(bd);
                    render();
                })
                .show();
    }

    private void commitBill(BillData bd, Invoice inv, List<Invoice> clashes) {
        store.save(bd);
        if (inv.pe <= 0) {
            BillData cur = store.load();
            if (cur.pe > 0) {
                inv.pe = cur.pe;
                inv.peNow = cur.peNow;
                inv.pp1 = cur.pp1;
                inv.pp2 = cur.pp2;
                inv.ps = cur.ps;
                inv.bono = cur.bono;
                inv.rent = cur.rent;
                inv.iee = cur.iee;
                inv.iva = cur.iva;
                // No son precios de esta factura: queda anotado de cuál vienen.
                inv.priceFrom = cur.priceFrom.isEmpty() ? cur.from : cur.priceFrom;
                inv.priceTo = cur.priceTo.isEmpty() ? cur.to : cur.priceTo;
            }
        }
        for (Invoice c : clashes) store.removeInvoice(c);
        List<Invoice> one = new ArrayList<>();
        one.add(inv);
        store.addInvoices(one);
        render();
        toast(clashes.isEmpty() ? "Factura guardada en el historial" : "Factura sustituida");
    }

    private static List<Invoice> overlapsFor(Invoice n, List<Invoice> saved) {
        List<Invoice> out = new ArrayList<>();
        for (Invoice s : saved) if (overlaps(n, s) && !same(out, s)) out.add(s);
        return out;
    }

    /** Al leer una factura nueva conservamos lo que no viene en el QR (placas, excedentes, batería). */
    private void keepUserExtras(BillData bd) {
        BillData prev = store.load();
        bd.autoKwh = prev.autoKwh;
        bd.surplusKwhYear = prev.surplusKwhYear;
        bd.surplusPriceManual = prev.surplusPriceManual;
        bd.battery = prev.battery;
        bd.surplusEur = bd.surplus > 0 ? bd.surplus : prev.surplusEur;
    }

    // ---------------- factura ----------------

    private void openScanner() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        startActivityForResult(new Intent(this, ScanActivity.class), REQ_LIVE);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_CAMERA) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(new Intent(this, ScanActivity.class), REQ_LIVE);
            } else {
                toast("Sin cámara puedes usar una foto de la galería o meter los datos a mano.");
            }
        }
    }

    /** Un solo botón para todo: cámara, foto, PDF, enlace o a mano. */
    /** Menú de opciones con filas anchas y su flecha: se toca sin apuntar. */
    private void showAddMenu() {
        final String[] opts = {
                "Escanear el QR (datos de suministro)",
                "Foto o captura del QR",
                "Importar PDFs (facturas)",
                "Pegar el enlace del QR",
                "Ver lo que leo de un PDF",
                "Meter la factura a mano"
        };
        LinearLayout body = Dialogs.body(this);
        final AlertDialog[] holder = new AlertDialog[1];
        for (int i = 0; i < opts.length; i++) {
            final int which = i;
            LinearLayout opt = Dialogs.option(this, opts[i]);
            opt.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                if (which == 0) openScanner();
                else if (which == 1) showPhotoOptions();
                else if (which == 2) pickPdf();
                else if (which == 3) pasteLink();
                else if (which == 4) pickPdfDiag();
                else showManual();
            });
            if (i > 0) body.addView(Dialogs.divider(this));
            body.addView(opt);
        }
        holder[0] = new AlertDialog.Builder(this)
                .setTitle("Añadir factura")
                .setView(Dialogs.scroller(this, body, 430))
                .setNegativeButton("Cancelar", null)
                .create();
        holder[0].show();
    }

    private void showPhotoOptions() {
        final String[] opts = {"Hacer foto de la factura", "Elegir una foto o captura de la galería"};
        LinearLayout body = Dialogs.body(this);
        final AlertDialog[] holder = new AlertDialog[1];
        for (int i = 0; i < opts.length; i++) {
            final int which = i;
            LinearLayout opt = Dialogs.option(this, opts[i]);
            opt.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                if (which == 0) takePhoto();
                else pickPhoto();
            });
            if (i > 0) body.addView(Dialogs.divider(this));
            body.addView(opt);
        }
        holder[0] = new AlertDialog.Builder(this)
                .setTitle("Foto del QR")
                .setView(body)
                .setNegativeButton("Cancelar", null)
                .create();
        holder[0].show();
    }

    private void takePhoto() {
        try {
            File dir = new File(getCacheDir(), "images");
            if (!dir.exists()) dir.mkdirs();
            pendingPhoto = new File(dir, "factura_" + System.currentTimeMillis() + ".jpg");
            Uri out = ImageProvider.uriFor(this, pendingPhoto);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, out);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAM);
        } catch (Exception e) {
            toast("No he podido abrir la cámara. Prueba con «Elegir una foto» o mételo a mano.");
        }
    }

    private void pickPhoto() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "Elige la foto de la factura"), REQ_GAL);
        } catch (Exception e) {
            toast("No he podido abrir la galería.");
        }
    }

    private void pasteLink() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null
                || cm.getPrimaryClip().getItemCount() == 0) {
            toast("Copia antes el enlace: abre la cámara del móvil y enfoca el QR, o mantén pulsado el enlace de la factura.");
            return;
        }
        CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        BillData bd = QrParser.parse(t == null ? "" : t.toString());
        if (bd == null) {
            toast("Ese texto no es el enlace del QR de la CNMC.");
            return;
        }
        applySupply(bd);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) return;
        if (req == REQ_BACKUP) {
            render();
            return;
        }
        if (req == REQ_CAM && pendingPhoto != null && pendingPhoto.exists()) {
            processImage(Uri.fromFile(pendingPhoto));
        } else if (req == REQ_GAL && data != null && data.getData() != null) {
            processImage(data.getData());
        } else if (req == REQ_DIAG) {
            if (data != null && data.getData() != null) inspectPdf(data.getData());
        } else if (req == REQ_PDF) {
            List<Uri> uris = new ArrayList<>();
            if (data != null) {
                ClipData cd = data.getClipData();
                if (cd != null) {
                    for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
                } else if (data.getData() != null) {
                    uris.add(data.getData());
                }
            }
            if (!uris.isEmpty()) importPdfs(uris);
        } else if (req == REQ_LIVE && data != null) {
            BillData bd = QrParser.parse(data.getStringExtra(ScanActivity.EXTRA_QR));
            if (bd != null) {
                applySupply(bd);
            }
        }
    }

    private void processImage(Uri uri) {
        toast("Leyendo la factura…");
        final Uri u = uri;
        pool.execute(() -> {
            Bitmap bmp = loadBitmap(u);
            String text = bmp == null ? null : QrDecoder.decode(bmp);
            BillData bd = text == null ? null : QrParser.parse(text);
            final boolean foundOtherQr = text != null && bd == null;
            ui.post(() -> {
                if (bd != null) {
                    applySupply(bd);
                } else if (foundOtherQr) {
                    new AlertDialog.Builder(this)
                            .setTitle("Ese QR no es el de la CNMC")
                            .setMessage("He visto un QR, pero no lleva los datos del comparador. Busca el que dice «Comparador de ofertas de energía» o introduce los datos a mano.")
                            .setPositiveButton("A mano", (d, w) -> showManual())
                            .setNegativeButton("Cerrar", null)
                            .show();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("No veo el QR")
                            .setMessage("Prueba con una foto más nítida, con el QR bien centrado y sin sombras. También puedes meter los datos a mano.")
                            .setPositiveButton("A mano", (d, w) -> showManual())
                            .setNegativeButton("Cerrar", null)
                            .show();
                }
            });
        });
    }

    private Bitmap loadBitmap(Uri uri) {
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, probe);
            if (in != null) in.close();

            int sample = 1;
            int side = Math.max(probe.outWidth, probe.outHeight);
            while (side / sample > 2400) sample *= 2;

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            InputStream in2 = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in2, null, o);
            if (in2 != null) in2.close();
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- autoconsumo mes a mes ----------------

    private void renderSolar(List<SolarMonth> months) {
        solarLink.setVisibility(months.isEmpty() ? View.GONE : View.VISIBLE);
        if (months.isEmpty()) {
            solarSummary.setText("Añade los meses del inversor (autoconsumo y vertido a la red) y sumo yo el año. Con 12 meses uso esas sumas para comparar.");
            solarSummary.setTextColor(getColor(R.color.muted));
            return;
        }
        List<SolarMonth> year = SolarMonth.lastYear(months);
        double self = SolarMonth.sumSelf(year);
        double exp = SolarMonth.sumExport(year);
        StringBuilder s = new StringBuilder();
        s.append(year.size()).append(year.size() == 1 ? " mes" : " meses")
                .append(" · autoconsumo ").append(Fmt.kwhDec(self))
                .append(" · a la red ").append(Fmt.kwhDec(exp));
        if (months.size() > SolarMonth.YEAR_MONTHS) {
            s.append("\nUso los 12 meses más recientes (")
                    .append(year.get(year.size() - 1).label()).append(" → ")
                    .append(year.get(0).label()).append("): los más viejos no suman.");
        }
        if (year.size() >= SolarMonth.YEAR_MONTHS) {
            s.append("\nAño completo: estas son las cifras que uso para comparar.");
            solarSummary.setTextColor(getColor(R.color.accent));
        } else {
            s.append("\nParcial: con 12 meses lo usaré para comparar. Toca aquí para ver o borrar meses.");
            solarSummary.setTextColor(getColor(R.color.muted));
        }
        solarSummary.setText(s.toString());
    }

    private void addSolarMonth() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6), dp(4), dp(6), 0);

        // Por defecto, el mes siguiente al último guardado: para meterlos seguidos.
        final List<SolarMonth> saved = store.solarMonths();   // más reciente primero
        String def = saved.isEmpty()
                ? java.time.LocalDate.now().minusMonths(1).toString().substring(0, 7)
                : SolarMonth.nextMonth(saved.get(0).month);
        String today = java.time.LocalDate.now().toString().substring(0, 7);
        if (def.compareTo(today) > 0) def = today;   // no ofrece meses que no han pasado
        int defYear = Integer.parseInt(def.substring(0, 4));
        int defMonth = Integer.parseInt(def.substring(5, 7));

        TextView monthTitle = new TextView(this);
        monthTitle.setText("Mes y año");
        monthTitle.setTextColor(getColor(R.color.muted));
        monthTitle.setTextSize(12);
        monthTitle.setPadding(dp(4), dp(10), dp(4), dp(6));
        box.addView(monthTitle);

        String[] names = new String[12];
        for (int i = 0; i < 12; i++) names[i] = SolarMonth.monthName(i + 1);
        Spinner spMonth = new Spinner(this);
        spMonth.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names));
        spMonth.setSelection(defMonth - 1);

        int curYear = java.time.LocalDate.now().getYear();
        int firstYear = Math.min(defYear, curYear) - 6;
        for (SolarMonth m : saved) {
            int y = Integer.parseInt(m.month.substring(0, 4));
            if (y < firstYear) firstYear = y;
        }
        int lastYear = Math.max(defYear, curYear);
        String[] years = new String[lastYear - firstYear + 1];
        for (int i = 0; i < years.length; i++) years[i] = String.valueOf(firstYear + i);
        Spinner spYear = new Spinner(this);
        spYear.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, years));
        spYear.setSelection(defYear - firstYear);

        LinearLayout pick = new LinearLayout(this);
        pick.setOrientation(LinearLayout.HORIZONTAL);
        pick.addView(spMonth, new LinearLayout.LayoutParams(0, dp(50), 1.6f));
        pick.addView(spYear, new LinearLayout.LayoutParams(0, dp(50), 1f));
        box.addView(pick);

        EditText eSelf = field(box, "Autoconsumo: de las placas a la casa (kWh)", "", num());
        EditText eExp = field(box, "Vertido a la red (kWh)", "", num());

        // La producción total no se pregunta: es autoconsumo + vertido.
        TextView prodView = new TextView(this);
        prodView.setTextSize(12);
        prodView.setTextColor(getColor(R.color.accent));
        prodView.setPadding(dp(4), dp(10), dp(4), 0);
        prodView.setText("Producción total: se suma sola (autoconsumo + vertido a la red)");
        box.addView(prodView);
        TextWatcher sum = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                double tot = parseNumber(eSelf.getText().toString())
                        + parseNumber(eExp.getText().toString());
                prodView.setText("Producción total: " + Fmt.kwhDec(tot)
                        + " (autoconsumo + vertido a la red)");
            }
        };
        eSelf.addTextChangedListener(sum);
        eExp.addTextChangedListener(sum);

        EditText eGrid = field(box, "Consumo de la red (kWh, opcional)", "", num());

        TextView help = new TextView(this);
        help.setText("En la app del inversor, «autoconsumo» es lo que usas de las placas y «a la red» lo que viertes; la producción total la sumo yo. Si repites un mes, lo sustituyo. Los kWh se guardan con sus decimales (317,65).");
        help.setTextColor(getColor(R.color.muted));
        help.setTextSize(12);
        help.setPadding(dp(4), dp(10), dp(4), 0);
        Dialogs.fill(help);
        box.addView(help);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Mes del inversor")
                .setView(scroll)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String m = String.format(java.util.Locale.ROOT, "%04d-%02d",
                    Integer.parseInt((String) spYear.getSelectedItem()),
                    spMonth.getSelectedItemPosition() + 1);
            double self = parseNumber(eSelf.getText().toString());
            double exp = parseNumber(eExp.getText().toString());
            double grid = parseNumber(eGrid.getText().toString());
            if (self <= 0 && exp <= 0) {
                toast("Falta el autoconsumo o lo vertido a la red.");
                return;
            }
            store.saveSolarMonth(SolarMonth.of(m, self, exp, self + exp, grid));
            d.dismiss();
            render();
            toast("Guardado " + SolarMonth.monthLabel(m));
        });
    }

    private void showSolarMonths() {
        final List<SolarMonth> months = store.solarMonths();
        if (months.isEmpty()) {
            addSolarMonth();
            return;
        }
        List<SolarMonth> year = SolarMonth.lastYear(months);
        double prod = 0;
        for (SolarMonth m : year) prod += m.productionKwh;

        LinearLayout box = Dialogs.body(this);
        Dialogs.section(this, box, "Totales · " + year.size() + (year.size() == 1 ? " mes" : " meses"));
        Dialogs.row(this, box, "Autoconsumo", Fmt.kwhDec(SolarMonth.sumSelf(year)));
        Dialogs.row(this, box, "Vertido a la red", Fmt.kwhDec(SolarMonth.sumExport(year)));
        Dialogs.row(this, box, "Producción", Fmt.kwhDec(Math.round(prod * 100) / 100.0));
        if (months.size() > year.size()) {
            Dialogs.note(this, box, "Hay " + (months.size() - year.size())
                    + " meses más antiguos: para comparar uso solo los 12 más recientes.");
        }
        Dialogs.note(this, box, "Toca un mes para borrarlo.");
        Dialogs.section(this, box, "Mes a mes");

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (SolarMonth m : months) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(12), 0, dp(2));
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setOnClickListener(v -> confirmRemoveMonth(m));

            TextView title = new TextView(this);
            title.setText(m.label());
            title.setTextColor(getColor(R.color.text));
            title.setTextSize(14);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            row.addView(title);

            StringBuilder t = new StringBuilder();
            t.append("Autoconsumo ").append(Fmt.kwhDec(m.selfKwh))
             .append(" · a la red ").append(Fmt.kwhDec(m.exportKwh))
             .append(" · producción ").append(Fmt.kwhDec(m.productionKwh));
            if (m.gridKwh > 0) t.append(" · red ").append(Fmt.kwhDec(m.gridKwh));

            TextView detail = new TextView(this);
            detail.setText(t.toString());
            detail.setTextColor(getColor(R.color.muted));
            detail.setTextSize(12);
            detail.setLineSpacing(dp(3), 1f);
            row.addView(detail);
            list.addView(row);
        }

        box.addView(Dialogs.scroller(this, list, 400));

        new AlertDialog.Builder(this)
                .setTitle("Tus meses del inversor")
                .setView(box)
                .setPositiveButton("Añadir mes", (d, w) -> addSolarMonth())
                .setNegativeButton("Cerrar", null)
                .show();
    }

    /** Una fila por mes, con lo de la factura y lo del inversor juntos. */
    private static class MonthRow {
        final String month;
        double kwh, eur, exc;
        String from = "", to = "";
        boolean bills;
        SolarMonth solar;

        MonthRow(String month) {
            this.month = month;
        }
    }

    private void showMonths() {
        java.util.TreeMap<String, MonthRow> map = new java.util.TreeMap<>();
        for (Invoice i : store.invoices()) {
            if (i.to == null || i.to.length() < 7) continue;
            String key = i.to.substring(0, 7);
            MonthRow r = map.get(key);
            if (r == null) {
                r = new MonthRow(key);
                map.put(key, r);
            }
            r.bills = true;
            r.kwh += i.kwh();
            r.eur += i.elec > 0 ? i.elec : i.total;
            r.exc += i.surplus;
            if (r.from.isEmpty() || i.from.compareTo(r.from) < 0) r.from = i.from;
            if (r.to.isEmpty() || i.to.compareTo(r.to) > 0) r.to = i.to;
        }
        for (SolarMonth m : store.solarMonths()) {
            MonthRow r = map.get(m.month);
            if (r == null) {
                r = new MonthRow(m.month);
                map.put(m.month, r);
            }
            r.solar = m;
        }
        if (map.isEmpty()) {
            toast("Aún no hay facturas ni meses que enseñar.");
            return;
        }

        List<MonthRow> rows = new ArrayList<>(map.values());
        rows.sort((a, b) -> b.month.compareTo(a.month));

        LinearLayout box = Dialogs.body(this);

        TextView note = new TextView(this);
        note.setText("Cada factura cuenta en el mes en el que termina su periodo: si va del 3 al 2, es el mes del 2.");
        note.setTextSize(12);
        note.setTextColor(getColor(R.color.muted));
        note.setLineSpacing(dp(3), 1f);
        Dialogs.fill(note);
        box.addView(note);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (MonthRow r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(12), 0, dp(2));

            TextView title = new TextView(this);
            title.setText(SolarMonth.monthLabel(r.month));
            title.setTextColor(getColor(R.color.text));
            title.setTextSize(14);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            row.addView(title);

            StringBuilder t = new StringBuilder();
            if (r.bills) {
                t.append("Factura ").append(shortDate(r.from)).append(" → ").append(shortDate(r.to))
                 .append(": ").append(Fmt.kwh(Math.round(r.kwh)))
                 .append(" · ").append(Fmt.eur2(r.eur));
                if (r.exc > 0) t.append(" · excedentes ").append(Fmt.eur2(r.exc));
            } else {
                t.append("Sin factura de ese mes");
            }
            t.append('\n');
            if (r.solar != null) {
                t.append("Placas: autoconsumo ").append(Fmt.kwhDec(r.solar.selfKwh))
                 .append(" · a la red ").append(Fmt.kwhDec(r.solar.exportKwh))
                 .append(" · producción ").append(Fmt.kwhDec(r.solar.productionKwh));
                if (r.solar.gridKwh > 0) {
                    t.append(" · red ").append(Fmt.kwhDec(r.solar.gridKwh));
                }
            } else {
                t.append("Sin datos del inversor");
            }

            TextView detail = new TextView(this);
            detail.setText(t.toString());
            detail.setTextColor(getColor(R.color.muted));
            detail.setTextSize(12);
            detail.setLineSpacing(dp(3), 1f);
            row.addView(detail);
            list.addView(row);
        }

        box.addView(Dialogs.scroller(this, list, 400));

        new AlertDialog.Builder(this)
                .setTitle(rows.size() == 1 ? "Mes a mes · 1 mes" : "Mes a mes · " + rows.size() + " meses")
                .setView(box)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void confirmRemoveMonth(final SolarMonth m) {
        LinearLayout body = Dialogs.body(this);
        Dialogs.row(this, body, "Autoconsumo", Fmt.kwhDec(m.selfKwh));
        Dialogs.row(this, body, "Vertido a la red", Fmt.kwhDec(m.exportKwh));
        if (m.productionKwh > 0) Dialogs.row(this, body, "Producción", Fmt.kwhDec(m.productionKwh));
        Dialogs.note(this, body, "Se quita de la suma del año.");

        new AlertDialog.Builder(this)
                .setTitle("Borrar " + m.label())
                .setView(body)
                .setPositiveButton("Borrar", (d, w) -> {
                    store.removeSolarMonth(m.month);
                    render();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showManual() {
        BillData cur = store.load();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6), dp(4), dp(6), 0);

        EditText eCp = field(box, "Código postal", cur.cp, InputType.TYPE_CLASS_NUMBER);
        EditText eP1 = field(box, "Potencia P1 (kW)", v(cur.p1), num());
        EditText eP2 = field(box, "Potencia P2 (kW)", v(cur.p2), num());
        EditText eYear = field(box, "Consumo de los últimos 12 meses (kWh)", v(cur.kwhYear), InputType.TYPE_CLASS_NUMBER);
        EditText eK1 = field(box, "Consumo P1 (kWh)", v(cur.kwh1), InputType.TYPE_CLASS_NUMBER);
        EditText eK2 = field(box, "Consumo P2 (kWh)", v(cur.kwh2), InputType.TYPE_CLASS_NUMBER);
        EditText eK3 = field(box, "Consumo P3 (kWh)", v(cur.kwh3), InputType.TYPE_CLASS_NUMBER);
        EditText eImp = field(box, "Importe de tu última factura (€)", v(cur.invoiceTotal), num());
        EditText eDays = field(box, "Días que cubre esa factura", v(cur.invoiceDays), InputType.TYPE_CLASS_NUMBER);

        TextView help = new TextView(this);
        help.setText("En potencias e importes puedes escribir con coma o con punto (4,6 o 4.6). Los kWh por tramo son opcionales: si no los sabes, reparto el consumo a partes 40/30/30.");
        help.setTextColor(getColor(R.color.muted));
        help.setTextSize(12);
        help.setPadding(dp(4), dp(10), dp(4), 0);
        Dialogs.fill(help);
        box.addView(help);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Tus datos")
                .setView(scroll)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            BillData bd = new BillData();
            bd.cp = eCp.getText().toString().replaceAll("[^0-9]", "").trim();
            bd.p1 = parseNumber(eP1.getText().toString());
            bd.p2 = parseNumber(eP2.getText().toString());
            bd.kwhYear = (int) Math.round(parseNumber(eYear.getText().toString()));
            bd.kwh1 = (int) Math.round(parseNumber(eK1.getText().toString()));
            bd.kwh2 = (int) Math.round(parseNumber(eK2.getText().toString()));
            bd.kwh3 = (int) Math.round(parseNumber(eK3.getText().toString()));
            bd.invoiceTotal = parseNumber(eImp.getText().toString());
            bd.invoiceDays = (int) Math.round(parseNumber(eDays.getText().toString()));
            if (bd.invoiceDays <= 0) bd.invoiceDays = 30;
            if (bd.cp.length() != 5) {
                toast("El código postal debe tener 5 cifras.");
                return;
            }
            if (bd.kwhYear <= 0) {
                toast("Falta el consumo anual (kWh).");
                return;
            }
            if (bd.kwh1 + bd.kwh2 + bd.kwh3 <= 0) {
                bd.kwh1 = (int) Math.round(bd.kwhYear * 0.40);
                bd.kwh2 = (int) Math.round(bd.kwhYear * 0.30);
                bd.kwh3 = bd.kwhYear - bd.kwh1 - bd.kwh2;
            } else {
                int sum = bd.kwh1 + bd.kwh2 + bd.kwh3;
                if (sum > bd.kwhYear * 1.5) {
                    // los tramos no cuadran con el consumo anual: reescalamos
                    double f = bd.kwhYear / (double) sum;
                    bd.kwh1 = (int) Math.round(bd.kwh1 * f);
                    bd.kwh2 = (int) Math.round(bd.kwh2 * f);
                    bd.kwh3 = bd.kwhYear - bd.kwh1 - bd.kwh2;
                }
            }
            d.dismiss();
            saveBill(bd, "Manual");
        });
    }

    private EditText field(LinearLayout parent, String label, String value, int inputType) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(getColor(R.color.muted));
        t.setTextSize(12);
        t.setPadding(dp(4), dp(12), dp(4), dp(6));
        parent.addView(t);

        EditText e = new EditText(this);
        e.setInputType(inputType);
        boolean decimals = (inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0;
        if ((inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER) {
            e.setKeyListener(DigitsKeyListener.getInstance(decimals ? "0123456789.," : "0123456789"));
        }
        e.setText(value == null ? "" : value);
        e.setBackground(getDrawable(R.drawable.bg_input));
        e.setSingleLine(true);
        e.setTextColor(getColor(R.color.text));
        e.setTextSize(15);
        e.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        parent.addView(e, lp);
        return e;
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Borrar tus datos")
                .setMessage("Se borrarán la factura y los ajustes guardados en este móvil.")
                .setPositiveButton("Borrar", (d, w) -> {
                    store.clearBill();
                    store.clearInvoices();
                    store.setAutoKwh(0);
                    store.setAutoOn(false);
                    render();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------------- curva horaria de Datadis ----------------

    /** Si la última descarga está guardada, se revisa sin conectarse; si no, se pide la cuenta. */
    private void connectDatadis() {
        Datadis.Result saved = store.datadisResult();
        if (saved != null) {
            showDatadisMonths(saved, true);
            return;
        }
        askDatadis();
    }

    private void askDatadis() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        final EditText nif = new EditText(this);
        nif.setHint("NIF o NIE de Datadis");
        nif.setSingleLine(true);
        nif.setText(store.datadisNif());
        nif.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        final EditText pass = new EditText(this);
        pass.setHint("Contraseña");
        pass.setSingleLine(true);
        pass.setText(store.datadisPass());
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        box.addView(nif);
        box.addView(pass);

        TextView note = new TextView(this);
        note.setText("Los mismos datos con los que entras en datadis.es. Se guardan en este "
                + "móvil para no teclearlos cada vez; si cambian, se escriben encima.\n\n"
                + "Se descarga el consumo horario de los últimos 12 meses cerrados, y Datadis "
                + "no deja repetir la misma consulta hasta que pasen 24 horas.");
        note.setTextSize(12);
        note.setTextColor(getColor(R.color.muted));
        note.setPadding(0, dp(10), 0, 0);
        box.addView(note);

        new AlertDialog.Builder(this)
                .setTitle("Curva de consumo desde Datadis")
                .setView(box)
                .setPositiveButton("Entrar", (d, w) -> startDatadis(
                        nif.getText().toString().trim(), pass.getText().toString()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void startDatadis(final String nif, final String pass) {
        if (nif.isEmpty() || pass.isEmpty()) {
            toast("Hacen falta el NIF y la contraseña de Datadis.");
            return;
        }
        toast("Entrando en Datadis…");
        pool.execute(() -> {
            try {
                String token = Datadis.token(nif, pass);
                // Si Datadis los ha aceptado, se quedan guardados para la próxima.
                store.saveDatadisLogin(nif, pass);
                final List<Datadis.Supply> all = Datadis.supplies(token);
                ui.post(() -> pickDatadisSupply(token, all));
            } catch (final Exception e) {
                ui.post(() -> datadisError(e));
            }
        });
    }

    private void pickDatadisSupply(final String token, final List<Datadis.Supply> all) {
        if (all.size() == 1) {
            fetchDatadis(token, all.get(0));
            return;
        }
        LinearLayout body = Dialogs.body(this);
        Dialogs.note(this, body, "En Datadis hay " + all.size()
                + " suministros a tu nombre. Toca el de esta casa:");
        for (int i = 0; i < all.size(); i++) {
            final Datadis.Supply s = all.get(i);
            LinearLayout opt = Dialogs.option(this,
                    s.address.isEmpty() ? s.cups : s.address + "\n" + s.cups);
            opt.setOnClickListener(v -> fetchDatadis(token, s));
            body.addView(opt);
        }
        Dialogs.width(new AlertDialog.Builder(this)
                .setTitle("¿De qué suministro?")
                .setView(Dialogs.scroller(this, body, 380))
                .setNegativeButton("Cancelar", null)
                .show());
    }

    private void fetchDatadis(final String token, final Datadis.Supply s) {
        toast("Descargando la curva de 12 meses… puede tardar un poco.");
        pool.execute(() -> {
            try {
                final Datadis.Result r = Datadis.curve(token, s, 12);
                ui.post(() -> {
                    // La curva sí se guarda: es lo que hace falta para comparar.
                    store.saveCurve(r.curve);
                    store.saveDatadisResult(r);
                    render();
                    if (r.hasSolar()) {
                        showDatadisMonths(r, false);
                    } else {
                        // Autoconsumo y vertido a cero: la distribuidora no los publica.
                        showCurvaLeida(r.curve);
                    }
                });
            } catch (final Exception e) {
                ui.post(() -> datadisError(e));
            }
        });
    }

    private void datadisError(Exception e) {
        String msg = e.getMessage();
        LinearLayout body = Dialogs.body(this);
        body.addView(Dialogs.banner(this,
                msg == null || msg.isEmpty() ? "Fallo de conexión con Datadis." : msg,
                R.drawable.bg_banner, getColor(R.color.warn)));
        Dialogs.note(this, body, "Vuelve a probar más tarde; si sigue igual, entra en datadis.es "
                + "y comprueba que la cuenta y el suministro están bien.");
        Dialogs.width(new AlertDialog.Builder(this)
                .setTitle("No he podido leer la curva")
                .setView(body)
                .setPositiveButton("Entendido", null)
                .show());
    }

    /**
     * Lo que Datadis sabe de las placas, al lado de lo que ya hay. No se guarda ningún mes
     * hasta que se confirma: el vertido mueve el descuento de excedentes de cada oferta.
     */
    private void showDatadisMonths(final Datadis.Result r, final boolean allowDownload) {
        BillData b = store.load();
        List<SolarMonth> saved = store.solarMonths();
        int replace = 0;
        for (SolarMonth m : r.months) {
            for (SolarMonth old : saved) {
                if (old.month.equals(m.month)) {
                    replace++;
                    break;
                }
            }
        }

        // Si no hay curva en el resultado es porque viene de la descarga guardada.
        boolean fresh = r.curve.ok();
        Curve cv = fresh ? r.curve : store.curve();

        LinearLayout body = Dialogs.body(this);
        if (!fresh) {
            String when = "";
            if (r.at > 0) {
                java.time.LocalDate day = java.time.Instant.ofEpochMilli(r.at)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                when = " Del " + shortDate(day.toString()) + ".";
            }
            body.addView(Dialogs.banner(this,
                    "Es de la última descarga guardada: no he preguntado a Datadis." + when,
                    R.drawable.bg_pill_mute, getColor(R.color.muted)));
        }

        Dialogs.section(this, body, "La curva");
        Dialogs.row(this, body, "Lecturas",
                cv.ok() ? cv.days + (cv.days == 1 ? " día" : " días") : "—");
        if (cv.ok()) {
            Dialogs.row(this, body, "Reparto", "P1 " + pct(cv.share1()) + "% · P2 "
                    + pct(cv.share2()) + "% · P3 " + pct(cv.share3()) + "%");
        }

        Dialogs.section(this, body, "Datadis · " + r.months.size()
                + (r.months.size() == 1 ? " mes" : " meses"));
        Dialogs.row(this, body, "Autoconsumo", Fmt.kwhDec(round2(r.self)) + " (no lo ve)",
                getColor(R.color.muted));
        Dialogs.row(this, body, "Vertido a la red", Fmt.kwhDec(round2(r.exported)));
        Dialogs.row(this, body, "Producción", Fmt.kwhDec(round2(r.production))
                + " (= el vertido)", getColor(R.color.muted));

        Dialogs.section(this, body, "Lo que ya tienes");
        if (b.autoKwh > 0 || b.surplusKwhYear > 0) {
            Dialogs.row(this, body, "Autoconsumo",
                    b.autoKwh > 0 ? Fmt.kwhDec(b.autoKwh) : "sin dato");
            Dialogs.row(this, body, "Vertido a la red",
                    b.surplusKwhYear > 0 ? Fmt.kwhDec(b.surplusKwhYear) : "sin dato");
        } else {
            Dialogs.row(this, body, "Guardado", "nada metido todavía", getColor(R.color.muted));
        }
        if (!saved.isEmpty()) {
            Dialogs.row(this, body, "Meses del inversor", saved.size()
                    + (replace > 0 ? " · " + replace + " cambian su vertido" : ""));
        }

        Dialogs.note(this, body, "La curva ya está guardada. Al guardar los meses solo se cambia el "
                + "vertido de cada uno (el de Datadis, que es el que se paga): el autoconsumo del "
                + "inversor se queda como está, porque no pasa por el contador y Datadis no lo ve.");

        AlertDialog.Builder box = new AlertDialog.Builder(this)
                .setTitle(fresh ? "Datadis también trae tu autoconsumo"
                        : "Lo que dijo Datadis la última vez")
                .setView(Dialogs.scroller(this, body, 430))
                .setPositiveButton("Guardar los meses", (d, w) -> {
                    // Solo el vertido: el autoconsumo del inversor no se toca.
                    for (SolarMonth m : r.months) store.saveVertidoMonth(m);
                    render();
                    toast("Vertido actualizado en " + r.months.size()
                            + " meses (autoconsumo intacto).");
                })
                .setNegativeButton("No tocar nada", null);
        if (allowDownload) box.setNeutralButton("Actualizar", (d, w) -> actualizarDatadis());
        Dialogs.width(box.show());
    }

    /**
     * «Actualizar»: Datadis no admite la misma consulta hasta que pasen 24 horas, así que si
     * la última descarga es de hace menos de un día se avisa en vez de pedirla para nada.
     */
    private void actualizarDatadis() {
        final Datadis.Result r = store.datadisResult();
        final long at = r == null ? 0 : r.at;
        final long now = System.currentTimeMillis();
        final long day = 24L * 60 * 60 * 1000;
        if (at <= 0 || now - at >= day) {
            askDatadis();
            return;
        }

        long left = day - (now - at);
        long hours = Math.max(1, Math.round(left / 3600000.0));

        LinearLayout body = Dialogs.body(this);
        Dialogs.row(this, body, "Descargado", quando(at));
        Dialogs.row(this, body, "Podrás actualizar en", hours == 1 ? "1 hora" : hours + " horas");
        Dialogs.note(this, body, "Datadis no deja repetir la misma consulta hasta 24 horas "
                + "después, así que ahora mismo te daría un aviso de «espera un rato». La curva "
                + "y los meses que ya tienes siguen ahí y se pueden usar tal cual.");

        Dialogs.width(new AlertDialog.Builder(this)
                .setTitle("La curva es de hace poco")
                .setView(body)
                .setPositiveButton("Consultar igualmente", (d, w) -> askDatadis())
                .setNegativeButton("Dejarlo así", null)
                .show());
    }

    /** «hoy a las 13:24», «ayer a las 20:05» o «el 22/11/25 a las 20:05». */
    private static String quando(long millis) {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalDate day = java.time.Instant.ofEpochMilli(millis)
                .atZone(zone).toLocalDate();
        java.time.LocalDate today = java.time.LocalDate.now();
        String when;
        if (day.equals(today)) when = "hoy";
        else if (day.equals(today.minusDays(1))) when = "ayer";
        else when = "el " + shortDate(day.toString());
        return when + " a las " + clock(millis);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ---------------- PVPC de ESIOS ----------------

    /**
     * PVPC de los últimos 12 meses cruzado con el reparto real de la curva. El precio del
     * PVPC es el término de energía: sirve para comparar la energía, no la potencia.
     */
    private void showPvpc() {
        final Pvpc p = store.pvpc();
        if (p == null) {
            askEsios();
            return;
        }
        BillData b = store.load();

        LinearLayout body = Dialogs.body(this);
        Dialogs.section(this, body, "PVPC 2.0TD · Península");
        Dialogs.row(this, body, "Periodo", shortDate(p.from) + " → " + shortDate(p.to));
        Dialogs.row(this, body, "Horas leídas", String.valueOf(p.hours));
        Dialogs.row(this, body, "Precio medio", Fmt.dec(p.all, 4) + " €/kWh");
        Dialogs.row(this, body, "Media P1", Fmt.dec(p.p1, 4) + " €/kWh");
        Dialogs.row(this, body, "Media P2", Fmt.dec(p.p2, 4) + " €/kWh");
        Dialogs.row(this, body, "Media P3", Fmt.dec(p.p3, 4) + " €/kWh");

        if (b.kwhYear > 0) {
            double pvpc = p.energyCostTaxed(b);
            Dialogs.section(this, body, "Término de energía al año");
            Dialogs.row(this, body, "Tus kWh", Fmt.kwh(b.kwhYear));
            if (b.kwh1 + b.kwh2 + b.kwh3 > 0) {
                Dialogs.row(this, body, "Reparto P1 · P2 · P3",
                        b.kwh1 + " · " + b.kwh2 + " · " + b.kwh3 + " kWh");
            }
            Dialogs.row(this, body, "Con PVPC", Fmt.eur(pvpc));
            if (b.pe > 0) {
                double mine = b.energyCostYear() * BillData.TAX;
                Dialogs.row(this, body, "Con tu tarifa", Fmt.eur(mine));
                double diff = mine - pvpc;
                String txt = diff >= 0
                        ? "Con PVPC pagarías " + Fmt.eur(diff) + " menos al año por la energía."
                        : "Con PVPC pagarías " + Fmt.eur(-diff) + " más al año por la energía.";
                body.addView(Dialogs.banner(this, txt, R.drawable.bg_banner,
                        getColor(diff >= 0 ? R.color.accent : R.color.warn)));
            }
        }

        if (p.surplus > 0 && b.surplusKwhYear > 0) {
            Dialogs.section(this, body, "Excedentes");
            Dialogs.row(this, body, "El PVPC paga (media)", Fmt.dec(p.surplus, 4) + " €/kWh");
            if (b.surplusPrice() > 0) {
                Dialogs.row(this, body, "Tu precio", Fmt.dec(b.surplusPrice(), 4) + " €/kWh");
            }
        }

        Dialogs.note(this, body, "Es el término de energía del PVPC: ya lleva peajes y cargos de "
                + "energía, pero no el término de potencia ni el alquiler del contador, que van "
                + "aparte. La media de cada tramo se cruza con tu reparto real de la curva.");
        Dialogs.note(this, body, store.esiosToken().isEmpty()
                ? "La próxima vez te pediré el token otra vez."
                : "Token guardado en este móvil.");

        new AlertDialog.Builder(this)
                .setTitle("PVPC con tu curva")
                .setView(Dialogs.scroller(this, body, 460))
                .setPositiveButton("Actualizar", (d, w) -> actualizarPvpc())
                .setNegativeButton("Cerrar", null)
                .show();
    }

    /**
     * «Actualizar»: si lo que se está mirando es de hoy, no se vuelve a preguntar a ESIOS.
     * El PVPC se publica con un día de antelación y la ventana son meses ya cerrados, así que
     * repetir la consulta el mismo día solo gasta dos peticiones del límite para nada.
     */
    private void actualizarPvpc() {
        final Pvpc p = store.pvpc();
        if (p == null || !sameDay(p.at, System.currentTimeMillis())) {
            askEsios();
            return;
        }

        LinearLayout body = Dialogs.body(this);
        Dialogs.row(this, body, "Consultado", "hoy a las " + clock(p.at));
        Dialogs.note(this, body, "ESIOS publica el PVPC con un día de antelación y la ventana "
                + "son 12 meses ya cerrados: pedirlo otra vez hoy devolvería los mismos números "
                + "y gastaría dos consultas de tu límite.");
        Dialogs.note(this, body, "Mañana, al abrirlo y pulsar «Actualizar», se pide solo.");

        new AlertDialog.Builder(this)
                .setTitle("Los datos son de hoy")
                .setView(body)
                .setPositiveButton("Consultar otra vez", (d, w) -> askEsios())
                .setNegativeButton("Dejarlo así", null)
                .show();
    }

    /** ¿Los dos instantes caen en el mismo día del calendario del móvil? */
    private static boolean sameDay(long a, long b) {
        if (a <= 0) return false;
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        return java.time.Instant.ofEpochMilli(a).atZone(zone).toLocalDate()
                .equals(java.time.Instant.ofEpochMilli(b).atZone(zone).toLocalDate());
    }

    /** Hora del último consulta, para enseñarla tal cual («13:24»). */
    private static String clock(long millis) {
        if (millis <= 0) return "—";
        return java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** El token se guarda en el móvil cuando ESIOS lo acepta; aquí se rellena solo. */
    private void askEsios() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6), dp(4), dp(6), 0);

        EditText token = field(box, "Token de ESIOS", store.esiosToken(), InputType.TYPE_CLASS_TEXT);
        token.setHint("pégalo aquí");

        TextView note = new TextView(this);
        note.setText("Se pide en esios.ree.es: tu perfil → «Solicitud de token» (llega por correo). "
                + "Se guarda en este móvil para no pegarlo cada vez; si cambia, se escribe encima.");
        note.setTextColor(getColor(R.color.muted));
        note.setTextSize(12);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(dp(4), dp(10), dp(4), 0);
        box.addView(note);

        new AlertDialog.Builder(this)
                .setTitle("PVPC desde ESIOS")
                .setView(Dialogs.scroller(this, box, 320))
                .setPositiveButton("Consultar", (d, w) -> {
                    String t = token.getText().toString().trim();
                    if (t.isEmpty()) {
                        toast("Falta el token de ESIOS.");
                        return;
                    }
                    fetchPvpc(t);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void fetchPvpc(final String token) {
        // Los 12 meses ya cerrados, igual que la curva.
        final java.time.LocalDate last = java.time.LocalDate.now().withDayOfMonth(1).minusDays(1);
        final java.time.LocalDate first = last.minusMonths(11).withDayOfMonth(1);
        toast("Consultando el PVPC de 12 meses en ESIOS…");
        pool.execute(() -> {
            try {
                final Esios.Prices pr = Esios.fetch(token, first, last);
                final Pvpc p = Pvpc.of(pr);
                p.at = System.currentTimeMillis();
                ui.post(() -> {
                    store.saveEsiosToken(token);
                    store.savePvpc(p);
                    toast("PVPC guardado: " + p.hours + " horas.");
                    showPvpc();
                });
            } catch (final Exception e) {
                ui.post(() -> esiosError(e));
            }
        });
    }

    private void esiosError(Exception e) {
        String msg = e.getMessage();
        LinearLayout body = Dialogs.body(this);
        body.addView(Dialogs.banner(this,
                msg == null || msg.isEmpty() ? "Fallo de conexión con ESIOS." : msg,
                R.drawable.bg_banner, getColor(R.color.warn)));
        Dialogs.note(this, body, "Mira que el token esté completo y sin caducar, y que el móvil "
                + "tenga internet. ESIOS también corta si se le piden muchas consultas seguidas.");
        new AlertDialog.Builder(this)
                .setTitle("No he podido leer el PVPC")
                .setView(body)
                .setPositiveButton("Entendido", null)
                .show();
    }

    /** Lo que ha llegado de la curva cuando la distribuidora no publica el autoconsumo. */
    private void showCurvaLeida(Curve c) {
        LinearLayout body = Dialogs.body(this);

        Dialogs.section(this, body, "La curva");
        Dialogs.row(this, body, "Lecturas", c.days + (c.days == 1 ? " día" : " días"));
        Dialogs.row(this, body, "Consumo", Fmt.kwhDec(c.kwh));
        if (!c.from.isEmpty()) {
            Dialogs.row(this, body, "Del al", shortDate(c.from) + " → " + shortDate(c.to));
        }
        Dialogs.section(this, body, "Reparto 2.0TD");
        Dialogs.row(this, body, "P1 · P2 · P3", pct(c.share1()) + "% · " + pct(c.share2())
                + "% · " + pct(c.share3()) + "%");
        if (c.maxP1 > 0) {
            Dialogs.section(this, body, "Máxima media horaria");
            Dialogs.row(this, body, "Punta", Fmt.dec(c.maxP1, 2) + " kW");
            Dialogs.row(this, body, "Llano", Fmt.dec(c.maxP2, 2) + " kW");
            Dialogs.row(this, body, "Valle", Fmt.dec(c.maxP3, 2) + " kW");
        }

        Dialogs.note(this, body, "Con esto el comparador reparte tu consumo anual como lo "
                + "gastas de verdad, que es lo que necesita para calcular las tarifas con hora "
                + "(el PVPC y las demás). Los festivos entre semana cuentan como laborables: la "
                + "curva no trae el calendario.");
        Dialogs.note(this, body, "De este suministro no vienen autoconsumo ni vertido: la "
                + "distribuidora no los publica. Los que tengas guardados no se tocan.");

        Dialogs.width(new AlertDialog.Builder(this)
                .setTitle("Curva de consumo leída")
                .setView(Dialogs.scroller(this, body, 420))
                .setPositiveButton("Entendido", null)
                .show());
    }

    private static int pct(double share) {
        return (int) Math.round(share * 100);
    }

    // ---------------- utilidades ----------------

    /**
     * El campo del autoconsumo: solo se puede escribir si el interruptor está encendido y
     * no hay 12 meses del inversor (entonces la cifra sale de la suma). Apagado se ve en
     * gris para que se note, pero conserva el número.
     */
    private void applyAutoState(boolean on) {
        // Los meses mandan cuando de verdad traen autoconsumo. Si Datadis guardó 12 meses
        // con el autoconsumo a cero (la distribuidora no lo publica), la cifra anual sigue
        // siendo la que se escribe a mano.
        List<SolarMonth> solar = store.solarMonths();
        boolean editable = on && !(solar.size() >= SolarMonth.YEAR_MONTHS
                && SolarMonth.sumSelf(solar) > 0);
        inAutoKwh.setEnabled(editable);
        // El color depende solo del interruptor: encendido siempre se lee en negro, aunque
        // con 12 meses el número venga de la suma y no se pueda escribir.
        inAutoKwh.setTextColor(getColor(on ? R.color.text : R.color.muted));
        inAutoKwh.setAlpha(on ? 1f : 0.5f);
        // Sin autoconsumo no hay excedentes que guardar: la batería virtual se apaga con él
        // (y se guarda apagada) y no se puede tocar hasta volver a encender las placas.
        swBattery.setEnabled(on);
        if (!on) {
            if (swBattery.isChecked()) swBattery.setChecked(false);
            BillData bd = store.load();
            if (bd.battery) {
                bd.battery = false;
                store.save(bd);
            }
        }
    }

    private int num() {
        return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    }

    private String v(double d) {
        if (d <= 0) return "";
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.valueOf(d).replace('.', ',');
    }

    private String v(int i) {
        return i > 0 ? String.valueOf(i) : "";
    }

    /** Lo escrito a mano: coma y punto valen igual como decimales (Fmt.parse). */
    private static double parseNumber(String s) {
        return Fmt.parse(s);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
    }
}
