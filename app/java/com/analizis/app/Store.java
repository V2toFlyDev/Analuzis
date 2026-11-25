package com.analizis.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Guarda en el propio móvil la factura y los ajustes. Sin nube. */
public class Store {

    private static final String FILE = "analizis";
    private final SharedPreferences sp;
    private final Context ctx;

    public Store(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        ctx = c.getApplicationContext();
    }

    // ---------- factura ----------

    /**
     * Perfil de suministro y factura actual, todo sacado del historial.
     * En preferencias solo viven los ajustes del usuario (autoconsumo, excedentes, batería).
     */
    public BillData load() {
        migrateOnce();
        BillData b = new BillData();
        b.autoKwh = getNum("autoKwh", 0);
        // Si no hay interruptor guardado (instalaciones antiguas), vale que el dato exista.
        b.autoOn = sp.getBoolean("autoOn", b.autoKwh > 0);
        b.surplusKwhYear = getNum("surplusKwhYear", 0);
        b.surplusPriceManual = sp.getFloat("surplusPrice", 0f);
        b.battery = sp.getBoolean("battery", false);

        // Suministro: lo que aporta el QR (no es una factura). Si aún no se ha
        // escaneado, se usa lo que traiga la factura más reciente.
        b.cp = sp.getString("cp", "");
        b.cups = sp.getString("cups", "");
        b.supplierCode = sp.getString("supplierCode", "");
        b.kwhYear = sp.getInt("kwhYear", 0);
        b.kwh1 = sp.getInt("kwh1", 0);
        b.kwh2 = sp.getInt("kwh2", 0);
        b.kwh3 = sp.getInt("kwh3", 0);

        // Autoconsumo mes a mes: con 12 meses, sus sumas mandan sobre lo escrito a mano.
        // La suma es siempre la de los 12 meses más recientes, aunque haya más guardados.
        List<SolarMonth> solar = solarMonths();
        if (solar.size() >= SolarMonth.YEAR_MONTHS) {
            double self = SolarMonth.sumSelf(solar);
            double exported = SolarMonth.sumExport(solar);
            if (self > 0) b.autoKwh = self;
            if (exported > 0) b.surplusKwhYear = exported;
        }

        List<Invoice> all = invoices();
        if (!all.isEmpty()) {
            all.sort(Invoice.byDate());
            if (b.cp.isEmpty() || b.kwhYear <= 0) {
                for (Invoice i : all) {
                    if (i.cp != null && !i.cp.isEmpty()) {
                        if (b.cp.isEmpty()) {
                            b.cp = i.cp;
                            b.cups = i.cups;
                            b.supplierCode = i.supplier;
                        }
                        if (b.kwhYear <= 0 && i.aYear > 0) {
                            b.kwhYear = i.aYear;
                            b.kwh1 = i.a1;
                            b.kwh2 = i.a2;
                            b.kwh3 = i.a3;
                        }
                        break;
                    }
                }
            }
            applyLatestInvoice(b, all);
        }

        // Con la curva horaria de Datadis, el reparto P1/P2/P3 del año es el suyo: la CNMC
        // lo usa para calcular las tarifas con hora, y sin él usa un perfil estándar.
        Curve cv = curve();
        if (cv.ok() && b.kwhYear > 0) {
            int k1 = (int) Math.round(b.kwhYear * cv.share1());
            int k2 = (int) Math.round(b.kwhYear * cv.share2());
            b.kwh1 = k1;
            b.kwh2 = k2;
            b.kwh3 = Math.max(0, b.kwhYear - k1 - k2);
        }
        return b;
    }

