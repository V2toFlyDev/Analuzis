package com.analizis.app;

import org.json.JSONObject;

import java.util.List;

/** Una factura concreta: el periodo que cubre y lo que se usó y se pagó en ese periodo. */
public class Invoice {

    public String from = "";     // yyyy-MM-dd
    public String to = "";
    public String cp = "";
    public String cups = "";
    public String supplier = "";
    public int aYear = 0;         // consumo anual declarado en el QR
    public int a1, a2, a3;        // reparto anual por tramos
    public double pe, pp1, pp2, ps;   // precios reales: energía, potencia P1/P2, excedentes
    public double pe1, pe2, pe3;      // energía por tramo (0 si la factura tiene precio único)
    public double peNow;              // energía vigente hoy (0 = no se leyó aparte: vale pe)
    public double pp1Now, pp2Now;     // potencia vigente hoy (0 = vale pp1/pp2)
    public double bono, rent;         // €/día bono social y contador
    public double taxKwh;             // €/kWh de impuesto eléctrico mínimo
    public double iee = 5.112696, iva = 21;
    public int days = 30;
    public int kwh1, kwh2, kwh3;  // tramos de las lecturas
    public int billed;            // kWh que cobra la factura (0 = usar los tramos)
    public double p1, p2;
    public double total;         // € con impuestos, lo que se paga
    public double elec;          // € con impuestos de la luz, antes de la batería virtual
    public double surplus;       // € de excedentes sin impuestos
    public String origin = "";   // PDF / QR / foto
    public String pdf = "";      // copia guardada en la app, para poder abrirla luego
    // Si los precios no se leyeron de esta factura sino que se arrastraron de otra,
    // aquí queda de qué factura son, para poder decirlo en vez de hacerlos pasar por suyos.
    public String priceFrom = "";
    public String priceTo = "";

    public int kwh() {
        return billed > 0 ? billed : kwh1 + kwh2 + kwh3;
    }

    /**
     * Más reciente primero, por fecha de fin y, si coincide, por fecha de inicio:
     * así manda la última factura por fecha y no el orden de importación.
     */
    public static java.util.Comparator<Invoice> byDate() {
        return (a, z) -> {
            int c = z.to.compareTo(a.to);
            return c != 0 ? c : z.from.compareTo(a.from);
        };
    }

    /** La última factura por fecha, sea cual sea el orden de la lista. */
    public static Invoice latest(List<Invoice> all) {
        Invoice out = null;
        if (all == null) return null;
        for (Invoice i : all) {
            if (i == null) continue;
            if (out == null || byDate().compare(i, out) < 0) out = i;
        }
        return out;
    }

    /** Dos facturas son la misma si cubren el mismo periodo. */
    public String key() {
        return from + "|" + to;
    }

