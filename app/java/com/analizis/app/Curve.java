package com.analizis.app;

import org.json.JSONObject;

import java.time.LocalDate;
import java.util.TreeSet;

/**
 * Curva de consumo horario que llega de la API de Datadis.
 *
 * La llena {@link Datadis} lectura a lectura y se guarda ya resumida en las preferencias.
 *
 * No guarda la curva entera: solo lo que hace falta para comparar bien. La CNMC necesita
 * el reparto del consumo entre P1, P2 y P3; sin él calcula las tarifas con hora (PVPC y
 * demás precios variables) con un perfil estándar, y esas ofertas dejan de ser comparables.
 *
 * El reparto es el del peaje 2.0TD: laborables, P1 de 10 a 14 y de 18 a 22; P2 de 8 a 10,
 * de 14 a 18 y de 22 a 24; P3 el resto y todo el fin de semana. Los festivos entre semana
 * cuentan como laborables, porque el calendario de cada año no viene en los datos.
 */
public class Curve {

    public double kwh = 0;          // total del periodo, kWh
    public double kwh1 = 0;         // reparto por periodos 2.0TD
    public double kwh2 = 0;
    public double kwh3 = 0;
    public double maxP1 = 0;        // máxima media horaria en punta, kW
    public double maxP2 = 0;        // máxima media horaria en llano, kW
    public double maxP3 = 0;
    public int days = 0;            // días distintos que trae la curva
    public int points = 0;          // lecturas leídas
    public String from = "";
    public String to = "";

    private final TreeSet<String> dates = new TreeSet<>();

    public boolean ok() {
        return points > 0 && kwh > 0 && days > 0;
    }

    public double share1() { return kwh > 0 ? kwh1 / kwh : 0; }
    public double share2() { return kwh > 0 ? kwh2 / kwh : 0; }
    public double share3() { return kwh > 0 ? kwh3 / kwh : 0; }

    /**
     * Una lectura: el día, la hora en la que empieza el intervalo y los kWh del intervalo
     * (con lo que dura, para poder sacar la potencia media de las horas de 15 minutos).
     */
    public void add(LocalDate d, double startHour, double kwh, double hours) {
        if (d == null || startHour < 0 || kwh <= 0 || hours <= 0) return;
        double kw = kwh / hours;
        int p = period(d, startHour);
        if (p == 1) {
            kwh1 += kwh;
            if (kw > maxP1) maxP1 = kw;
        } else if (p == 2) {
            kwh2 += kwh;
            if (kw > maxP2) maxP2 = kw;
        } else {
            kwh3 += kwh;
            if (kw > maxP3) maxP3 = kw;
        }
        points++;
        dates.add(d.toString());
    }

    /** Cierra la curva: totales, días y fechas. */
    void finish() {
        kwh = kwh1 + kwh2 + kwh3;
        days = dates.size();
        from = dates.isEmpty() ? "" : dates.first();
        to = dates.isEmpty() ? "" : dates.last();
    }

    /** Periodo del 2.0TD: 1 punta, 2 llano, 3 valle. */
    static int period(LocalDate d, double hour) {
        if (d.getDayOfWeek().getValue() >= 6) return 3;
        int h = (int) Math.floor(hour);
        if ((h >= 10 && h < 14) || (h >= 18 && h < 22)) return 1;
        if ((h >= 8 && h < 10) || (h >= 14 && h < 18) || (h >= 22 && h < 24)) return 2;
        return 3;
    }

    static LocalDate date(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        int sp = t.indexOf(' ');
        if (sp > 0) t = t.substring(0, sp);
        try {
            if (t.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(t);
            if (t.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
                String[] p = t.split("/");
                return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Hora del dato tal y como la manda Datadis ("13:00", "13:15"), de 0 a 24. Es la hora
     * en la que ACABA el intervalo: "01:00" son las kWh de 00:00 a 01:00. -1 si no lo
     * parece.
     */
    static double hour(String s) {
        if (s == null) return -1;
        String t = s.trim();
        int colon = t.indexOf(':');
        if (colon <= 0) return -1;
        try {
            if (!t.substring(0, colon).trim().matches("\\d{1,2}")) return -1;
            int h = Integer.parseInt(t.substring(0, colon).trim());
            int m = Integer.parseInt(t.substring(colon + 1).replaceAll("[^0-9]", ""));
            if (h < 0 || h > 24 || (m != 0 && m != 15 && m != 30 && m != 45)) return -1;
            return h + m / 60.0;
        } catch (Exception e) {
            return -1;
        }
    }

    // ---------------- guardado ----------------

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("kwh", kwh);
        o.put("kwh1", kwh1);
        o.put("kwh2", kwh2);
        o.put("kwh3", kwh3);
        o.put("maxP1", maxP1);
        o.put("maxP2", maxP2);
        o.put("maxP3", maxP3);
        o.put("days", days);
        o.put("points", points);
        o.put("from", from);
        o.put("to", to);
        return o;
    }

    public static Curve fromJson(JSONObject o) {
        Curve c = new Curve();
        if (o == null) return c;
        c.kwh = o.optDouble("kwh", 0);
        c.kwh1 = o.optDouble("kwh1", 0);
        c.kwh2 = o.optDouble("kwh2", 0);
        c.kwh3 = o.optDouble("kwh3", 0);
        c.maxP1 = o.optDouble("maxP1", 0);
        c.maxP2 = o.optDouble("maxP2", 0);
        c.maxP3 = o.optDouble("maxP3", 0);
        c.days = o.optInt("days", 0);
        c.points = o.optInt("points", 0);
        c.from = o.optString("from", "");
        c.to = o.optString("to", "");
        return c;
    }
}
