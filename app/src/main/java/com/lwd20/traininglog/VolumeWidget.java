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
 * 주간 부위별 세트 달성률.
 *
 * 크기 구간
 *   0 small  2칸 — 남은 세트가 가장 많은 2부위
 *   1 medium 3칸 — 같은 기준으로 4부위
 *   2 large  4칸 — 여섯 부위 전부, 정렬은 원래 순서로 되돌리고 합계 푸터
 *
 * 어느 부위를 감출지가 요점이다. 배열 순서로 자르면 팔과 유산소가 영구히 사라지는데
 * 그 위치에는 아무 의미도 없다. 남은 세트가 많은 순으로 세우면 잘리는 쪽은
 * 이미 목표를 채운 부위가 되므로, 감춰도 잃는 정보가 없다.
 * 반대로 여섯 개가 다 보이는 large 에서는 정렬을 끈다 — 순위는 무엇을 지울지
 * 정하기 위한 장치이므로, 지울 게 없으면 익숙한 순서가 낫다.
 */
public class VolumeWidget extends AppWidgetProvider {
  static final int[] LB = {R.id.l1, R.id.l2, R.id.l3, R.id.l4, R.id.l5, R.id.l6};
  static final int[] BR = {R.id.b1, R.id.b2, R.id.b3, R.id.b4, R.id.b5, R.id.b6};

  /** 구간별 최소 상자(dp) — 폭·높이 모두 단조 증가 */
  static final int[][] BOX = {{145, 110}, {145, 180}, {215, 250}};
  static final int FALLBACK = 2;
  static final int[] ROWS = {2, 4, 6};

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
    int[] ids = m.getAppWidgetIds(new ComponentName(c, VolumeWidget.class));
    if (ids == null || ids.length == 0) return;

    String[] nm = new String[6];
    int[] cur = new int[6], tgt = new int[6];
    boolean has = false;
    try {
      String raw = c.getSharedPreferences("widget", 0).getString("data", null);
      if (raw != null) {
        JSONArray v = new JSONObject(raw).getJSONArray("v");
        for (int i = 0; i < 6; i++) {
          if (i < v.length()) {
            JSONArray r = v.getJSONArray(i);
            nm[i] = r.getString(0);
            cur[i] = r.getInt(1);
            tgt[i] = Math.max(1, r.getInt(2));
          } else { nm[i] = ""; cur[i] = 0; tgt[i] = 1; }
        }
        has = true;
      }
    } catch (Exception e) { has = false; }

    /* 남은 세트가 많은 순. 같으면 원래 순서 유지(안정 정렬) */
    int[] rank = new int[6];
    for (int i = 0; i < 6; i++) rank[i] = i;
    for (int i = 1; i < 6; i++) {
      int k = rank[i], j = i - 1;
      while (j >= 0 && rem(cur, tgt, rank[j]) < rem(cur, tgt, k)) { rank[j + 1] = rank[j]; j--; }
      rank[j + 1] = k;
    }

    int sc = 0, st = 0;
    for (int i = 0; i < 6; i++) { sc += cur[i]; st += tgt[i]; }
    int pct = st > 0 ? Math.round(sc * 100f / st) : 0;
    String stale = WDate.staleTag(c);

    PendingIntent pi = PendingIntent.getActivity(c, 0,
        new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

    for (int wi = 0; wi < ids.length; wi++) {
      int id = ids[wi];
      int cbg = WCfg.bg(c, id), cop = WCfg.op(c, id);
      boolean lt = !WCfg.lightText(cbg, cop, WCfg.txMode(c, id));
      RemoteViews[] v = new RemoteViews[BOX.length];
      for (int b = 0; b < BOX.length; b++) {
        v[b] = build(c, b, nm, cur, tgt, rank, has, sc, st, pct, stale, lt, cbg, cop, pi);
      }
      RemoteViews out = WSize.combine(c, id, BOX, v, FALLBACK);
      if (out != null) m.updateAppWidget(id, out);
    }
  }

  static int rem(int[] cur, int[] tgt, int i) { return Math.max(0, tgt[i] - cur[i]); }

  static RemoteViews build(Context c, int bucket, String[] nm, int[] cur, int[] tgt, int[] rank,
                           boolean has, int sc, int st, int pct, String stale,
                           boolean lt, int cbg, int cop, PendingIntent pi) {
    RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_volume);
    int rows = ROWS[Math.max(0, Math.min(ROWS.length - 1, bucket))];
    boolean full = rows >= 6;

    String hd = "WEEKLY SETS";
    if (has) hd += "  " + pct + "%";
    if (full) hd += stale;
    rv.setTextViewText(R.id.vhd, hd);

    for (int slot = 0; slot < 6; slot++) {
      boolean on = has && slot < rows;
      rv.setViewVisibility(LB[slot], on ? View.VISIBLE : View.GONE);
      rv.setViewVisibility(BR[slot], on ? View.VISIBLE : View.GONE);
      if (!on) continue;
      /* 전부 보일 때는 원래 순서, 잘라낼 때는 남은 세트 순 */
      int i = full ? slot : rank[slot];
      rv.setTextViewText(LB[slot], nm[i] + "  " + cur[i] + "/" + tgt[i]);
      rv.setProgressBar(BR[slot], tgt[i], Math.min(cur[i], tgt[i]), false);
      rv.setTextColor(LB[slot], WTheme.label(lt));
    }
    if (!has) {
      rv.setViewVisibility(LB[0], View.VISIBLE);
      rv.setTextViewText(LB[0], "앱을 한 번 열면 동기화됨");
      rv.setTextColor(LB[0], WTheme.dim(lt));
    }

    boolean foot = full && has;
    rv.setViewVisibility(R.id.vfoot, foot ? View.VISIBLE : View.GONE);
    if (foot) {
      rv.setTextViewText(R.id.vfoot, "남은 " + Math.max(0, st - sc) + "세트 · 총 " + sc + "/" + st);
      rv.setTextColor(R.id.vfoot, WTheme.dim(lt));
    }
    rv.setTextColor(R.id.vhd, WTheme.head(lt));
    rv.setOnClickPendingIntent(R.id.root, pi);
    WCfg.paint(rv, cbg, cop);
    return rv;
  }
}
