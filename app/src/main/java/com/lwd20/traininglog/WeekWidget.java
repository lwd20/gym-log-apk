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
 * 요일별 수행 부위 격자.
 *
 * 크기 구간
 *   0 tiny   1칸 — 요일 일곱 글자만. 훈련한 날은 강조색, 오늘은 알약
 *   1 small  2칸 — 헤더 + 요일 줄 + 한 줄짜리 부위 상태(한 것 / 안 한 것)
 *   2 medium 3칸 — 헤더 + 요일 줄 + 부위 네 줄 (유산소·팔 순으로 접음)
 *   3 large  4칸 — 여섯 줄 전부 + 요약 푸터
 *
 * 격자의 값어치는 두 축(요일 x 부위)이 동시에 보인다는 점이므로, 한 축만 남길
 * 바에는 축을 바꾼다. tiny 는 '언제 갔나', small 은 '무엇을 했나'로 질문이 다르다.
 */
public class WeekWidget extends AppWidgetProvider {
  static final int[] DD = {R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6};
  static final int[] PL = {R.id.pl0, R.id.pl1, R.id.pl2, R.id.pl3, R.id.pl4, R.id.pl5};
  static final int[] ROW = {R.id.wrow0, R.id.wrow1, R.id.wrow2, R.id.wrow3, R.id.wrow4, R.id.wrow5};
  static final int[][] CE = {
    {R.id.w00, R.id.w01, R.id.w02, R.id.w03, R.id.w04, R.id.w05, R.id.w06},
    {R.id.w10, R.id.w11, R.id.w12, R.id.w13, R.id.w14, R.id.w15, R.id.w16},
    {R.id.w20, R.id.w21, R.id.w22, R.id.w23, R.id.w24, R.id.w25, R.id.w26},
    {R.id.w30, R.id.w31, R.id.w32, R.id.w33, R.id.w34, R.id.w35, R.id.w36},
    {R.id.w40, R.id.w41, R.id.w42, R.id.w43, R.id.w44, R.id.w45, R.id.w46},
    {R.id.w50, R.id.w51, R.id.w52, R.id.w53, R.id.w54, R.id.w55, R.id.w56}
  };
  static final String[] DAY = {"M", "T", "W", "T", "F", "S", "S"};
  static final String[] PART = {"가슴", "등", "어깨", "하체", "팔", "유산소"};

  /** 구간별 최소 상자(dp) — 폭·높이 모두 단조 증가 */
  static final int[][] BOX = {{145, 40}, {215, 110}, {215, 180}, {250, 250}};
  static final int FALLBACK = 3;
  /** 구간별로 보여줄 부위 줄 수 (0=격자 없음) */
  static final int[] ROWS = {0, 0, 4, 6};

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
    int[] ids = m.getAppWidgetIds(new ComponentName(c, WeekWidget.class));
    if (ids == null || ids.length == 0) return;

    JSONArray w = null;
    try {
      String raw = c.getSharedPreferences("widget", 0).getString("data", null);
      if (raw != null) w = new JSONObject(raw).getJSONArray("w");
    } catch (Exception e) {}

    boolean[][] done = new boolean[6][7];
    for (int d = 0; d < 7; d++) {
      String parts = "";
      try { if (w != null) parts = w.getString(d); } catch (Exception e) {}
      for (int p = 0; p < 6; p++) done[p][d] = parts.contains(PART[p]);
    }
    /* 그 날 무엇이든 했는지 */
    boolean[] any = new boolean[7];
    int days = 0;
    for (int d = 0; d < 7; d++) {
      for (int p = 0; p < 6; p++) if (done[p][d]) { any[d] = true; break; }
      if (any[d]) days++;
    }

    boolean stale = WDate.weekStale(c);
    int today = stale ? -1 : WDate.dow();
    String tag = WDate.staleTag(c);

    PendingIntent pi = PendingIntent.getActivity(c, 0,
        new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

    for (int wi = 0; wi < ids.length; wi++) {
      int id = ids[wi];
      int cbg = WCfg.bg(c, id), cop = WCfg.op(c, id);
      boolean lt = !WCfg.lightText(cbg, cop, WCfg.txMode(c, id));
      RemoteViews[] v = new RemoteViews[BOX.length];
      for (int b = 0; b < BOX.length; b++) {
        v[b] = build(c, b, done, any, days, today, tag, lt, cbg, cop, pi);
      }
      RemoteViews out = WSize.combine(c, id, BOX, v, FALLBACK);
      if (out != null) m.updateAppWidget(id, out);
    }
  }

