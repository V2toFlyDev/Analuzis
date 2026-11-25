package com.analizis.app;

/**
 * Datos del suministro que se usan para comparar.
 * Ojo con los impuestos: el comparador de la CNMC da importes CON IVA e impuesto
 * eléctrico, y el QR de la factura da la compensación de excedentes SIN impuestos.
 */
public class BillData {

    /** Impuesto especial de la electricidad (5,1127%) por el IVA (21%). */
    public static final double TAX = 1.051127 * 1.21;

    public String cp = "";
    public double p1 = 0;          // potencia contratada punta (kW)
    public double p2 = 0;          // potencia contratada valle (kW)

    public int kwhYear = 0;        // consumo de los últimos 12 meses
    public int kwh1 = 0;           // reparto por tramos (P1, P2, P3)
    public int kwh2 = 0;
    public int kwh3 = 0;

    public int billKwh1 = 0;       // consumo del periodo facturado, por tramos (lecturas)
    public int billKwh2 = 0;
    public int billKwh3 = 0;
    public int billed = 0;         // kWh que la factura cobra de verdad («Consumo electricidad»)

    public double invoiceTotal = 0;   // € CON impuestos, lo que se paga (con batería virtual)
    public double invoiceElec = 0;    // € CON impuestos de la luz, antes de la batería virtual
    public int invoiceDays = 30;
    public double surplus = 0;        // importe de excedentes del periodo, SIN impuestos
    public String cups = "";
    public String supplierCode = "";
    public String from = "";          // periodo de la factura (yyyy-MM-dd)
    public String to = "";
    // Si los precios no son de esta factura, periodo de la factura de la que vienen.
    public String priceFrom = "";
    public String priceTo = "";

    public double autoKwh = 0;           // kWh de autoconsumo (puesto por el usuario)
    public boolean autoOn = false;       // el interruptor, aparte del dato: apagarlo no lo borra
    public double surplusKwhYear = 0;    // kWh vertidos a la red al año
    public double surplusEur = 0;        // € de excedentes del periodo, SIN impuestos
    public double surplusPriceManual = 0; // €/kWh SIN impuestos, si el usuario lo sabe

    public boolean battery = false;      // tarifa con batería virtual

    // Precios reales de tu tarifa, SIN impuestos, tal como vienen en la factura.
    public double pe = 0;       // €/kWh energía **efectiva**: reproduce el importe de la factura
    public double pe1 = 0;      // €/kWh energía en punta
    public double pe2 = 0;      // €/kWh energía en llano
    public double pe3 = 0;      // €/kWh energía en valle
    // €/kWh que está **vigente hoy** (la última línea de cargo de la factura, no la media del
    // periodo). Solo se usa para repreciar el año en el comparador; el cuadre va con `pe`.
    // 0 = no se leyó aparte (se usa `pe`).
    public double peNow = 0;
    public double pp1 = 0;      // €/kW·día potencia P1
    public double pp2 = 0;      // €/kW·día potencia P2
    public double ps = 0;       // €/kWh excedentes
    public double bono = 0;     // €/día financiación bono social
    public double rent = 0;     // €/día alquiler de contador
    public double taxKwh = 0;   // €/kWh de impuesto eléctrico mínimo ("mínimo comunitario")
    public double iee = 5.112696; // % impuesto electricidad
    public double iva = 21;       // % IVA

    public int historyDays = 0;          // días que cubre el historial guardado
    public int historyCount = 0;         // número de facturas guardadas

    public BillData copy() {
        BillData b = new BillData();
        b.cp = cp;
        b.p1 = p1;
        b.p2 = p2;
        b.kwhYear = kwhYear;
        b.kwh1 = kwh1;
        b.kwh2 = kwh2;
        b.kwh3 = kwh3;
        b.billKwh1 = billKwh1;
        b.billKwh2 = billKwh2;
        b.billKwh3 = billKwh3;
        b.billed = billed;
        b.invoiceTotal = invoiceTotal;
        b.invoiceElec = invoiceElec;
        b.invoiceDays = invoiceDays;
        b.surplus = surplus;
        b.cups = cups;
        b.supplierCode = supplierCode;
        b.from = from;
        b.to = to;
        b.priceFrom = priceFrom;
        b.priceTo = priceTo;
        b.autoKwh = autoKwh;
        b.autoOn = autoOn;
        b.surplusKwhYear = surplusKwhYear;
        b.surplusEur = surplusEur;
        b.surplusPriceManual = surplusPriceManual;
        b.battery = battery;
        b.pe = pe;
        b.pe1 = pe1;
        b.pe2 = pe2;
        b.pe3 = pe3;
        b.peNow = peNow;
        b.pp1 = pp1;
        b.pp2 = pp2;
        b.ps = ps;
        b.bono = bono;
        b.rent = rent;
        b.taxKwh = taxKwh;
        b.iee = iee;
        b.iva = iva;
        b.historyDays = historyDays;
        b.historyCount = historyCount;
        return b;
    }

    /** El importe del periodo viene de la factura: manda sobre precio × kWh. */
    public boolean surplusFromBill() {
        return surplusEur > 0;
    }

