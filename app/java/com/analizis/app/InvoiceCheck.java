package com.analizis.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Repaso de las facturas guardadas: no cambia nada, solo mira si cada una tiene sus precios
 * y si con ellos se reproduce lo que dice la factura.
 *
 * Ojo con lo que significa «no cuadra»: al importar se comprueba con los datos del PDF, pero
 * lo que queda guardado es la factura ya montada, y en ella la potencia y los días pueden ser
 * los del QR y los precios los de otra factura (heredados). Por eso esto es un aviso para
 * mirar esa factura, no una prueba de que los precios estén mal.
 */
public class InvoiceCheck {

    public int total = 0;       // facturas guardadas con lecturas
    public int withPrices = 0;  // con algún precio de energía o potencia
    public int square = 0;      // las que se reproducen con sus precios
    public int noElec = 0;      // sin la luz de la factura: solo se puede comparar con el total
    public final List<String> bad = new ArrayList<>();  // fin del periodo de las que no cuadran

    public static InvoiceCheck run(List<Invoice> all) {
        InvoiceCheck c = new InvoiceCheck();
        if (all == null) return c;
        for (Invoice i : all) {
            if (i == null || i.kwh() <= 0) continue;
            c.total++;
            if (i.elec <= 0) c.noElec++;
            if (i.pe > 0 || i.pe1 > 0 || i.pe2 > 0 || i.pe3 > 0
                    || i.pp1 > 0 || i.pp2 > 0) {
                c.withPrices++;
            }
            double bill = i.elec > 0 ? i.elec : i.total;
            if (bill <= 0 || !hasPrices(i)) continue;
            BillData.Sim s = data(i).simulate();
            double diff = Math.abs(s.total - bill);
            if (diff <= tolerance(bill)) {
                c.square++;
            } else {
                c.bad.add(i.to.isEmpty() ? i.from : i.to);
            }
        }
        return c;
    }

    public static boolean hasPrices(Invoice i) {
        return i.pe > 0 || i.pe1 > 0 || i.pe2 > 0 || i.pe3 > 0 || i.pp1 > 0 || i.pp2 > 0;
    }

    public static double tolerance(double bill) {
        return Math.max(0.10, Math.abs(bill) * 0.01);
    }

    /** Lo que debería salir con los precios guardados, y lo que dice la factura. */
    public static double simTotal(Invoice i) {
        return data(i).simulate().total;
    }

    /** Importe de la factura con el que se compara: la luz, o el total si no hay luz. */
    public static double bill(Invoice i) {
        return i.elec > 0 ? i.elec : i.total;
    }

    /** Los datos de una factura como si se fueran a reproducir sus números. */
    public static BillData data(Invoice i) {
        BillData d = new BillData();
        d.pe = i.pe;
        d.pe1 = i.pe1;
        d.pe2 = i.pe2;
        d.pe3 = i.pe3;
        d.pp1 = i.pp1;
        d.pp2 = i.pp2;
        d.ps = i.ps;
        d.bono = i.bono;
        d.rent = i.rent;
        d.taxKwh = i.taxKwh;
        // El IEE de la factura se copia tal cual, incluso 0: hay facturas que no cobran el
        // impuesto por porcentaje sino como «mínimo comunitario» por kWh, y si aquí se pusiera
        // el 5,11 % por defecto saldrían descuadradas facturas que están perfectas.
        d.iee = i.iee;
        if (i.iva > 0) d.iva = i.iva;
        d.invoiceDays = i.days > 0 ? i.days : 30;
        d.billKwh1 = i.kwh1;
        d.billKwh2 = i.kwh2;
        d.billKwh3 = i.kwh3;
        d.billed = i.billed;
        d.p1 = i.p1;
        d.p2 = i.p2;
        d.surplus = i.surplus;
        d.surplusEur = i.surplus;
        return d;
    }
}
