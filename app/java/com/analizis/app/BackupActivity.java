package com.analizis.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Copia de seguridad local: crea o restaura un JSON con los datos de la app. */
public class BackupActivity extends Activity {

    private static final int REQ_SAVE = 201;
    private static final int REQ_OPEN = 202;

    private Store store;
    private Button btnExport, btnRestore;
    private TextView summary, status;

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_backup);
        Insets.apply(findViewById(R.id.root), dp(20), dp(28));
        store = new Store(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnExport = findViewById(R.id.btnExport);
        btnRestore = findViewById(R.id.btnRestore);
        summary = findViewById(R.id.backupSummary);
        status = findViewById(R.id.backupStatus);

        btnExport.setOnClickListener(v -> chooseExport());
        btnRestore.setOnClickListener(v -> chooseRestore());
        renderSummary();
    }

    private void renderSummary() {
        int invoices = store.invoices().size();
        int solar = store.solarMonths().size();
        Datadis.Result datadis = store.datadisResult();
        int datadisMonths = datadis == null ? 0 : datadis.months.size();
        boolean curve = store.curve().ok();
        boolean pvpc = store.pvpc() != null;
        summary.setText(invoices + (invoices == 1 ? " factura" : " facturas")
                + " · " + solar + " meses del inversor"
                + " · " + datadisMonths + " meses de Datadis"
                + " · " + (curve ? "curva sí" : "sin curva")
                + " · " + (pvpc ? "PVPC sí" : "sin PVPC"));
    }

    private void chooseExport() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, Backup.suggestedName());
        try {
            startActivityForResult(i, REQ_SAVE);
        } catch (Exception e) {
            error("No he podido abrir el selector de archivos.");
        }
    }

    private void chooseRestore() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        try {
            startActivityForResult(i, REQ_OPEN);
        } catch (Exception e) {
            error("No he podido abrir el selector de archivos.");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_SAVE) save(uri);
        else if (req == REQ_OPEN) load(uri);
    }

    private void save(final Uri uri) {
        setBusy(true, "Creando la copia…");
        pool.execute(() -> {
            try {
                JSONObject root = Backup.capture(this);
                String text = root.toString();
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new Exception("No se puede escribir en ese archivo.");
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                }
                ui.post(() -> {
                    setBusy(false, "");
                    toast("Copia guardada");
                    renderSummary();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    setBusy(false, "");
                    error("No he podido crear la copia: " + message(e));
                });
            }
        });
    }

    private void load(final Uri uri) {
        setBusy(true, "Leyendo la copia…");
        pool.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception("No se puede leer ese archivo.");
                String text = Backup.readText(in);
                JSONObject root = new JSONObject(text);
                if (!"Analuzis".equals(root.optString("app", ""))
                        || root.optInt("format", 0) != Backup.FORMAT) {
                    throw new Exception("El archivo no es una copia de Analuzis compatible.");
                }
                Backup.Summary s = Backup.summary(root);
                ui.post(() -> {
                    setBusy(false, "");
                    confirmRestore(root, s);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    setBusy(false, "");
                    error("No he podido leer la copia: " + message(e));
                });
            }
        });
    }

    private void confirmRestore(final JSONObject root, Backup.Summary s) {
        new AlertDialog.Builder(this)
                .setTitle("¿Restaurar esta copia?")
                .setMessage("Se sustituirán los datos actuales de Analuzis.\n\n" + s.text()
                        + "\n\nNo se restauran el NIF ni la contraseña de Datadis, ni el token de ESIOS.")
                .setPositiveButton("Restaurar", (d, w) -> restore(root))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void restore(final JSONObject root) {
        setBusy(true, "Restaurando…");
        pool.execute(() -> {
            try {
                Backup.Summary s = Backup.restore(this, root);
                ui.post(() -> {
                    setBusy(false, "");
                    setResult(RESULT_OK);
                    toast("Datos restaurados · " + s.text());
                    finish();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    setBusy(false, "");
                    error("No he podido restaurar: " + message(e));
                });
            }
        });
    }

    private void setBusy(boolean busy, String text) {
        btnExport.setEnabled(!busy);
        btnRestore.setEnabled(!busy);
        status.setVisibility(busy || (text != null && !text.isEmpty()) ? View.VISIBLE : View.GONE);
        if (text != null) status.setText(text);
    }

    private void error(String text) {
        new AlertDialog.Builder(this)
                .setTitle("Copia de seguridad")
                .setMessage(text)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private static String message(Exception e) {
        String s = e.getMessage();
        return s == null || s.isEmpty() ? e.getClass().getSimpleName() : s;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
    }
}
