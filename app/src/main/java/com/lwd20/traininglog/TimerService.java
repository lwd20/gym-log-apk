package com.lwd20.traininglog;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.widget.RemoteViews;

public class TimerService extends Service {
  public static final String ACT_START = "start";
  public static final String ACT_STOP = "stop";
  public static final String ACT_RESET = "reset";
  private static final String CH = "rest";
  /** 이만큼 넘게 쉬었으면 세트로 돌아가지 않은 것 — 스스로 접는다 */
  private static final long MAX_MS = 240000L;
  private long base = 0L;
  private final Handler h = new Handler(Looper.getMainLooper());
  private long ivMs = 120000L;
  private volatile boolean running = false;
  private final Runnable pulse = new Runnable() {
    @Override public void run() {
      if (!running) return;
      /* 상한을 넘겼으면 한 번 더 울리지 않고 조용히 끝낸다 */
      if (SystemClock.elapsedRealtime() - base >= MAX_MS) { finishUp(); return; }
      try {
        Vibrator vb = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        long[] w = {0, 240, 130, 240};
        if (vb != null) {
          if (Build.VERSION.SDK_INT >= 26) vb.vibrate(VibrationEffect.createWaveform(w, -1));
          else vb.vibrate(w, -1);
        }
      } catch (Exception e) {}
      bringApp();
      if (running && SystemClock.elapsedRealtime() - base + ivMs < MAX_MS)
        h.postDelayed(this, ivMs);
      else if (running) h.postDelayed(this, Math.max(1000L,
        MAX_MS - (SystemClock.elapsedRealtime() - base)));
    }
  };

  /** 정지와 같은 마무리 — 알림을 걷고 서비스를 내린다 */
  private void finishUp() {
    running = false;
    h.removeCallbacksAndMessages(null);
    mark("stop");
    sync("stop");
    try {
      NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      nm.cancel(1); nm.cancel(2);
    } catch (Exception e) {}
    try {
      if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
      else stopForeground(true);
    } catch (Exception e) {}
    stopSelf();
  }

