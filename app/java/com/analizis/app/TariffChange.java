package com.analizis.app;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Un cambio de tarifa detectado al guardar una factura nueva.
 *
 * Guarda los precios de la factura anterior y de la nueva, y la comparación hecha
 * con el mismo consumo de la factura nueva. Así el aviso se puede volver a abrir
 * aunque después se borre alguna factura. La comparación deja fuera los excedentes:
 * solo mide el efecto de los precios de la tarifa (energía, potencia y fijos) con
 * los impuestos de la factura nueva.
 */
public class TariffChange {

    // La factura redondea cada importe a céntimos. En potencia, recalcular el precio
    // desde ese importe puede moverlo unas millonésimas según los días del mes; eso no
    // es un cambio de tarifa. La energía y los excedentes llevan su propia tolerancia.
    private static final double ENERGY_TOLERANCE = 0.00005;
    private static final double POWER_TOLERANCE = 0.0001;
    private static final double SURPLUS_TOLERANCE = 0.0001;

    public String fromKey = "";
    public String toKey = "";
    public String fromFrom = "";
    public String fromTo = "";
    public String toFrom = "";
    public String toTo = "";
    public String supplierBefore = "";
    public String supplierAfter = "";
    public String reason = "";

    public double oldPe, newPe;
    public double oldPe1, oldPe2, oldPe3, newPe1, newPe2, newPe3;
    public double oldPp1, oldPp2, newPp1, newPp2;
    public double oldPs, newPs;

    public int kwh;
    public int days;
    public double actual;
    public double oldTotal, newTotal, oldAnnual, newAnnual;
    public double delta, annualDelta;
    public boolean midCycle;
    public long createdAt;

    /**
     * Devuelve null si no hay un cambio apreciable o si no hay precios suficientes
     * para comparar. Las fechas y el consumo salen de la factura nueva.
     */
    public static TariffChange between(Invoice before, Invoice after) {
        if (before == null || after == null) return null;
        if (after.from == null || after.from.isEmpty()
                || after.to == null || after.to.isEmpty()) return null;

        double oldTotal = costWith(after, before);
        double newTotal = costWith(after, after);
        if (oldTotal <= 0 || newTotal <= 0) return null;

        String why = reason(before, after);
        if (why.isEmpty()) return null;

        TariffChange c = new TariffChange();
        c.fromKey = before.key();
        c.toKey = after.key();
        c.fromFrom = before.from;
        c.fromTo = before.to;
        c.toFrom = after.from;
        c.toTo = after.to;
        c.supplierBefore = before.supplier == null ? "" : before.supplier;
        c.supplierAfter = after.supplier == null ? "" : after.supplier;
        c.reason = why;

        c.oldPe = currentEnergy(before);
        c.newPe = currentEnergy(after);
        c.oldPe1 = before.pe1;
        c.oldPe2 = before.pe2;
        c.oldPe3 = before.pe3;
        c.newPe1 = after.pe1;
        c.newPe2 = after.pe2;
        c.newPe3 = after.pe3;
        c.oldPp1 = currentPower(before, 1);
        c.oldPp2 = currentPower(before, 2);
        c.newPp1 = currentPower(after, 1);
        c.newPp2 = currentPower(after, 2);
        c.oldPs = before.ps;
        c.newPs = after.ps;

        c.kwh = after.kwh();
        c.days = after.days > 0 ? after.days : 30;
        c.actual = InvoiceCheck.bill(after);
        c.oldTotal = oldTotal;
        c.newTotal = newTotal;
        c.oldAnnual = oldTotal * 365.0 / c.days;
        c.newAnnual = newTotal * 365.0 / c.days;
        c.delta = newTotal - oldTotal;
        c.annualDelta = c.newAnnual - c.oldAnnual;
        c.midCycle = after.peNow > 0 && after.pe > 0
                && Math.abs(after.peNow - after.pe) > 0.00005;
        c.createdAt = System.currentTimeMillis();
        return c;
    }

    /** Coste de la factura nueva usando los precios de la tarifa indicada. */
    private static double costWith(Invoice consumption, Invoice tariff) {
        BillData d = InvoiceCheck.data(consumption);
        // Los excedentes se dejan fuera: el cambio de tarifa se mide con el mismo
        // consumo y sin mezclar la compensación, que tiene su propio precio.
        d.surplus = 0;
        d.surplusEur = 0;
        boolean tramos = tariff.pe1 > 0 && tariff.pe2 > 0 && tariff.pe3 > 0;
        d.pe1 = tramos ? tariff.pe1 : 0;
        d.pe2 = tramos ? tariff.pe2 : 0;
        d.pe3 = tramos ? tariff.pe3 : 0;
        d.pe = tramos ? tariff.pe : currentEnergy(tariff);
        d.peNow = tariff.peNow;
        d.pp1 = currentPower(tariff, 1);
        d.pp2 = currentPower(tariff, 2);
        d.ps = tariff.ps;
        d.bono = tariff.bono;
        d.rent = tariff.rent;
        d.taxKwh = tariff.taxKwh;
        d.iee = tariff.iee;
        if (tariff.iva > 0) d.iva = tariff.iva;
        return d.simulate().total;
    }

