package com.lwd20.traininglog;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 볼륨 랜드마크(MRV) 위젯 — 분석탭의 그래프를 홈 화면으로 옮긴 것.
 *
 * 구간(MEV / MAV / 감량 보정 MRV)은 부위마다 고정된 상수라 레이아웃의
 * layout_weight 에 구워져 있다. 여기서는 현재 세트 수만 얹는다.
 * 비트맵을 쓰지 않으므로 Binder 전송량이나 메모리 부담이 없다.
 *
 * 자리마다 부위가 정해져 있다는 점이 중요하다. 구간 띠가 그 부위의 값으로
 * 구워져 있으므로 데이터를 다른 자리로 옮기면 띠와 값이 어긋난다.
 * 그래서 작게 줄일 때는 순서를 바꾸지 않고 '보일 행만' 고른다.
 */
public class LmWidget extends AppWidgetProvider {
  static final int[] ROWV = {R.id.lmr0, R.id.lmr1, R.id.lmr2, R.id.lmr3, R.id.lmr4};
  static final int[] NM = {R.id.lmn0, R.id.lmn1, R.id.lmn2, R.id.lmn3, R.id.lmn4};
  /** 점을 감싼 칸 — 왼쪽 패딩을 밀어 점 위치를 정한다 */
  static final int[] DOTW = {R.id.ldw0, R.id.ldw1, R.id.ldw2, R.id.ldw3, R.id.ldw4};
  static final int[] DOT = {R.id.ld0, R.id.ld1, R.id.ld2, R.id.ld3, R.id.ld4};
  /** 막대 바탕 — 배경을 투명하게 두면 벽지에 묻히므로 밝기에 맞춰 갈아 낀다 */
  static final int[] TRK = {R.id.ltk0, R.id.ltk1, R.id.ltk2, R.id.ltk3, R.id.ltk4};
  /** 구간 띠와 눈금 — 분석탭과 같은 색을 쓰되 배경 밝기에 따라 갈아 낀다 */
  static final int[] ZA = {R.id.za0, R.id.za1, R.id.za2, R.id.za3, R.id.za4};
  static final int[] ZB = {R.id.zb0, R.id.zb1, R.id.zb2, R.id.zb3, R.id.zb4};
  static final int[] ZC = {R.id.zc0, R.id.zc1, R.id.zc2, R.id.zc3, R.id.zc4};
  static final int[] TK = {R.id.tk0, R.id.tk1, R.id.tk2, R.id.tk3, R.id.tk4};
  static final int[] VAL = {R.id.lmv0, R.id.lmv1, R.id.lmv2, R.id.lmv3, R.id.lmv4};
  /** 막대를 뺀 나머지 칸의 합(dp) — 좌우 여백 14+14, 이름 46, 값 22, 사이 6 */
  static final int SIDE_DP = 102, DOT_DP = 8, MIN_BAR_DP = 60;
  /** 구간별 최소 상자(dp). 실측 2행 76.9 / 3행 97.0 / 5행+푸터 152.8 */
  static final int[][] BOX = {{180, 82}, {180, 102}, {180, 158}};
  static final int FALLBACK = 2;
  static final int[] ROWS = {2, 3, 5};

  @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { push(c); }

