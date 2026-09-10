package com.lwd20.traininglog;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.os.Environment;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import android.widget.RemoteViews;
import android.os.SystemClock;

public class MainActivity extends Activity {
  private static final String URL = "https://lwd20.github.io/Health_Logging/";
  private static final String CH = "rest";
  private WebView web;
  /** 자동 백업 주기 — 7일 */
  private static final long BK_IV = 7L * 24 * 3600 * 1000;
  /** Downloads에 남겨둘 자동 백업 개수 */
  private static final int BK_KEEP = 6;
  private static final String BK_PRE = "plus25-auto-";
  private ValueCallback<android.net.Uri[]> fileCb;
  private static final int PICK_FILE = 11;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    CrashLog.install(this);
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
    }
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel c = new NotificationChannel(CH, "Rest timer", NotificationManager.IMPORTANCE_LOW);
      c.setShowBadge(false);
      nm.createNotificationChannel(c);
    }
    web = new WebView(this);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        String u = r.getUrl().toString();
        if (u.startsWith(URL)) return false;
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        return true;
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView v, ValueCallback<android.net.Uri[]> cb, FileChooserParams p) {
        if (fileCb != null) fileCb.onReceiveValue(null);
        fileCb = cb;
        Intent it = null;
        try { it = p.createIntent(); } catch (Exception e) {}
        if (it == null) {
          it = new Intent(Intent.ACTION_GET_CONTENT);
          it.addCategory(Intent.CATEGORY_OPENABLE);
          it.setType("*/*");
        }
        try {
          startActivityForResult(Intent.createChooser(it, "Select backup"), PICK_FILE);
        } catch (Exception e) {
          fileCb = null;
          return false;
        }
        return true;
      }
    });
    /* 화면을 상태바 밑까지 늘린다 — 상단 판의 색과 무늬가 시계 뒤까지 이어진다.
       웹은 viewport-fit=cover 와 padding-top:env(safe-area-inset-top) 으로 이미 대비돼 있다. */
    Window win = getWindow();
    win.getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    win.setStatusBarColor(android.graphics.Color.TRANSPARENT);
    web.addJavascriptInterface(new UiBridge(), "AndroidUI");
    web.addJavascriptInterface(new Bridge(), "AndroidTimer");
    web.addJavascriptInterface(new Bridge(), "AndroidApp");
    web.addJavascriptInterface(new ClipBridge(), "AndroidClip");
    web.addJavascriptInterface(new FileBridge(), "AndroidFile");
    web.addJavascriptInterface(new WidgetBridge(), "AndroidWidget");
    web.addJavascriptInterface(new AiBridge(), "AndroidAI");
    web.addJavascriptInterface(new HealthBridge(this, web), "AndroidHealth");
    android.content.IntentFilter tf = new android.content.IntentFilter("com.lwd20.traininglog.TMR_SYNC");
    if (Build.VERSION.SDK_INT >= 33) registerReceiver(tmrRx, tf, android.content.Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(tmrRx, tf);
    RefreshReceiver.pushAll(this);
    RefreshReceiver.schedule(this);
    setContentView(web);
    /* 새 통로를 켠다. 안드로이드 13 미만에서는 이 메서드가 아예 안 불리고,
       그런 기기는 onBackPressed 로 온다. 별도 메서드로 뺀 까닭은 예전 기기에서
       클래스를 못 찾아 검증이 터지지 않게 하기 위해서다 — ART 는 메서드 단위로
       늦게 검증하므로, 안 불리는 메서드 안에 있으면 안전하다. */
    if (Build.VERSION.SDK_INT >= 33) { try { registerBackCallback(); } catch (Throwable t) {} }
    web.loadUrl(URL);
  }

  /* 상태바 영역을 웹에 알려 준다.
     env(safe-area-inset-top) 이 0 을 돌려주는 기기가 있어 머리글이 시계에 가려질 수
     있으므로, 실제 인셋 높이를 CSS 픽셀로 넘겨 웹이 그 값을 우선 쓰게 한다.
     밝은 팔레트에서는 흰 아이콘이 안 보이므로 아이콘 명암도 여기서 뒤집는다. */
  public class UiBridge {
    @JavascriptInterface public int inset() {
      try {
        int px = 0;
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) px = getResources().getDimensionPixelSize(id);
        float dens = getResources().getDisplayMetrics().density;
        return dens > 0 ? Math.round(px / dens) : 0;
      } catch (Exception e) { return 0; }
    }
    @JavascriptInterface public void icons(final boolean darkIcons) {
      runOnUiThread(new Runnable() { public void run() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
          View d = getWindow().getDecorView();
          int f = d.getSystemUiVisibility();
          if (darkIcons) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
          else f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
          d.setSystemUiVisibility(f);
        } catch (Exception e) {}
      }});
    }
  }

  private final android.content.BroadcastReceiver tmrRx = new android.content.BroadcastReceiver() {
    @Override public void onReceive(android.content.Context c, Intent i) {
      final String cmd = i.getStringExtra("cmd");
      final long at = i.getLongExtra("at", System.currentTimeMillis());
      if (web == null || cmd == null) return;
      web.post(new Runnable() { public void run() {
        try { web.evaluateJavascript("window.tmrNative&&tmrNative('" + cmd + "'," + at + ")", null); } catch (Exception e) {}
      }});
    }
  };

  @Override protected void onDestroy() {
    try { unregisterReceiver(tmrRx); } catch (Exception e) {}
    super.onDestroy();
  }

  class Bridge {
    @JavascriptInterface public String last() {
      android.content.SharedPreferences sp = getSharedPreferences("timer", 0);
      return sp.getString("act", "") + "|" + sp.getLong("at", 0L);
    }
    @JavascriptInterface public void start(double elapsedMs) { start(elapsedMs, 120000d); }
    @JavascriptInterface public void start(double elapsedMs, double ivMs) {
      Intent i = new Intent(MainActivity.this, TimerService.class);
      i.setAction(TimerService.ACT_START);
      i.putExtra("elapsed", (long) elapsedMs);
      i.putExtra("iv", (long) ivMs);
      if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
      else startService(i);
    }
    @JavascriptInterface public void exit() {
      runOnUiThread(new Runnable() { @Override public void run() {
        try { finishAffinity(); } catch (Exception e) { finish(); }
      }});
    }
    @JavascriptInterface public String pull() {
      try {
        android.content.SharedPreferences sp = getSharedPreferences("timer", 0);
        String a = sp.getString("act", "");
        if (a == null || a.length() == 0) return "";
        sp.edit().remove("act").apply();
        return a;
      } catch (Exception e) { return ""; }
    }
    @JavascriptInterface public void stop() {
      Intent i = new Intent(MainActivity.this, TimerService.class);
      i.setAction(TimerService.ACT_STOP);
      try { startService(i); } catch (Exception e) {}
      try { stopService(new Intent(MainActivity.this, TimerService.class)); } catch (Exception e) {}
    }
  }

  class AiBridge {
    @JavascriptInterface public void run(String spec) {
      Intent i = new Intent(MainActivity.this, AiService.class);
      i.putExtra("spec", spec);
      if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }
    @JavascriptInterface public String take() {
      android.content.SharedPreferences sp = getSharedPreferences("ai_bg", MODE_PRIVATE);
      String s = sp.getString("res", "");
      if (s != null && !s.isEmpty()) sp.edit().remove("res").apply();
      return s == null ? "" : s;
    }
  }

  class WidgetBridge {
    @JavascriptInterface public void sync(String json) {
      try {
        getSharedPreferences("widget", 0).edit().putString("data", json).apply();
        VolumeWidget.push(MainActivity.this);
        WeekWidget.push(MainActivity.this);
        FreqWidget.push(MainActivity.this);
          LastWidget.push(MainActivity.this);
          TodayWidget.push(MainActivity.this);
          CutWidget.push(MainActivity.this);
          NextWidget.push(MainActivity.this);
          PrWidget.push(MainActivity.this);
          LmWidget.push(MainActivity.this);
      } catch (Exception e) {}
    }
  }

  class ClipBridge {
    @JavascriptInterface public String read() {
      final String[] out = new String[]{""};
      final CountDownLatch latch = new CountDownLatch(1);
      runOnUiThread(new Runnable() { public void run() {
        try {
          ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
          ClipData cd = cm.getPrimaryClip();
          if (cd != null && cd.getItemCount() > 0) {
            CharSequence t = cd.getItemAt(0).coerceToText(MainActivity.this);
            if (t != null) out[0] = t.toString();
          }
        } catch (Exception e) {}
        latch.countDown();
      }});
      try { latch.await(); } catch (InterruptedException e) {}
      return out[0];
    }
  }

  class FileBridge {
    @JavascriptInterface public boolean save(String name, String content) {
      boolean ok = write(name, content);
      if (ok) runOnUiThread(new Runnable() { public void run() {
        Toast.makeText(MainActivity.this, "Downloads에 저장됨", Toast.LENGTH_SHORT).show();
      }});
      return ok;
    }

    /**
     * 마지막 자동 백업으로부터 7일이 지났으면 Downloads에 저장함.
     * 저장했으면 그 날짜(ISO)를, 아직 이르거나 실패했으면 빈 문자열을 돌려줌.
     * 기록은 앱이 열려 있을 때만 늘어나므로 앱 실행 시점에 검사하면 충분함.
     */
    @JavascriptInterface public String autoBackup(String content) {
      try {
        if (content == null || content.length() < 32) return "";
        android.content.SharedPreferences sp = getSharedPreferences("backup", MODE_PRIVATE);
        long last = sp.getLong("at", 0L), now = System.currentTimeMillis();
        if (last > 0 && now - last < BK_IV) return "";
        String date = WDate.todayIso();
        if (!write(BK_PRE + date + ".json", content)) return "";
        sp.edit().putLong("at", now).putString("date", date).apply();
        prune();
        return date;
      } catch (Exception e) { return ""; }
    }

    /**
     * 웹 데이터를 앱 전용 저장소에 복제해둠.
     * WebView 저장소가 비워져도(캐시 삭제·저장공간 회수) 여기서 되살릴 수 있음.
     * 임시 파일에 쓰고 이름을 바꾸는 방식이라 쓰다 끊겨도 직전 사본이 남음.
     */
    @JavascriptInterface public boolean mirror(String content) {
      try {
        if (content == null || content.length() < 32) return false;
        File dir = getFilesDir();
        File tmp = new File(dir, "mirror.tmp");
        FileOutputStream fo = new FileOutputStream(tmp);
        fo.write(content.getBytes("UTF-8"));
        fo.getFD().sync();
        fo.close();
        File cur = new File(dir, "mirror.json");
        File prev = new File(dir, "mirror.prev.json");
        if (cur.exists()) { prev.delete(); cur.renameTo(prev); }
        if (!tmp.renameTo(cur)) return false;
        getSharedPreferences("backup", MODE_PRIVATE).edit()
          .putString("mirror", WDate.todayIso()).apply();
        return true;
      } catch (Exception e) { return false; }
    }

    /** 복제본을 돌려줌. 최신본이 깨져 있으면 직전 사본으로 물러남. */
    @JavascriptInterface public String mirrorRead() {
      String s = readPrivate("mirror.json");
      if (s.length() < 32) s = readPrivate("mirror.prev.json");
      return s;
    }

    /** 마지막 복제 날짜 — 설정 화면 표시용 */
    @JavascriptInterface public String mirrorDate() {
      try {
        return getSharedPreferences("backup", MODE_PRIVATE).getString("mirror", "");
      } catch (Exception e) { return ""; }
    }

    String readPrivate(String name) {
      java.io.FileInputStream in = null;
      try {
        File f = new File(getFilesDir(), name);
        if (!f.exists() || f.length() < 32) return "";
        byte[] b = new byte[(int) f.length()];
        in = new java.io.FileInputStream(f);
        int off = 0, r;
        while (off < b.length && (r = in.read(b, off, b.length - off)) > 0) off += r;
        return new String(b, 0, off, "UTF-8");
      } catch (Exception e) { return ""; }
      finally { try { if (in != null) in.close(); } catch (Exception e) {} }
    }

    boolean write(String name, String content) {
      try {
        if (Build.VERSION.SDK_INT >= 29) {
          ContentValues cv = new ContentValues();
          cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
          cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
          android.net.Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
          if (uri == null) return false;
          OutputStream os = getContentResolver().openOutputStream(uri);
          os.write(content.getBytes("UTF-8"));
          os.close();
        } else {
          File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
          if (!dir.exists()) dir.mkdirs();
          FileOutputStream fo = new FileOutputStream(new File(dir, name));
          fo.write(content.getBytes("UTF-8"));
          fo.close();
        }
        return true;
      } catch (Exception e) { return false; }
    }

    /**
     * 자동 백업만 최신 BK_KEEP개로 정리함.
     * 이름이 plus25-auto- 로 시작하는 것만 대상으로 하므로
     * 직접 저장한 백업(plus25-<날짜>.json)은 건드리지 않음.
     */
    void prune() {
      try {
        if (Build.VERSION.SDK_INT >= 29) {
          android.database.Cursor cu = getContentResolver().query(
              MediaStore.Downloads.EXTERNAL_CONTENT_URI,
              new String[]{MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME},
              MediaStore.Downloads.DISPLAY_NAME + " LIKE ?",
              new String[]{BK_PRE + "%"},
              MediaStore.Downloads.DISPLAY_NAME + " DESC");
          if (cu == null) return;
          int seen = 0;
          try {
            while (cu.moveToNext()) {
              String nm = cu.getString(1);
              if (nm == null || !nm.startsWith(BK_PRE)) continue;
              seen++;
              if (seen <= BK_KEEP) continue;
              android.net.Uri u = android.content.ContentUris.withAppendedId(
                  MediaStore.Downloads.EXTERNAL_CONTENT_URI, cu.getLong(0));
              try { getContentResolver().delete(u, null, null); } catch (Exception e) {}
            }
          } finally { cu.close(); }
        } else {
          File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
          File[] fs = dir.listFiles();
          if (fs == null) return;
          java.util.ArrayList<File> mine = new java.util.ArrayList<File>();
          for (int i = 0; i < fs.length; i++)
            if (fs[i].isFile() && fs[i].getName().startsWith(BK_PRE)) mine.add(fs[i]);
          java.util.Collections.sort(mine, new java.util.Comparator<File>() {
            public int compare(File a, File b) { return b.getName().compareTo(a.getName()); }
          });
          for (int i = BK_KEEP; i < mine.size(); i++) { try { mine.get(i).delete(); } catch (Exception e) {} }
        }
      } catch (Exception e) {}
    }
  }

  @Override protected void onActivityResult(int req, int res, Intent data) {
    super.onActivityResult(req, res, data);
    if (req == PICK_FILE && fileCb != null) {
      android.net.Uri[] out = null;
      if (res == RESULT_OK) {
        out = WebChromeClient.FileChooserParams.parseResult(res, data);
        if (out == null && data != null && data.getData() != null) out = new android.net.Uri[]{ data.getData() };
      }
      fileCb.onReceiveValue(out);
      fileCb = null;
    }
  }

  /* 뒤로가기를 한 곳에서 처리한다. 예전에는 onBackPressed 하나였는데,
     안드로이드 13 부터 예측형 뒤로가기가 들어오면서 새 통로(OnBackInvokedCallback)
     가 생겼고 enableOnBackInvokedCallback 을 켜면 옛 통로는 안 불린다.
     안 옮기면 뒤로가기가 웹에 안 가고 액티비티가 그냥 닫힌다 — 시트를 닫으려던
     손짓이 앱을 끄는 손짓이 된다. 두 통로가 같은 함수를 부르게 두면
     어느 안드로이드에서도 같게 움직인다. */
  private void handleBack() {
    /* 웹이 pushState로 히스토리를 쌓으므로 항상 웹에 위임 — 시트 닫기/종료 확인은 웹이 판단 */
    if (web.canGoBack()) { web.goBack(); return; }
    try { web.evaluateJavascript("(function(){try{exitAsk(true);return 1;}catch(e){return 0;}})()", null); }
    catch (Exception e) { try { finish(); } catch (Exception e2) {} }
  }

  /* 옛 통로. 안드로이드 12 이하와, 13+ 라도 예측형이 꺼진 기기가 여기로 온다 */
  @Override public void onBackPressed() { handleBack(); }

  /* 새 통로 등록. 안드로이드 13 미만에서는 부르지 않는다 —
     그 기기에는 이 클래스들이 아예 없다. */
  @android.annotation.TargetApi(33)
  private void registerBackCallback() {
    getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
      android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
      new android.window.OnBackInvokedCallback() {
        @Override public void onBackInvoked() { handleBack(); }
      });
  }
}