    public static Invoice of(BillData b, String origin) {
        Invoice i = new Invoice();
        i.from = b.from == null ? "" : b.from;
        i.to = b.to == null ? "" : b.to;
        i.cp = b.cp;
        i.cups = b.cups;
        i.supplier = b.supplierCode;
        i.aYear = b.kwhYear;
        i.a1 = b.kwh1;
        i.a2 = b.kwh2;
        i.a3 = b.kwh3;
        i.pe = b.pe;
        i.pe1 = b.pe1;
        i.pe2 = b.pe2;
        i.pe3 = b.pe3;
        i.peNow = b.peNow;
        i.pp1 = b.pp1;
        i.pp2 = b.pp2;
        i.pp1Now = b.pp1Now;
        i.pp2Now = b.pp2Now;
        i.ps = b.ps;
        i.bono = b.bono;
        i.rent = b.rent;
        i.taxKwh = b.taxKwh;
        i.iee = b.iee;
        i.iva = b.iva;
        i.days = b.invoiceDays > 0 ? b.invoiceDays : 30;
        i.kwh1 = b.billKwh1;
        i.kwh2 = b.billKwh2;
        i.kwh3 = b.billKwh3;
        i.billed = b.billed;
        i.p1 = b.p1;
        i.p2 = b.p2;
        i.total = b.invoiceTotal;
        i.elec = b.invoiceElec;
        i.surplus = b.surplus;
        i.origin = origin;
        return i;
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("from", from);
        o.put("to", to);
        o.put("cp", cp);
        o.put("cups", cups);
        o.put("supplier", supplier);
        o.put("aYear", aYear);
        o.put("a1", a1);
        o.put("a2", a2);
        o.put("a3", a3);
        o.put("pe", pe);
        o.put("p1e", pe1);
        o.put("p2e", pe2);
        o.put("p3e", pe3);
        o.put("pene", peNow);
        o.put("pp1", pp1);
        o.put("pp2", pp2);
        o.put("pp1n", pp1Now);
        o.put("pp2n", pp2Now);
        o.put("ps", ps);
        o.put("bono", bono);
        o.put("rent", rent);
        o.put("taxKwh", taxKwh);
        o.put("iee", iee);
        o.put("iva", iva);
        o.put("days", days);
        o.put("k1", kwh1);
        o.put("k2", kwh2);
        o.put("k3", kwh3);
        o.put("billed", billed);
        o.put("elec", elec);
        o.put("p1", p1);
        o.put("p2", p2);
        o.put("total", total);
        o.put("surplus", surplus);
        o.put("origin", origin);
        o.put("pdf", pdf);
        o.put("pFrom", priceFrom);
        o.put("pTo", priceTo);
        return o;
    }

    public static Invoice fromJson(JSONObject o) {
        Invoice i = new Invoice();
        i.from = o.optString("from", "");
        i.to = o.optString("to", "");
        i.cp = o.optString("cp", "");
        i.cups = o.optString("cups", "");
        i.supplier = o.optString("supplier", "");
        i.aYear = o.optInt("aYear", 0);
        i.a1 = o.optInt("a1", 0);
        i.a2 = o.optInt("a2", 0);
        i.a3 = o.optInt("a3", 0);
        i.pe = o.optDouble("pe", 0);
        i.pe1 = o.optDouble("p1e", 0);
        i.pe2 = o.optDouble("p2e", 0);
        i.pe3 = o.optDouble("p3e", 0);
        i.peNow = o.optDouble("pene", 0);
        i.pp1 = o.optDouble("pp1", 0);
        i.pp2 = o.optDouble("pp2", 0);
        i.pp1Now = o.optDouble("pp1n", 0);
        i.pp2Now = o.optDouble("pp2n", 0);
        // Facturas guardadas antes de separar la potencia vigente: el efectivo es
        // la mejor aproximación disponible.
        if (i.pp1Now <= 0) i.pp1Now = i.pp1;
        if (i.pp2Now <= 0) i.pp2Now = i.pp2;
        i.ps = o.optDouble("ps", 0);
        i.bono = o.optDouble("bono", 0);
        i.rent = o.optDouble("rent", 0);
        i.taxKwh = o.optDouble("taxKwh", 0);
        i.iee = o.optDouble("iee", 5.112696);
        i.iva = o.optDouble("iva", 21);
        i.days = o.optInt("days", 30);
        i.kwh1 = o.optInt("k1", 0);
        i.kwh2 = o.optInt("k2", 0);
        i.kwh3 = o.optInt("k3", 0);
        i.billed = o.optInt("billed", 0);
        i.elec = o.optDouble("elec", 0);
        i.p1 = o.optDouble("p1", 0);
        i.p2 = o.optDouble("p2", 0);
        i.total = o.optDouble("total", 0);
        i.surplus = o.optDouble("surplus", 0);
        i.origin = o.optString("origin", "");
        i.pdf = o.optString("pdf", "");
        i.priceFrom = o.optString("pFrom", "");
        i.priceTo = o.optString("pTo", "");
        return i;
    }

    public String label() {
        String a = from == null || from.isEmpty() ? "?" : from;
        String b = to == null || to.isEmpty() ? "?" : to;
        return a + " → " + b;
    }
}
