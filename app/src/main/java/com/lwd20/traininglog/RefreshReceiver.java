package com.lwd20.traininglog;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.Calendar;

/**
 * 자정에 위젯을 다시 그려 날짜 의존 값(경과일·요일·주간 범위)을 갱신함.
 *
 * updatePeriodMillis(30분)만으로도 결국 갱신되지만 자정 직후 최대 30분간
 * 어제 값이 남으므로, 날이 바뀌는 순간에 한 번 확실히 깨움.
 * 정확한 알람 권한이 필요 없는 set()을 쓰므로 다소 늦게 올 수 있고,
 * 그 경우에도 주기 갱신이 받쳐줌.
 */
public class RefreshReceiver extends BroadcastReceiver {
  public static final String ACT = "com.lwd20.traininglog.REFRESH";
  private static final int REQ = 7;

  @Override public void onReceive(Context c, Intent i) {
    CrashLog.install(c);
    try {
      pushAll(c);
      schedule(c);
    } catch (Throwable t) { CrashLog.save(c, t); }
  }

  public static void pushAll(Context c) {
    VolumeWidget.push(c);
    WeekWidget.push(c);
    FreqWidget.push(c);
    LastWidget.push(c);
    TodayWidget.push(c);
    CutWidget.push(c);
    NextWidget.push(c);
    PrWidget.push(c);
    LmWidget.push(c);
  }

  /** 다음 자정 직후로 예약 (매번 스스로 다시 예약) */
  public static void schedule(Context c) {
    try {
      AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
      if (am == null) return;
      Calendar n = Calendar.getInstance();
      n.add(Calendar.DAY_OF_MONTH, 1);
      n.set(Calendar.HOUR_OF_DAY, 0);
      n.set(Calendar.MINUTE, 0);
      n.set(Calendar.SECOND, 20);
      n.set(Calendar.MILLISECOND, 0);
      int flags = PendingIntent.FLAG_UPDATE_CURRENT;
      if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
      PendingIntent pi = PendingIntent.getBroadcast(c, REQ,
          new Intent(c, RefreshReceiver.class).setAction(ACT), flags);
      am.set(AlarmManager.RTC, n.getTimeInMillis(), pi);
    } catch (Exception e) {}
  }
}
