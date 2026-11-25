package com.analizis.app;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Una oferta metida a mano con los precios que publica su web. No viene de la CNMC y no se
 * actualiza sola: es una foto que el usuario guarda y puede editar.
 *
 * El cálculo no tiene fórmula propia: copia tu perfil (consumo, potencia, bono, contador,
 * impuestos) y solo cambia los precios de energía y potencia; luego usa
 * `BillData.annualGrossFromPrices()` y `netCostWith`, las mismas cuentas que el resto.
 */
public class ManualOffer {

    public long id = 0;
    public String name = "";
    public String supplier = "";
    public String web = "";

    // €/kWh sin impuestos. `energy` es el precio único; si están los tres tramos, mandan ellos.
    public double energy = 0;
    public double e1 = 0;
    public double e2 = 0;
    public double e3 = 0;

    // €/kW·día sin impuestos (las webs suelen dar €/kW·año: se divide entre 365).
    public double power1 = 0;
    public double power2 = 0;

    public boolean compensates = false;
    public double surplusPrice = 0;   // €/kWh sin impuestos
    public boolean battery = false;
    public boolean green = false;
    public boolean penalty = false;

    public long createdAt = 0;
    public long updatedAt = 0;

    public boolean byTramos() {
        return e1 > 0 && e2 > 0 && e3 > 0;
    }

    public boolean hasPrices() {
        return (energy > 0 || byTramos()) && (power1 > 0 || power2 > 0);
    }

    /** Comercializadora + nombre en minúsculas: sirve para casar con una oferta de la CNMC. */
    public String key() {
        return (safe(supplier) + "|" + safe(name)).trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Tu perfil con los precios de esta oferta. */
    public BillData withPrices(BillData b) {
        BillData x = b.copy();
        boolean tramos = byTramos();
        // `pe` es el precio de respaldo si falta el reparto por tramos del año.
        x.pe = tramos ? (e1 + e2 + e3) / 3.0 : energy;
        x.peNow = x.pe;
        x.pe1 = tramos ? e1 : 0;
        x.pe2 = tramos ? e2 : 0;
        x.pe3 = tramos ? e3 : 0;
        x.pp1 = power1 > 0 ? power1 : power2;
        x.pp2 = power2 > 0 ? power2 : power1;
        return x;
    }

    public BillData.Breakdown breakdown(BillData b) {
        return withPrices(b).pricesBreakdown();
    }

    /** Coste anual CON impuestos y sin descontar excedentes. */
    public double gross(BillData b) {
        return breakdown(b).gross;
    }

    /** Lo que paga esta oferta por tus excedentes; si no lo sabes, tu precio. */
    public double usedSurplus(BillData b) {
        if (!compensates) return 0;
        if (surplusPrice > 0) return surplusPrice;
        return b.surplusPrice();
    }

    public double credit(BillData b) {
        if (!compensates) return 0;
        return b.surplusCreditFor(usedSurplus(b));
    }

    /** Coste anual con la compensación que paga esta oferta, como a las de la CNMC. */
    public double net(BillData b) {
        double g = gross(b);
        if (g <= 0) return 0;
        return Math.max(0, g - credit(b));
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("supplier", supplier);
        o.put("web", web);
        o.put("energy", energy);
        o.put("e1", e1);
        o.put("e2", e2);
        o.put("e3", e3);
        o.put("p1", power1);
        o.put("p2", power2);
        o.put("comp", compensates);
        o.put("surplus", surplusPrice);
        o.put("battery", battery);
        o.put("green", green);
        o.put("penalty", penalty);
        o.put("created", createdAt);
        o.put("updated", updatedAt);
        return o;
    }

    public static ManualOffer fromJson(JSONObject o) {
        ManualOffer m = new ManualOffer();
        if (o == null) return m;
        m.id = o.optLong("id", 0);
        m.name = o.optString("name", "");
        m.supplier = o.optString("supplier", "");
        m.web = o.optString("web", "");
        m.energy = o.optDouble("energy", 0);
        m.e1 = o.optDouble("e1", 0);
        m.e2 = o.optDouble("e2", 0);
        m.e3 = o.optDouble("e3", 0);
        m.power1 = o.optDouble("p1", 0);
        m.power2 = o.optDouble("p2", 0);
        m.compensates = o.optBoolean("comp", false);
        m.surplusPrice = o.optDouble("surplus", 0);
        m.battery = o.optBoolean("battery", false);
        m.green = o.optBoolean("green", false);
        m.penalty = o.optBoolean("penalty", false);
        m.createdAt = o.optLong("created", 0);
        m.updatedAt = o.optLong("updated", 0);
        return m;
    }
}
