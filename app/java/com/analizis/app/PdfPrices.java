package com.analizis.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee del texto de la factura los precios reales de la tarifa: energía (€/kWh),
 * potencia P1/P2 (€/kW·día), excedentes (€/kWh) y los recargos fijos (€/día),
 * además del periodo, el importe, las potencias contratadas y los kWh por tramo.
 *
 * El texto de un PDF sale en el orden en que está dibujado, no en el que se lee: en
 * una tabla el precio y su unidad pueden acabar en líneas distintas, y el rótulo
 * («Término de energía») en otra. Por eso cada concepto se busca por su etiqueta y
 * se queda con el precio de la primera unidad que aparece después; si la factura no
 * trae etiqueta clara, se busca la unidad y se toma el número más cercano.
 */
public class PdfPrices {

    // Unidades tal y como las deja norm(). El € es opcional: hay PDFs que pierden el
    // glifo del euro y dejan «/kWh». Que el precio sea bueno lo decide el total.
    private static final String U_KWH = "€?/kWh";
    private static final String U_KWD = "€?/kW·día";
    private static final String U_DAY = "€?/día";

    // Forma canónica a la que se llevan las unidades en norm().
    private static final String L_KWH = "€/kWh";
    private static final String L_KWD = "€/kW·día";
    private static final String L_DAY = "€/día";