    private static String reason(Invoice before, Invoice after) {
        List<String> out = new ArrayList<>();
        if (both(before.supplier, after.supplier)
                && !before.supplier.equalsIgnoreCase(after.supplier)) {
            out.add("comercializadora");
        }
        if (energyChanged(before, after)) out.add("energía");
        if (powerChanged(currentPower(before, 1), currentPower(after, 1))
                || powerChanged(currentPower(before, 2), currentPower(after, 2))) out.add("potencia");
        if (bothPositive(before.ps, after.ps)
                && Math.abs(before.ps - after.ps) > SURPLUS_TOLERANCE) out.add("excedentes");
        return join(out);
    }

    private static boolean energyChanged(Invoice a, Invoice b) {
        if (hasTramos(a) && hasTramos(b)) {
            return Math.abs(a.pe1 - b.pe1) > ENERGY_TOLERANCE
                    || Math.abs(a.pe2 - b.pe2) > ENERGY_TOLERANCE
                    || Math.abs(a.pe3 - b.pe3) > ENERGY_TOLERANCE;
        }
        return bothPositive(currentEnergy(a), currentEnergy(b))
                && Math.abs(currentEnergy(a) - currentEnergy(b)) > ENERGY_TOLERANCE;
    }

    private static boolean powerChanged(double a, double b) {
        return bothPositive(a, b) && Math.abs(a - b) > POWER_TOLERANCE;
    }

    /**
     * ¿Queda algún cambio real en esta comparación? Sirve para descartar avisos
     * guardados por versiones anteriores que confundían el redondeo de la potencia
     * con un cambio de tarifa. Los precios reales se comparan con las mismas
     * tolerancias que usa la detección.
     */
    public boolean isMeaningful() {
        if (both(supplierBefore, supplierAfter)
                && !supplierBefore.equalsIgnoreCase(supplierAfter)) {
            return true;
        }

        boolean oldTramos = oldPe1 > 0 && oldPe2 > 0 && oldPe3 > 0;
        boolean newTramos = newPe1 > 0 && newPe2 > 0 && newPe3 > 0;
        if (oldTramos && newTramos) {
            if (Math.abs(oldPe1 - newPe1) > ENERGY_TOLERANCE
                    || Math.abs(oldPe2 - newPe2) > ENERGY_TOLERANCE
                    || Math.abs(oldPe3 - newPe3) > ENERGY_TOLERANCE) {
                return true;
            }
        } else if (bothPositive(oldPe, newPe)
                && Math.abs(oldPe - newPe) > ENERGY_TOLERANCE) {
            return true;
        }

        if (powerChanged(oldPp1, newPp1) || powerChanged(oldPp2, newPp2)) return true;
        return bothPositive(oldPs, newPs)
                && Math.abs(oldPs - newPs) > SURPLUS_TOLERANCE;
    }

    private static boolean bothPositive(double a, double b) {
        return a > 0 && b > 0;
    }

    private static boolean both(String a, String b) {
        return a != null && !a.trim().isEmpty() && b != null && !b.trim().isEmpty();
    }

    private static boolean hasTramos(Invoice i) {
        return i.pe1 > 0 && i.pe2 > 0 && i.pe3 > 0;
    }

    private static double currentEnergy(Invoice i) {
        return i.peNow > 0 ? i.peNow : i.pe;
    }

    /** Precio de potencia vigente; si no se leyó aparte, el efectivo de la factura. */
    private static double currentPower(Invoice i, int which) {
        if (which == 1) return i.pp1Now > 0 ? i.pp1Now : i.pp1;
        return i.pp2Now > 0 ? i.pp2Now : i.pp2;
    }

    /**
     * Comparaciones que genera un bloque de facturas nuevas: cada una se mide con la
     * anterior por fecha, aunque no sea la más reciente. Así al importar un histórico
     * aparecen también los cambios antiguos. `replaced` son las facturas que se
     * sustituyen y no deben servir como versión anterior.
     */
    public static List<TariffChange> detect(List<Invoice> added, List<Invoice> before,
                                             List<Invoice> replaced) {
        List<TariffChange> out = new ArrayList<>();
        if (added == null || added.isEmpty()) return out;

        List<Invoice> pool = new ArrayList<>();
        if (before != null) {
            for (Invoice old : before) {
                if (old == null || sameKey(replaced, old)) continue;
                addUnique(pool, old);
            }
        }
        for (Invoice invoice : added) {
            if (invoice != null) addUnique(pool, invoice);
        }
        pool.sort(Invoice.byDate());

        List<Invoice> candidates = new ArrayList<>();
        for (Invoice invoice : added) {
            if (invoice == null || invoice.from == null || invoice.from.isEmpty()
                    || invoice.to == null || invoice.to.isEmpty()) continue;
            addUnique(candidates, invoice);
        }
        candidates.sort(Invoice.byDate());
        Collections.reverse(candidates);

        for (Invoice next : candidates) {
            Invoice prev = null;
            for (Invoice old : pool) {
                if (old == null || old.key().equals(next.key())) continue;
                if (old.from == null || old.from.isEmpty()
                        || old.to == null || old.to.isEmpty()) continue;
                if (old.to.compareTo(next.from) <= 0) {
                    prev = old;
                    break;
                }
            }
            if (prev == null || prev.to.equals(next.to)) continue;
            TariffChange change = between(prev, next);
            if (change != null) out.add(change);
        }
        return out;
    }