    /** Días del periodo que se usan para llevar el excedente de la factura al año. */
    public int surplusDays() {
        return invoiceDays > 0 ? invoiceDays : 30;
    }

    /**
     * El precio con el que se reprecía el año: el de la última factura (`ps`) y, si no lo
     * trae, el que hayas puesto a mano. Es el mismo criterio que la energía y la potencia.
     */
    public double annualSurplusPrice() {
        if (ps > 0) return ps;
        return surplusPriceManual > 0 ? surplusPriceManual : 0;
    }

    /** El precio que se usa de verdad para la compensación; 0 si no hay kWh con los que aplicarlo. */
    public double surplusPrice() {
        double p = annualSurplusPrice();
        if (p > 0 && surplusKwhYear > 0) return p;
        if (surplusFromBill() && surplusKwhYear > 0) {
            return annualSurplusPaid() / surplusKwhYear;
        }
        return 0;
    }

    /**
     * Compensación de excedentes en un año, SIN impuestos, repreciada como la energía:
     * precio de la última factura × kWh/año. Solo si no hay precio se usa el importe real
     * de las facturas (×365/días). Nada de mezclar euros históricos con kWh del año.
     */
    public double annualSurplus() {
        double p = annualSurplusPrice();
        if (p > 0 && surplusKwhYear > 0) return surplusKwhYear * p;
        return annualSurplusPaid();
    }

    /** La suma real de las facturas, llevada al año. Solo para enseñarla, no para comparar. */
    public double annualSurplusPaid() {
        if (!surplusFromBill()) return 0;
        return surplusEur * 365.0 / surplusDays();
    }

    /** La misma compensación, pero con los impuestos que se ahorra la factura. */
    public double annualSurplusTaxed() {
        return annualSurplus() * TAX;
    }

    /** Coste anual de una oferta (con impuestos) descontando tus excedentes. */
    public double netCost(double yearly) {
        double credit = annualSurplusTaxed();
        if (credit <= 0) return yearly;
        return Math.max(0, yearly - credit);
    }

    /**
     * Lo que te compensarían tus kWh vertidos a un precio dado (€/kWh SIN impuestos), con los
     * impuestos que se ahorra la factura. Sirve para las ofertas que pagan distinto que tú.
     */
    public double surplusCredit(double price) {
        if (surplusKwhYear <= 0 || price <= 0) return 0;
        return surplusKwhYear * price * TAX;
    }

    /**
     * Compensación anual con impuestos para un precio dado. Con kWh y precio se escala;
     * sin kWh vertidos se usa la compensación de la factura, que es el mismo importe y
     * evita descontar 0 en un lado y el importe en el otro.
     */
    public double surplusCreditFor(double price) {
        double c = surplusCredit(price);
        return c > 0 ? c : annualSurplusTaxed();
    }

    /** Coste anual de una oferta con la compensación que paga **esa** oferta. */
    public double netCostWith(double yearly, double price) {
        double credit = surplusCreditFor(price);
        if (credit <= 0) return yearly;
        return Math.max(0, yearly - credit);
    }

    /** ¿Hay meses suficientes para hablar de un año con dinero real? Al menos 2 facturas y 60 días. */
    public boolean hasYearData() {
        return historyCount >= 2 && historyDays >= 60;
    }

    /**
     * Coste anual con TUS precios y el consumo anual declarado. No extrapola un mes:
     * usa los kWh del año y 365 días de potencia. Sin redondeos intermedios.
     */
    /** Desglose del año con precios, para enseñarlo y para el comparador manual. */
    public static class Breakdown {
        public double energy, power, bono, tax, iee, rent, iva, gross;
    }

    /**
     * El año con precios: energía (por tramos o única), potencia, bono social, mínimo
     * comunitario, impuesto eléctrico, contador e IVA. Es la única fórmula; la usan
     * `annualGrossFromPrices` y el comparador manual, para no tener dos cuentas.
     */
    public Breakdown pricesBreakdown() {
        Breakdown d = new Breakdown();
        if (!hasPrices() || kwhYear <= 0) return d;
        d.energy = energyCostYear();
        d.power = (p1 * pp1 + p2 * pp2) * 365.0;
        d.bono = bono * 365.0;
        d.tax = kwhYear * taxKwh;
        double base = d.energy + d.power + d.bono + d.tax;
        d.iee = base * iee / 100.0;
        d.rent = rent * 365.0;
        d.iva = (base + d.iee + d.rent) * iva / 100.0;
        d.gross = base + d.iee + d.rent + d.iva;
        return d;
    }

    /** El año con precios, TODO incluido y SIN descontar excedentes. */
    public double annualGrossFromPrices() {
        return pricesBreakdown().gross;
    }

    /**
     * Lo mismo con la compensación de excedentes descontada, y **con la misma función** que se
     * usa para descontársela a cada oferta (`netCost`). Así el ahorro compara dos cifras netas:
     * la compensación se resta una vez en cada lado, con el mismo importe, y no se descuenta
     * dos veces ni con dos criterios distintos.
     */
    public double annualCostFromPrices() {
        double gross = annualGrossFromPrices();
        return gross > 0 ? netCost(gross) : 0;
    }

