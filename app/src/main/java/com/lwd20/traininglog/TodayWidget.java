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

public class TodayWidget extends AppWidgetProvider {
  static final int[] N = {R.id.tdn0, R.id.tdn1, R.id.tdn2, R.id.tdn3, R.id.tdn4, R.id.tdn5};
  static final int[] V = {R.id.tdv0, R.id.tdv1, R.id.tdv2, R.id.tdv3, R.id.tdv4, R.id.tdv5};
  static final int[] S = {R.id.tds0, R.id.tds1, R.id.tds2, R.id.tds3, R.id.tds4, R.id.tds5};
  static final int[] ROWV = {R.id.tdrow0, R.id.tdrow1, R.id.tdrow2, R.id.tdrow3, R.id.tdrow4, R.id.tdrow5};
  /** 구간별 최소 상자(dp). 실측 2행 93.9 / 4행 149.5 / 6행 205.1 */
  static final int[][] BOX = {{180, 100}, {180, 155}, {180, 210}};
  static final int FALLBACK = 2;
  static final int[] ROWS = {2, 4, 6};

  @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { push(c); }

  @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, android.os.Bundle o) {
    push(c);
  }

  static void push(Context c) {
    CrashLog.install(c);
    try { pushInner(c); } catch (Throwable t) { CrashLog.save(c, t); }
  }

  static void pushInner(Context c) {
    AppWidgetManager m = AppWidgetManager.getInstance(c);
    int[] ids = m.getAppWidgetIds(new ComponentName(c, TodayWidget.class));
    if (ids == null || ids.length == 0) return;
    String[] rn = new String[6], rvv = new String[6], rs = new String[6];
    boolean[] vis = new boolean[6];
    int n = 0;
    try {
      String raw = c.getSharedPreferences("widget", 0).getString("data", null);
      JSONArray a = raw == null ? null : new JSONObject(raw).optJSONArray("td");
      n = a == null ? 0 : a.length();
      for (int i = 0; i < 6; i++) {
        if (i < n) {
          try {
            JSONArray r = a.getJSONArray(i);
            rn[i] = r.getString(0);
            rvv[i] = r.getString(1);
            rs[i] = tail(r, 2);
            vis[i] = true;
            continue;
          } catch (Exception e) {}
        }
        vis[i] = false;
      }
    } catch (Exception e) { n = 0; }

    /* 스냅샷이 오늘 것이 아니면 어제 기록이 남아 있는 것이므로 비움 */
    if (!WDate.syncedToday(c)) {
      n = 0;
      for (int i = 0; i < 6; i++) vis[i] = false;
    }

    PendingIntent pi = PendingIntent.getActivity(c, 0,
        new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

    for (int wi = 0; wi < ids.length; wi++) {
      int id = ids[wi];
      int cbg0 = WCfg.bg(c, id), cop0 = WCfg.op(c, id);
      boolean lt0 = !WCfg.lightText(cbg0, cop0, WCfg.txMode(c, id));
      RemoteViews[] vs = new RemoteViews[BOX.length];
      for (int b = 0; b < BOX.length; b++)
        vs[b] = build(c, ROWS[b], rn, rvv, rs, vis, n, lt0, cbg0, cop0, pi);
      RemoteViews outRv = WSize.combine(c, id, BOX, vs, FALLBACK);
      if (outRv != null) m.updateAppWidget(id, outRv);
    }
  }

  static RemoteViews build(Context c, int rows, String[] rn, String[] rvv, String[] rs,
                           boolean[] vis, int n, boolean lt, int cbg, int cop, PendingIntent pi) {
    {
      RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_today);
      rv.setTextViewText(R.id.root_hd, "TODAY");
      int shown = 0;
      for (int i = 0; i < 6; i++) {
        /* 행 컨테이너째 감춘다 — 안의 글자만 감추면 높이는 0 이 되어도
           marginTop 이 유령 여백으로 남는다 */
        boolean on = vis[i] && shown < rows;
        rv.setViewVisibility(ROWV[i], on ? View.VISIBLE : View.GONE);
        if (!on) continue;
        shown++;
        rv.setTextViewText(N[i], rn[i]);
        rv.setTextViewText(V[i], rvv[i]);
        rv.setTextViewText(S[i], rs[i]);
      }
      rv.setTextViewText(R.id.root_cnt,
          n > 0 ? (shown < n ? (shown + "/" + n) : String.valueOf(n)) : "");
      rv.setViewVisibility(R.id.root_empty, n == 0 ? View.VISIBLE : View.GONE);
      if (n == 0) rv.setTextViewText(R.id.root_empty, "오늘 기록 없음");
      rv.setOnClickPendingIntent(R.id.root, pi);
      WCfg.paint(rv, cbg, cop);
      applyTheme(rv, lt);
      return rv;
    }
  }

  static String tail(JSONArray r, int idx) {
    try {
      int v = r.getInt(idx);
      if ("td".equals("td")) return v + "\uc138\ud2b8";
      if (v <= 0) return "\uc624\ub298";
      return v + "\uc77c \uc804";
    } catch (Exception e) { return ""; }
  }

  static void applyTheme(RemoteViews rv, boolean lt) {
    rv.setTextColor(R.id.root_hd, WTheme.head(lt));
    rv.setTextColor(R.id.root_cnt, WTheme.mute(lt));
    rv.setTextColor(R.id.root_empty, WTheme.dim(lt));
    WTheme.tint(rv, N, WTheme.label(lt));
    WTheme.tint(rv, V, WTheme.value(lt));
    WTheme.tint(rv, S, WTheme.dim(lt));
  }
}
