package com.analizis.app;

import org.json.JSONObject;

/**
 * Resumen del PVPC que se guarda en el móvil: los precios medios de los últimos 12 meses,
 * nunca el token. Con el reparto P1/P2/P3 de la curva se saca el término de energía del año.
 */
public class Pvpc {

    public double p1, p2, p3;   // €/kWh medios por periodo 2.0TD
    public double all;          // €/kWh medio del periodo
    public double surplus;      // €/kWh medio de los excedentes del PVPC
    public int hours;           // lecturas leídas
    public String from = "";    // yyyy-MM-dd
    public String to = "";
    public long at = 0;         // cuándo se consultó

    public static Pvpc of(Esios.Prices p) {
        Pvpc o = new Pvpc();
        if (p == null) return o;
        o.p1 = p.p1;
        o.p2 = p.p2;
        o.p3 = p.p3;
        o.all = p.all;
        o.surplus = p.surplus;
        o.hours = p.hours;
        o.from = p.from;
        o.to = p.to;
        return o;
    }

    public boolean ok() {
        return hours > 0 && all > 0;
    }

    /** Término de energía del año con tu reparto real, sin impuestos. */
    public double energyCost(BillData b) {
        if (b.kwh1 + b.kwh2 + b.kwh3 > 0) {
            return b.kwh1 * p1 + b.kwh2 * p2 + b.kwh3 * p3;
        }
        return b.kwhYear * all;
    }

    /** Lo mismo con el impuesto eléctrico y el IVA que llevan tus facturas. */
    public double energyCostTaxed(BillData b) {
        return energyCost(b) * BillData.TAX;
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("p1", p1);
        o.put("p2", p2);
        o.put("p3", p3);
        o.put("all", all);
        o.put("exp", surplus);
        o.put("hours", hours);
        o.put("from", from);
        o.put("to", to);
        o.put("at", at);
        return o;
    }

    public static Pvpc fromJson(JSONObject o) {
        Pvpc p = new Pvpc();
        if (o == null) return p;
        p.p1 = o.optDouble("p1", 0);
        p.p2 = o.optDouble("p2", 0);
        p.p3 = o.optDouble("p3", 0);
        p.all = o.optDouble("all", 0);
        p.surplus = o.optDouble("exp", 0);
        p.hours = o.optInt("hours", 0);
        p.from = o.optString("from", "");
        p.to = o.optString("to", "");
        p.at = o.optLong("at", 0);
        return p;
    }
}
