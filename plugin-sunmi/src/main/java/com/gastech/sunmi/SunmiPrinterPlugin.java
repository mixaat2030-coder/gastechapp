package com.gastech.sunmi;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import woyou.aidlservice.jiuiv5.ICallback;
import woyou.aidlservice.jiuiv5.IWoyouService;

@CapacitorPlugin(name = "SunmiPrinter")
public class SunmiPrinterPlugin extends Plugin {
    private static final String TAG = "SunmiPrinter";
    private IWoyouService printerService;
    private boolean bound = false;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            printerService = IWoyouService.Stub.asInterface(service);
            bound = true;
            Log.i(TAG, "Sunmi printer service connected");
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            printerService = null;
            bound = false;
        }
    };

    @Override public void load() {
        super.load();
        bindService();
    }

    private void bindService() {
        Context ctx = getContext();
        Intent intent = new Intent();
        intent.setPackage("woyou.aidlservice.jiuiv5");
        intent.setAction("woyou.aidlservice.jiuiv5.IWoyouService");
        ctx.startService(intent);
        ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE);
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("available", bound && printerService != null);
        call.resolve(ret);
    }

    @PluginMethod
    public void printReceipt(PluginCall call) {
        if (!bound || printerService == null) {
            call.reject("Sunmi service not bound");
            return;
        }
        try {
            String json = call.getString("payload", "{}");
            JSONObject p = new JSONObject(json);

            // Render entire receipt to bitmap (handles Arabic perfectly)
            Bitmap bmp = BitmapReceiptRenderer.render(getContext(), p);

            printerService.printerInit(null);
            printerService.setAlignment(1, null);

            // Logo if present
            String logoB64 = p.optString("logoBase64", "");
            if (!logoB64.isEmpty() && logoB64.contains(",")) {
                try {
                    byte[] data = Base64.decode(logoB64.split(",")[1], Base64.DEFAULT);
                    Bitmap logo = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (logo != null) printerService.printBitmap(logo, null);
                } catch (Exception ignored) {}
            }

            printerService.printBitmap(bmp, new ICallback.Stub() {
                @Override public void onRunResult(boolean isSuccess) {}
                @Override public void onReturnString(String result) {}
                @Override public void onRaiseException(int code, String msg) {}
                @Override public void onPrintResult(int code, String msg) {}
            });

            printerService.lineWrap(4, null);
            printerService.cutPaper(null);

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "printReceipt failed", e);
            call.reject("Print failed: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void printText(PluginCall call) {
        if (!bound || printerService == null) {
            call.reject("Sunmi service not bound");
            return;
        }
        try {
            String text = call.getString("text", "");
            printerService.printText(text + "\n", null);
            printerService.lineWrap(2, null);
            call.resolve();
        } catch (RemoteException e) {
            call.reject(e.getMessage(), e);
        }
    }

    @PluginMethod
    public void cutPaper(PluginCall call) {
        if (!bound || printerService == null) { call.reject("Not bound"); return; }
        try {
            printerService.cutPaper(null);
            call.resolve();
        } catch (RemoteException e) { call.reject(e.getMessage(), e); }
    }

    @PluginMethod
    public void openCashDrawer(PluginCall call) {
        if (!bound || printerService == null) { call.reject("Not bound"); return; }
        try {
            printerService.openDrawer(null);
            call.resolve();
        } catch (RemoteException e) { call.reject(e.getMessage(), e); }
    }
}