  @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
    push(c);
  }

  static void push(Context c) {
    CrashLog.install(c);
    try { pushInner(c); } catch (Throwable t) { CrashLog.save(c, t); }
  }

  static void pushInner(Context c) {
    AppWidgetManager m = AppWidgetManager.getInstance(c);
    int[] ids = m.getAppWidgetIds(new ComponentName(c, LmWidget.class));
    if (ids == null || ids.length == 0) return;

    String[] nm = new String[5];
    int[] cur = new int[5];
    double[] mx = new double[5];
    boolean[] over = new boolean[5];
    boolean has = false;
    try {
      String raw = c.getSharedPreferences("widget", 0).getString("data", null);
      JSONArray a = raw == null ? null : new JSONObject(raw).optJSONArray("lm");
      if (a != null) {
        for (int i = 0; i < 5 && i < a.length(); i++) {
          JSONArray r = a.getJSONArray(i);
          nm[i] = r.getString(0);
          cur[i] = r.getInt(1);
          mx[i] = r.getDouble(2);
          over[i] = r.optInt(3, 0) == 1;   /* 보정 MRV 초과 여부는 웹이 판정 */
          has = true;
        }
      }
    } catch (Exception e) { has = false; }

    String stale = WDate.staleTag(c);
    PendingIntent pi = PendingIntent.getActivity(c, 0,
        new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

    for (int wi = 0; wi < ids.length; wi++) {
      int id = ids[wi];
      int cbg = WCfg.bg(c, id), cop = WCfg.op(c, id);
      /* 배치 직후엔 옵션이 비어 오므로 가장 작은 구간의 폭으로 버틴다 */
      int wDp = 0;
      try {
        Bundle wo = m.getAppWidgetOptions(id);
        if (wo != null) wDp = wo.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
      } catch (Exception e) {}
      if (wDp <= 0) wDp = BOX[0][0];
      boolean lt = !WCfg.lightText(cbg, cop, WCfg.txMode(c, id));
      RemoteViews[] vs = new RemoteViews[BOX.length];
      for (int b = 0; b < BOX.length; b++) {
        vs[b] = build(c, b, nm, cur, mx, over, has, stale, lt, cbg, cop, pi, wDp);
      }
      RemoteViews out = WSize.combine(c, id, BOX, vs, FALLBACK);
      if (out != null) m.updateAppWidget(id, out);
    }
  }

  /**
   * 자리를 줄여야 할 때 남길 행을 고름. 클수록 먼저 남는다.
   * 보정 MRV 를 넘긴 부위가 무조건 위, 그 다음 축 대비 덜 채운 순.
   * 배열 순서로 자르면 팔·기타가 늘 사라지는데 그 위치엔 아무 의미도 없다.
   */
  static double score(int[] cur, double[] mx, boolean[] over, int i) {
    if (mx[i] <= 0) return -1;
    return (over[i] ? 100 : 0) + (1 - cur[i] / mx[i]);
  }

  static RemoteViews build(Context c, int bucket, String[] nm, int[] cur, double[] mx,
                           boolean[] over, boolean has, String stale,
                           boolean lt, int cbg, int cop, PendingIntent pi, int widthDp) {
    RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_lm);
    int rows = ROWS[Math.max(0, Math.min(ROWS.length - 1, bucket))];
    boolean full = rows >= 5;

    /* 점수 상위 rows 개만 남긴다. 자리는 그대로 두므로 띠와 값이 어긋나지 않는다. */
    boolean[] show = new boolean[5];
    if (full) {
      for (int i = 0; i < 5; i++) show[i] = true;
    } else {
      for (int n = 0; n < rows; n++) {
        int best = -1;
        for (int i = 0; i < 5; i++) {
          if (show[i] || mx[i] <= 0) continue;
          if (best < 0 || score(cur, mx, over, i) > score(cur, mx, over, best)) best = i;
        }
        if (best < 0) break;
        show[best] = true;
      }
    }

    /* 막대는 weight 로 늘어나므로 폭을 알아야 점을 놓을 수 있다.
       MIN_WIDTH 는 세로 방향에서의 실제 폭이라 여기에 쓸 수 있다. */
    float dens = c.getResources().getDisplayMetrics().density;
    /* 폭 추정이 조금이라도 크면 점이 막대를 벗어난다. 그건 그래프가 아니라
       오류로 보이므로 오른쪽에 여유를 두고 안쪽으로 물린다. */
    int barDp = Math.max(MIN_BAR_DP, widthDp - SIDE_DP);
    int travel = Math.max(0, Math.round((barDp - DOT_DP - 6) * dens));

    rv.setTextViewText(R.id.lm_hd, "VOLUME LANDMARKS" + (full ? stale : ""));
    rv.setTextColor(R.id.lm_hd, WTheme.head(lt));

    for (int i = 0; i < 5; i++) {
      boolean on = has && nm[i] != null && show[i];
      rv.setViewVisibility(ROWV[i], on ? View.VISIBLE : View.GONE);
      if (!on) continue;
      rv.setTextViewText(NM[i], nm[i]);
      rv.setTextColor(NM[i], WTheme.head(lt));
      rv.setTextViewText(VAL[i], String.valueOf(cur[i]));
      /* 보정 MRV 를 넘겼으면 숫자를 경고색으로 — 막대 드로어블은 런타임에 못 바꾼다 */
      rv.setTextColor(VAL[i], over[i] ? 0xFFD9603F : WTheme.value(lt));
      /* 막대를 가득 채우면 구간 띠를 덮어 그래프 구실을 못 한다.
         분석탭처럼 현재값을 점 하나로만 찍는다. */
      boolean out = mx[i] > 0 && cur[i] > mx[i];
      double frac = mx[i] > 0 ? cur[i] / mx[i] : 0;
      if (frac < 0) frac = 0;
      if (frac > 1) frac = 1;
      /* 축을 벗어나면 자리로는 구분되지 않으므로(28 이든 40 이든 끝)
         모양 자체를 마름모로 바꿔 눈금 밖임을 알린다 */
      /* 구간 띠가 금색이라 점도 금색이면 겹쳐서 안 보인다 —
         배경 밝기에 따라 가장 먼 색으로 뒤집는다 */
      rv.setImageViewResource(DOT[i],
          out ? R.drawable.wg_lm_out
              : (over[i] ? R.drawable.wg_lm_dot_over
                         : (lt ? R.drawable.wg_lm_dot_d : R.drawable.wg_lm_dot)));
      rv.setViewPadding(DOTW[i], (int) Math.round(frac * travel), 0, 0, 0);
      rv.setInt(TRK[i], "setBackgroundResource",
          lt ? R.drawable.wg_lm_track_d : R.drawable.wg_lm_track);
      rv.setInt(ZA[i], "setBackgroundResource",
          lt ? R.drawable.wg_lm_z1_d : R.drawable.wg_lm_z1);
      rv.setInt(ZB[i], "setBackgroundResource",
          lt ? R.drawable.wg_lm_z2_d : R.drawable.wg_lm_z2);
      rv.setInt(ZC[i], "setBackgroundResource",
          lt ? R.drawable.wg_lm_z2_d : R.drawable.wg_lm_z2);
      rv.setInt(TK[i], "setBackgroundResource",
          lt ? R.drawable.wg_lm_tick_d : R.drawable.wg_lm_tick);
    }

    boolean foot = full || !has;
    rv.setViewVisibility(R.id.lm_foot, foot ? View.VISIBLE : View.GONE);
    if (foot) {
      rv.setTextViewText(R.id.lm_foot, has ? "눈금 = 감량 보정 MRV" : "앱을 한 번 열면 동기화됨");
      rv.setTextColor(R.id.lm_foot, WTheme.dim(lt));
    }

    rv.setOnClickPendingIntent(R.id.root, pi);
    WCfg.paint(rv, cbg, cop);
    return rv;
  }
}
