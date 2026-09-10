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
 * 부위별 주간 수행 횟수.
 *
 * 크기 구간
 *   0 tiny  1칸  — 몇 개 부위를 건드렸는지만 (4/6 부위, 빈 2)
 *   1 small 1칸  — 여섯 부위를 한 줄에 축약 (가3 등2 어2 하2 팔1 유0)
 *   2 medium 2칸 — 지금까지의 여섯 열
 *   3 large 3칸  — 열 + 상대 빈도 막대 + 요일/미실시 푸터
 *
 * 한 칸 높이에서는 여섯 열이 물리적으로 불가능하다. 잘라내는 대신 질문을 바꿨다.
 */
public class FreqWidget extends AppWidgetProvider {
  static final int[] FL = {R.id.fl0, R.id.fl1, R.id.fl2, R.id.fl3, R.id.fl4, R.id.fl5};
  static final int[] FC = {R.id.fc0, R.id.fc1, R.id.fc2, R.id.fc3, R.id.fc4, R.id.fc5};
  static final int[] FB = {R.id.fb0, R.id.fb1, R.id.fb2, R.id.fb3, R.id.fb4, R.id.fb5};
  static final int[] SL = {R.id.sl0, R.id.sl1, R.id.sl2, R.id.sl3, R.id.sl4, R.id.sl5};
  static final int[] SC = {R.id.sc0, R.id.sc1, R.id.sc2, R.id.sc3, R.id.sc4, R.id.sc5};
  static final String[] DEF = {"가슴", "등", "어깨", "하체", "팔", "유산소"};

  /** 구간별 최소 상자(dp) — 폭·높이 모두 단조 증가 */
  static final int[][] BOX = {{110, 40}, {145, 40}, {250, 110}, {250, 180}};
  static final int FALLBACK = 2;
  /** 막대 눈금 — 주 4회를 가득 참으로 봄 */
  static final int BAR_MAX = 4;

  @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { push(c); }

  /** 크기가 바뀌어도 onUpdate 는 오지 않으므로 여기서 다시 그려야 함 */
  @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
    push(c);
  }

  static void push(Context c) {
    CrashLog.install(c);
    try { pushInner(c); } catch (Throwable t) { CrashLog.save(c, t); }
  }

  static void pushInner(Context c) {
    AppWidgetManager m = AppWidgetManager.getInstance(c);
    int[] ids = m.getAppWidgetIds(new ComponentName(c, FreqWidget.class));
    if (ids == null || ids.length == 0) return;

    String[] lb = new String[6];
    int[] fq = new int[6];
    boolean has = false;
    JSONArray arr = null;
    try {
      String raw = c.getSharedPreferences("widget", 0).getString("data", null);
      if (raw != null) arr = new JSONObject(raw).optJSONArray("c");
    } catch (Exception e) {}
    for (int i = 0; i < 6; i++) {
      lb[i] = DEF[i]; fq[i] = -1;
      try {
        if (arr != null && i < arr.length()) {
          JSONArray r = arr.getJSONArray(i);
          lb[i] = r.getString(0);
          fq[i] = r.getInt(1);
          has = true;
        }
      } catch (Exception e) {}
    }

    int done = 0;
    for (int i = 0; i < 6; i++) if (fq[i] > 0) done++;
    String stale = WDate.staleTag(c);

    PendingIntent pi = PendingIntent.getActivity(c, 0,
        new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

    for (int wi = 0; wi < ids.length; wi++) {
      int id = ids[wi];
      int cbg = WCfg.bg(c, id), cop = WCfg.op(c, id);
      boolean lt = !WCfg.lightText(cbg, cop, WCfg.txMode(c, id));
      RemoteViews[] v = new RemoteViews[BOX.length];
      for (int b = 0; b < BOX.length; b++) {
        v[b] = build(c, b, lb, fq, has, done, stale, lt, cbg, cop, pi);
      }
      RemoteViews out = WSize.combine(c, id, BOX, v, FALLBACK);
      if (out != null) m.updateAppWidget(id, out);
    }
  }

  static RemoteViews build(Context c, int bucket, String[] lb, int[] fq, boolean has,
                           int done, String stale, boolean lt, int cbg, int cop, PendingIntent pi) {
    RemoteViews rv;
    if (bucket == 0) {
      rv = new RemoteViews(c.getPackageName(), R.layout.widget_freq_t);
      rv.setTextViewText(R.id.tcov, has ? String.valueOf(done) : "-");
      rv.setTextViewText(R.id.tden, " /6 부위");
      int empty = 6 - done;
      rv.setTextViewText(R.id.tgap,
          !has ? "앱을 한 번 열기" : (stale.length() > 0 ? "지난주" : (empty == 0 ? "완료" : "빈 " + empty)));
      rv.setTextColor(R.id.tcov, has && stale.length() == 0 ? WTheme.value(lt) : WTheme.mute(lt));
      rv.setTextColor(R.id.tden, WTheme.dim(lt));
      rv.setTextColor(R.id.tgap, WTheme.head(lt));

    } else if (bucket == 1) {
      rv = new RemoteViews(c.getPackageName(), R.layout.widget_freq_s);
      for (int i = 0; i < 6; i++) {
        /* 한 칸 높이에서는 열 폭이 30dp 남짓이라 한 글자로 줄임 */
        String s = lb[i];
        rv.setTextViewText(SL[i], s.length() > 0 ? s.substring(0, 1) : "");
        rv.setTextViewText(SC[i], fq[i] >= 0 ? String.valueOf(fq[i]) : "-");
        rv.setTextColor(SL[i], WTheme.head(lt));
        rv.setTextColor(SC[i], fq[i] > 0 && stale.length() == 0 ? WTheme.value(lt) : WTheme.mute(lt));
      }

    } else {
      boolean large = bucket >= 3;
      rv = new RemoteViews(c.getPackageName(), R.layout.widget_freq);
      rv.setTextViewText(R.id.fhd, "SESSIONS / WEEK" + stale);
      rv.setTextColor(R.id.fhd, WTheme.head(lt));
      for (int i = 0; i < 6; i++) {
        rv.setTextViewText(FL[i], lb[i]);
        rv.setTextViewText(FC[i], fq[i] >= 0 ? String.valueOf(fq[i]) : "-");
        rv.setTextColor(FL[i], WTheme.head(lt));
        rv.setTextColor(FC[i], fq[i] > 0 && stale.length() == 0 ? WTheme.value(lt) : WTheme.mute(lt));
        rv.setViewVisibility(FB[i], large ? View.VISIBLE : View.GONE);
        if (large) rv.setProgressBar(FB[i], BAR_MAX, Math.max(0, Math.min(BAR_MAX, fq[i])), false);
      }
      rv.setViewVisibility(R.id.ffoot, large ? View.VISIBLE : View.GONE);
      if (large) {
        StringBuilder miss = new StringBuilder();
        for (int i = 0; i < 6; i++) {
          if (fq[i] == 0) {
            if (miss.length() > 0) miss.append('·');
            miss.append(lb[i]);
          }
        }
        String left = WDate.dayLabel() + " · " + done + "/6 부위";
        rv.setTextViewText(R.id.ffoot, miss.length() > 0 ? (left + "   미실시 " + miss) : left);
        rv.setTextColor(R.id.ffoot, WTheme.dim(lt));
      }
    }
    rv.setOnClickPendingIntent(R.id.root, pi);
    WCfg.paint(rv, cbg, cop);
    return rv;
  }
}
