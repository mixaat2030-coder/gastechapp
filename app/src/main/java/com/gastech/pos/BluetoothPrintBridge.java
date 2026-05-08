package com.gastech.pos;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Bluetooth thermal-printer bridge for ESC/POS printers (58mm/80mm).
 * Exposed to JS as `window.AndroidBT`.
 *
 *   AndroidBT.listPaired()      -> JSON [{name, address}]
 *   AndroidBT.connect(address)  -> "ok" | "error: ..."
 *   AndroidBT.printJson(json)   -> renders bitmap, sends as ESC/POS GS v 0
 *   AndroidBT.testPrint()       -> sends "Hello" + cut
 *   AndroidBT.disconnect()
 */
public class BluetoothPrintBridge {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final Activity activity;
    private BluetoothSocket socket;
    private OutputStream out;

    public BluetoothPrintBridge(Activity activity) { this.activity = activity; }

    private void toast(String msg) {
        activity.runOnUiThread(() -> Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
    }

    @JavascriptInterface
    public String listPaired() {
        try {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            if (ba == null) return "[]";
            if (!ba.isEnabled()) return "[]";
            Set<BluetoothDevice> devices = ba.getBondedDevices();
            JSONArray arr = new JSONArray();
            for (BluetoothDevice d : devices) {
                JSONObject o = new JSONObject();
                o.put("name", d.getName() == null ? "" : d.getName());
                o.put("address", d.getAddress());
                arr.put(o);
            }
            return arr.toString();
        } catch (SecurityException se) {
            return "[]";
        } catch (Exception e) {
            return "[]";
        }
    }

    @JavascriptInterface
    public String connect(String address) {
        try {
            disconnect();
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            if (ba == null) return "error: no adapter";
            BluetoothDevice dev = ba.getRemoteDevice(address);
            socket = dev.createRfcommSocketToServiceRecord(SPP);
            ba.cancelDiscovery();
            socket.connect();
            out = socket.getOutputStream();
            return "ok";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    @JavascriptInterface
    public void disconnect() {
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        out = null; socket = null;
    }

    @JavascriptInterface
    public boolean isConnected() { return socket != null && socket.isConnected(); }

    @JavascriptInterface
    public void testPrint() {
        if (!isConnected()) { toast("BT printer not connected"); return; }
        try {
            out.write(new byte[]{0x1B, 0x40}); // init
            out.write("GasTech POS - test print\n\n\n".getBytes("UTF-8"));
            out.write(new byte[]{0x1D, 0x56, 0x00}); // cut
            out.flush();
        } catch (Exception e) { toast("BT test failed: " + e.getMessage()); }
    }

    @JavascriptInterface
    public void printJson(String jsonStr) {
        if (!isConnected()) { toast("BT printer not connected"); return; }
        new Thread(() -> {
            try {
                JSONObject d = new JSONObject(jsonStr);
                Bitmap bmp = BitmapReceiptRenderer.render(activity, d);
                out.write(new byte[]{0x1B, 0x40}); // init
                writeBitmapEscPos(bmp);
                out.write("\n\n\n".getBytes("UTF-8"));
                out.write(new byte[]{0x1D, 0x56, 0x00}); // cut
                out.flush();
            } catch (Exception e) {
                toast("BT print error: " + e.getMessage());
            }
        }).start();
    }

    /** Convert a bitmap to monochrome ESC/POS raster bit-image (GS v 0). */
    private void writeBitmapEscPos(Bitmap bmp) throws Exception {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        int widthBytes = (width + 7) / 8;
        // GS v 0 m xL xH yL yH d1...dk
        out.write(new byte[]{0x1D, 0x76, 0x30, 0x00,
                (byte)(widthBytes & 0xFF), (byte)((widthBytes >> 8) & 0xFF),
                (byte)(height & 0xFF), (byte)((height >> 8) & 0xFF)});
        byte[] row = new byte[widthBytes];
        for (int y = 0; y < height; y++) {
            java.util.Arrays.fill(row, (byte)0);
            for (int x = 0; x < width; x++) {
                int px = bmp.getPixel(x, y);
                int r = Color.red(px), g = Color.green(px), b = Color.blue(px);
                int gray = (r + g + b) / 3;
                if (gray < 128) {
                    row[x / 8] |= (byte)(0x80 >> (x % 8));
                }
            }
            out.write(row);
        }
    }
}
