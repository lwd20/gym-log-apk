package com.lwd20.traininglog;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

/** AI 리포트를 백그라운드에서 완주시키는 포그라운드 서비스.
 *  JS가 넘긴 {url,headers,body,eng} 스펙 하나를 실행하고
 *  결과를 SharedPreferences("ai_bg")에 저장한 뒤 알림을 띄운다. */
public class AiService extends Service {
  public static final String CH = "ai_bg";

  @Override public IBinder onBind(Intent i) { return null; }

  @Override public int onStartCommand(final Intent it, int flags, int id) {
    createCh();
    startForeground(77, base("AI 리포트 생성 중…", true).build());
    final String spec = it != null ? it.getStringExtra("spec") : null;
    new Thread(new Runnable() { public void run() { work(spec); } }).start();
    return START_NOT_STICKY;
  }

  private void work(String spec) {
    String out;
    try {
      JSONObject o = new JSONObject(spec == null ? "{}" : spec);
      String body = o.optString("body");
      JSONObject hs = o.optJSONObject("headers");
      String eng = o.optString("eng", "gem");
      org.json.JSONArray ua = o.optJSONArray("urls");
      org.json.JSONArray ma = o.optJSONArray("models");
      java.util.ArrayList<String> urls = new java.util.ArrayList<String>();
      if (ua != null) for (int i = 0; i < ua.length(); i++) urls.add(ua.optString(i));
      if (urls.isEmpty()) urls.add(o.optString("url"));
      int status = 0; String resp = null; String err = null;
      StringBuilder trail = new StringBuilder();
      outer:
      for (int u = 0; u < urls.size(); u++) {
        String url = urls.get(u);
        String tag = (ma != null && u < ma.length()) ? ma.optString(u) : ("#" + (u + 1));
        for (int a = 0; a < 2; a++) {
          status = 0; resp = null;
          try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false); /* POST→GET 변조(405 원인) 차단 */
            c.setConnectTimeout(20000);
            c.setReadTimeout(180000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            if (hs != null) {
              Iterator<String> ks = hs.keys();
              while (ks.hasNext()) { String k = ks.next(); c.setRequestProperty(k, hs.optString(k)); }
            }
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
            status = c.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] bf = new byte[8192]; int r;
            while (is != null && (r = is.read(bf)) > 0) bo.write(bf, 0, r);
            resp = bo.toString("UTF-8");
          } catch (Exception ex) { err = ex.getMessage(); }
          if (trail.length() > 0) trail.append(",");
          trail.append(tag).append(":").append(status > 0 ? String.valueOf(status) : "ERR");
          if (status >= 200 && status < 300 && resp != null) break outer; /* 성공 */
          if (status >= 300 && status < 400) break; /* 리다이렉트 = 이 모델 포기 → 다음 */
          if (status == 429 || status >= 500 || status == 0) { /* 일시 오류 → 같은 모델 재시도 */
            try { Thread.sleep(3000L * (a + 1)); } catch (Exception e2) {}
            continue;
          }
          break; /* 그 외 4xx = 이 모델 포기 → 다음 */
        }
      }
      JSONObject res = new JSONObject();
      boolean ok = status >= 200 && status < 300 && resp != null;
      res.put("ok", ok);
      res.put("status", status);
      res.put("eng", eng);
      res.put("trail", trail.toString());
      if (resp != null) res.put("body", resp);
      if (!ok) res.put("err", err != null ? err : ("HTTP " + status));
      out = res.toString();
    } catch (Exception e) {
      try {
        JSONObject res = new JSONObject();
        res.put("ok", false);
        res.put("err", String.valueOf(e.getMessage()));
        out = res.toString();
      } catch (Exception e3) { out = "{\"ok\":false,\"err\":\"internal\"}"; }
    }
    getSharedPreferences("ai_bg", MODE_PRIVATE).edit()
      .putString("res", out)
      .putLong("ts", System.currentTimeMillis())
      .apply();
    boolean okF = out.contains("\"ok\":true");
    Notification done = base(okF ? "AI 리포트 완료 — 탭하여 보기" : "AI 리포트 실패 — 앱에서 확인", false)
      .setAutoCancel(true).build();
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    stopForeground(true);
    if (nm != null) nm.notify(78, done);
    stopSelf();
  }

  private Notification.Builder base(String t, boolean ongoing) {
    Intent i = new Intent(this, MainActivity.class);
    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent pi = PendingIntent.getActivity(this, 7, i,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b = Build.VERSION.SDK_INT >= 26
      ? new Notification.Builder(this, CH) : new Notification.Builder(this);
    b.setContentTitle("PLUS2.5")
     .setContentText(t)
     .setSmallIcon(android.R.drawable.stat_notify_sync)
     .setContentIntent(pi)
     .setOngoing(ongoing);
    return b;
  }

  private void createCh() {
    if (Build.VERSION.SDK_INT < 26) return;
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (nm == null || nm.getNotificationChannel(CH) != null) return;
    NotificationChannel c = new NotificationChannel(CH, "AI Report", NotificationManager.IMPORTANCE_DEFAULT);
    c.enableLights(true);
    c.setLightColor(Color.parseColor("#cfa648"));
    nm.createNotificationChannel(c);
  }
}
