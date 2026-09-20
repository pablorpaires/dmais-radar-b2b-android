package br.com.dmaismkt.radarb2b;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String START_URL = "https://dmaismkt.com.br/radar-b2b/?radar_app=android";
    private static final String TRUSTED_HOST = "dmaismkt.com.br";
    private static final int REQ_WEB_PERMISSIONS = 1401;
    private static final int REQ_GEOLOCATION = 1402;
    private static final int REQ_FILE = 1403;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingWebPermission;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(8, 11, 16));
        getWindow().setNavigationBarColor(Color.rgb(8, 11, 16));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(8, 11, 16));
        setContentView(webView);

        configureWebView();
        if (savedInstanceState == null) webView.loadUrl(START_URL);
        else webView.restoreState(savedInstanceState);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportMultipleWindows(false);
        s.setUserAgentString(s.getUserAgentString() + " DMaisRadarAndroid/1.0.2");

        if (android.os.Build.VERSION.SDK_INT >= 26) WebView.startSafeBrowsing(this, null);
        WebView.setWebContentsDebuggingEnabled(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new NativeBridge(), "DMaisRadarNative");
        webView.setWebViewClient(new RadarWebViewClient());
        webView.setWebChromeClient(new RadarChromeClient());
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    private boolean isTrusted(Uri uri) {
        if (uri == null || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase();
        return host.equals(TRUSTED_HOST) || host.equals("www." + TRUSTED_HOST);
    }

    private void openExternal(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return;
        try {
            Uri uri = Uri.parse(rawUrl);
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir este link.", Toast.LENGTH_SHORT).show();
        }
    }

    private class RadarWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if ((scheme.equals("https") || scheme.equals("http")) && isTrusted(uri)) return false;
            openExternal(uri.toString());
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            Uri uri = Uri.parse(url);
            if (!isTrusted(uri)) return;
            String js = "(function(){" +
                    "if(window.__dmaisNativeHooks)return;window.__dmaisNativeHooks=1;" +
                    "var oldOpen=window.open;window.open=function(u){try{if(window.DMaisRadarNative&&u){window.DMaisRadarNative.openExternal(String(u));return null;}}catch(e){}return oldOpen?oldOpen.apply(window,arguments):null;};" +
                    "document.addEventListener('click',function(e){var a=e.target&&e.target.closest?e.target.closest('a[target=\"_blank\"]'):null;if(a&&a.href&&window.DMaisRadarNative){e.preventDefault();window.DMaisRadarNative.openExternal(a.href);}},true);" +
                    "})();";
            view.evaluateJavascript(js, null);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            Toast.makeText(MainActivity.this, "Conexão segura não pôde ser validada.", Toast.LENGTH_LONG).show();
        }
    }

    private class RadarChromeClient extends WebChromeClient {
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            Uri originUri = Uri.parse(origin);
            if (!isTrusted(originUri)) { callback.invoke(origin, false, false); return; }
            if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                callback.invoke(origin, true, false);
                return;
            }
            pendingGeoOrigin = origin;
            pendingGeoCallback = callback;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_GEOLOCATION);
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> {
                if (!isTrusted(request.getOrigin())) { request.deny(); return; }
                List<String> androidPerms = new ArrayList<>();
                List<String> grantResources = new ArrayList<>();
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) {
                        grantResources.add(r);
                        if (!hasPermission(Manifest.permission.CAMERA)) androidPerms.add(Manifest.permission.CAMERA);
                    } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) {
                        grantResources.add(r);
                        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) androidPerms.add(Manifest.permission.RECORD_AUDIO);
                    }
                }
                if (grantResources.isEmpty()) { request.deny(); return; }
                if (androidPerms.isEmpty()) request.grant(grantResources.toArray(new String[0]));
                else {
                    pendingWebPermission = request;
                    requestPermissions(androidPerms.toArray(new String[0]), REQ_WEB_PERMISSIONS);
                }
            });
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            Intent intent = params.createIntent();
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(intent, REQ_FILE);
                return true;
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                Toast.makeText(MainActivity.this, "Nenhum seletor de arquivo disponível.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_GEOLOCATION && pendingGeoCallback != null) {
            boolean ok = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
            pendingGeoCallback.invoke(pendingGeoOrigin, ok, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
            return;
        }
        if (requestCode == REQ_WEB_PERMISSIONS && pendingWebPermission != null) {
            List<String> allowed = new ArrayList<>();
            for (String r : pendingWebPermission.getResources()) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && hasPermission(Manifest.permission.CAMERA)) allowed.add(r);
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && hasPermission(Manifest.permission.RECORD_AUDIO)) allowed.add(r);
            }
            if (allowed.isEmpty()) pendingWebPermission.deny();
            else pendingWebPermission.grant(allowed.toArray(new String[0]));
            pendingWebPermission = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                result = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) result[i] = clip.getItemAt(i).getUri();
            } else if (data.getData() != null) result = new Uri[]{data.getData()};
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        if (webView != null) { webView.onPause(); CookieManager.getInstance().flush(); }
        super.onPause();
    }

    private class NativeBridge {
        @JavascriptInterface
        public void share(String title, String text, String url) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, title == null ? "D'Mais Radar B2B" : title);
                String body = (text == null ? "" : text) + (url == null || url.isEmpty() ? "" : "\n\n" + url);
                send.putExtra(Intent.EXTRA_TEXT, body.trim());
                startActivity(Intent.createChooser(send, "Compartilhar diagnóstico"));
            });
        }

        @JavascriptInterface
        public void openExternal(String url) {
            runOnUiThread(() -> MainActivity.this.openExternal(url));
        }

        @JavascriptInterface
        public String appVersion() { return "1.0.2"; }
    }
}
