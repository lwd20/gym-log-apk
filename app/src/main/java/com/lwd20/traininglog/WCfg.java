package com.lwd20.traininglog;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.RemoteViews;
import org.json.JSONObject;

/** 위젯 인스턴스별 스타일(배경색·투명도·글자) — 앱 설정값을 기본으로 사용 */
public class WCfg {

  /** 위젯을 개별 설정하기 전의 기본 투명도.
      앱 쪽 전역 기본값은 실제로 쓰이지 않아 제거했으므로 상수로 둔다. */
  public static int defOp(Context c) { return 100; }

  /** 기본 배경색 — 앱 테마 브론즈 */
  public static int defBg(Context c) {
    return 0xFF1A140C;
  }

  public static int bg(Context c, int wid) {
    return c.getSharedPreferences("widget", 0).getInt("bg_" + wid, defBg(c));
  }

  public static int op(Context c, int wid) {
    return c.getSharedPreferences("widget", 0).getInt("op_" + wid, defOp(c));
  }

  public static int txMode(Context c, int wid) {
    SharedPreferences sp = c.getSharedPreferences("widget", 0);
    if (sp.contains("tx_" + wid)) return sp.getInt("tx_" + wid, 0);
    /* 개별 설정 전에는 배경 밝기로 자동 판단한다(lightText 참고) */
    return 0;
  }

  /** true = 밝은 글자(어두운 배경용) */
  public static boolean lightText(int bg, int op, int txMode) {
    if (txMode == 1) return true;
    if (txMode == 2) return false;
    /* 자동: 배경이 투명할수록 배경화면이 드러나므로 밝다고 가정 */
    double lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0;
    double eff = lum * (op / 100.0) + 0.85 * (1.0 - (op / 100.0));
    return eff < 0.5;
  }

  /** 배경 이미지에 색·투명도 적용 */
  public static void paint(RemoteViews rv, int bg, int op) {
    rv.setInt(R.id.bgimg, "setColorFilter", 0xFF000000 | (bg & 0x00FFFFFF));
    rv.setInt(R.id.bgimg, "setImageAlpha", Math.round(op * 255f / 100f));
  }
}