    /** Lo que has pagado de verdad, llevado al año (con los precios de cada mes). */
    public double paidAnnual() {
        return annualEstimate();
    }

    /**
     * Lo que habrías pagado este mismo año con los precios de tu última factura. Es la cifra
     * con la que se mide el ahorro: si tu tarifa cambió, lo pagado mezcla precios viejos.
     * Devuelve 0 si no hay precios con los que rehacer la cuenta.
     */
    public double repricedAnnual() {
        return annualCostFromPrices();
    }

    /** La que hay que usar para comparar: la repreciada si se puede, si no lo pagado. */
    public double baselineAnnual() {
        double r = repricedAnnual();
        return r > 0 ? r : paidAnnual();
    }

    /**
     * Tu coste anual, CON impuestos, para comparar con las ofertas:
     * con historial suficiente manda el dinero real; si no, tus precios y el año del QR.
     * Devuelve 0 si no hay base para calcularlo (no se inventa nada).
     */
    public double annualEstimate() {
        if (hasYearData() && invoiceTotal > 0 && invoiceDays > 0) {
            return invoiceTotal * 365.0 / invoiceDays;
        }
        // Una sola factura: se lleva a un año por su luz, no por el total a pagar,
        // que puede incluir fianzas o saldo de la batería.
        double base = invoiceElec > 0 ? invoiceElec : invoiceTotal;
        if (base > 0 && invoiceDays > 0) return base * 365.0 / invoiceDays;
        return annualCostFromPrices();
    }

    public double billingKwh() {
        if (billed > 0) return billed;
        return billKwh1 + billKwh2 + billKwh3;
    }

    public double pricePerKwh() {
        double k = billingKwh();
        if (k > 0 && invoiceTotal > 0) return invoiceTotal / k;
        return 0;
    }

    /** ¿Tengo los precios de la tarifa para poder reproducir la factura? */
    public boolean hasPrices() {
        return (pe > 0 || hasTramos()) && (pp1 > 0 || pp2 > 0);
    }

    /** Los tres precios de energía por tramo (si falta alguno, se usa el precio único). */
    public boolean hasTramos() {
        return pe1 > 0 && pe2 > 0 && pe3 > 0;
    }

    /** Precio de la energía en un tramo 2.0TD: el suyo si lo hay, si no el único. */
    public double priceFor(int tramo) {
        if (tramo == 1 && pe1 > 0) return pe1;
        if (tramo == 2 && pe2 > 0) return pe2;
        if (tramo == 3 && pe3 > 0) return pe3;
        return pe;
    }

    /** El precio de energía que está vigente hoy: el leído aparte, o el único si no hay. */
    public double nowEnergyPrice() {
        return peNow > 0 ? peNow : pe;
    }

    /**
     * Término de energía del año con tu tarifa: por tramos si están los tres precios y el
     * reparto del año, y si no con el precio **vigente hoy**. Esto es para repreciar el año
     * (el comparador); reproducir una factura concreta es `energyCostBill()`, que va con el
     * efectivo.
     */
    public double energyCostYear() {
        if (kwhYear <= 0) return 0;
        if (hasTramos() && kwh1 + kwh2 + kwh3 > 0) {
            return kwh1 * pe1 + kwh2 * pe2 + kwh3 * pe3;
        }
        return kwhYear * nowEnergyPrice();
    }

    /** Lo mismo para un periodo facturado, con sus kWh por tramo. */
    public double energyCostBill() {
        int tramos = billKwh1 + billKwh2 + billKwh3;
        double kwh = billingKwh();
        if (hasTramos() && tramos > 0
                && (billed <= 0 || Math.abs(billed - tramos) <= Math.max(2, tramos * 0.02))) {
            return billKwh1 * pe1 + billKwh2 * pe2 + billKwh3 * pe3;
        }
        return kwh * pe;
    }

    /** Desglose calculado con tus precios, redondeando cada línea como la factura. */
    public static class Sim {
        public int days = 30;
        public double energy, comp, power, bono, rent, tax, subtotal, iee, totalElec, iva, total;
    }

    public Sim simulate() {
        Sim s = new Sim();
        s.days = invoiceDays > 0 ? invoiceDays : 30;
        double kwh = billingKwh();
        s.energy = round2(energyCostBill());
        s.comp = round2(Math.min(Math.max(0, surplusEur), s.energy));
        s.power = round2((p1 * pp1 + p2 * pp2) * s.days);
        s.bono = round2(bono * s.days);
        s.rent = round2(rent * s.days);
        s.tax = round2(kwh * taxKwh);
        s.subtotal = round2(s.energy - s.comp + s.power + s.bono + s.tax);
        s.iee = round2(s.subtotal * iee / 100.0);
        s.totalElec = round2(s.subtotal + s.iee + s.rent);
        s.iva = round2(s.totalElec * iva / 100.0);
        s.total = round2(s.totalElec + s.iva);
        return s;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public boolean complete() {
        return cp != null && !cp.trim().isEmpty() && kwhYear > 0;
    }
}