  private void bringApp() {
    Intent i = new Intent(this, MainActivity.class);
    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    try { startActivity(i); } catch (Exception e) {}
    /* 백그라운드 실행 제한 대비: 풀스크린 인텐트 헤드업 */
    try {
      NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      if (Build.VERSION.SDK_INT >= 26) {
        NotificationChannel c = new NotificationChannel("alert", "Rest alert", NotificationManager.IMPORTANCE_HIGH);
        c.setShowBadge(false);
        nm.createNotificationChannel(c);
      }
      Notification.Builder nb;
      if (Build.VERSION.SDK_INT >= 26) nb = new Notification.Builder(this, "alert");
      else nb = new Notification.Builder(this);
      PendingIntent fpi = PendingIntent.getActivity(this, 2, i,
          PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
      /* 제목 한 줄뿐이라 무엇을 하라는 건지가 없었다. 얼마를 채웠는지 적고,
         여기서 바로 다음 세트로 갈 수 있게 버튼을 단다.
         머리줄의 초 세는 숫자는 순간 알림에 쓸모가 없어 뺐다. */
      nb.setSmallIcon(R.drawable.ic_stat_timer)
        .setColor(0xFF8A6320)
        .setContentTitle("\ud734\uc2dd \uc885\ub8cc")
        .setContentText(mmss(ivMs) + " \ucc44\uc6c0 \u00b7 \ub2e4\uc74c \uc138\ud2b8")
        .setShowWhen(false)
        .setAutoCancel(true)
        .setContentIntent(fpi)
        .setFullScreenIntent(fpi, true);
      if (Build.VERSION.SDK_INT >= 21) nb.setCategory(Notification.CATEGORY_ALARM);
      if (Build.VERSION.SDK_INT < 26) nb.setPriority(Notification.PRIORITY_HIGH);
      nb.addAction(0, "\ub2e4\uc2dc \uc2dc\uc791", pib(TimerActions.A_RESET, 22));
      nb.addAction(0, "\ud0c0\uc774\uba38 \uc885\ub8cc", pib(TimerActions.A_STOP, 21));
      nm.notify(2, nb.build());
      h.postDelayed(new Runnable() { public void run() { nm.cancel(2); } }, 8000L);
    } catch (Exception e) {}
  }

  @Override public IBinder onBind(Intent i) { return null; }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    CrashLog.install(this);
    String act = intent == null ? ACT_STOP : intent.getAction();
    if (ACT_RESET.equals(act)) {
      base = SystemClock.elapsedRealtime();
      running = true;
      startForeground(1, build(0L));
      h.removeCallbacksAndMessages(null);
      h.postDelayed(pulse, ivMs);
      mark("reset");
      sync("reset");
      return START_NOT_STICKY;
    }
    if (ACT_START.equals(act)) {
      long elapsed = intent.getLongExtra("elapsed", 0L);
      ivMs = intent.getLongExtra("iv", 120000L);
      if (ivMs < 30000L) ivMs = 30000L;
      running = true;
      base = SystemClock.elapsedRealtime() - elapsed;
      Notification n = build(elapsed);
      startForeground(1, n);
      /* 일부 기기에서 표시가 늦는 문제 대비: 같은 id로 즉시 재게시 */
      try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, n); } catch (Exception e) {}
      try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(2); } catch (Exception e) {}
      h.removeCallbacksAndMessages(null);
      long delay = ivMs - (elapsed % ivMs);
      if (delay < 500L) delay += ivMs;
      h.postDelayed(pulse, delay);
    } else {
      running = false;
      h.removeCallbacksAndMessages(null);
      mark("stop");
      sync("stop");
      try {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(1); nm.cancel(2);
      } catch (Exception e) {}
      if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
      else stopForeground(true);
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  @Override public void onDestroy() {
    running = false;
    h.removeCallbacksAndMessages(null);
    try {
      NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      nm.cancel(1); nm.cancel(2);
    } catch (Exception e) {}
    super.onDestroy();
  }

  private void mark(String a) {
    try {
      getSharedPreferences("timer", 0).edit()
        .putString("act", a).putLong("at", System.currentTimeMillis()).apply();
    } catch (Exception e) {}
  }

  private Notification build(long elapsedMs) {
    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel c = new NotificationChannel(CH, "Rest timer", NotificationManager.IMPORTANCE_LOW);
      c.setShowBadge(false);
      nm.createNotificationChannel(c);
    }
    Notification.Builder nb;
    if (Build.VERSION.SDK_INT >= 26) nb = new Notification.Builder(this, CH);
    else nb = new Notification.Builder(this);
    Intent i = new Intent(this, MainActivity.class);
    PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
    /* 접힌 커스텀 뷰는 높이가 40dp 안팎에서 잘린다. 두 줄로 만들면 아래 버튼이
       잘려 나가므로, 접힘은 한 줄이고 펼침만 버튼을 아래로 내린다. */
    RemoteViews rv = pills(R.layout.noti_rest, elapsedMs);
    RemoteViews rvBig = pills(R.layout.noti_rest_big, elapsedMs);
    /* setColorized 로 판을 검게 칠하면 기기 테마와 어긋나 혼자 튄다.
       색은 아이콘·앱이름 강조에만 쓰고 판은 시스템이 칠하게 둔다. */
    nb.setSmallIcon(R.drawable.ic_stat_timer)
      .setColor(0xFF8A6320)
      /* 머리줄의 '오후 8:40' 은 휴식 시간과 헷갈리기만 한다 — 숫자는 하나면 된다 */
      .setShowWhen(false)
      .setOngoing(true)
      .setStyle(new Notification.DecoratedCustomViewStyle())
      .setCustomContentView(rv)
      .setCustomBigContentView(rvBig)
      ;
    if (Build.VERSION.SDK_INT >= 31) nb.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
    nb
      .setContentIntent(pi);
    return nb.build();
  }

  private RemoteViews pills(int layout, long elapsedMs) {
    RemoteViews rv = new RemoteViews(getPackageName(), layout);
    rv.setChronometer(R.id.ch, SystemClock.elapsedRealtime() - elapsedMs, null, true);
    /* 숫자 하나만 덩그러니 두면 무엇을 향해 세는지 알 수 없다 */
    rv.setTextViewText(R.id.tgt, "/ " + mmss(ivMs));
    rv.setOnClickPendingIntent(R.id.bReset, pib(TimerActions.A_RESET, 12));
    rv.setOnClickPendingIntent(R.id.bStop, pib(TimerActions.A_STOP, 11));
    return rv;
  }

  private static String mmss(long ms) {
    long t = ms / 1000L;
    return String.format(java.util.Locale.US, "%d:%02d", t / 60L, t % 60L);
  }

  private PendingIntent pib(String action, int rc) {
    Intent i = new Intent(this, TimerActions.class).setAction(action);
    return PendingIntent.getBroadcast(this, rc, i,
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private void sync(String cmd) {
    try {
      sendBroadcast(new Intent("com.lwd20.traininglog.TMR_SYNC")
        .setPackage(getPackageName())
        .putExtra("cmd", cmd)
        .putExtra("at", System.currentTimeMillis()));
    } catch (Exception e) {}
  }

}
