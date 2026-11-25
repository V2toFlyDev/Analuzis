package com.analizis.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Escanea el QR de la factura con la cámara, en directo. */
public class ScanActivity extends Activity implements SurfaceHolder.Callback {

    public static final String EXTRA_QR = "qr";
    private static final String TAG = "AnaluzisScan";

    private SurfaceView preview;
    private TextView hint;
    private Button btnCancel;

    private HandlerThread thread;
    private Handler handler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private Size size;
    private boolean surfaceReady = false;
    private boolean done = false;
    private boolean firstFrame = false;

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private long lastDecode = 0;
    private long lastAf = 0;
    private int frames = 0;
    private boolean warned = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_scan);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Insets.apply(findViewById(R.id.topBar), dp(20), dp(0));

        preview = findViewById(R.id.preview);
        hint = findViewById(R.id.hint);
        btnCancel = findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> finish());
        preview.getHolder().addCallback(this);

        thread = new HandlerThread("camara");
        thread.start();
        handler = new Handler(thread.getLooper());

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == 1 && (res.length == 0 || res[0] != PackageManager.PERMISSION_GRANTED)) {
            toast("Sin permiso de cámara no puedo escanear. Usa una foto o mételo a mano.");
            finish();
        }
    }

    // ---------------- cámara ----------------

    private void openCamera() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (cm == null) { fail("No encuentro la cámara en este móvil."); return; }

            String id = null;
            for (String c : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(c);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { id = c; break; }
            }
            if (id == null) { fail("No encuentro la cámara trasera."); return; }

            CameraCharacteristics ch = cm.getCameraCharacteristics(id);
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) { fail("La cámara no responde."); return; }
            size = chooseSize(map.getOutputSizes(ImageFormat.YUV_420_888));
            if (size == null) { fail("La cámara no admite el modo que necesito."); return; }
            Log.i(TAG, "tamaño de captura " + size.getWidth() + "x" + size.getHeight());
            applyPreviewAspect();

            cm.openCamera(id, deviceCallback, handler);
        } catch (Exception e) {
            fail("No he podido abrir la cámara.");
        }
    }

    /** La vista previa mantiene la proporción de la cámara: nada de estirones. */
    private void applyPreviewAspect() {
        if (size == null) return;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        float portrait = size.getHeight() / (float) size.getWidth();
        int targetW = Math.round(screenH * portrait);
        int targetH = screenH;
        if (targetW < screenW) {
            targetW = screenW;
            targetH = Math.round(screenW / portrait);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(targetW, targetH);
        lp.gravity = Gravity.CENTER;
        preview.setLayoutParams(lp);
    }

    private final CameraDevice.StateCallback deviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice c) {
            camera = c;
            if (surfaceReady) startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice c) {
            c.close();
            camera = null;
        }

        @Override
        public void onError(CameraDevice c, int error) {
            c.close();
            camera = null;
            fail("La cámara ha dado un error (" + error + ").");
        }
    };

    private void startPreview() {
        if (camera == null || done) return;
        try {
            Surface surface = preview.getHolder().getSurface();
            if (surface == null || !surface.isValid()) return;

            if (reader == null) {
                reader = ImageReader.newInstance(size.getWidth(), size.getHeight(),
                        ImageFormat.YUV_420_888, 3);
                reader.setOnImageAvailableListener(this::onFrame, handler);
            }

            List<Surface> out = new ArrayList<>();
            out.add(surface);
            out.add(reader.getSurface());

            camera.createCaptureSession(out, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession s) {
                    session = s;
                    try {
                        CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        b.addTarget(preview.getHolder().getSurface());
                        b.addTarget(reader.getSurface());
                        b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        session.setRepeatingRequest(b.build(), null, handler);
                        Log.i(TAG, "vista previa en marcha");
                    } catch (Exception e) {
                        fail("No he podido arrancar la vista de la cámara.");
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession s) {
                    fail("La cámara no admite este modo.");
                }
            }, handler);
        } catch (Exception e) {
            fail("No he podido arrancar la cámara.");
        }
    }

    private void onFrame(ImageReader r) {
        Image img = null;
        try {
            img = r.acquireNextImage();
            if (img == null || done) return;
            frames++;
            if (!firstFrame) {
                firstFrame = true;
                Log.i(TAG, "primer fotograma " + img.getWidth() + "x" + img.getHeight());
            }
            long now = System.currentTimeMillis();
            if (now - lastAf > 2500) {
                lastAf = now;
                triggerFocus();
            }
            if (now - lastDecode < 120) return;
            if (!busy.compareAndSet(false, true)) return;
            lastDecode = now;

            Image.Plane plane = img.getPlanes()[0];
            ByteBuffer buf = plane.getBuffer();
            int rowStride = plane.getRowStride();
            final int w = img.getWidth();
            final int h = img.getHeight();
            final byte[] data = new byte[Math.min(buf.remaining(), rowStride * h)];
            buf.get(data, 0, data.length);

            decoder.execute(() -> {
                try {
                    String text = QrYuvDecoder.decode(data, rowStride, w, h);
                    if (text != null) handleText(text);
                } catch (Exception e) {
                    Log.w(TAG, "fallo al leer", e);
                } finally {
                    busy.set(false);
                }
            });
        } catch (Exception e) {
            busy.set(false);
            Log.w(TAG, "fotograma descartado", e);
        } finally {
            if (img != null) img.close();
        }
    }

    /** Vuelve a enfocar cada pocos segundos: con el móvil cerca, el QR sale borroso. */
    private void triggerFocus() {
        try {
            if (camera == null || session == null) return;
            CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            b.addTarget(preview.getHolder().getSurface());
            b.addTarget(reader.getSurface());
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            b.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            session.capture(b.build(), null, handler);
        } catch (Exception ignored) {
        }
    }

    private void handleText(String text) {
        BillData bd = QrParser.parse(text);
        if (bd != null && !done) {
            done = true;
            Log.i(TAG, "QR de la CNMC leído");
            Intent i = new Intent();
            i.putExtra(EXTRA_QR, text);
            setResult(RESULT_OK, i);
            runOnUiThread(this::finish);
        } else if (!warned) {
            warned = true;
            Log.i(TAG, "QR distinto: " + text.substring(0, Math.min(40, text.length())));
            runOnUiThread(() -> {
                hint.setText("Ese QR no es el de la CNMC. Busca el que va junto a «comparador.cnmc.gob.es».");
                hint.setVisibility(View.VISIBLE);
            });
        }
    }

    // ---------------- ciclo de vida ----------------

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (camera != null) startPreview();
        else openCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (surfaceReady && camera == null && !done
                && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeCamera();
        decoder.shutdownNow();
        if (thread != null) thread.quitSafely();
    }

    private void closeCamera() {
        try {
            if (session != null) { session.close(); session = null; }
        } catch (Exception ignored) {
        }
        try {
            if (camera != null) { camera.close(); camera = null; }
        } catch (Exception ignored) {
        }
        try {
            if (reader != null) { reader.close(); reader = null; }
        } catch (Exception ignored) {
        }
    }

    private void fail(String msg) {
        Log.w(TAG, msg);
        runOnUiThread(() -> {
            toast(msg);
            finish();
        });
    }

    // ---------------- utilidades ----------------

    private static Size chooseSize(Size[] sizes) {
        if (sizes == null) return null;
        for (Size s : sizes) {
            if (s.getWidth() == 1920 && s.getHeight() == 1080) return s;
        }
        for (Size s : sizes) {
            if (s.getWidth() == 1280 && s.getHeight() == 720) return s;
        }
        Size best = null;
        for (Size s : sizes) {
            if (s.getWidth() < s.getHeight()) continue;   // horizontal
            if (best == null || Math.abs(s.getWidth() - 1280) < Math.abs(best.getWidth() - 1280)) {
                best = s;
            }
        }
        return best;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