    private static boolean sameKey(List<Invoice> list, Invoice invoice) {
        if (list == null) return false;
        for (Invoice old : list) {
            if (old != null && old.key().equals(invoice.key())) return true;
        }
        return false;
    }

    private static void addUnique(List<Invoice> list, Invoice invoice) {
        for (Invoice old : list) {
            if (old.key().equals(invoice.key())) return;
        }
        list.add(invoice);
    }

    private static String join(List<String> parts) {
        StringBuilder s = new StringBuilder();
        for (String p : parts) {
            if (s.length() > 0) s.append(", ");
            s.append(p);
        }
        return s.toString();
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("fromKey", fromKey);
        o.put("toKey", toKey);
        o.put("fromFrom", fromFrom);
        o.put("fromTo", fromTo);
        o.put("toFrom", toFrom);
        o.put("toTo", toTo);
        o.put("supplierBefore", supplierBefore);
        o.put("supplierAfter", supplierAfter);
        o.put("reason", reason);
        o.put("oldPe", oldPe);
        o.put("newPe", newPe);
        o.put("oldPe1", oldPe1);
        o.put("oldPe2", oldPe2);
        o.put("oldPe3", oldPe3);
        o.put("newPe1", newPe1);
        o.put("newPe2", newPe2);
        o.put("newPe3", newPe3);
        o.put("oldPp1", oldPp1);
        o.put("oldPp2", oldPp2);
        o.put("newPp1", newPp1);
        o.put("newPp2", newPp2);
        o.put("oldPs", oldPs);
        o.put("newPs", newPs);
        o.put("kwh", kwh);
        o.put("days", days);
        o.put("actual", actual);
        o.put("oldTotal", oldTotal);
        o.put("newTotal", newTotal);
        o.put("oldAnnual", oldAnnual);
        o.put("newAnnual", newAnnual);
        o.put("delta", delta);
        o.put("annualDelta", annualDelta);
        o.put("midCycle", midCycle);
        o.put("createdAt", createdAt);
        return o;
    }

    public static TariffChange fromJson(JSONObject o) {
        if (o == null) return null;
        TariffChange c = new TariffChange();
        c.fromKey = o.optString("fromKey", "");
        c.toKey = o.optString("toKey", "");
        c.fromFrom = o.optString("fromFrom", "");
        c.fromTo = o.optString("fromTo", "");
        c.toFrom = o.optString("toFrom", "");
        c.toTo = o.optString("toTo", "");
        c.supplierBefore = o.optString("supplierBefore", "");
        c.supplierAfter = o.optString("supplierAfter", "");
        c.reason = o.optString("reason", "");
        c.oldPe = o.optDouble("oldPe", 0);
        c.newPe = o.optDouble("newPe", 0);
        c.oldPe1 = o.optDouble("oldPe1", 0);
        c.oldPe2 = o.optDouble("oldPe2", 0);
        c.oldPe3 = o.optDouble("oldPe3", 0);
        c.newPe1 = o.optDouble("newPe1", 0);
        c.newPe2 = o.optDouble("newPe2", 0);
        c.newPe3 = o.optDouble("newPe3", 0);
        c.oldPp1 = o.optDouble("oldPp1", 0);
        c.oldPp2 = o.optDouble("oldPp2", 0);
        c.newPp1 = o.optDouble("newPp1", 0);
        c.newPp2 = o.optDouble("newPp2", 0);
        c.oldPs = o.optDouble("oldPs", 0);
        c.newPs = o.optDouble("newPs", 0);
        c.kwh = o.optInt("kwh", 0);
        c.days = o.optInt("days", 30);
        c.actual = o.optDouble("actual", 0);
        c.oldTotal = o.optDouble("oldTotal", 0);
        c.newTotal = o.optDouble("newTotal", 0);
        c.oldAnnual = o.optDouble("oldAnnual", 0);
        c.newAnnual = o.optDouble("newAnnual", 0);
        c.delta = o.optDouble("delta", 0);
        c.annualDelta = o.optDouble("annualDelta", 0);
        c.midCycle = o.optBoolean("midCycle", false);
        c.createdAt = o.optLong("createdAt", 0);
        if (c.toKey.isEmpty()) return null;
        return c;
    }
}
