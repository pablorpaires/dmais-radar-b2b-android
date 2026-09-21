package br.com.dmaismkt.radarb2b;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.gms.codescanner.GmsBarcodeScanner;
import com.google.android.gms.codescanner.GmsBarcodeScannerOptions;
import com.google.android.gms.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String BASE_URL = "https://dmaismkt.com.br/radar-b2b/";
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
        createWebView();

        if (savedInstanceState == null) {
            loadFreshStart();
        } else if (webView.restoreState(savedInstanceState) == null) {
            loadFreshStart();
        }
    }

    private String startUrl() {
        return Uri.parse(BASE_URL).buildUpon()
                .appendQueryParameter("radar_app", "android")
                .appendQueryParameter("app_v", "1.0.4")
                .appendQueryParameter("_ts", String.valueOf(System.currentTimeMillis()))
                .build().toString();
    }

    private void loadFreshStart() {
        webView.loadUrl(startUrl());
    }

    private void createWebView() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.destroy();
            } catch (Exception ignored) {}
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(8, 11, 16));
        setContentView(webView);
        configureWebView();
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
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadsImagesAutomatically(true);
        s.setUserAgentString(s.getUserAgentString() + " DMaisRadarAndroid/1.0.4");

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            WebView.startSafeBrowsing(this, null);
        }
        WebView.setWebContentsDebuggingEnabled(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);
        cm.flush();

        webView.addJavascriptInterface(new NativeBridge(), "DMaisRadarNative");
        webView.setWebViewClient(new RadarWebViewClient());
        webView.setWebChromeClient(new RadarChromeClient());
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
    }

    private boolean isTrusted(Uri uri) {
        if (uri == null || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase();
        return host.equals(TRUSTED_HOST) || host.equals("www." + TRUSTED_HOST);
    }

    private void openExternal(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)));
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir este link.", Toast.LENGTH_SHORT).show();
        }
    }

    private class RadarWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();

            if ((scheme.equals("https") || scheme.equals("http")) && isTrusted(uri)) {
                return false;
            }

            openExternal(uri.toString());
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            CookieManager.getInstance().flush();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request != null && request.isForMainFrame()) {
                Toast.makeText(MainActivity.this, "Falha ao carregar o Radar. Verifique sua internet e tente novamente.", Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            Toast.makeText(MainActivity.this, "Conexão segura não pôde ser validada.", Toast.LENGTH_LONG).show();
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "O Radar foi recarregado para recuperar a aplicação.", Toast.LENGTH_SHORT).show();
                createWebView();
                loadFreshStart();
            });
            return true;
        }
    }

    private class RadarChromeClient extends WebChromeClient {
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            Uri originUri = Uri.parse(origin);
            if (!isTrusted(originUri)) {
                callback.invoke(origin, false, false);
                return;
            }

            if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                callback.invoke(origin, true, false);
                return;
            }

            pendingGeoOrigin = origin;
            pendingGeoCallback = callback;
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_GEOLOCATION
            );
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> {
                if (!isTrusted(request.getOrigin())) {
                    request.deny();
                    return;
                }

                List<String> androidPerms = new ArrayList<>();
                List<String> grantResources = new ArrayList<>();

                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) {
                        grantResources.add(r);
                        if (!hasPermission(Manifest.permission.CAMERA)) {
                            androidPerms.add(Manifest.permission.CAMERA);
                        }
                    } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) {
                        grantResources.add(r);
                        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                            androidPerms.add(Manifest.permission.RECORD_AUDIO);
                        }
                    }
                }

                if (grantResources.isEmpty()) {
                    request.deny();
                    return;
                }

                if (androidPerms.isEmpty()) {
                    request.grant(grantResources.toArray(new String[0]));
                } else {
                    pendingWebPermission = request;
                    requestPermissions(androidPerms.toArray(new String[0]), REQ_WEB_PERMISSIONS);
                }
            });
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(null);
            }

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
            boolean ok = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
            pendingGeoCallback.invoke(pendingGeoOrigin, ok, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
            return;
        }

        if (requestCode == REQ_WEB_PERMISSIONS && pendingWebPermission != null) {
            List<String> allowed = new ArrayList<>();
            for (String r : pendingWebPermission.getResources()) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && hasPermission(Manifest.permission.CAMERA)) {
                    allowed.add(r);
                }
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    allowed.add(r);
                }
            }

            if (allowed.isEmpty()) {
                pendingWebPermission.deny();
            } else {
                pendingWebPermission.grant(allowed.toArray(new String[0]));
            }
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
                for (int i = 0; i < clip.getItemCount(); i++) {
                    result[i] = clip.getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }

        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) {
            webView.saveState(outState);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
            CookieManager.getInstance().flush();
        }
        super.onPause();
    }

    private void sendQrToWeb(String requestId, String value) {
        if (webView == null) return;
        String js = "window.DMaisRadarNativeQrResult&&window.DMaisRadarNativeQrResult("
                + JSONObject.quote(requestId == null ? "" : requestId) + ","
                + JSONObject.quote(value == null ? "" : value) + ");";
        webView.evaluateJavascript(js, null);
    }

    private void sendResolvedUrlToWeb(String requestId, String value) {
        if (webView == null) return;
        String js = "window.DMaisRadarNativeUrlResolved&&window.DMaisRadarNativeUrlResolved("
                + JSONObject.quote(requestId == null ? "" : requestId) + ","
                + JSONObject.quote(value == null ? "" : value) + ");";
        webView.evaluateJavascript(js, null);
    }

    private static boolean isAllowedGoogleHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase();
        if (host.startsWith("www.")) host = host.substring(4);
        return host.equals("share.google")
                || host.equals("search.app")
                || host.equals("maps.app.goo.gl")
                || host.equals("goo.gl")
                || host.equals("g.page")
                || host.endsWith(".g.page")
                || host.equals("google.com")
                || host.matches("google\\.[a-z.]+")
                || host.matches("(maps|search)\\.google\\.[a-z.]+")
                || host.endsWith(".google.com");
    }

    private static String normalizeGoogleUrl(String raw) {
        if (raw == null) return "";
        String value = raw.trim()
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\uFEFF", "");
        if (value.startsWith("www.") || value.startsWith("share.google/")
                || value.startsWith("search.app/") || value.startsWith("maps.app.goo.gl/")
                || value.startsWith("g.page/")) {
            value = "https://" + value.replaceFirst("^www\\.", "");
        }
        try {
            Uri u = Uri.parse(value);
            if (!"http".equalsIgnoreCase(u.getScheme()) && !"https".equalsIgnoreCase(u.getScheme())) return "";
            if (!isAllowedGoogleHost(u.getHost())) return "";
            return value;
        } catch (Exception e) {
            return "";
        }
    }

    private static String unwrapGoogleWrapper(String url) {
        try {
            Uri uri = Uri.parse(url);
            String[] keys = {"url", "u", "target", "continue", "dest", "destination"};
            for (String key : keys) {
                String v = uri.getQueryParameter(key);
                if (v == null || v.trim().isEmpty()) continue;
                String decoded = URLDecoder.decode(v, StandardCharsets.UTF_8.name());
                String normalized = normalizeGoogleUrl(decoded);
                if (!normalized.isEmpty()) return normalized;
            }
        } catch (Exception ignored) {}
        return url;
    }

    private static String absoluteGoogleUrl(String current, String candidate) {
        try {
            if (candidate == null) return "";
            candidate = candidate.trim()
                    .replace("&amp;", "&")
                    .replace("\\/","/")
                    .replace("\\u002F","/")
                    .replace("\\u002f","/")
                    .replace("\\u003A",":")
                    .replace("\\u003a",":")
                    .replace("\\u003D","=")
                    .replace("\\u003d","=")
                    .replace("\\u0026","&")
                    .replace("\\u003F","?")
                    .replace("\\u003f","?");
            URL base = new URL(current);
            URL next = new URL(base, candidate);
            String normalized = normalizeGoogleUrl(next.toString());
            return normalized.isEmpty() ? "" : unwrapGoogleWrapper(normalized);
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractGoogleUrlFromHtml(String current, String body) {
        if (body == null || body.isEmpty()) return "";

        String[] patterns = {
                "<meta[^>]+http-equiv=[\"']?refresh[\"']?[^>]+content=[\"'][^\"']*url=([^\"']+)[\"']",
                "<link[^>]+rel=[\"']canonical[\"'][^>]+href=[\"']([^\"']+)[\"']",
                "<meta[^>]+property=[\"']og:url[\"'][^>]+content=[\"']([^\"']+)[\"']",
                "(?:window\\.)?location(?:\\.href)?\\s*=\\s*[\"']([^\"']+)[\"']"
        };
        for (String p : patterns) {
            Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(body);
            if (m.find()) {
                String next = absoluteGoogleUrl(current, m.group(1));
                if (!next.isEmpty() && !next.equals(current)) return next;
            }
        }

        Matcher hrefs = Pattern.compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE).matcher(body);
        int inspected = 0;
        while (hrefs.find() && inspected++ < 100) {
            String href = hrefs.group(1);
            if (!href.matches("(?is).*(/maps/|/search\\?|placeid=|query_place_id=|ludocid=|[?&]cid=|kgmid=|/share\\.google).*")) {
                continue;
            }
            String next = absoluteGoogleUrl(current, href);
            if (!next.isEmpty() && !next.equals(current)) return next;
        }

        Matcher urls = Pattern.compile(
                "https?(?::|\\\\u003A)(?:/|\\\\/|\\\\u002F){2}(?:www\\.|maps\\.|search\\.)?google\\.[A-Za-z.]+[^\"'<> {}\\s]+"
                        + "|https?(?::|\\\\u003A)(?:/|\\\\/|\\\\u002F){2}(?:share\\.google|search\\.app|maps\\.app\\.goo\\.gl|g\\.page)[^\"'<> {}\\s]+",
                Pattern.CASE_INSENSITIVE).matcher(body);
        int found = 0;
        while (urls.find() && found++ < 40) {
            String next = absoluteGoogleUrl(current, urls.group());
            if (!next.isEmpty() && !next.equals(current)) return next;
        }
        return "";
    }

    private static String readResponseBody(HttpURLConnection connection) {
        try {
            InputStream stream = connection.getResponseCode() >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) return "";
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            int max = 650000;
            while ((n = reader.read(buf)) > 0 && out.length() < max) {
                out.append(buf, 0, Math.min(n, max - out.length()));
            }
            reader.close();
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String resolveGoogleRedirect(String rawUrl) {
        String current = normalizeGoogleUrl(rawUrl);
        if (current.isEmpty()) return rawUrl == null ? "" : rawUrl;

        Set<String> visited = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            current = unwrapGoogleWrapper(current);
            if (visited.contains(current)) break;
            visited.add(current);

            HttpURLConnection connection = null;
            try {
                URL url = new URL(current);
                if (!isAllowedGoogleHost(url.getHost())) break;
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36 DMaisRadarAndroid/1.0.4");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                connection.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.7,en;q=0.6");

                int code = connection.getResponseCode();
                String location = connection.getHeaderField("Location");
                if (location != null && !location.trim().isEmpty()) {
                    String next = absoluteGoogleUrl(current, location);
                    if (!next.isEmpty() && !next.equals(current)) {
                        current = next;
                        continue;
                    }
                }

                String body = readResponseBody(connection);
                String next = extractGoogleUrlFromHtml(current, body);
                if (!next.isEmpty() && !next.equals(current)) {
                    current = next;
                    continue;
                }

                if (code >= 200 && code < 400) break;
                break;
            } catch (Exception ignored) {
                break;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return current;
    }

    private class NativeBridge {
        @JavascriptInterface
        public void share(String title, String text, String url) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, title == null ? "D'Mais Radar B2B" : title);
                String body = (text == null ? "" : text)
                        + (url == null || url.isEmpty() ? "" : "\n\n" + url);
                send.putExtra(Intent.EXTRA_TEXT, body.trim());
                startActivity(Intent.createChooser(send, "Compartilhar diagnóstico"));
            });
        }

        @JavascriptInterface
        public void openExternal(String url) {
            runOnUiThread(() -> MainActivity.this.openExternal(url));
        }

        @JavascriptInterface
        public void scanQr(String requestId) {
            runOnUiThread(() -> {
                GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .enableAutoZoom()
                        .build();
                GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(MainActivity.this, options);
                scanner.startScan()
                        .addOnSuccessListener(barcode -> sendQrToWeb(requestId, barcode.getRawValue()))
                        .addOnCanceledListener(() -> sendQrToWeb(requestId, ""))
                        .addOnFailureListener(e -> {
                            Toast.makeText(MainActivity.this,
                                    "Não foi possível abrir o leitor de QR. Tente novamente.",
                                    Toast.LENGTH_LONG).show();
                            sendQrToWeb(requestId, "");
                        });
            });
        }

        @JavascriptInterface
        public void resolveGoogleUrl(String requestId, String rawUrl) {
            new Thread(() -> {
                String resolved = resolveGoogleRedirect(rawUrl);
                runOnUiThread(() -> sendResolvedUrlToWeb(requestId, resolved));
            }).start();
        }

        @JavascriptInterface
        public String appVersion() {
            return "1.0.4";
        }
    }
}
