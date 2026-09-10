package com.lwd20.traininglog;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.widget.RemoteViews;
import java.util.HashMap;
import java.util.Map;

/**
 * 위젯 크기에 따라 다른 화면을 고르는 헬퍼.
 *
 * API 31+ : RemoteViews(Map&lt;SizeF,RemoteViews&gt;) 로 모든 구간을 한 번에 넘기면
 *           시스템이 크기 변경과 회전 때 브로드캐스트 없이 스스로 다시 고름.
 * API 24~30: 크기를 아는 생성자가 없으므로 옵션에서 세로·가로 상자를 각각 구해
 *           RemoteViews(landscape, portrait) 2인자 생성자로 묶음.
 *
 * 옵션 값의 함정: MIN/MAX 는 '지금 크기'가 아니라 두 방향을 아우르는 경계임.
 *   세로 = (MIN_WIDTH, MAX_HEIGHT), 가로 = (MAX_WIDTH, MIN_HEIGHT).
 *   MIN 끼리 짝지으면 세로에서 절대 큰 구간에 닿지 못하고,
 *   MAX 끼리 짝지으면 어느 방향에도 없는 상자가 나옴.
 * 값은 이미 dp 이므로 density 를 곱하면 안 됨.
 *
 * 묶인 RemoteViews 는 불변이라 이후 addAction 계열이 예외를 던짐.
 * 각 구간을 완전히 구성한 뒤에 묶어야 함.
 */
public class WSize {

  private static Bundle opts(Context c, int id) {
    try {
      Bundle b = AppWidgetManager.getInstance(c).getAppWidgetOptions(id);
      if (b != null) return b;
    } catch (Exception e) {}
    return new Bundle();
  }

  /** 세로 방향의 상자 — 좁고 높은 쪽 */
  private static int[] portrait(Context c, int id) {
    Bundle o = opts(c, id);
    return new int[]{
        o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
        o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)};
  }

  /** 가로 방향의 상자 — 넓고 낮은 쪽 */
  private static int[] landscape(Context c, int id) {
    Bundle o = opts(c, id);
    return new int[]{
        o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0),
        o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)};
  }

  /**
   * 주어진 상자에 들어가는 가장 큰 구간의 번호.
   * table 은 폭·높이 모두 단조 증가해야 하며, 옵션이 비어 있으면 def 를 돌려줌
   * (배치 직후나 일부 런처에서는 옵션이 0 으로 온다).
   */
  public static int pick(int[][] table, int w, int h, int def) {
    if (w <= 0 || h <= 0) return def;
    int best = 0;
    for (int i = 0; i < table.length; i++) {
      if (w >= table[i][0] && h >= table[i][1]) best = i;
    }
    return best;
  }

  /** i 번 구간의 화면. 비어 있으면 아래쪽, 그래도 없으면 위쪽에서 가장 가까운 것. */
  private static RemoteViews at(RemoteViews[] v, int i) {
    if (i < 0) i = 0;
    if (i >= v.length) i = v.length - 1;
    for (int k = i; k >= 0; k--) if (v[k] != null) return v[k];
    for (int k = i + 1; k < v.length; k++) if (v[k] != null) return v[k];
    return null;
  }

  /**
   * 구간별로 이미 완성된 화면들을 하나로 묶음.
   * 전부 null 이면 null 을 돌려주므로 호출부에서 확인해야 함.
   */
  public static RemoteViews combine(Context c, int id, int[][] table, RemoteViews[] views, int def) {
    if (views == null || views.length == 0) return null;
    boolean any = false;
    for (int i = 0; i < views.length; i++) if (views[i] != null) { any = true; break; }
    if (!any) return null;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      RemoteViews r = Api31.sized(table, views);
      if (r != null) return r;
    }
    int[] p = portrait(c, id), l = landscape(c, id);
    RemoteViews rp = at(views, pick(table, p[0], p[1], def));
    RemoteViews rl = at(views, pick(table, l[0], l[1], def));
    if (rp == null) return rl;
    if (rl == null || rp == rl) return rp;
    return new RemoteViews(rl, rp);   /* 가로가 먼저 */
  }

  /** API 31 전용 — 별도 클래스라 하위 버전에서는 검증조차 되지 않음 */
  private static class Api31 {
    static RemoteViews sized(int[][] table, RemoteViews[] views) {
      Map<SizeF, RemoteViews> m = new HashMap<SizeF, RemoteViews>();
      for (int i = 0; i < table.length && i < views.length; i++) {
        if (views[i] != null) m.put(new SizeF(table[i][0], table[i][1]), views[i]);
      }
      if (m.isEmpty()) return null;
      return new RemoteViews(m);
    }
  }
}
