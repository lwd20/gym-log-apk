package com.lwd20.traininglog;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ext.SdkExtensions;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

/**
 * 삼성헬스가 헬스 커넥트에 써 둔 섭취 칼로리를 웹 쪽으로 넘긴다.
 *
 * 지금까지 앱은 섭취량을 'TDEE − 목표적자' 로 역산해서 보여 줬다. 그건 목표치지
 * 실제로 먹은 양이 아니다. 삼성헬스에 적은 음식이 헬스 커넥트를 거쳐 여기로 온다.
 *
 * API 34 클래스는 전부 HcReader 안에 가둬 두었다. 이 클래스는 옛 기기에서도
 * 안전하게 로딩되고, 못 읽는 이유를 문자열로 돌려준다 — 조용히 0 을 주면
 * 사용자는 자기가 기록을 안 한 줄 안다.
 */
public class HealthBridge {

  /** 헬스 커넥트 읽기 권한 — 상수가 API 34 라 문자열로 적는다 */
  static final String PERM = "android.permission.health.READ_NUTRITION";
  /* 체중은 따로 거부할 수 있다. 영양이 이 앱의 본줄기라 state() 는 영양만 보고,
     체중 허용 여부는 결과에 실어 보내 "권한 없음" 과 "기록 없음" 을 웹이 가른다. */
  static final String PERM_W = "android.permission.health.READ_WEIGHT";
  static final int REQ = 77;

  private static final String PREF = "hc";
  private static final String KEY = "res";

  private final Activity act;
  private final WebView web;

  HealthBridge(Activity a, WebView w) { act = a; web = w; }

  /** 왜 못 쓰는지까지 알려 준다. "ok" 가 아니면 그 값이 곧 이유다 */
  /** 체중 읽기가 허용됐는지 — 영양과 따로 거부될 수 있다 */
  @JavascriptInterface public boolean canWeight() {
    if (Build.VERSION.SDK_INT < 34) return false;
    try { return act.checkSelfPermission(PERM_W) == PackageManager.PERMISSION_GRANTED; }
    catch (Throwable t) { return false; }
  }

  @JavascriptInterface public String state() {
    if (Build.VERSION.SDK_INT < 34) return "old:" + Build.VERSION.SDK_INT;
    try {
      if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) < 1)
        return "ext";
    } catch (Throwable t) { return "ext"; }
    if (act.checkSelfPermission(PERM) != PackageManager.PERMISSION_GRANTED) return "perm";
    return "ok";
  }

  /** 권한 창을 띄운다. 결과는 사용자가 고르므로 여기서 기다리지 않는다 */
  @JavascriptInterface public void ask() {
    if (Build.VERSION.SDK_INT < 34) return;
    act.runOnUiThread(new Runnable() { public void run() {
      try { act.requestPermissions(new String[]{PERM, PERM_W}, REQ); } catch (Throwable t) {}
    }});
  }

  /**
   * 최근 며칠치를 읽어 온다. 읽기는 비동기라 끝나면 웹의 hcNative() 를 부르고,
   * 놓쳤을 때를 대비해 take() 로도 꺼낼 수 있게 남겨 둔다.
   */
  @JavascriptInterface public void pull(final int days) {
    final String st = state();
    if (!"ok".equals(st)) { deliver("{\"ok\":false,\"why\":\"" + st + "\"}"); return; }
    act.runOnUiThread(new Runnable() { public void run() {
      try {
        HcReader.read(act, days <= 0 ? 14 : days, new HcReader.Done() {
          public void ok(String json) { deliver(json); }
          public void fail(String why) { deliver("{\"ok\":false,\"why\":" + q(why) + "}"); }
        });
      } catch (Throwable t) {
        deliver("{\"ok\":false,\"why\":" + q(String.valueOf(t.getMessage())) + "}");
      }
    }});
  }

  /** 마지막 결과를 꺼내고 비운다 */
  @JavascriptInterface public String take() {
    SharedPreferences sp = act.getSharedPreferences(PREF, 0);
    String s = sp.getString(KEY, "");
    if (s != null && !s.isEmpty()) sp.edit().remove(KEY).apply();
    return s == null ? "" : s;
  }

  private void deliver(final String json) {
    act.getSharedPreferences(PREF, 0).edit().putString(KEY, json).apply();
    if (web == null) return;
    web.post(new Runnable() { public void run() {
      try { web.evaluateJavascript("window.hcNative&&hcNative(" + json + ")", null); }
      catch (Throwable t) {}
    }});
  }

  private static String q(String s) {
    if (s == null) return "\"\"";
    return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + '"';
  }
}
