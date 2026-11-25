import com.analizis.app.BillData;
import com.analizis.app.PdfPrices;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

/** Comprobación sin Android: lee un texto de factura y reproduce el importe. */
public class PriceCheck {

    public static void main(String[] args) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);

        BillData b = new BillData();
        b.billKwh1 = 120;
        b.billKwh2 = 80;
        b.billKwh3 = 100;
        b.invoiceDays = 30;
        b.invoiceTotal = 75.00;
        b.surplusEur = 9.00;
        b.p1 = 5;
        b.p2 = 5;
        b.kwhYear = 3000;
        b.historyCount = 1;
        b.historyDays = 30;

        boolean ok = PdfPrices.fill(b, text);
        System.out.println("encontrado=" + ok);
        System.out.println("periodo=" + b.from + " -> " + b.to + " (" + b.invoiceDays + " dias)"
                + " total=" + b.invoiceTotal + " pot=" + b.p1 + "/" + b.p2
                + " kwh=" + b.billKwh1 + "/" + b.billKwh2 + "/" + b.billKwh3);
        System.out.println("precios energia=" + b.pe + " P1=" + b.pp1 + " P2=" + b.pp2
                + " excedentes=" + b.ps + " bono=" + b.bono + " contador=" + b.rent
                + " iee=" + b.iee + " iva=" + b.iva);

        BillData.Sim s = b.simulate();
        System.out.println(String.format(Locale.US,
                "factura simulada: energia %.2f | exc -%.2f | pot %.2f | bono %.2f | subtotal %.2f"
                        + " | iee %.2f | contador %.2f | totalElec %.2f | iva %.2f | TOTAL %.2f",
                s.energy, s.comp, s.power, s.bono, s.subtotal, s.iee, s.rent, s.totalElec, s.iva, s.total));
        System.out.println(Math.abs(s.total - b.invoiceTotal) <= 0.02 ? "CUADRA" : "NO CUADRA");

        System.out.println(String.format(Locale.US,
                "anio con precios y %d kWh = %.2f | anualEstimate(historial 31d) = %.2f",
                b.kwhYear, b.annualCostFromPrices(), b.annualEstimate()));
    }
}
