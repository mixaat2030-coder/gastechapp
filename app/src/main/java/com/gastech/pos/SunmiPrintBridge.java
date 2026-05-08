package com.gastech.pos;

import android.app.Activity;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * Bridge exposed to JavaScript as `window.AndroidBridge`.
 * v6 — Bitmap-based receipt rendering (Loyverse-style):
 *   - All Arabic text is rendered to a single Bitmap and sent via Sunmi printBitmap()
 *     so the firmware never has to render Arabic glyphs itself.
 *   - Falls back to printText() only for plain ASCII test prints.
 *   - Adds getCurrentLocation() so the web app can grab GPS even when the
 *     WebView's geolocation prompt is suppressed.
 */
public class SunmiPrintBridge {

    private final Activity activity;
    private final PrinterHelper printer;

    public SunmiPrintBridge(Activity activity, PrinterHelper printer) {
        this.activity = activity;
        this.printer = printer;
    }

    @JavascriptInterface public boolean isPrinterReady() { return printer.isConnected(); }

    @JavascriptInterface public String getPrinterStatus() {
        if (printer.isConnected()) return "connected";
        printer.connect();
        return "disconnected";
    }

    @JavascriptInterface public void toast(final String msg) {
        activity.runOnUiThread(() -> Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
    }

    /** Hand off external links (WhatsApp, tel:, mailto:, http(s) outside our app) to Android. */
    @JavascriptInterface
    public void openExternal(final String url) {
        if (activity instanceof MainActivity) {
            activity.runOnUiThread(() -> ((MainActivity) activity).openExternalUrl(url));
        }
    }

    private boolean ensureConnected() {
        if (printer.isConnected()) return true;
        printer.connect();
        try { Thread.sleep(400); } catch (InterruptedException ignored) {}
        if (!printer.isConnected()) {
            toast("\u26a0\ufe0f Sunmi printer service not connected");
            return false;
        }
        return true;
    }

    @JavascriptInterface
    public void printText(final String text) {
        if (!ensureConnected()) return;
        try {
            printer.setAlignment(0);
            printer.printText(text + "\n");
            printer.lineWrap(3);
            printer.cutPaper();
        } catch (Exception e) { toast("Print error: " + e.getMessage()); }
    }

    @JavascriptInterface
    public void cutPaper() {
        if (!ensureConnected()) return;
        printer.cutPaper();
    }

    @JavascriptInterface
    public void openCashDrawer() {
        if (!ensureConnected()) return;
        printer.openCashDrawer();
    }

    /**
     * Print a structured receipt (rendered as Bitmap for proper Arabic).
     * Runs on a background thread so the WebView UI doesn't freeze.
     */
    @JavascriptInterface
    public void printReceipt(final String jsonStr) {
        if (!ensureConnected()) return;
        new Thread(() -> {
            try {
                JSONObject d = new JSONObject(jsonStr);
                Bitmap bmp = BitmapReceiptRenderer.render(activity, d);
                printer.setAlignment(1);
                printer.printBitmap(bmp);
                printer.lineWrap(4);
                printer.cutPaper();
            } catch (Exception e) {
                toast("Receipt error: " + e.getMessage());
            }
        }).start();
    }

    /** Get current GPS coordinates as JSON: {"lat":..., "lng":..., "accuracy":...} or {"error":"..."} */
    @JavascriptInterface
    public String getCurrentLocation() {
        try {
            LocationManager lm = (LocationManager) activity.getSystemService(Activity.LOCATION_SERVICE);
            if (lm == null) return "{\"error\":\"no manager\"}";
            Location best = null;
            for (String p : lm.getProviders(true)) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l == null) continue;
                    if (best == null || l.getAccuracy() < best.getAccuracy()) best = l;
                } catch (SecurityException se) { return "{\"error\":\"permission denied\"}"; }
            }
            if (best == null) return "{\"error\":\"no fix\"}";
            return "{\"lat\":" + best.getLatitude()
                    + ",\"lng\":" + best.getLongitude()
                    + ",\"accuracy\":" + best.getAccuracy() + "}";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
