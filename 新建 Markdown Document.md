```
From 0527fc420a4b85093af93c4d08d7a43d4b81a8a0 Mon Sep 17 00:00:00 2001
From: =?UTF-8?q?=E1=A1=A0=E1=A0=B5=E1=A1=A0=E1=A1=B3=20=E1=A1=A0=E1=A0=B5?=
 =?UTF-8?q?=E1=A1=A0=20=E1=A0=AE=E1=A0=A0=E1=A0=A8=E1=A1=A9=E1=A0=8B?=
 =?UTF-8?q?=E1=A0=A0=E1=A0=A8?=
 <125150101+UjuiUjuMandan@users.noreply.github.com>
Date: Thu, 16 Jul 2026 19:36:34 +0800
Subject: [PATCH 1/2] feat: add refresh igneous button to identity cookie
 dialog

Add a neutral button to the identity cookie preference dialog that
deletes the stored igneous cookie and re-fetches it from exhentai.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
---
 .../hippo/ehviewer/client/EhCookieStore.java  | 11 ++++
 .../preference/IdentityCookiePreference.java  | 50 ++++++++++++++++++-
 app/src/main/res/values-zh-rCN/strings.xml    |  1 +
 app/src/main/res/values/strings.xml           |  1 +
 4 files changed, 61 insertions(+), 2 deletions(-)

diff --git a/app/src/main/java/com/hippo/ehviewer/client/EhCookieStore.java b/app/src/main/java/com/hippo/ehviewer/client/EhCookieStore.java
index 59f1daf14..d97cc537f 100644
--- a/app/src/main/java/com/hippo/ehviewer/client/EhCookieStore.java
+++ b/app/src/main/java/com/hippo/ehviewer/client/EhCookieStore.java
@@ -56,6 +56,17 @@ public boolean hasSignedIn() {
                 contains(url, KEY_IPD_PASS_HASH);
     }
 
+    public void removeIgneous() {
+        HttpUrl exUrl = HttpUrl.parse(EhUrl.HOST_EX);
+        if (exUrl == null) return;
+        addCookie(new Cookie.Builder()
+                .name(KEY_IGNEOUS)
+                .value("deleted")
+                .domain(exUrl.host())
+                .expiresAt(0)
+                .build());
+    }
+
     public static Cookie newCookie(Cookie cookie, String newDomain, boolean forcePersistent,
             boolean forceLongLive, boolean forceNotHostOnly) {
         Cookie.Builder builder = new Cookie.Builder();
diff --git a/app/src/main/java/com/hippo/ehviewer/preference/IdentityCookiePreference.java b/app/src/main/java/com/hippo/ehviewer/preference/IdentityCookiePreference.java
index ee2964423..9f41a9ff6 100644
--- a/app/src/main/java/com/hippo/ehviewer/preference/IdentityCookiePreference.java
+++ b/app/src/main/java/com/hippo/ehviewer/preference/IdentityCookiePreference.java
@@ -19,12 +19,17 @@
 import android.content.ClipData;
 import android.content.ClipboardManager;
 import android.content.Context;
+import android.content.DialogInterface;
+import android.os.Handler;
+import android.os.Looper;
 import android.util.AttributeSet;
+import android.widget.Button;
 import android.widget.Toast;
 import androidx.appcompat.app.AlertDialog;
 import com.hippo.ehviewer.EhApplication;
 import com.hippo.ehviewer.R;
 import com.hippo.ehviewer.client.EhCookieStore;
+import com.hippo.ehviewer.client.EhRequestBuilder;
 import com.hippo.ehviewer.client.EhUrl;
 import com.hippo.preference.MessagePreference;
 import com.hippo.text.Html;
@@ -32,10 +37,15 @@
 import java.util.List;
 import okhttp3.Cookie;
 import okhttp3.HttpUrl;
+import okhttp3.OkHttpClient;
+import okhttp3.Request;
+import okhttp3.Response;
 
 public class IdentityCookiePreference extends MessagePreference {
 
+    private AlertDialog mDialog;
     private String message;
+    private CharSequence mDialogMessage;
 
     public IdentityCookiePreference(Context context) {
         super(context);
@@ -82,10 +92,13 @@ private void init() {
             message = EhCookieStore.KEY_IPD_MEMBER_ID + ": " + ipbMemberId + "<br>"
                     + EhCookieStore.KEY_IPD_PASS_HASH + ": " + ipbPassHash + "<br>"
                     + EhCookieStore.KEY_IGNEOUS + ": " + igneous;
-            setDialogMessage(Html.fromHtml(getContext().getString(R.string.settings_eh_identity_cookies_signed, message)));
+            mDialogMessage = Html.fromHtml(getContext().getString(R.string.settings_eh_identity_cookies_signed, message));
+            setDialogMessage(mDialogMessage);
             message = message.replace("<br>", "\n");
         } else {
-            setDialogMessage(getContext().getString(R.string.settings_eh_identity_cookies_tourist));
+            message = null;
+            mDialogMessage = getContext().getString(R.string.settings_eh_identity_cookies_tourist);
+            setDialogMessage(mDialogMessage);
         }
     }
 
@@ -100,6 +113,39 @@ protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
 
                 IdentityCookiePreference.this.onClick(dialog, which);
             });
+            builder.setNeutralButton(R.string.settings_eh_refresh_igneous, null);
         }
     }
+
+    @Override
+    protected void onDialogCreated(AlertDialog dialog) {
+        super.onDialogCreated(dialog);
+        mDialog = dialog;
+        Button refreshBtn = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
+        if (refreshBtn != null) {
+            refreshBtn.setOnClickListener(v -> performIgneousRefresh(refreshBtn));
+        }
+    }
+
+    private void performIgneousRefresh(Button btn) {
+        btn.setEnabled(false);
+        EhApplication.getEhCookieStore(getContext()).removeIgneous();
+        OkHttpClient client = EhApplication.getOkHttpClient(getContext());
+        new Thread(() -> {
+            try {
+                Request request = new EhRequestBuilder(EhUrl.URL_UCONFIG_EX).build();
+                try (Response response = client.newCall(request).execute()) {
+                    // cookie jar updated by interceptors
+                }
+            } catch (Exception ignored) {
+            }
+            new Handler(Looper.getMainLooper()).post(() -> {
+                init();
+                if (mDialog != null && mDialog.isShowing()) {
+                    mDialog.setMessage(mDialogMessage);
+                }
+                btn.setEnabled(true);
+            });
+        }).start();
+    }
 }
diff --git a/app/src/main/res/values-zh-rCN/strings.xml b/app/src/main/res/values-zh-rCN/strings.xml
index b0b50ef8d..31e38c86c 100644
--- a/app/src/main/res/values-zh-rCN/strings.xml
+++ b/app/src/main/res/values-zh-rCN/strings.xml
@@ -698,6 +698,7 @@
     <string name="settings_advanced_dns_over_http_summary">解决某些地区的 DNS 污染问题</string>
     <string name="settings_advanced_domain_fronting_title">域名前置</string>
     <string name="settings_advanced_domain_fronting_summary">绕过 SNI 封锁</string>
+    <string name="settings_eh_refresh_igneous">刷新 IGNEOUS</string>
     <string name="settings_advanced_url_replace_title">url伪装,需关闭SNI</string>
     <string name="settings_advanced_url_replace_summary">url伪装(需重启，开启后账户功能失效)</string>
     <string name="share_favorites_dialog_title">分享收藏</string>
diff --git a/app/src/main/res/values/strings.xml b/app/src/main/res/values/strings.xml
index cae06d48f..b2327f2db 100644
--- a/app/src/main/res/values/strings.xml
+++ b/app/src/main/res/values/strings.xml
@@ -30,6 +30,7 @@
     <string name="settings_advanced_dns_over_http_summary">解决某些地区的 DNS 污染问题</string>
     <string name="settings_advanced_domain_fronting_title">域名前置</string>
     <string name="settings_advanced_domain_fronting_summary">绕过 SNI 封锁</string>
+    <string name="settings_eh_refresh_igneous">REFRESH IGNEOUS</string>
     <string name="settings_advanced_url_replace_title">url伪装,需关闭SNI</string>
     <string name="settings_advanced_url_replace_summary">url伪装(需重启，开启后账户功能失效)</string>
     <string name="settings_about_donate_guide">支持指南</string>

From 9345a8761325c7febea312c8ffbb49890f7f2c21 Mon Sep 17 00:00:00 2001
From: =?UTF-8?q?=E1=A1=A0=E1=A0=B5=E1=A1=A0=E1=A1=B3=20=E1=A1=A0=E1=A0=B5?=
 =?UTF-8?q?=E1=A1=A0=20=E1=A0=AE=E1=A0=A0=E1=A0=A8=E1=A1=A9=E1=A0=8B?=
 =?UTF-8?q?=E1=A0=A0=E1=A0=A8?=
 <125150101+UjuiUjuMandan@users.noreply.github.com>
Date: Thu, 16 Jul 2026 18:54:19 +0800
Subject: [PATCH 2/2] feat: auto-retry exhentai with CF-Connecting-IP on
 igneous=mystery

Add a toggle in advanced settings to enable automatic igneous retry.
Off by default. Also expose the CF-Connecting-IP value as a user-
editable preference so it can be customised without touching defaults.

When exhentai returns igneous=mystery, retry the request with a
CF-Connecting-IP header spoofing a US Cloudflare WARP exit IP.
The igneous cookie is stripped from the retry so the server issues
a fresh valid token. IP is generated at startup; configurable in
advanced settings.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
---
 .../com/hippo/ehviewer/EhApplication.java     | 55 ++++++++++++++++++-
 .../java/com/hippo/ehviewer/Settings.java     | 33 +++++++++++
 .../ui/fragment/AdvancedFragment.java         | 16 ++++++
 app/src/main/res/values-zh-rCN/strings.xml    |  4 ++
 app/src/main/res/values/strings.xml           |  4 ++
 app/src/main/res/xml/advanced_settings.xml    | 12 ++++
 6 files changed, 122 insertions(+), 2 deletions(-)

diff --git a/app/src/main/java/com/hippo/ehviewer/EhApplication.java b/app/src/main/java/com/hippo/ehviewer/EhApplication.java
index 237357147..2a8b86df7 100644
--- a/app/src/main/java/com/hippo/ehviewer/EhApplication.java
+++ b/app/src/main/java/com/hippo/ehviewer/EhApplication.java
@@ -98,6 +98,7 @@
 import okhttp3.ConnectionPool;
 import okhttp3.ConnectionSpec;
 import okhttp3.OkHttpClient;
+import okhttp3.Request;
 import okhttp3.Response;
 
 public class EhApplication extends RecordingApplication {
@@ -177,6 +178,9 @@ public void onCreate() {
         GetText.initialize(this);
         StatusCodeException.initialize(this);
         Settings.initialize(this);
+        if (Settings.getCfConnectingIp() == null) {
+            Settings.putCfConnectingIp(Settings.generateUsWarpIp());
+        }
         ArchiverDownloadCompleter.resumePendingDownloads(this);
         ReadableTime.initialize(this);
         Html.initialize(this);
@@ -402,10 +406,44 @@ public static OkHttpClient getOkHttpClient(@NonNull Context context) {
                     .cache(getOkHttpCache(application))
 //                    .hostnameVerifier((hostname, session) -> true)
 //                    .dispatcher(dispatcher)
+                    .addInterceptor(chain -> {
+                        Request request = chain.request();
+                        Response response = chain.proceed(request);
+                        if ("exhentai.org".equals(request.url().host())) {
+                            boolean mysteryIgneous = false;
+                            for (String setCookie : response.headers("Set-Cookie")) {
+                                if (setCookie.contains("igneous=mystery")) {
+                                    mysteryIgneous = true;
+                                    break;
+                                }
+                            }
+                            if (mysteryIgneous && Settings.getCfAutoRetry()) {
+                                response.close();
+                                String cfIp = Settings.getCfConnectingIp();
+                                if (cfIp != null && !cfIp.isEmpty()) {
+                                    Request retryRequest = request.newBuilder()
+                                            .header("CF-Connecting-IP", cfIp)
+                                            .header("X-Retry-Igneous", "1")
+                                            .build();
+                                    response = chain.proceed(retryRequest);
+                                }
+                            }
+                        }
+                        return response;
+                    })
                     .dns(new EhHosts(application))
-                    .addNetworkInterceptor(sprocket -> {
+                    .addNetworkInterceptor(chain -> {
+                        Request request = chain.request();
+                        if (request.header("X-Retry-Igneous") != null) {
+                            String cookieHeader = request.header("Cookie");
+                            String filtered = stripCookie(cookieHeader, "igneous");
+                            request = request.newBuilder()
+                                    .removeHeader("X-Retry-Igneous")
+                                    .header("Cookie", filtered)
+                                    .build();
+                        }
                         try {
-                            return sprocket.proceed(sprocket.request());
+                            return chain.proceed(request);
                         } catch (NullPointerException e) {
                             throw new NullPointerException(e.getMessage());
                         }
@@ -639,6 +677,19 @@ public static String getDeveloperEmail() {
         return "xiaojieonly$foxmail.com".replace('$', '@');
     }
 
+    private static String stripCookie(String cookieHeader, String cookieName) {
+        if (cookieHeader == null) return "";
+        StringBuilder result = new StringBuilder();
+        for (String part : cookieHeader.split(";")) {
+            String trimmed = part.trim();
+            if (!trimmed.startsWith(cookieName + "=") && !trimmed.equals(cookieName)) {
+                if (result.length() > 0) result.append("; ");
+                result.append(trimmed);
+            }
+        }
+        return result.toString();
+    }
+
     public void registerActivity(Activity activity) {
         mActivityList.add(activity);
     }
diff --git a/app/src/main/java/com/hippo/ehviewer/Settings.java b/app/src/main/java/com/hippo/ehviewer/Settings.java
index 8cc2efc8f..ddb75250d 100644
--- a/app/src/main/java/com/hippo/ehviewer/Settings.java
+++ b/app/src/main/java/com/hippo/ehviewer/Settings.java
@@ -1335,6 +1335,39 @@ public static void putDF(boolean value) {
     }
 
 
+    public static final String KEY_CF_AUTO_RETRY = "cf_auto_retry";
+
+    public static boolean getCfAutoRetry() {
+        return getBoolean(KEY_CF_AUTO_RETRY, false);
+    }
+
+    public static void putCfAutoRetry(boolean value) {
+        putBoolean(KEY_CF_AUTO_RETRY, value);
+    }
+
+    public static final String KEY_CF_CONNECTING_IP = "cf_connecting_ip";
+
+    public static String getCfConnectingIp() {
+        return getString(KEY_CF_CONNECTING_IP, null);
+    }
+
+    public static void putCfConnectingIp(String value) {
+        putString(KEY_CF_CONNECTING_IP, value);
+    }
+
+    public static String generateUsWarpIp() {
+        java.util.Random random = new java.util.Random();
+        char[] choices = {'8', 'a', 'c', 'e'};
+        char x = choices[random.nextInt(4)];
+        int cityFirstNibble = random.nextInt(10);
+        int cityRest = random.nextInt(0x1000);
+        int rand7 = random.nextInt(0x1000);
+        int rand8 = random.nextInt(0x1000);
+        return String.format("2a09:bac1:76%c0:%x%03x:0000:0000:0%03x:0%03x",
+                x, cityFirstNibble, cityRest, rand7, rand8);
+    }
+
+
     private static final String KEY_DOWNLOAD_DELAY = "download_delay";
     private static final int DEFAULT_DOWNLOAD_DELAY = 0;
 
diff --git a/app/src/main/java/com/hippo/ehviewer/ui/fragment/AdvancedFragment.java b/app/src/main/java/com/hippo/ehviewer/ui/fragment/AdvancedFragment.java
index 045f2463e..27ed918cd 100644
--- a/app/src/main/java/com/hippo/ehviewer/ui/fragment/AdvancedFragment.java
+++ b/app/src/main/java/com/hippo/ehviewer/ui/fragment/AdvancedFragment.java
@@ -29,12 +29,14 @@
 import androidx.annotation.NonNull;
 import androidx.annotation.Nullable;
 import androidx.appcompat.app.AlertDialog;
+import androidx.preference.EditTextPreference;
 import androidx.preference.Preference;
 
 import com.hippo.ehviewer.AppConfig;
 import com.hippo.ehviewer.EhApplication;
 import com.hippo.ehviewer.EhDB;
 import com.hippo.ehviewer.R;
+import com.hippo.ehviewer.Settings;
 import com.hippo.ehviewer.ui.wifi.WiFiClientActivity;
 import com.hippo.ehviewer.ui.wifi.WiFiServerActivity;
 import com.hippo.ehviewer.widget.ProgressHelper;
@@ -58,6 +60,8 @@ public class AdvancedFragment extends BasePreferenceFragmentCompat
     private static final String KEY_IMPORT_DATA = "import_data";
     private static final String KEY_WIFI_SERVER = "wifi_server";
     private static final String KEY_WIFI_CLIENT = "wifi_client";
+    private static final String KEY_CF_AUTO_RETRY = Settings.KEY_CF_AUTO_RETRY;
+    private static final String KEY_CF_CONNECTING_IP = Settings.KEY_CF_CONNECTING_IP;
 
     private final DbSyncHandle dbSyncHandle = new DbSyncHandle(Looper.getMainLooper());
 
@@ -74,6 +78,7 @@ public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable S
         Preference importData = findPreference(KEY_IMPORT_DATA);
         Preference socketData = findPreference(KEY_WIFI_SERVER);
         Preference clientData = findPreference(KEY_WIFI_CLIENT);
+        EditTextPreference cfConnectingIp = findPreference(KEY_CF_CONNECTING_IP);
 
         dumpLogcat.setOnPreferenceClickListener(this);
         clearMemoryCache.setOnPreferenceClickListener(this);
@@ -82,6 +87,13 @@ public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable S
         clientData.setOnPreferenceClickListener(this);
 
         appLanguage.setOnPreferenceChangeListener(this);
+
+        if (cfConnectingIp != null) {
+            String currentIp = Settings.getCfConnectingIp();
+            cfConnectingIp.setText(currentIp);
+            cfConnectingIp.setSummary(currentIp);
+            cfConnectingIp.setOnPreferenceChangeListener(this);
+        }
     }
 
     @Override
@@ -192,6 +204,10 @@ public boolean onPreferenceChange(Preference preference, Object newValue) {
             ((EhApplication) getActivity().getApplication()).recreate();
             return true;
         }
+        if (KEY_CF_CONNECTING_IP.equals(key)) {
+            preference.setSummary((String) newValue);
+            return true;
+        }
         return false;
     }
 
diff --git a/app/src/main/res/values-zh-rCN/strings.xml b/app/src/main/res/values-zh-rCN/strings.xml
index 31e38c86c..6614c6ed0 100644
--- a/app/src/main/res/values-zh-rCN/strings.xml
+++ b/app/src/main/res/values-zh-rCN/strings.xml
@@ -699,6 +699,10 @@
     <string name="settings_advanced_domain_fronting_title">域名前置</string>
     <string name="settings_advanced_domain_fronting_summary">绕过 SNI 封锁</string>
     <string name="settings_eh_refresh_igneous">刷新 IGNEOUS</string>
+    <string name="settings_advanced_cf_auto_retry_title">IGNEOUS 自动重试</string>
+    <string name="settings_advanced_cf_auto_retry_summary">igneous=mystery 时自动使用 CF-Connecting-IP 重试</string>
+    <string name="settings_advanced_cf_connecting_ip_title">CF-Connecting-IP</string>
+    <string name="settings_advanced_cf_connecting_ip_summary">CF-Connecting-IP 头内的伪装 IP 地址</string>
     <string name="settings_advanced_url_replace_title">url伪装,需关闭SNI</string>
     <string name="settings_advanced_url_replace_summary">url伪装(需重启，开启后账户功能失效)</string>
     <string name="share_favorites_dialog_title">分享收藏</string>
diff --git a/app/src/main/res/values/strings.xml b/app/src/main/res/values/strings.xml
index b2327f2db..00a18dac1 100644
--- a/app/src/main/res/values/strings.xml
+++ b/app/src/main/res/values/strings.xml
@@ -31,6 +31,10 @@
     <string name="settings_advanced_domain_fronting_title">域名前置</string>
     <string name="settings_advanced_domain_fronting_summary">绕过 SNI 封锁</string>
     <string name="settings_eh_refresh_igneous">REFRESH IGNEOUS</string>
+    <string name="settings_advanced_cf_auto_retry_title">IGNEOUS auto-retry</string>
+    <string name="settings_advanced_cf_auto_retry_summary">Retry with CF-Connecting-IP if igneous=mystery</string>
+    <string name="settings_advanced_cf_connecting_ip_title">CF-Connecting-IP</string>
+    <string name="settings_advanced_cf_connecting_ip_summary">IP address to spoof in CF-Connecting-IP header</string>
     <string name="settings_advanced_url_replace_title">url伪装,需关闭SNI</string>
     <string name="settings_advanced_url_replace_summary">url伪装(需重启，开启后账户功能失效)</string>
     <string name="settings_about_donate_guide">支持指南</string>
diff --git a/app/src/main/res/xml/advanced_settings.xml b/app/src/main/res/xml/advanced_settings.xml
index 610f692f4..e62a6fad0 100644
--- a/app/src/main/res/xml/advanced_settings.xml
+++ b/app/src/main/res/xml/advanced_settings.xml
@@ -101,6 +101,18 @@
         android:summary="@string/settings_advanced_domain_fronting_summary"
         android:title="@string/settings_advanced_domain_fronting_title" />
 
+    <com.hippo.preference.SwitchPreference
+        android:defaultValue="false"
+        android:key="cf_auto_retry"
+        android:summary="@string/settings_advanced_cf_auto_retry_summary"
+        android:title="@string/settings_advanced_cf_auto_retry_title"
+        app:allowDividerAbove="true" />
+
+    <androidx.preference.EditTextPreference
+        android:key="cf_connecting_ip"
+        android:title="@string/settings_advanced_cf_connecting_ip_title"
+        android:dialogTitle="@string/settings_advanced_cf_connecting_ip_title" />
+
     <!--    <com.hippo.preference.SwitchPreference-->
     <!--        android:defaultValue="false"-->
     <!--        android:key="url_replace"-->
```