  static RemoteViews build(Context c, int bucket, boolean[][] done, boolean[] any, int days,
                           int today, String tag, boolean lt, int cbg, int cop, PendingIntent pi) {
    RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_week);
    int rows = ROWS[Math.max(0, Math.min(ROWS.length - 1, bucket))];
    boolean tiny = bucket == 0;
    boolean small = bucket == 1;

    /* 한 칸(40dp)에는 기본 여백 25dp 로는 요일 줄이 들어가지 않는다.
       setViewPadding 은 dp 가 아니라 px 를 받으므로 density 로 환산한다. */
    if (tiny) {
      float d = c.getResources().getDisplayMetrics().density;
      rv.setViewPadding(R.id.content,
          Math.round(16 * d), Math.round(4 * d), Math.round(16 * d), Math.round(4 * d));
    }

    rv.setViewVisibility(R.id.whd, tiny ? View.GONE : View.VISIBLE);
    if (!tiny) {
      rv.setTextViewText(R.id.whd, "THIS WEEK" + tag);
      rv.setTextColor(R.id.whd, WTheme.head(lt));
    }
    /* 격자가 없으면 왼쪽 라벨 칸도 필요 없다 */
    rv.setViewVisibility(R.id.wgut, rows > 0 ? View.VISIBLE : View.GONE);

    for (int d = 0; d < 7; d++) {
      rv.setTextViewText(DD[d], DAY[d]);
      if (d == today) {
        rv.setInt(DD[d], "setBackgroundResource", WTheme.today(lt));
        rv.setTextColor(DD[d], WTheme.todayText(lt));
      } else {
        rv.setInt(DD[d], "setBackgroundResource", 0);
        /* 격자가 없는 구간에서는 요일 글자가 그날의 수행 여부를 대신 나른다 */
        boolean hot = rows == 0 && any[d];
        rv.setTextColor(DD[d], hot ? WTheme.value(lt) : WTheme.dim(lt));
      }
    }

    for (int p = 0; p < 6; p++) {
      boolean on = p < rows;
      rv.setViewVisibility(ROW[p], on ? View.VISIBLE : View.GONE);
      if (!on) continue;
      rv.setTextViewText(PL[p], PART[p]);
      rv.setTextColor(PL[p], WTheme.head(lt));
      for (int d = 0; d < 7; d++) {
        rv.setImageViewResource(CE[p][d], done[p][d] ? WTheme.dotOn(lt) : WTheme.dotOff(lt));
      }
    }

    /* small — 격자 대신 한 줄로 무엇을 했고 무엇이 남았는지 */
    rv.setViewVisibility(R.id.wstat, small ? View.VISIBLE : View.GONE);
    if (small) {
      StringBuilder hit = new StringBuilder(), miss = new StringBuilder();
      for (int p = 0; p < 6; p++) {
        boolean d0 = false;
        for (int d = 0; d < 7; d++) if (done[p][d]) { d0 = true; break; }
        StringBuilder b = d0 ? hit : miss;
        if (b.length() > 0) b.append('·');
        b.append(PART[p]);
      }
      rv.setTextViewText(R.id.wstat, hit.length() > 0 ? hit.toString() : "기록 없음");
      rv.setTextColor(R.id.wstat, hit.length() > 0 ? WTheme.value(lt) : WTheme.dim(lt));
    }

    boolean foot = rows >= 6;
    rv.setViewVisibility(R.id.wfoot, foot ? View.VISIBLE : View.GONE);
    if (foot) {
      StringBuilder miss = new StringBuilder();
      for (int p = 0; p < 6; p++) {
        boolean d0 = false;
        for (int d = 0; d < 7; d++) if (done[p][d]) { d0 = true; break; }
        if (!d0) { if (miss.length() > 0) miss.append('·'); miss.append(PART[p]); }
      }
      rv.setTextViewText(R.id.wfoot,
          days + "/7일" + (miss.length() > 0 ? ("   미실시 " + miss) : ""));
      rv.setTextColor(R.id.wfoot, WTheme.dim(lt));
    }

    rv.setOnClickPendingIntent(R.id.root, pi);
    WCfg.paint(rv, cbg, cop);
    return rv;
  }
}
