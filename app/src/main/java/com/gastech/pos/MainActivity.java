package com.gastech.pos;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String APP_URL = "https://gastech.lovable.app/auth";
    private static final long BACKGROUND_LOCK_MS = 2 * 60 * 1000L;

    private static final int REQ_PERMS = 1001;
    private WebView webView;
    private SwipeRefreshLayout swipe;
    private PrinterHelper printer;
    private ImageButton fabSync;
    private long pausedAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_main);

        swipe = findViewById(R.id.swipe);
        webView = findViewById(R.id.webview);
        fabSync = findViewById(R.id.fab_sync);

        printer = new PrinterHelper(this);
        printer.connect();

        setupWebView();
        setupSyncButton();
        requestNeededPermissions();

        webView.loadUrl(APP_URL);
        swipe.setOnRefreshListener(this::syncFromServer);
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setGeolocationEnabled(true);
        s.setUserAgentString(s.getUserAgentString() + " GastechAPK/6.0");

        webView.addJavascriptInterface(new SunmiPrintBridge(this, printer), "AndroidBridge");
        webView.addJavascriptInterface(new BluetoothPrintBridge(this), "AndroidBT");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleExternalUrl(view, url);
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return handleExternalUrl(view, request.getUrl().toString());
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                swipe.setRefreshing(false);
                view.evaluateJavascript(
                    "window.IS_GASTECH_APK = true; " +
                    "window.GASTECH_APK_VERSION = '6.0.0'; " +
                    "window.dispatchEvent(new Event('android-bridge-ready'));",
                    null
                );
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
            // Auto-grant geolocation requests from the WebView
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
            @Override
            public void onCloseWindow(WebView window) {
                if (webView.canGoBack()) webView.goBack();
            }
        });
    }

    private boolean handleExternalUrl(WebView view, String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        if (lower.startsWith("https://wa.me/")
                || lower.startsWith("https://api.whatsapp.com/")
                || lower.startsWith("http://wa.me/")
                || lower.startsWith("http://api.whatsapp.com/")
                || lower.startsWith("whatsapp:")) {
            openExternalUrl(url);
            return true;
        }
        if (lower.startsWith("tel:")
                || lower.startsWith("mailto:")
                || lower.startsWith("sms:")
                || lower.startsWith("geo:")
                || lower.startsWith("intent:")) {
            openExternalUrl(url);
            return true;
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            try {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (host != null && !host.endsWith("gastech.lovable.app")
                        && !host.endsWith("lovable.app")
                        && !host.endsWith("supabase.co")) {
                    openExternalUrl(url);
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    public void openExternalUrl(final String url) {
        try {
            Intent intent;
            if (url.startsWith("intent:")) {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            } else if (url.toLowerCase().startsWith("https://wa.me/")
                    || url.toLowerCase().startsWith("https://api.whatsapp.com/")
                    || url.toLowerCase().startsWith("whatsapp:")) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage("com.whatsapp");
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } catch (Exception ignored) {
                Toast.makeText(this, "لا يوجد تطبيق لفتح هذا الرابط", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "خطأ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupSyncButton() {
        fabSync.setOnLongClickListener(v -> {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        });
        fabSync.setOnClickListener(v -> showSyncMenu());
        fabSync.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY; long downTime; boolean dragged = false;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        downTime = System.currentTimeMillis();
                        dragged = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        float nx = event.getRawX() + dX;
                        float ny = event.getRawY() + dY;
                        if (Math.abs(nx - view.getX()) > 10 || Math.abs(ny - view.getY()) > 10) dragged = true;
                        view.setX(nx); view.setY(ny);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragged) return true;
                        return false;
                }
                return false;
            }
        });
    }

    private void showSyncMenu() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.sync_button)
            .setItems(new CharSequence[]{
                getString(R.string.menu_refresh),
                getString(R.string.menu_clear_cache),
                getString(R.string.menu_about)
            }, (dialog, which) -> {
                switch (which) {
                    case 0: syncFromServer(); break;
                    case 1: clearCacheAndReload(); break;
                    case 2: startActivity(new Intent(this, AboutActivity.class)); break;
                }
            })
            .show();
    }

    private void syncFromServer() {
        webView.clearCache(false);
        webView.reload();
        Toast.makeText(this, R.string.sync_done, Toast.LENGTH_SHORT).show();
    }

    private void clearCacheAndReload() {
        webView.clearCache(true);
        webView.clearHistory();
        WebStorage.getInstance().deleteAllData();
        CookieManager.getInstance().flush();
        webView.loadUrl(APP_URL);
        Toast.makeText(this, R.string.sync_done, Toast.LENGTH_SHORT).show();
    }

    private void requestNeededPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.CAMERA);
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
            perms.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        List<String> need = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                need.add(p);
            }
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERMS);
        }
    }

    @Override protected void onPause() { super.onPause(); pausedAt = System.currentTimeMillis(); }

    @Override
    protected void onResume() {
        super.onResume();
        if (pausedAt > 0 && webView != null) {
            long away = System.currentTimeMillis() - pausedAt;
            if (away >= BACKGROUND_LOCK_MS) {
                webView.evaluateJavascript(
                    "try { window.dispatchEvent(new CustomEvent('app:require-passcode')); } catch(e){}",
                    null
                );
            }
        }
        pausedAt = 0L;
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else {
            new AlertDialog.Builder(this)
                .setMessage(R.string.exit_confirm)
                .setPositiveButton(R.string.yes, (d, w) -> super.onBackPressed())
                .setNegativeButton(R.string.cancel, null)
                .show();
        }
    }

    @Override
    protected void onDestroy() {
        if (printer != null) printer.disconnect();
        super.onDestroy();
    }
}