    /**
     * «Tu tarifa real» = solo la última factura por fecha: sus precios, su periodo,
     * sus kWh, sus potencias y su total. No se rellena con los precios de una factura
     * anterior: si la última no los trae, quedan a 0 y se dice que no los hay.
     */
    static void applyLatestInvoice(BillData b, List<Invoice> all) {
        Invoice last = Invoice.latest(all);
        if (last == null) return;
        b.pe = last.pe;
        b.pe1 = last.pe1;
        b.pe2 = last.pe2;
        b.pe3 = last.pe3;
        b.peNow = last.peNow;
        b.pp1 = last.pp1;
        b.pp2 = last.pp2;
        b.ps = last.ps;
        b.bono = last.bono;
        b.rent = last.rent;
        b.taxKwh = last.taxKwh;
        if (last.iee > 0) b.iee = last.iee;
        if (last.iva > 0) b.iva = last.iva;
        b.from = last.from;
        b.to = last.to;
        b.invoiceDays = last.days > 0 ? last.days : 30;
        b.invoiceTotal = last.total;
        b.surplus = last.surplus;
        b.surplusEur = last.surplus;
        b.billKwh1 = last.kwh1;
        b.billKwh2 = last.kwh2;
        b.billKwh3 = last.kwh3;
        b.billed = last.billed;
        b.invoiceElec = last.elec;
        if (last.p1 > 0) b.p1 = last.p1;
        if (last.p2 > 0) b.p2 = last.p2;
        b.priceFrom = last.priceFrom;
        b.priceTo = last.priceTo;
    }

    /**
     * Migraciones de una sola vez: la factura suelta antigua pasa al historial y el
     * suministro que quedó copiado en preferencias se mete en las facturas, para
     * borrar después esa copia. Sin historial no se borra nada.
     */
    private void migrateOnce() {
        boolean dirty = false;

        if (!sp.getBoolean("oneSource", false)) {
            int k1 = sp.getInt("billKwh1", 0), k2 = sp.getInt("billKwh2", 0), k3 = sp.getInt("billKwh3", 0);
            String f0 = sp.getString("from", "");
            String t0 = sp.getString("to", "");
            // Solo si aún no hay historial y la factura antigua tenía periodo: si ya hay
            // facturas, la actual es una de ellas y no hay que duplicarla.
            if (k1 + k2 + k3 > 0 && !f0.isEmpty() && !t0.isEmpty() && invoices().isEmpty()) {
                Invoice i = new Invoice();
                i.from = f0;
                i.to = t0;
                i.cp = sp.getString("cp", "");
                i.cups = sp.getString("cups", "");
                i.supplier = sp.getString("supplierCode", "");
                i.aYear = sp.getInt("kwhYear", 0);
                i.a1 = sp.getInt("kwh1", 0);
                i.a2 = sp.getInt("kwh2", 0);
                i.a3 = sp.getInt("kwh3", 0);
                i.days = sp.getInt("invoiceDays", 30);
                i.kwh1 = k1;
                i.kwh2 = k2;
                i.kwh3 = k3;
                i.p1 = sp.getFloat("p1", 0f);
                i.p2 = sp.getFloat("p2", 0f);
                i.total = sp.getFloat("invoiceTotal", 0f);
                i.surplus = sp.getFloat("surplusEur", 0f);
                if (i.surplus <= 0) i.surplus = sp.getFloat("surplus", 0f);
                i.origin = "QR";
                List<Invoice> one = new ArrayList<>();
                one.add(i);
                saveInvoices(one);
            }
            dirty = true;
        }

        if (!sp.getBoolean("historyOnly", false)) {
            String cp = sp.getString("cp", "");
            int yr = sp.getInt("kwhYear", 0);
            List<Invoice> all = invoices();
            boolean changed = false;
            for (Invoice i : all) {
                if (cp != null && !cp.isEmpty() && (i.cp == null || i.cp.isEmpty())) {
                    i.cp = cp;
                    i.cups = sp.getString("cups", "");
                    i.supplier = sp.getString("supplierCode", "");
                    changed = true;
                }
                if (yr > 0 && i.aYear <= 0) {
                    i.aYear = yr;
                    i.a1 = sp.getInt("kwh1", 0);
                    i.a2 = sp.getInt("kwh2", 0);
                    i.a3 = sp.getInt("kwh3", 0);
                    changed = true;
                }
                if (i.p1 <= 0) i.p1 = sp.getFloat("p1", 0f);
                if (i.p2 <= 0) i.p2 = sp.getFloat("p2", 0f);
            }
            if (changed) saveInvoices(all);
            dirty = true;
        }

        if (dirty) {
            sp.edit()
                    .putBoolean("oneSource", true)
                    .putBoolean("historyOnly", true)
                    .remove("cp").remove("cups").remove("supplierCode")
                    .remove("p1").remove("p2")
                    .remove("kwhYear").remove("kwh1").remove("kwh2").remove("kwh3")
                    .remove("billKwh1").remove("billKwh2").remove("billKwh3")
                    .remove("from").remove("to")
                    .remove("invoiceTotal").remove("invoiceDays")
                    .remove("surplus").remove("surplusEur").remove("surplusKwh")
                    .remove("savedAt")
                    .apply();
        }
    }

