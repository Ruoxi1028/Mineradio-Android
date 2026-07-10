package com.mineradio.android;

import android.annotation.SuppressLint;
import android.content.Intent;
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
            // 必须在主线程操作 WebView
            MainActivity.this.runOnUiThread(() -> {
                if (webView != null && url != null && !url.isEmpty()) {
                    webView.loadUrl(url);
                } else {
                    Toast.makeText(MainActivity.this, "无法打开链接", Toast.LENGTH_SHORT).show();
                }
            });
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
                WebResourceResponse response = assetLoader.shouldInterceptRequest(uri);
                if (response != null) return response;
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 登录页面不注入按钮
                if (url.contains("music.163.com") || url.contains("y.qq.com")) {
                    return;
                }
                injectDesktopStubs();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("https://mineradio.local/") ||
                    url.startsWith("http://127.0.0.1:") ||
                    url.contains("music.163.com") ||
                    url.contains("y.qq.com") ||
                    url.contains("qq.com") ||
                    url.contains("163.com")) {
                    return false;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "无法打开链接", Toast.LENGTH_SHORT).show();
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
            "    if(window.AndroidBridge)window.AndroidBridge.showToast('正在打开网易云登录...');" +
            "    if(window.AndroidBridge)window.AndroidBridge.openExternalBrowser('https://music.163.com/#/login');" +
            "    return Promise.resolve({ok:true,cookie:''});" +
            "  }," +
            "  clearNeteaseMusicLogin:function(){return Promise.resolve();}," +
            "  openQQMusicLogin:function(){" +
            "    if(window.AndroidBridge)window.AndroidBridge.showToast('正在打开QQ音乐登录...');" +
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

            // 添加“粘贴 Cookie”浮动按钮
            "var btn = document.createElement('button');" +
            "btn.innerText = '📋 粘贴Cookie';" +
            "btn.style.cssText = 'position:fixed;bottom:20px;right:20px;z-index:9999;padding:12px 18px;background:#1DB954;color:white;border:none;border-radius:30px;font-size:14px;box-shadow:0 2px 10px rgba(0,0,0,0.3);';" +
            "btn.onclick = function() {" +
            "  var cookie = prompt('请粘贴你从网易云/QQ音乐复制的Cookie（全部内容）：');" +
            "  if (cookie) {" +
            "    fetch('https://mineradio-android-production-8c4b.up.railway.app/api/login/netease/cookie', {" +
            "      method: 'POST'," +
            "      headers: {'Content-Type':'application/json'}," +
            "      body: JSON.stringify({cookie: cookie})" +
            "    }).then(r => r.json()).then(data => {" +
            "      alert(data.ok ? '✅ 登录成功！' : '❌ 失败：'+data.message);" +
            "    }).catch(e => alert('请求失败，请检查网络'));" +
            "  }" +
            "};" +
            "document.body.appendChild(btn);" +

            "var _origFetch = window.fetch;" +
            "window.__apiBase = \"https://mineradio-android-production-8c4b.up.railway.app\"; window.fetch = function(url, opts) { if (typeof url === \"string\" \u0026\u0026 url.startsWith(\"/api/\")) { url = window.__apiBase + url; }" +
            "  if (typeof url === 'string' && url.startsWith('/api/')) {" +
            "    if (window.AndroidBridge) window.AndroidBridge.showToast('后端未启动');" +
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
            webView.goBack();
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