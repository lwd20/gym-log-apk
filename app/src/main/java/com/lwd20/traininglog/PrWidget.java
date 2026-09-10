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
 * 최근 기록 경신 위젯.
 * 웹이 최근 3주 안에 개인 최고를 넘긴 종목을 [이름, 톱세트, 상승률, 날짜]로 내려줌.
 * 경과일은 날짜에서 그리는 시점에 다시 계산함.
 */
public class PrWidget extends AppWidgetProvider {
  /* 종목명(Cable Art Chest in Shoulder 등)이 길어 이름은 한 줄을 통째로 씀 */
  static final int[] N = {R.id.pr0, R.id.pr1, R.id.pr2};
  static final int[] V = {R.id.prv0, R.id.prv1, R.id.prv2};
  static final int MAX = 3;

  @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { push(c); }

  static void push(Context c) {
    CrashLog.install(c);
    try { pushInner(c); } catch (Throwable t) { CrashLog.save(c, t); }
  }

  static void pushInner(Context c) {
    AppWidgetManager m = AppWidgetManager.getInstance(c);
    int[] ids = m.getAppWidgetIds(new ComponentName(c, PrWidget.class));
    if (ids == null || ids.length == 0) return;

    String[] nm = new String[MAX], vv = new String[MAX];
    int n = 0;
    try {
      String raw = c.getSharedPreferences("widget", 0).getString("data", null);
      JSONArray a = raw == null ? null : new JSONObject(raw).optJSONArray("prs");
      if (a != null) {
        n = Math.min(MAX, a.length());
        for (int i = 0; i < n; i++) {
          JSONArray r = a.getJSONArray(i);
          nm[i] = r.getString(0);
          int d = WDate.daysAgo(r.optString(3, ""));
          String when = d < 0 ? "" : (d == 0 ? "오늘" : d + "일 전");
          String pct = r.optString(2, "");
          StringBuilder sb = new StringBuilder(r.getString(1));
          if (pct.length() > 0) sb.append("  ·  ").append(pct).append('%');
          if (when.length() > 0) sb.append("  ·  ").append(when);
          vv[i] = sb.toString();
        }
      }
    } catch (Exception e) { n = 0; }

    PendingIntent pi = PendingIntent.getActivity(c, 0,
        new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

    for (int wi = 0; wi < ids.length; wi++) {
      int id = ids[wi];
      RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_pr);
      rv.setTextViewText(R.id.pr_hd, "RECENT PR");
      rv.setTextViewText(R.id.pr_cnt, n > 0 ? String.valueOf(n) : "");
      for (int i = 0; i < MAX; i++) {
        boolean on = i < n;
        rv.setViewVisibility(N[i], on ? View.VISIBLE : View.GONE);
        rv.setViewVisibility(V[i], on ? View.VISIBLE : View.GONE);
        if (on) {
          rv.setTextViewText(N[i], nm[i]);
          rv.setTextViewText(V[i], vv[i]);
        }
      }
      rv.setViewVisibility(R.id.pr_empty, n == 0 ? View.VISIBLE : View.GONE);
      if (n == 0) rv.setTextViewText(R.id.pr_empty, "최근 3주 경신 없음");
      rv.setOnClickPendingIntent(R.id.root, pi);
      int cbg = WCfg.bg(c, id), cop = WCfg.op(c, id);
      boolean cl = !WCfg.lightText(cbg, cop, WCfg.txMode(c, id));
      WCfg.paint(rv, cbg, cop);
      applyTheme(rv, cl);
      m.updateAppWidget(id, rv);
    }
  }

  static void applyTheme(RemoteViews rv, boolean lt) {
    rv.setTextColor(R.id.pr_hd, WTheme.head(lt));
    rv.setTextColor(R.id.pr_cnt, WTheme.mute(lt));
    rv.setTextColor(R.id.pr_empty, WTheme.dim(lt));
    WTheme.tint(rv, N, WTheme.label(lt));
    WTheme.tint(rv, V, WTheme.value(lt));
  }
}