    /** El QR no es una factura: solo aporta los datos de suministro. */
    public void saveSupply(BillData b) {
        sp.edit()
                .putString("cp", b.cp == null ? "" : b.cp)
                .putString("cups", b.cups == null ? "" : b.cups)
                .putString("supplierCode", b.supplierCode == null ? "" : b.supplierCode)
                .putInt("kwhYear", b.kwhYear)
                .putInt("kwh1", b.kwh1)
                .putInt("kwh2", b.kwh2)
                .putInt("kwh3", b.kwh3)
                .apply();
    }

    public String supplierName() { return sp.getString("supplierName", ""); }

    // ---------- última comparativa buena ----------

    /** Lo guardado de la última consulta que salió bien, para poder enseñarlo si la CNMC falla. */
    public static class SavedOffers {
        public long at = 0;
        public double pvpcFixed = 0;
        public boolean pvpcSelfUse = false;
        public final List<Offer> list = new ArrayList<>();
        public final List<String> avisos = new ArrayList<>();
    }

    public void saveOffers(List<Offer> all, double pvpcFixed, boolean pvpcSelfUse,
                           List<String> avisos) {
        if (all == null || all.isEmpty()) return;
        try {
            JSONObject o = new JSONObject();
            o.put("at", System.currentTimeMillis());
            o.put("pvpcFixed", pvpcFixed);
            o.put("pvpcSelfUse", pvpcSelfUse);
            JSONArray a = new JSONArray();
            for (Offer f : all) a.put(f.toJson());
            o.put("ofertas", a);
            JSONArray av = new JSONArray();
            if (avisos != null) for (String s : avisos) av.put(s);
            o.put("avisos", av);
            sp.edit().putString("offersCache", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public SavedOffers savedOffers() {
        String s = sp.getString("offersCache", "");
        if (s == null || s.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(s);
            SavedOffers r = new SavedOffers();
            r.at = o.optLong("at", 0);
            r.pvpcFixed = o.optDouble("pvpcFixed", 0);
            r.pvpcSelfUse = o.optBoolean("pvpcSelfUse", false);
            JSONArray a = o.optJSONArray("ofertas");
            for (int i = 0; a != null && i < a.length(); i++) {
                Offer f = Offer.fromJson(a.optJSONObject(i));
                if (f.year1 > 0) r.list.add(f);
            }
            JSONArray av = o.optJSONArray("avisos");
            for (int i = 0; av != null && i < av.length(); i++) r.avisos.add(av.optString(i, ""));
            return r.list.isEmpty() ? null : r;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- precio de excedentes por oferta ----------

    /**
     * La oferta se identifica por comercializadora + nombre, **no** por el id de la CNMC:
     * ese cambia cada vez que revisan la oferta y se perdería el precio puesto a mano.
     */
    private static String surplusKey(Offer o) {
        String s = (o.supplier == null ? "" : o.supplier) + "|" + (o.name == null ? "" : o.name);
        return "surplus:" + s.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Lo que paga una oferta por tus excedentes (€/kWh sin impuestos), si lo has puesto tú. */
    public double offerSurplus(Offer o) {
        return o == null ? 0 : getNum(surplusKey(o), 0);
    }

    /** 0 borra el dato y vuelve a usar tu precio. */
    public void setOfferSurplus(Offer o, double price) {
        if (o == null) return;
        if (price > 0) sp.edit().putFloat(surplusKey(o), (float) price).apply();
        else sp.edit().remove(surplusKey(o)).apply();
    }

    // ---------- lo que dice la ficha de la CNMC, por oferta ----------

    /**
     * Misma clave que el precio manual (comercializadora + nombre, no el id, que cambia): así
     * lo que publique la CNMC sobrevive a que revisen la oferta y se puede refrescar aparte.
     */
    private static String cnmcKey(Offer o) {
        String s = (o.supplier == null ? "" : o.supplier) + "|" + (o.name == null ? "" : o.name);
        return "surplusCnmc:" + s.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Precio de excedentes que publica su ficha en la CNMC (0 si no lo dice). */
    public double cnmcSurplus(Offer o) {
        String[] v = parseCnmc(o);
        if (v[0].isEmpty()) return 0;
        try {
            return Double.parseDouble(v[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Si su ficha menciona batería virtual, wallet o monedero. */
    public boolean cnmcBattery(Offer o) {
        return "1".equals(parseCnmc(o)[1]);
    }

    /** Cuándo se miró la ficha (0 = nunca): sirve para no repetir la consulta cada día. */
    public long cnmcSurplusAt(Offer o) {
        try {
            return Long.parseLong(parseCnmc(o)[2]);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Guarda el resultado de mirar la ficha, aunque sea «no lo dice» (para no repetirlo). */
    public void setCnmcSurplus(Offer o, double price, boolean battery) {
        if (o == null) return;
        String v = String.format(java.util.Locale.ROOT, "%.6f", Math.max(0, price))
                + "|" + (battery ? 1 : 0) + "|" + System.currentTimeMillis();
        sp.edit().putString(cnmcKey(o), v).apply();
    }

    /** «precio|batería|cuándo», o vacío si no se ha mirado. */
    private String[] parseCnmc(Offer o) {
        String[] none = {"", "", ""};
        if (o == null) return none;
        String v = sp.getString(cnmcKey(o), "");
        if (v == null || v.isEmpty()) return none;
        String[] p = v.split("\\|");
        return p.length == 3 ? p : none;
    }

    // ---------- lo que dice la web de la comercializadora (cuando la ficha no lo dice) ----------

    /** Misma clave que la ficha: comercializadora + nombre, no el id de la CNMC. */
    private static String webKey(Offer o) {
        String s = (o.supplier == null ? "" : o.supplier) + "|" + (o.name == null ? "" : o.name);
        return "surplusWeb:" + s.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Precio de excedentes que publica la web de la comercializadora (0 si no lo dice). */
    public double webSurplus(Offer o) {
        String[] v = parseWeb(o);
        if (v[0].isEmpty()) return 0;
        try {
            return Double.parseDouble(v[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Si su web menciona batería virtual, wallet o monedero. */
    public boolean webBattery(Offer o) {
        return "1".equals(parseWeb(o)[1]);
    }

    /** Cuándo se miró su web (0 = nunca). */
    public long webSurplusAt(Offer o) {
        try {
            return Long.parseLong(parseWeb(o)[2]);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Si la página se llegó a leer (aunque no dijera el precio). */
    public boolean webRead(Offer o) {
        return "1".equals(parseWeb(o)[3]);
    }

    /**
     * Guarda lo que se vio en su web. `read` distingue «no lo dice» (vale una semana) de
     * «no se pudo abrir» (se vuelve a intentar al día siguiente).
     */
    public void setWebSurplus(Offer o, double price, boolean battery, boolean read) {
        if (o == null) return;
        String v = String.format(java.util.Locale.ROOT, "%.6f", Math.max(0, price))
                + "|" + (battery ? 1 : 0) + "|" + System.currentTimeMillis()
                + "|" + (read ? 1 : 0);
        sp.edit().putString(webKey(o), v).apply();
    }

    /** «precio|batería|cuándo|leída». */
    private String[] parseWeb(Offer o) {
        String[] none = {"", "", "", ""};
        if (o == null) return none;
        String v = sp.getString(webKey(o), "");
        if (v == null || v.isEmpty()) return none;
        String[] p = v.split("\\|");
        return p.length == 4 ? p : none;
    }

    // ---------- ofertas metidas a mano ----------

    /**
     * Ofertas con los precios de su web, guardadas tal cual. Tienen id propio: aunque la CNMC
     * cambie o retire su oferta, la tuya sigue donde la dejaste hasta que la borres.
     */
    public List<ManualOffer> manualOffers() {
        List<ManualOffer> out = new ArrayList<>();
        String s = sp.getString("manualOffers", "");
        if (s == null || s.isEmpty()) return out;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                ManualOffer m = ManualOffer.fromJson(a.optJSONObject(i));
                if (m.id > 0 && m.name != null && !m.name.isEmpty()) out.add(m);
            }
        } catch (Exception ignored) {
        }
        out.sort((x, y) -> Long.compare(y.updatedAt, x.updatedAt));
        return out;
    }

    public ManualOffer manualOffer(long id) {
        if (id <= 0) return null;
        for (ManualOffer m : manualOffers()) if (m.id == id) return m;
        return null;
    }

    /** Guarda (nueva o editada) y devuelve la entrada con su id y sus fechas ya puestas. */
    public ManualOffer saveManualOffer(ManualOffer m) {
        if (m == null) return null;
        List<ManualOffer> all = manualOffers();
        long now = System.currentTimeMillis();
        if (m.createdAt <= 0) m.createdAt = now;
        m.updatedAt = now;
        boolean done = false;
        for (int i = 0; i < all.size(); i++) {
            if (m.id > 0 && all.get(i).id == m.id) {
                all.set(i, m);
                done = true;
                break;
            }
        }
        if (!done) {
            long max = 0;
            for (ManualOffer x : all) max = Math.max(max, x.id);
            m.id = max + 1;
            all.add(m);
        }
        saveManual(all);
        return m;
    }

    public void deleteManualOffer(long id) {
        if (id <= 0) return;
        List<ManualOffer> all = manualOffers();
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).id == id) all.remove(i);
        saveManual(all);
    }

    private void saveManual(List<ManualOffer> all) {
        JSONArray a = new JSONArray();
        for (ManualOffer m : all) {
            try {
                a.put(m.toJson());
            } catch (Exception ignored) {
            }
        }
        // La fecha de cambio es lo que mira el ranking para volver a pintarse (onResume).
        sp.edit().putString("manualOffers", a.toString())
                .putLong("manualStamp", System.currentTimeMillis()).apply();
    }

    /** Cambia cada vez que se guarda o borra una oferta a mano. */
    public long manualStamp() {
        return sp.getLong("manualStamp", 0);
    }

    // ---------- censo de comercializadoras (solo para ver quién falta) ----------

    public Censo.Result censo() {
        String s = sp.getString("censo", "");
        if (s == null || s.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(s);
            Censo.Result r = new Censo.Result();
            r.at = o.optLong("at", 0);
            r.total = o.optInt("total", 0);
            r.activas = o.optInt("activas", 0);
            r.nacionales = o.optInt("nacionales", 0);
            JSONArray a = o.optJSONArray("items");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject it = a.optJSONObject(i);
                if (it == null) continue;
                Censo.Item x = new Censo.Item();
                x.name = it.optString("n", "");
                x.web = it.optString("w", "");
                r.items.add(x);
            }
            return r.ok() ? r : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void saveCenso(Censo.Result r) {
        if (r == null || !r.ok()) return;
        try {
            JSONObject o = new JSONObject();
            o.put("at", r.at > 0 ? r.at : System.currentTimeMillis());
            o.put("total", r.total);
            o.put("activas", r.activas);
            o.put("nacionales", r.nacionales);
            JSONArray a = new JSONArray();
            for (Censo.Item i : r.items) {
                JSONObject x = new JSONObject();
                x.put("n", i.name);
                x.put("w", i.web);
                a.put(x);
            }
            o.put("items", a);
            sp.edit().putString("censo", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public void setSupplierName(String name) {
        if (name != null && !name.isEmpty()) sp.edit().putString("supplierName", name).apply();
    }

    /** Lee un ajuste numérico aunque se guardara antes como entero. */
    private double getNum(String key, double def) {
        Object v = sp.getAll().get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    /** En preferencias solo se guardan los ajustes: la factura está en el historial. */
    public void save(BillData b) {
        sp.edit()
                .putFloat("autoKwh", (float) b.autoKwh)
                .putBoolean("autoOn", b.autoOn)
                .putFloat("surplusKwhYear", (float) b.surplusKwhYear)
                .putFloat("surplusPrice", (float) b.surplusPriceManual)
                .putBoolean("battery", b.battery)
                .apply();
    }

    public void clearBill() {
        sp.edit()
                .remove("cp").remove("p1").remove("p2")
                .remove("kwhYear").remove("kwh1").remove("kwh2").remove("kwh3")
                .remove("billKwh1").remove("billKwh2").remove("billKwh3")
                .remove("invoiceTotal").remove("invoiceDays").remove("surplus")
                .remove("cups").remove("supplierCode").remove("savedAt").remove("from").remove("to")
                .remove("surplusKwh").remove("surplusKwhYear").remove("surplusEur")
                .remove("surplusPrice").remove("battery")
                .remove("autoKwh").remove("autoOn").remove("curve")
                .remove("oneSource").remove("historyOnly")
                .remove("solarMonths").remove("datadisMonths").remove("pvpc")
                .remove("manualOffers").remove("manualStamp")
                .apply();
    }

    // ---------- ajustes ----------
    public double getAutoKwh() {
        return getNum("autoKwh", 0);
    }

    public void setAutoKwh(double kwh) {
        sp.edit().putFloat("autoKwh", (float) kwh).apply();
    }

    public void setAutoOn(boolean on) {
        sp.edit().putBoolean("autoOn", on).apply();
    }

    // ---------- curva horaria (Datadis) ----------

    public Curve curve() {
        String s = sp.getString("curve", "");
        if (s == null || s.isEmpty()) return new Curve();
        try {
            return Curve.fromJson(new JSONObject(s));
        } catch (Exception e) {
            return new Curve();
        }
    }

    public void saveCurve(Curve c) {
        if (c == null || !c.ok()) return;
        try {
            sp.edit().putString("curve", c.toJson().toString()).apply();
        } catch (Exception ignored) {
        }
    }

    // ---------- PVPC de ESIOS ----------

    /**
     * Token personal de ESIOS. Se guarda en el móvil (SharedPreferences, sin nube) para no
     * tener que pegarlo en cada consulta; solo se guarda cuando ESIOS lo ha aceptado.
     */
    public String esiosToken() {
        String s = sp.getString("esiosToken", "");
        return s == null ? "" : s;
    }

    public void saveEsiosToken(String token) {
        if (token == null || token.trim().isEmpty()) return;
        sp.edit().putString("esiosToken", token.trim()).apply();
    }

    /** Null si todavía no se ha consultado. */
    public Pvpc pvpc() {
        String s = sp.getString("pvpc", "");
        if (s == null || s.isEmpty()) return null;
        try {
            Pvpc p = Pvpc.fromJson(new JSONObject(s));
            return p.ok() ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void savePvpc(Pvpc p) {
        if (p == null || !p.ok()) return;
        try {
            sp.edit().putString("pvpc", p.toJson().toString()).apply();
        } catch (Exception ignored) {
        }
    }

    // ---------- acceso a Datadis ----------

    /**
     * NIF y contraseña de Datadis. Se guardan en el móvil (SharedPreferences, sin nube) la
     * primera vez que Datadis los acepta, para no teclearlos en cada consulta.
     */
    public String datadisNif() {
        String s = sp.getString("datadisNif", "");
        return s == null ? "" : s;
    }

    public String datadisPass() {
        String s = sp.getString("datadisPass", "");
        return s == null ? "" : s;
    }

    public void saveDatadisLogin(String nif, String pass) {
        if (nif == null || nif.trim().isEmpty() || pass == null || pass.isEmpty()) return;
        sp.edit().putString("datadisNif", nif.trim()).putString("datadisPass", pass).apply();
    }

    // ---------- lo último que dijo Datadis ----------

    /**
     * Los meses de la última consulta se guardan tal cual: así se pueden revisar y aplicar
     * sin volver a pedírselos a Datadis (que no deja repetir la misma consulta en 24 h).
     */
    public void saveDatadisResult(Datadis.Result r) {
        if (r == null || r.months.isEmpty()) return;
        try {
            JSONArray a = new JSONArray();
            for (SolarMonth m : r.months) a.put(m.toJson());
            JSONObject o = new JSONObject();
            o.put("months", a);
            o.put("self", r.self);
            o.put("exp", r.exported);
            o.put("prod", r.production);
            o.put("grid", r.grid);
            o.put("at", System.currentTimeMillis());
            sp.edit().putString("datadisMonths", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Null si no hay ninguna descarga guardada con meses. */
    public Datadis.Result datadisResult() {
        String s = sp.getString("datadisMonths", "");
        if (s == null || s.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(s);
            Datadis.Result r = new Datadis.Result();
            JSONArray a = o.optJSONArray("months");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject m = a.optJSONObject(i);
                if (m != null) r.months.add(SolarMonth.fromJson(m));
            }
            r.self = o.optDouble("self", 0);
            r.exported = o.optDouble("exp", 0);
            r.production = o.optDouble("prod", 0);
            r.grid = o.optDouble("grid", 0);
            r.at = o.optLong("at", 0);
            return r.months.isEmpty() ? null : r;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- autoconsumo mes a mes ----------

    public List<SolarMonth> solarMonths() {
        List<SolarMonth> out = new ArrayList<>();
        String s = sp.getString("solarMonths", "");
        if (s == null || s.isEmpty()) return out;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                SolarMonth m = SolarMonth.fromJson(o);
                if (m.month == null || m.month.length() < 7) continue;
                out.add(m);
            }
        } catch (Exception ignored) {
        }
        out.sort((x, y) -> y.month.compareTo(x.month));
        return out;
    }

    /** Un mes por clave: si repites el mes, se sustituye el que había. */
    /**
     * Guarda el vertido que dice Datadis **sin pisar lo del inversor**: si el mes ya existe
     * con autoconsumo, se queda su autoconsumo (y su producción) y solo cambia el vertido.
     * Datadis no ve el autoconsumo porque no pasa por el contador, así que su 0 no es un dato:
     * es lo que no puede medir. Tampoco enciende el interruptor del autoconsumo.
     */
    public void saveVertidoMonth(SolarMonth m) {
        if (m == null || m.month == null || m.month.isEmpty()) return;
        List<SolarMonth> all = solarMonths();
        for (int i = 0; i < all.size(); i++) {
            SolarMonth old = all.get(i);
            if (!old.month.equals(m.month)) continue;
            if (m.exportKwh > 0) old.exportKwh = SolarMonth.round2(m.exportKwh);
            // La producción del inversor es autoconsumo + vertido; la de Datadis es solo el
            // vertido, así que solo rellena si el mes no tenía ninguna.
            if (old.productionKwh <= 0 && m.productionKwh > 0) {
                old.productionKwh = SolarMonth.round2(m.productionKwh);
            }
            saveSolarMonths(all);
            return;
        }
        // Mes que no tenías del inversor: entra tal cual (autoconsumo 0, que es lo real).
        all.add(m);
        saveSolarMonths(all);
    }

    public void saveSolarMonth(SolarMonth m) {
        if (m == null || m.month == null || m.month.isEmpty()) return;
        // Si mete meses del inversor, es que tiene placas: el interruptor queda encendido.
        sp.edit().putBoolean("autoOn", true).apply();
        List<SolarMonth> all = solarMonths();
        boolean done = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).month.equals(m.month)) {
                all.set(i, m);
                done = true;
                break;
            }
        }
        if (!done) all.add(m);
        saveSolarMonths(all);
    }

    public void removeSolarMonth(String month) {
        List<SolarMonth> all = solarMonths();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).month.equals(month)) all.remove(i);
        }
        saveSolarMonths(all);
    }

    private void saveSolarMonths(List<SolarMonth> all) {
        JSONArray a = new JSONArray();
        for (SolarMonth m : all) {
            try {
                a.put(m.toJson());
            } catch (Exception ignored) {
            }
        }
        sp.edit().putString("solarMonths", a.toString()).apply();
    }

    // ---------- historial de facturas ----------

    public List<Invoice> invoices() {
        List<Invoice> out = new ArrayList<>();
        String s = sp.getString("invoices", "");
        if (s == null || s.isEmpty()) return out;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Invoice inv = Invoice.fromJson(o);
                // Sin periodo no se puede sumar al año: se ignora.
                if (inv.from == null || inv.from.isEmpty() || inv.to == null || inv.to.isEmpty()) continue;
                // Lecturas imposibles guardadas por versiones anteriores: se descartan
                // para que no infilen el año.
                if (inv.billed > 50000) inv.billed = 0;
                if (inv.kwh1 + inv.kwh2 + inv.kwh3 > 50000) {
                    inv.kwh1 = 0;
                    inv.kwh2 = 0;
                    inv.kwh3 = 0;
                }
                out.add(inv);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public void addInvoices(List<Invoice> add) {
        List<Invoice> all = invoices();
        for (Invoice n : add) {
            boolean done = false;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).key().equals(n.key())) {
                    all.set(i, n);
                    done = true;
                    break;
                }
            }
            if (!done) all.add(n);
        }
        saveInvoices(all);
    }

    public void saveInvoices(List<Invoice> all) {
        JSONArray a = new JSONArray();
        for (Invoice i : all) {
            try {
                a.put(i.toJson());
            } catch (Exception ignored) {
            }
        }
        sp.edit().putString("invoices", a.toString()).apply();
    }

    /** Guarda en la factura más reciente los precios de energía por tramo (0 = sin tramos). */
    public void updateTramos(double pe1, double pe2, double pe3) {
        List<Invoice> all = invoices();
        if (all.isEmpty()) return;
        all.sort(Invoice.byDate());
        Invoice n = all.get(0);
        n.pe1 = pe1;
        n.pe2 = pe2;
        n.pe3 = pe3;
        n.priceFrom = "";
        n.priceTo = "";
        saveInvoices(all);
    }

    /**
     * Guarda los precios corregidos a mano en la factura más reciente. El de energía es el
     * precio **vigente hoy** (el del campo): va a `peNow` y, si la factura no traía ninguno,
     * también a `pe`. Así se reprecia el año con el precio nuevo sin borrar el efectivo con
     * el que se reproduce la factura ya guardada.
     */
    public void updatePrices(double pe, double pp1, double pp2, double ps,
                             double bono, double rent) {
        List<Invoice> all = invoices();
        if (all.isEmpty()) return;
        all.sort(Invoice.byDate());
        Invoice n = all.get(0);
        n.peNow = pe;
        if (n.pe <= 0) n.pe = pe;
        n.pp1 = pp1;
        n.pp2 = pp2;
        n.ps = ps;
        n.bono = bono;
        n.rent = rent;
        // Los ha puesto el usuario para esta factura: ya son suyos.
        n.priceFrom = "";
        n.priceTo = "";
        saveInvoices(all);
    }

    public void removeInvoice(Invoice target) {
        if (target == null) return;
        List<Invoice> all = invoices();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).key().equals(target.key())) all.remove(i);
        }
        saveInvoices(all);
        // El PDF guardado se va con su factura: si no, quedaría ocupando sitio.
        if (target.pdf != null && !target.pdf.isEmpty()) {
            boolean used = false;
            for (Invoice i : all) if (target.pdf.equals(i.pdf)) used = true;
            if (!used) Pdfs.delete(ctx, target.pdf);
        }
    }

    public void clearInvoices() {
        sp.edit().remove("invoices").apply();
        File[] files = Pdfs.dir(ctx).listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    public boolean historyOpen() { return sp.getBoolean("historyOpen", false); }

    public void setHistoryOpen(boolean open) { sp.edit().putBoolean("historyOpen", open).apply(); }

    public boolean filterFixed() { return sp.getBoolean("f_fixed", false); }
    public boolean filterSelfUse() { return sp.getBoolean("f_self", false); }
    public boolean filterGreen() { return sp.getBoolean("f_green", false); }
    public void setFilter(String key, boolean value) {
        sp.edit().putBoolean(key, value).apply();
    }
}
