package com.analizis.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Historial de facturas guardadas en el móvil. Con él se construye el perfil anual
 * (consumo por tramo, coste real con impuestos y excedentes) en vez de multiplicar
 * una sola factura por doce.
 */
public class History {

    private static final int ENOUGH_DAYS = 300;  // con esto manda el historial
    private static final int USEFUL_DAYS = 60;   // con esto ya sirve para el coste

    private final List<Invoice> list = new ArrayList<>();

    public History(List<Invoice> in) {
        if (in != null) {
            for (Invoice i : in) {
                if (i != null && i.kwh() > 0 && i.days > 0) list.add(i);
            }
        }
        // más reciente primero, por fecha (no por orden de importación)
        list.sort(Invoice.byDate());
    }

    public List<Invoice> invoices() {
        return list;
    }

    public int count() {
        return list.size();
    }

    public int days() {
        int d = 0;
        for (Invoice i : list) d += i.days;
        return d;
    }

    /** Aplica el historial sobre los datos leídos del QR y devuelve el perfil anual. */
    public BillData apply(BillData b) {
        b.historyCount = list.size();
        b.historyDays = days();
        if (list.isEmpty()) return b;
        // Lo que se enseña es lo que se usa: la ventana de 12 meses, no todo el historial.

        // ventana de los últimos 365 días
        List<Invoice> w = new ArrayList<>();
        int acc = 0;
        for (Invoice i : list) {
            w.add(i);
            acc += i.days;
            if (acc >= 365) break;
        }
        int wDays = 0;
        double t1 = 0, t2 = 0, t3 = 0, cost = 0, exc = 0;
        for (Invoice i : w) {
            wDays += i.days;
            t1 += i.kwh1;
            t2 += i.kwh2;
            t3 += i.kwh3;
            // Solo luz: el «total a pagar» puede traer fianzas, devoluciones o lo que
            // se tira de la batería virtual, y eso no es lo que cuesta la energía.
            cost += i.elec > 0 ? i.elec : i.total;
            exc += i.surplus;
        }
        b.historyCount = w.size();
        b.historyDays = wDays;
        if (wDays <= 0) return b;

        double f = 365.0 / wDays;

        // El consumo por tramos del historial solo manda si cubre casi el año entero:
        // con dos o tres meses de invierno saldría un perfil falso.
        if (wDays >= ENOUGH_DAYS) {
            b.kwh1 = (int) Math.round(t1 * f);
            b.kwh2 = (int) Math.round(t2 * f);
            b.kwh3 = (int) Math.round(t3 * f);
            b.kwhYear = b.kwh1 + b.kwh2 + b.kwh3;
        }

        // Importes: guardamos la suma del periodo y sus días. La app anualiza sola
        // (×365/días), así que todo sigue siendo comparable con el comparador.
        if (wDays >= USEFUL_DAYS && cost > 0) {
            b.invoiceTotal = cost;
            b.surplusEur = exc;
            b.invoiceDays = wDays;
        }

        Invoice last = list.get(0);
        if (last.p1 > 0) b.p1 = last.p1;
        if (last.p2 > 0) b.p2 = last.p2;
        b.from = last.from;
        b.to = last.to;
        return b;
    }
}
