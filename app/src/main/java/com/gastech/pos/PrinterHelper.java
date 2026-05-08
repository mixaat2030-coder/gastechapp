package com.gastech.pos;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import woyou.aidlservice.jiuiv5.IWoyouService;

/**
 * Helper to connect to Sunmi's built-in printer service (woyou.aidlservice.jiuiv5).
 * v6: adds printBitmap() so we can render the receipt as an image (Loyverse-style),
 * which is the only reliable way to print Arabic on Sunmi V2 firmware.
 */
public class PrinterHelper {

    private static final String TAG = "PrinterHelper";
    private final Context ctx;
    private IWoyouService service;
    private boolean connected = false;

    public PrinterHelper(Context ctx) { this.ctx = ctx; }

    public void connect() {
        Intent intent = new Intent();
        intent.setPackage("woyou.aidlservice.jiuiv5");
        intent.setAction("woyou.aidlservice.jiuiv5.IWoyouService");
        try {
            boolean ok = ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE);
            Log.d(TAG, "bindService: " + ok);
        } catch (Exception e) { Log.e(TAG, "bindService error", e); }
    }

    public void disconnect() {
        if (connected) {
            try { ctx.unbindService(conn); } catch (Exception ignored) {}
            connected = false;
        }
    }

    public boolean isConnected() { return connected && service != null; }

    public void printText(String text) {
        if (!isConnected()) return;
        try { service.printText(text, null); } catch (RemoteException e) { Log.e(TAG, "printText", e); }
    }

    public void printBitmap(Bitmap bmp) {
        if (!isConnected() || bmp == null) return;
        try { service.printBitmap(bmp, null); } catch (RemoteException e) { Log.e(TAG, "printBitmap", e); }
    }

    public void setAlignment(int alignment) {
        if (!isConnected()) return;
        try { service.setAlignment(alignment, null); } catch (RemoteException e) { Log.e(TAG, "setAlignment", e); }
    }

    public void lineWrap(int n) {
        if (!isConnected()) return;
        try { service.lineWrap(n, null); } catch (RemoteException e) { Log.e(TAG, "lineWrap", e); }
    }

    public void cutPaper() {
        if (!isConnected()) return;
        try { service.cutPaper(null); } catch (RemoteException e) { Log.e(TAG, "cutPaper", e); }
    }

    public void printQRCode(String data, int moduleSize, int errorLevel) {
        if (!isConnected()) return;
        try { service.printQRCode(data, moduleSize, errorLevel, null); } catch (RemoteException e) { Log.e(TAG, "printQRCode", e); }
    }

    public void printBarcode(String data, int symbology, int height, int width, int textPos) {
        if (!isConnected()) return;
        try { service.printBarCode(data, symbology, height, width, textPos, null); } catch (RemoteException e) { Log.e(TAG, "printBarcode", e); }
    }

    public void openCashDrawer() {
        if (!isConnected()) return;
        try { service.openDrawer(null); } catch (RemoteException e) { Log.e(TAG, "openDrawer", e); }
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IWoyouService.Stub.asInterface(binder);
            connected = true;
            Log.d(TAG, "Sunmi printer service connected");
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null; connected = false;
            Log.d(TAG, "Sunmi printer service disconnected");
        }
    };
}
