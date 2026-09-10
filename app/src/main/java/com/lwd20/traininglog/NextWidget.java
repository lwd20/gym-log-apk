package com.lwd20.traininglog;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 다음에 할 부위 추천 위젯.
 * 방치 일수와 주간 세트 부족분을 웹에서 계산해 순서대로 내려주고,
 * 여기서는 그리기만 함.
 */
public class NextWidget extends AppWidgetProvider {
  static final int[] N = {R.id.nx0, R.id.nx1, R.id.nx2};
  static final int[] S = {R.id.nxs0, R.id.nxs1, R.id.nxs2};

  @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { push(c); }

  static void push(Context c) {
    CrashLog.install(c);
    try { pushInner(c); } catch (Throwable t) { CrashLog.save(c, t); }
  }

  static void pushInner(Context c) {
    AppWidgetManager m = AppWidgetManager.getInstance(c);
    int[] ids = m.getAppWidgetIds(new ComponentName(c, NextWidget.class));
    if (ids == null || ids.length == 0) return;

    String[] nm = new String[3], sb = new String[3];
    int n = 0;
    try {
      String raw = c.getSharedPreferences("widget", 0).getString("data", null);
      JSONArray a = raw == null ? null : new JSONObject(raw).optJSONArray("next");
      if (a != null) {
        n = Math.min(3, a.length());
        for (int i = 0; i < n; i++) {
          JSONArray r = a.getJSONArray(i);
          nm[i] = r.getString(0);
          sb[i] = r.getString(1);
        }
      }
    } catch (Exception e) { n = 0; }

    /* 주간 집계가 지난주 것이면 추천 근거도 오래된 것이므로 표시해 둠 */
    String hd = "NEXT UP" + WDate.staleTag(c);

    PendingIntent pi = PendingIntent.getActivity(c, 0,
        new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

    for (int wi = 0; wi < ids.length; wi++) {
      int id = ids[wi];
      RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_next);
      rv.setTextViewText(R.id.nx_hd, hd);
      for (int i = 0; i < 3; i++) {
        boolean on = i < n;
        rv.setViewVisibility(N[i], on ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(S[i], on ? View.VISIBLE : View.GONE);
        if (on) {
          rv.setTextViewText(N[i], nm[i]);
          rv.setTextViewText(S[i], sb[i]);
        }
      }
      rv.setViewVisibility(R.id.nx_empty, n == 0 ? View.VISIBLE : View.GONE);
      if (n == 0) rv.setTextViewText(R.id.nx_empty,
          WDate.syncIso(c).length() > 0 ? "오늘 전부 다룸" : "앱을 한 번 열면 동기화됨");
      rv.setOnClickPendingIntent(R.id.root, pi);
      int cbg = WCfg.bg(c, id), cop = WCfg.op(c, id);
      boolean cl = !WCfg.lightText(cbg, cop, WCfg.txMode(c, id));
      WCfg.paint(rv, cbg, cop);
      applyTheme(rv, cl);
      m.updateAppWidget(id, rv);
    }
  }

  static void applyTheme(RemoteViews rv, boolean lt) {
    rv.setTextColor(R.id.nx_hd, WTheme.head(lt));
    rv.setTextColor(R.id.nx_empty, WTheme.dim(lt));
    WTheme.tint(rv, N, WTheme.value(lt));
    WTheme.tint(rv, S, WTheme.dim(lt));
  }
}
