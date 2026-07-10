package com.mineradio.android;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private NodeService nodeService;

    public class AndroidBridge {
        @JavascriptInterface
        public String getPlatform() { return "android"; }

        @JavascriptInterface
        public boolean isAndroid() { return true; }

        @JavascriptInterface
        public int getServerPort() {
            return (nodeService != null) ? nodeService.getPort() : 0;
        }

        @JavascriptInterface
        public void openExternalBrowser(String url) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "閺冪姵纭堕幍鎾崇磻濞村繗顫嶉崳? " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        @JavascriptInterface
        public void showToast(String msg) {
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        nodeService = new NodeService();
        nodeService.start(this, () -> runOnUiThread(this::initWebView));

        initWebView();
    }

    private void initWebView() {
        if (webView != null) return;

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        webView = new WebView(this);
        setContentView(webView);
        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onCreateWindow(android.webkit.WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                android.webkit.WebView newView = new android.webkit.WebView(MainActivity.this);
                android.webkit.WebView.WebViewTransport transport = (android.webkit.WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newView);
                resultMsg.sendToTarget();
                return true;
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/vendor/", new WebViewAssetLoader.AssetsPathHandler(this))
                .setDomain("mineradio.local")
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String path = uri.getPath();
                WebResourceResponse response = assetLoader.shouldInterceptRequest(uri);
                if (response != null) return response;
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectDesktopStubs();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("https://mineradio.local/") || url.startsWith("http://127.0.0.1:")) {
                    return false;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "閺冪姵纭堕幍鎾崇磻闁剧偓甯?, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        int port = (nodeService != null) ? nodeService.getPort() : 0;
        if (port > 0) {
            webView.loadUrl("http://127.0.0.1:" + port + "/index.html");
        } else {
            webView.loadUrl("https://mineradio.local/index.html");
        }
    }

    private void injectDesktopStubs() {
        String js = "javascript:(function() {" +
            "if (window.desktopWindow) return;" +
            "window.desktopWindow = { apiBase: \"https://mineradio-android-production-8c4b.up.railway.app\"," +
            "  isDesktop: true," +
            "  minimize: function(){return Promise.resolve();}," +
            "  toggleMaximize: function(){return Promise.resolve();}," +
            "  toggleFullscreen: function(){" +
            "    if(document.documentElement.requestFullscreen&&!document.fullscreenElement)" +
            "      document.documentElement.requestFullscreen();" +
            "    else if(document.exitFullscreen) document.exitFullscreen();" +
            "    return Promise.resolve();" +
            "  }," +
            "  exitFullscreenWindowed: function(){" +
            "    if(document.fullscreenElement) document.exitFullscreen();" +
            "    return Promise.resolve();" +
            "  }," +
            "  getState: function(){return Promise.resolve({isMaximized:false,isMinimized:false,isFullscreen:!!document.fullscreenElement});}," +
            "  close: function(){/* no-op */return Promise.resolve();}," +
            "  openNeteaseMusicLogin:function(){" +
            "    if(window.AndroidBridge)window.AndroidBridge.showToast('鐠囧嘲婀鐟板毉閻ㄥ嫭绁荤憴鍫濇珤娑擃厾娅ヨぐ鏇犵秹閺勬挷绨棅鍏呯\\n閻ц缍嶉崥搴℃礀閸掔増婀版い鍨閸斻劎鐭樼拹纰縪okie');" +
            "    if(window.AndroidBridge)window.AndroidBridge.openExternalBrowser('https://music.163.com/#/login');" +
            "    return Promise.resolve({ok:true,cookie:''});" +
            "  }," +
            "  clearNeteaseMusicLogin:function(){return Promise.resolve();}," +
            "  openQQMusicLogin:function(){" +
            "    if(window.AndroidBridge)window.AndroidBridge.showToast('鐠囧嘲婀鐟板毉閻ㄥ嫭绁荤憴鍫濇珤娑擃厾娅ヨぐ鏄婹闂婂厖绠癨\n閻ц缍嶉崥搴℃礀閸掔増婀版い鍨閸斻劎鐭樼拹纰縪okie');" +
            "    if(window.AndroidBridge)window.AndroidBridge.openExternalBrowser('https://y.qq.com/n/ryqq/profile');" +
            "    return Promise.resolve({ok:true,cookie:''});" +
            "  }," +
            "  clearQQMusicLogin:function(){return Promise.resolve();}," +
            "  openUpdateInstaller:function(){return Promise.resolve();}," +
            "  restartApp:function(){return Promise.resolve();}," +
            "  configureGlobalHotkeys:function(){return Promise.resolve();}," +
            "  exportJsonFile:function(){return Promise.resolve();}," +
            "  importJsonFile:function(){return Promise.resolve();}," +
            "  setDesktopLyricsEnabled:function(){return Promise.resolve();}," +
            "  updateDesktopLyrics:function(){return Promise.resolve();}," +
            "  setWallpaperMode:function(){return Promise.resolve();}," +
            "  updateWallpaperMode:function(){return Promise.resolve();}," +
            "  onGlobalHotkey:function(){return function(){};}," +
            "  onDesktopLyricsLockState:function(){return function(){};}," +
            "  onDesktopLyricsEnabledState:function(){return function(){};}," +
            "  onStateChange:function(){return function(){};}," +
            "};" +
            "document.documentElement.classList.add('simple-mode-preload');" +
            "document.body.classList.add('android-shell');" +

            "var _origFetch = window.fetch;" +
            "window.__apiBase = \"https://mineradio-android-production-8c4b.up.railway.app\"; window.fetch = function(url, opts) { if (typeof url === \"string\" \u0026\u0026 url.startsWith(\"/api/\")) { url = window.__apiBase + url; }" +
            "  if (typeof url === 'string' && url.startsWith('/api/')) {" +
            "    if (window.AndroidBridge) window.AndroidBridge.showToast('闂団偓鐟曚礁鎮楃粩顖涙箛閸斺€虫珤閺€顖涘瘮鐎瑰本鏆ｉ崝鐔诲厴');" +
            "    return Promise.resolve({json:function(){return Promise.resolve({error:'backend not available',loggedIn:false,playlists:[],tracks:[],songs:[]});}});" +
            "  }" +
            "  return _origFetch.call(window, url, opts);" +
            "};" +
        "})();";
        webView.evaluateJavascript(js, null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.evaluateJavascript("window.history.back();", null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (nodeService != null) nodeService.stop();
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}