    private static final String ENERGY_LABEL =
            "(?:t[eé]rmino\\s+(?:de\\s+)?energ[ií]a"
                    + "|energ[ií]a\\s+consumida"
                    + "|precio\\s+(?:de\\s+la\\s+)?energ[ií]a"
                    + "|coste\\s+(?:de\\s+la\\s+)?energ[ií]a)";
    private static final String POWER_LABEL =
            "potencia(?:\\s+contratada)?[^€]{0,40}?(?:\\bP%d\\b|\\b%s\\b)";

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern PERIOD = Pattern.compile(
            "[Pp]er[ií]odo[^0-9]{0,40}?(\\d{1,2})/(\\d{1,2})/(\\d{4})\\s*(?:al|a|hasta|-)\\s*(\\d{1,2})/(\\d{1,2})/(\\d{4})");
    private static final Pattern PERIOD_ANY = Pattern.compile(
            "(\\d{1,2})/(\\d{1,2})/(\\d{4})\\s*(?:al|a|hasta|-)\\s*(\\d{1,2})/(\\d{1,2})/(\\d{4})");
    private static final Pattern TOTAL = Pattern.compile(
            "[Tt]otal\\s+(?:a\\s+pagar|factura|importe|de\\s+la\\s+factura)[^0-9]{0,25}([0-9]+[.,][0-9]+)\\s*€");
    // «Total factura» es la luz con IVA ANTES de descontar la batería virtual; es lo
    // que reproducen los precios. «Total a pagar» puede ser menor si se tira del saldo.
    private static final Pattern ELEC_TOTAL = Pattern.compile(
            "[Tt]otal\\s+factura[^0-9]{0,25}([0-9]+[.,][0-9]+)\\s*€");
    private static final Pattern TOTAL_ANY = Pattern.compile(
            "[Tt][Oo][Tt][Aa][Ll]\\s+(?:FACTURA|A PAGAR)?[^0-9]{0,25}([0-9]+[.,][0-9]+)\\s*€");
    // «Total electricidad» es la luz SIN IVA (antes del contador y de fianzas, saldos
    // o devoluciones). Con su IVA da lo que cuesta la luz, que es lo comparable con
    // las ofertas: el «Total a pagar» puede traer cosas que no son electricidad.
    private static final Pattern TOTAL_ELEC_NET = Pattern.compile(
            "[Tt]otal\\s+electricidad[^0-9]{0,25}([0-9]+[.,][0-9]+)\\s*€");
    private static final Pattern ENERGY_SUBTOTAL = Pattern.compile(
            "[Ss]ubtotal\\s+(?:de\\s+)?[Ee]nerg[ií]a(?:\\s+[Dd]emandada)?[^0-9]{0,25}([0-9]+[.,][0-9]+)\\s*€");
    // Lo que la factura compensa de verdad: si una parte se va a la batería virtual,
    // el subtotal es menor que la valoración de los excedentes.
    private static final Pattern SURPLUS_APPLIED = Pattern.compile(
            "[Ss]ubtotal\\s+[Cc]ompensaci[oó]n\\s+[Ee]xcedentes[^0-9]{0,20}(-?[0-9]+[.,][0-9]+)\\s*€(?!\\s*/)");
    // La misma línea con otros nombres. El importe puede venir en negativo; el €/kWh de
    // una condición no cuenta, solo un importe en euros.
    private static final Pattern SURPLUS_ANY = Pattern.compile(
            "(?:[Cc]ompensaci[oó]n(?:\\s+de)?\\s+(?:los\\s+)?[Ee]xcedentes"
                    + "|[Vv]aloraci[oó]n(?:\\s+de)?\\s+(?:los\\s+)?[Ee]xcedentes"
                    + "|[Ii]mporte\\s+(?:de\\s+la\\s+)?[Cc]ompensaci[oó]n(?:\\s+de\\s+excedentes)?)"
                    + "[^0-9]{0,25}(-?[0-9]+[.,][0-9]+)\\s*€(?!\\s*/)");
    // «1.075 kWh x 0,120471 €/kWh»: el consumo que la factura cobra de verdad.
    private static final Pattern ENERGY_CHARGE = Pattern.compile(
            "(-?)([0-9][0-9.,]*)\\s*kWh\\s*x\\s*[0-9][0-9.,]*\\s*€?/kWh",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POW_P1 = Pattern.compile(
            "Potencia\\s+contratada\\s*(?:P1|punta)[^0-9]{0,20}([0-9]+[.,][0-9]+)\\s*kW");
    private static final Pattern POW_P2 = Pattern.compile(
            "Potencia\\s+contratada\\s*(?:P2|valle)[^0-9]{0,20}([0-9]+[.,][0-9]+)\\s*kW");
    private static final Pattern KWH_P = Pattern.compile(
            "Punta\\s+(?:real|estimad[oa])[^0-9]{0,15}([0-9.]+)\\s+([0-9.]+)\\s*kWh");
    private static final Pattern KWH_L = Pattern.compile(
            "Llano\\s+(?:real|estimad[oa])[^0-9]{0,15}([0-9.]+)\\s+([0-9.]+)\\s*kWh");
    private static final Pattern KWH_V = Pattern.compile(
            "Valle\\s+(?:real|estimad[oa])[^0-9]{0,15}([0-9.]+)\\s+([0-9.]+)\\s*kWh");
    private static final Pattern BILLED = Pattern.compile(
            "[Cc]onsumo\\s+(?:de\\s+)?(?:electricidad|neto|total)[^0-9]{0,40}([0-9][0-9.,]*)\\s*kWh");
    private static final Pattern IEE = Pattern.compile(
            "[Ii]mpuesto\\s+(?:especial\\s+)?(?:sobre\\s+)?(?:la\\s+)?electricidad[^%]{0,120}?([0-9]+[.,][0-9]+)\\s*%");
    private static final Pattern IVA = Pattern.compile(
            "IVA\\s*(?:\\(([0-9]+[.,]?[0-9]*)\\s*%\\)|([0-9]+[.,]?[0-9]*)\\s*%)");

    /** Rellena los precios de la factura. Devuelve true si ha encontrado la energía. */
    public static boolean fill(BillData b, String text) {
        if (b == null || text == null || text.isEmpty()) return false;
        String t = norm(text);

        // ---- precios ----
        List<Hit> kwhHits = hits(t, U_KWH, 0.005, 0.90);
        double ps = afterLabel(t, "excedent", U_KWH, 0.005, 0.90);
        if (ps <= 0) ps = windowPick(kwhHits, new String[]{"excedent"}, null);

        double pe = afterLabel(t, ENERGY_LABEL, U_KWH, 0.01, 0.90);
        if (pe <= 0) {
            pe = windowPick(kwhHits,
                    new String[]{"energ", "consum", "término", "termino"},
                    new String[]{"excedent"});
        }
        if (pe <= 0) pe = windowPick(kwhHits, null, new String[]{"excedent"});

        double p1 = afterLabel(t, powerLabel(1, "punta"), U_KWD, 0.0005, 1.20);
        double p2 = afterLabel(t, powerLabel(2, "valle"), U_KWD, 0.0005, 1.20);
        if (p1 <= 0 || p2 <= 0) {
            List<Double> pv = distinct(hits(t, U_KWD, 0.0005, 1.20));
            if (p1 > 0 && p2 <= 0) {
                p2 = other(pv, p1);
            } else if (p2 > 0 && p1 <= 0) {
                p1 = other(pv, p2);
            } else {
                if (!pv.isEmpty()) p1 = pv.get(0);
                if (pv.size() > 1) p2 = pv.get(1);
            }
        }
        if (p1 <= 0 && p2 > 0) p1 = p2;
        if (p2 <= 0 && p1 > 0) p2 = p1;

        // ---- precios por tramo (punta, llano, valle) ----
        // Son las líneas «379 kWh x 0,150000 €/kWh» de cada bloque. Si no están las tres,
        // se quedan fuera y manda el precio único: nunca se mezcla una con las demás.
        double[] tp = periodPrices(t);
        if (tp != null) {
            b.pe1 = tp[0];
            b.pe2 = tp[1];
            b.pe3 = tp[2];
        }
        // El precio vigente hoy es el de la última línea de cargo del consumo. Solo se lee
        // aparte cuando la factura tiene un precio único: con tramos, los tres precios de
        // periodo ya son los de la tarifa y no hay media que separar.
        if (tp == null) {
            double now = lastChargePrice(t);
            if (now > 0) b.peNow = now;
        }

        boolean any = false;
        if (pe > 0) {
            b.pe = pe;
            any = true;
        }
        if (ps > 0) b.ps = ps;
        if (p1 > 0) b.pp1 = p1;
        if (p2 > 0) b.pp2 = p2;

        // ---- periodo, importe, potencias contratadas y tramos ----
        Matcher per = PERIOD.matcher(t);
        boolean hasPeriod = per.find();
        if (!hasPeriod) {
            per = PERIOD_ANY.matcher(t);
            hasPeriod = per.find();
        }
        if (hasPeriod) {
            LocalDate from = date(per.group(1), per.group(2), per.group(3));
            LocalDate to = date(per.group(4), per.group(5), per.group(6));
            if (from != null && to != null && !to.isBefore(from)) {
                b.from = from.toString();
                b.to = to.toString();
                b.invoiceDays = (int) ChronoUnit.DAYS.between(from, to) + 1;
            }
        }

        double total = first(TOTAL, t, 1);
        if (total <= 0) total = first(TOTAL_ANY, t, 1);
        if (total > 0) b.invoiceTotal = total;

        // La luz con impuestos: «Total electricidad» más su IVA. Así quedan fuera las
        // fianzas, devoluciones o el saldo de la batería que van en el total a pagar.
        double ivaFactura = first(IVA, t, 1);
        if (ivaFactura <= 0) ivaFactura = first(IVA, t, 2);
        double elec = 0;
        double elecNet = first(TOTAL_ELEC_NET, t, 1);
        if (elecNet > 0) {
            double iv = ivaFactura > 0 && ivaFactura < 100 ? ivaFactura : b.iva;
            elec = BillData.round2(elecNet + BillData.round2(elecNet * iv / 100.0));
        }
        if (elec <= 0) elec = first(ELEC_TOTAL, t, 1);
        // Si tampoco hay «Total factura», lo que se paga ya es la luz.
        if (elec <= 0) elec = total;
        if (elec > 0) b.invoiceElec = elec;

        // Excedentes aplicados en la factura (sin impuestos). Se acepta el importe en
        // negativo y los nombres que usan las comercializadoras para la misma línea.
        double applied = first(SURPLUS_APPLIED, t, 1);
        if (applied == 0) applied = first(SURPLUS_ANY, t, 1);
        applied = Math.abs(applied);
        if (applied > 0 && applied < 100000) {
            b.surplus = applied;
            b.surplusEur = applied;
        }

        // La energía se cobra por los kWh del detalle (379), no por la suma de las
        // lecturas del contador (398).
        int billed = billedKwh(text);
        if (billed > 0 && billed <= 50000) b.billed = billed;

        // Una vivienda no pasa de 100 kW: si sale más, es una lectura mala.
        double pw1 = first(POW_P1, t, 1);
        if (pw1 > 0 && pw1 <= 100) b.p1 = pw1;
        double pw2 = first(POW_P2, t, 1);
        if (pw2 > 0 && pw2 <= 100) b.p2 = pw2;


        // Si el periodo viene partido por un cambio de tarifa, el término de potencia
        // son dos líneas: se usa el precio efectivo para reproducir la factura. Con una
        // sola línea se conserva el precio exacto de la factura: recalcularlo desde un
        // importe redondeado a céntimos cambia el precio en millonésimas y hacía creer
        // que la tarifa había cambiado de un mes a otro. En ambos casos se guarda aparte
        // el precio de la última línea, que es el vigente para comparar tarifas.
        if (b.invoiceDays > 0) {
            double[] c1 = powerCharge(t, "P1|punta");
            double[] c2 = powerCharge(t, "P2|valle");
            if (c1[0] > 0 && b.p1 > 0 && c1[1] > 1) {
                double eff = c1[0] / (b.p1 * b.invoiceDays);
                if (eff > 0.0005 && eff < 1.20) b.pp1 = eff;
            }
            if (c2[0] > 0 && b.p2 > 0 && c2[1] > 1) {
                double eff = c2[0] / (b.p2 * b.invoiceDays);
                if (eff > 0.0005 && eff < 1.20) b.pp2 = eff;
            }
            if (c1[2] > 0) b.pp1Now = c1[2];
            if (c2[2] > 0) b.pp2Now = c2[2];
        }

        int kp = kwhOf(KWH_P, t), kl = kwhOf(KWH_L, t), kv = kwhOf(KWH_V, t);
        // Una casa no gasta 50.000 kWh en un periodo: si sale más, la lectura es mala.
        if (kp + kl + kv > 0 && kp + kl + kv <= 50000) {
            b.billKwh1 = kp;
            b.billKwh2 = kl;
            b.billKwh3 = kv;
        }

        // Los kWh que cobra la factura tienen que parecerse a la suma de sus lecturas
        // (pueden diferir por el ajuste de la distribuidora). Si se van mucho, el dato
        // leído es basura: se descarta y se usan las lecturas.
        int tramos = b.billKwh1 + b.billKwh2 + b.billKwh3;
        if (b.billed > 0 && tramos > 0
                && (b.billed < tramos * 0.5 || b.billed > tramos * 2)) {
            b.billed = 0;
        }

        // Con dos precios en el mismo periodo (cambio de tarifa a mitad de mes), el
        // precio que reproduce la factura es el efectivo: coste / kWh. Se calcula ya
        // con los kWh validados, no con la primera lectura.
        int cobrados = b.billed > 0 ? b.billed : tramos;
        if (cobrados > 0) {
            double energyCost = first(ENERGY_SUBTOTAL, t, 1);
            if (energyCost > 0) {
                double eff = energyCost / cobrados;
                if (eff >= 0.005 && eff <= 0.90) b.pe = eff;
            }
        }

        // ---- recargos fijos e impuestos ----
        double bono = afterLabel(t, "\\bbono\\b", U_DAY, 0.000001, 3.0);
        if (bono <= 0) bono = windowPick(hits(t, U_DAY, 0.000001, 3.0), new String[]{"bono"}, null);
        if (bono > 0) b.bono = bono;

        double rent = afterLabel(t, "\\bcontador\\b|\\balquiler\\b", U_DAY, 0.000001, 3.0);
        if (rent > 0) b.rent = rent;

        double iee = first(IEE, t, 1);
        double iva = first(IVA, t, 1);
        if (iva <= 0) iva = first(IVA, t, 2);
        if (iva > 0 && iva < 100) b.iva = iva;

        // «Mínimo comunitario»: cuando la factura no cobra el impuesto eléctrico por
        // porcentaje, lo cobra como un fijo por kWh dentro del subtotal.
        double minTax = afterLabel(t, "m[ií]nimo\\s+comunitario", U_KWH, 0.00001, 0.05);
        if (minTax > 0) {
            b.taxKwh = minTax;
            b.iee = iee > 0 && iee < 30 ? iee : 0;
        } else if (iee > 0 && iee < 30) {
            b.iee = iee;
        }

        return any;
    }

    /**
     * kWh que la factura cobra de verdad («Consumo electricidad 53 kWh»). No siempre
     * coincide con lo que declara el QR, que va por lecturas de contador: para cuadrar
     * el importe hay que usar este.
     */
    public static int billedKwh(String text) {
        if (text == null || text.isEmpty()) return 0;
        String t = norm(text);
        String low = t.toLowerCase(java.util.Locale.ROOT);

        // Se suman las líneas «N kWh x precio» del bloque de consumo, sin contar los
        // excedentes (van en negativo y en su propio apartado). Cubre las facturas con
        // el mes partido en dos por un cambio de precio.
        int start = low.indexOf("consumo electricidad");
        if (start < 0) start = low.indexOf("consumo de electricidad");
        if (start >= 0) {
            int end = low.length();
            for (String stop : new String[]{"valoración excedentes", "valoracion excedentes",
                    "subtotal energía", "subtotal energia"}) {
                int p = low.indexOf(stop, start);
                if (p > start && p < end) end = p;
            }
            double sum = 0;
            Matcher m = ENERGY_CHARGE.matcher(t.substring(start, end));
            while (m.find()) {
                if (m.group(1).isEmpty()) sum += kwhNum(m.group(2));
            }
            if (sum > 0) return (int) Math.round(sum);
        }

        Matcher m = BILLED.matcher(t);
        if (!m.find()) return 0;
        return (int) Math.round(kwhNum(m.group(1)));
    }

    /**
     * kWh con separador de miles («1.075» son mil setenta y cinco, no 1,075).
     * Solo se interpreta así un número con un único separador y tres cifras detrás.
     */
    static double kwhNum(String s) {
        if (s == null) return 0;
        String v = s.trim();
        if (v.matches("[0-9]{1,3}[.,][0-9]{3}")) {
            try {
                return Double.parseDouble(v.substring(0, v.length() - 4)
                        + v.substring(v.length() - 3));
            } catch (Exception e) {
                return num(v);
            }
        }
        return num(v);
    }

    /**
     * Suma, número de cargos y último precio del término de potencia de un periodo.
     * El bloque acaba donde empieza el otro término o la sección siguiente.
     */
    private static double[] powerCharge(String t, String which) {
        Matcher l = Pattern.compile("t[eé]rmino\\s+potencia\\s*(?:" + which + ")",
                Pattern.CASE_INSENSITIVE).matcher(t);
        if (!l.find()) return new double[]{0, 0, 0};
        int start = l.end();
        int end = Math.min(t.length(), start + 400);
        Matcher next = Pattern.compile(
                "t[eé]rmino\\s+potencia|financiaci[oó]n|bono social|subtotal|otros conceptos|alquiler",
                Pattern.CASE_INSENSITIVE).matcher(t);
        next.region(start, end);
        if (next.find()) end = next.start();
        double sum = 0;
        double last = 0;
        int count = 0;
        Matcher m = Pattern.compile(L_KWD + "\\s*([0-9]+[.,][0-9]+)\\s*€").matcher(t);
        m.region(start, end);
        while (m.find()) {
            sum += num(m.group(1));
            count++;
        }
        if (count > 0) last = lastPowerPrice(t, start, end);
        return new double[]{sum, count, last};
    }

    /** Último precio €/kW·día del bloque, que es el vigente si el mes viene partido. */
    private static double lastPowerPrice(String t, int start, int end) {
        Matcher m = Pattern.compile(L_KWD).matcher(t);
        m.region(start, end);
        double last = 0;
        while (m.find()) {
            Double v = closest(t, m.start(), m.end());
            if (v != null && v > 0.0005 && v < 1.20) last = v;
        }
        return last;
    }

    private static String powerLabel(int n, String word) {
        return String.format(POWER_LABEL, n, word);
    }

    // «379 kWh x 0,150000 €/kWh»: la unidad ya viene normalizada.
    private static final Pattern CHARGE = Pattern.compile(
            "(?i)kwh\\s*x\\s*([0-9][0-9.,]*)\\s*€?/\\s*k\\s*wh");

    /**
     * Precio de la energía en cada tramo, sacado de la línea de cargo de su bloque. Se mira
     * el texto que hay justo antes del precio y gana el periodo cuya palabra esté más cerca;
     * si no aparecen los tres, no se devuelve nada (mejor el precio único que uno inventado).
     */
    private static double[] periodPrices(String t) {
        double[] out = new double[]{0, 0, 0};
        Matcher m = CHARGE.matcher(t);
        while (m.find()) {
            double v = num(m.group(1));
            if (v < 0.005 || v > 0.90) continue;
            String before = t.substring(Math.max(0, m.start() - 160), m.start())
                    .toLowerCase(java.util.Locale.ROOT);
            int punta = before.lastIndexOf("punta");
            int llano = before.lastIndexOf("llano");
            int valle = before.lastIndexOf("valle");
            int which = 0;
            int near = -1;
            if (punta > near) {
                near = punta;
                which = 1;
            }
            if (llano > near) {
                near = llano;
                which = 2;
            }
            if (valle > near) {
                near = valle;
                which = 3;
            }
            if (which > 0 && out[which - 1] <= 0) out[which - 1] = v;
        }
        if (out[0] <= 0 || out[1] <= 0 || out[2] <= 0) return null;
        return out;
    }

    /**
     * Precio de la última línea «N kWh x P €/kWh» del bloque de consumo: si el mes viene
     * partido por un cambio de precio, esa es la que está vigente. Devuelve 0 si no la
     * encuentra (entonces se usa el precio efectivo también para repreciar).
     */
    private static double lastChargePrice(String t) {
        String low = t.toLowerCase(java.util.Locale.ROOT);
        int start = low.indexOf("consumo electricidad");
        if (start < 0) start = low.indexOf("consumo de electricidad");
        if (start < 0) return 0;
        int end = low.length();
        for (String stop : new String[]{"valoración excedentes", "valoracion excedentes",
                "subtotal energía", "subtotal energia"}) {
            int p = low.indexOf(stop, start);
            if (p > start && p < end) end = p;
        }
        double last = 0;
        Matcher m = CHARGE.matcher(t.substring(start, end));
        while (m.find()) {
            double v = num(m.group(1));
            if (v >= 0.005 && v <= 0.90) last = v;
        }
        return last;
    }

    // ================= precios =================

    /** Un precio encontrado y el trozo de texto que lo rodea (para saber de qué es). */
    private static final class Hit {
        double value;
        String around;
    }

    /**
     * Con la etiqueta delante («Bono social», «Término de potencia P2»…), se queda con
     * el primer precio válido de la unidad que venga después. Así no se mezclan filas
     * vecinas que comparten unidad.
     */
    private static double afterLabel(String t, String labelRe, String unitRe, double min, double max) {
        List<Double> vals = new ArrayList<>();
        Matcher l = Pattern.compile(labelRe, Pattern.CASE_INSENSITIVE).matcher(t);
        List<int[]> us = spans(t, unitRe);
        while (l.find()) {
            int limit = Math.min(t.length(), l.end() + 220);
            for (int[] u : us) {
                if (u[0] < l.end() || u[0] > limit) continue;
                Double v = closest(t, u[0], u[1]);
                if (v != null && v >= min && v <= max) {
                    vals.add(v);
                    break;
                }
            }
        }
        return mode(vals);
    }

    /** Posiciones de cada unidad (€/kWh, /kWh…) en el texto ya normalizado. */
    private static List<int[]> spans(String t, String unitRe) {
        List<int[]> out = new ArrayList<>();
        Matcher m = Pattern.compile(unitRe).matcher(t);
        while (m.find()) out.add(new int[]{m.start(), m.end()});
        return out;
    }

    /** Sin etiqueta fiable: cada aparición de la unidad con el número más cercano. */
    private static List<Hit> hits(String t, String unitRe, double min, double max) {
        List<Hit> out = new ArrayList<>();
        for (int[] u : spans(t, unitRe)) {
            boolean cents = u[0] > 0 && t.charAt(u[0] - 1) == 'c';
            Double v = closest(t, u[0], u[1]);
            if (v == null) continue;
            if (cents) v = v / 100.0;
            if (v < min || v > max) continue;
            Hit h = new Hit();
            h.value = v;
            int from = Math.max(0, u[0] - 80);
            int to = Math.min(t.length(), u[1] + 12);
            h.around = t.substring(from, to).toLowerCase();
            out.add(h);
        }
        return out;
    }

    /** El número más cercano a la unidad, ignorando cantidades (kWh, kW) y porcentajes. */
    private static Double closest(String t, int unitStart, int unitEnd) {
        Double best = null;
        int bestD = Integer.MAX_VALUE;
        int from = Math.max(0, unitStart - 60);
        int to = Math.min(t.length(), unitEnd + 60);
        Matcher m = NUMBER.matcher(t);
        m.region(from, to);
        while (m.find()) {
            String after = t.substring(m.end(), Math.min(t.length(), m.end() + 8))
                    .trim().toLowerCase();
            if (after.startsWith("kwh") || after.startsWith("kw") || after.startsWith("%")
                    || after.startsWith("días") || after.startsWith("dias")) {
                continue;   // es una cantidad o un impuesto, no un precio
            }
            // Se prefiere el número de la izquierda de la unidad.
            int d = m.end() <= unitStart
                    ? unitStart - m.end()
                    : (m.start() - unitEnd) + 10;
            if (d < bestD) {
                bestD = d;
                best = num(m.group());
            }
        }
        return bestD <= 60 ? best : null;
    }

    private static double windowPick(List<Hit> list, String[] must, String[] forbid) {
        List<Double> vals = new ArrayList<>();
        for (Hit h : list) {
            if (must != null && !anyOf(h.around, must)) continue;
            if (forbid != null && anyOf(h.around, forbid)) continue;
            vals.add(h.value);
        }
        return mode(vals);
    }

    /** Gana el precio que más se repite (sale en el detalle y en el resumen); si empatan, el primero. */
    private static double mode(List<Double> vals) {
        Map<Long, Integer> count = new LinkedHashMap<>();
        Map<Long, Double> value = new LinkedHashMap<>();
        for (double v : vals) {
            long k = Math.round(v * 1000000);
            int c = count.containsKey(k) ? count.get(k) : 0;
            count.put(k, c + 1);
            if (!value.containsKey(k)) value.put(k, v);
        }
        long best = 0;
        int bestCount = 0;
        for (Map.Entry<Long, Integer> e : count.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return bestCount == 0 ? 0 : value.get(best);
    }

    /** Valores distintos, en el orden en que aparecen. */
    private static List<Double> distinct(List<Hit> list) {
        List<Double> out = new ArrayList<>();
        for (Hit h : list) {
            boolean seen = false;
            for (double v : out) if (Math.abs(v - h.value) < 0.0000001) { seen = true; break; }
            if (!seen) out.add(h.value);
        }
        return out;
    }

    private static double other(List<Double> values, double known) {
        for (double v : values) if (Math.abs(v - known) > 0.0000001) return v;
        return known;
    }

    private static boolean anyOf(String text, String[] words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }

    // ================= normalización =================

    /**
     * Deja el texto en una sola línea y con las unidades siempre igual escritas, para
     * buscar sin depender de dónde puso el PDF cada salto de línea.
     */
    private static String norm(String s) {
        String t = s.replace('\u00A0', ' ').replace('\u2009', ' ').replace('\u202F', ' ')
                .replace("EUR", "€").replace("Eur", "€").replace("eur", "€")
                .replace('\u00A4', '€')
                .replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        // Un decimal partido por un espacio: "0,134 400" o "0 ,134400".
        t = t.replaceAll("(?<=\\d)\\s+(?=[.,])", "");
        t = t.replaceAll("(?<=[.,])\\s+(?=\\d)", "");
        // Unidades: €/kWh, €/kW·día (o «kW día», «kW y día»), €/día; el euro puede
        // faltar y la unidad queda como «/kWh».
        t = t.replaceAll("(?i)(€\\s*)?/\\s*kW\\s*(?:[·.]|\\bde\\b|\\by\\b)?\\s*d[ií]a", L_KWD);
        t = t.replaceAll("(?i)(€\\s*)?/\\s*k\\s*wh", L_KWH);
        t = t.replaceAll("(?i)(€\\s*)?/\\s*d[ií]a", L_DAY);
        t = t.replaceAll("\\s+", " ");
        return t;
    }

    private static LocalDate date(String d, String m, String y) {
        try {
            return LocalDate.of(Integer.parseInt(y), Integer.parseInt(m), Integer.parseInt(d));
        } catch (Exception e) {
            return null;
        }
    }

    private static int kwhOf(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (!m.find()) return 0;
        // el grupo 1 es la lectura del contador y el 2 los kWh del periodo
        int v = (int) Math.round(num(m.group(2)));
        if (v <= 0) {
            // por si el orden viene al revés (kWh antes de la lectura)
            v = (int) Math.round(num(m.group(1)));
            if (v > 100000) v = 0;
        }
        return v;
    }

    private static double first(Pattern p, String text, int group) {
        Matcher m = p.matcher(text);
        if (m.find()) {
            try {
                return num(m.group(group));
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    /** Acepta 0,134400 y 0.134400 (y 1.234,56 con miles). */
    static double num(String s) {
        if (s == null) return 0;
        String v = s.trim().replace("€", "").replace("%", "").trim();
        if (v.isEmpty()) return 0;
        int comma = v.lastIndexOf(',');
        int dot = v.lastIndexOf('.');
        try {
            if (comma > dot) {
                v = v.replace(".", "").replace(",", ".");
            } else if (dot > comma) {
                v = v.replace(",", "");
            } else {
                v = v.replace(",", ".");
            }
            return Double.parseDouble(v);
        } catch (Exception e) {
            return 0;
        }
    }
}
