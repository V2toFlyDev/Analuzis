import com.analizis.app.BillData;
import com.analizis.app.PdfPrices;
import com.analizis.app.PdfReader;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

/** Comprobación de extremo a extremo: PDF -> texto -> precios -> importe. */
public class PdfCheck {

    public static void main(String[] args) throws Exception {
        byte[] pdf = Files.readAllBytes(Paths.get(args[0]));
        String text = PdfReader.text(pdf);
        System.out.println("--- texto extraído (" + text.length() + " caracteres) ---");
        System.out.println(text.length() > 900 ? text.substring(0, 900) : text);

        BillData b = new BillData();
        b.billKwh1 = 120;
        b.billKwh2 = 80;
        b.billKwh3 = 100;
        b.invoiceDays = 30;
        b.invoiceTotal = 75.00;
        b.surplusEur = 9.00;
        b.p1 = 5;
        b.p2 = 5;

        boolean ok = PdfPrices.fill(b, text);
        System.out.println("--- precios --- encontrado=" + ok
                + " energia=" + b.pe + " P1=" + b.pp1 + " P2=" + b.pp2
                + " excedentes=" + b.ps + " bono=" + b.bono + " contador=" + b.rent
                + " iee=" + b.iee + " iva=" + b.iva);

        BillData.Sim s = b.simulate();
        System.out.println(String.format(Locale.US, "TOTAL %.2f (factura %.2f) -> %s",
                s.total, b.invoiceTotal,
                Math.abs(s.total - b.invoiceTotal) <= 0.02 ? "CUADRA" : "NO CUADRA"));
    }
}
