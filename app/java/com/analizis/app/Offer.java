package com.analizis.app;

import org.json.JSONObject;

/** Una oferta del comparador de la CNMC. */
public class Offer {

    public int id;
    public String supplier = "";
    public String name = "";
    public double year1 = 0;
    public double year2 = 0;
    public boolean green = false;
    public boolean penalty = false;
    public boolean extras = false;
    public boolean selfUse = false;
    public String kind = "";          // TE, PVPC...
    // `selfUse` es lo que dice la CNMC: si la oferta admite compensación de excedentes. Solo a
    // esas se les puede descontar tu compensación; a las demás no se les resta nada.
    public String validity = "";
    public double penaltyAmount = 0;
    // Fila que no viene de la CNMC: el PVPC calculado con los precios reales de ESIOS y tu
    // reparto P1/P2/P3, en vez del perfil estándar que usa el comparador.
    public boolean pvpcMine = false;
    // Fila escrita a mano por el usuario (`ManualOffer`): su coste sale de sus precios, no de
    // `year1`, y sustituye a la oferta de la CNMC con la misma comercializadora y nombre.
    public boolean manual = false;
    public long manualId = 0;

    public boolean isRegulated() {
        return "PVPC".equalsIgnoreCase(kind);
    }

    /** ¿La oferta compensa excedentes? Si no, tu compensación no se le puede restar. */
    public boolean compensates() {
        return selfUse;
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("supplier", supplier);
        o.put("name", name);
        o.put("year1", year1);
        o.put("year2", year2);
        o.put("green", green);
        o.put("penalty", penalty);
        o.put("extras", extras);
        o.put("selfUse", selfUse);
        o.put("kind", kind);
        o.put("validity", validity);
        o.put("penaltyAmount", penaltyAmount);
        o.put("pvpcMine", pvpcMine);
        o.put("manual", manual);
        o.put("manualId", manualId);
        return o;
    }

    public static Offer fromJson(JSONObject o) {
        Offer f = new Offer();
        if (o == null) return f;
        f.id = o.optInt("id");
        f.supplier = o.optString("supplier", "");
        f.name = o.optString("name", "");
        f.year1 = o.optDouble("year1", 0);
        f.year2 = o.optDouble("year2", 0);
        f.green = o.optBoolean("green", false);
        f.penalty = o.optBoolean("penalty", false);
        f.extras = o.optBoolean("extras", false);
        f.selfUse = o.optBoolean("selfUse", false);
        f.kind = o.optString("kind", "");
        f.validity = o.optString("validity", "");
        f.penaltyAmount = o.optDouble("penaltyAmount", 0);
        f.pvpcMine = o.optBoolean("pvpcMine", false);
        f.manual = o.optBoolean("manual", false);
        f.manualId = o.optLong("manualId", 0);
        return f;
    }

    public String kindLabel() {
        if (isRegulated()) return "Precio variable (PVPC)";
        return "Precio fijo";
    }
}
