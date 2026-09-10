package com.lwd20.traininglog;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONObject;

/** 감량 진행 위젯 — 목표 체지방 도달까지 남은 일수·체중·페이스 */
public class CutWidget extends AppWidgetProvider {

  @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { push(c); }

  static void push(Context c) {
    CrashLog.install(c);
    try { pushInner(c); } catch (Throwable t) { CrashLog.save(c, t); }
  }

  static void pushInner(Context c) {
    AppWidgetManager m = AppWidgetManager.getInstance(c);
    int[] ids = m.getAppWidgetIds(new ComponentName(c, CutWidget.class));
    if (ids == null || ids.length == 0) return;

    JSONObject cut = null;
    try {
      String raw = c.getSharedPreferences("widget", 0).getString("data", null);
      if (raw != null) cut = new JSONObject(raw).optJSONObject("cut");
    } catch (Exception e) {}

    String dd = "D-?", main = "", sub = "", tag = "";
    int pct = 0, tagColor = 0;
    boolean has = cut != null;

    if (has) {
      int d = cut.optInt("d", -1);
      if (d == -2) dd = "완료";
      else if (d >= 0) dd = "D-" + d;
      else dd = "D-?";

      pct = Math.max(0, Math.min(100, cut.optInt("pct", 0)));
      String w = cut.optString("w", ""), tw = cut.optString("tw", "");
      if (w.length() > 0 && tw.length() > 0) main = w + " → " + tw + " kg";
      else main = "인바디 기록 필요";

      String rate = cut.optString("rate", ""), wr = cut.optString("wr", "");
      if (rate.length() > 0) sub = "실측 " + rate + " · 목표 " + wr + " kg/주";
      else sub = "인바디 2건부터 페이스 계산";

      tag = cut.optString("tag", "");
      if (tag.contains("과속") || tag.contains("증량")) tagColor = 0xFFD9603F;
      else if (tag.contains("부족")) tagColor = 0xFF8F887C;
      else tagColor = 0xFF6FA86B;
    }

    PendingIntent pi = PendingIntent.getActivity(c, 0,
        new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

    for (int wi = 0; wi < ids.length; wi++) {
      int id = ids[wi];
      RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_cut);
      int cbg = WCfg.bg(c, id), cop = WCfg.op(c, id);
      boolean cl = !WCfg.lightText(cbg, cop, WCfg.txMode(c, id));

      if (!has) {
        rv.setTextViewText(R.id.ct_dd, "");
        rv.setTextViewText(R.id.ct_main, "앱을 한 번 열면 동기화됨");
        rv.setTextViewText(R.id.ct_sub, "");
        rv.setTextViewText(R.id.ct_tag, "");
        rv.setViewVisibility(R.id.ct_bar, View.GONE);
      } else {
        rv.setTextViewText(R.id.ct_dd, dd);
        rv.setTextViewText(R.id.ct_main, main);
        rv.setTextViewText(R.id.ct_sub, sub);
        rv.setTextViewText(R.id.ct_tag, tag);
        rv.setViewVisibility(R.id.ct_bar, View.VISIBLE);
        rv.setProgressBar(R.id.ct_bar, 100, pct, false);
      }

      rv.setOnClickPendingIntent(R.id.root, pi);
      WCfg.paint(rv, cbg, cop);
      applyTheme(rv, cl);
      if (has && tag.length() > 0) rv.setTextColor(R.id.ct_tag, tagColor);
      m.updateAppWidget(id, rv);
    }
  }

  static void applyTheme(RemoteViews rv, boolean lt) {
    rv.setTextColor(R.id.ct_hd, WTheme.head(lt));
    rv.setTextColor(R.id.ct_dd, WTheme.value(lt));
    rv.setTextColor(R.id.ct_main, WTheme.label(lt));
    rv.setTextColor(R.id.ct_sub, WTheme.dim(lt));
    rv.setTextColor(R.id.ct_tag, WTheme.dim(lt));
  }
}
