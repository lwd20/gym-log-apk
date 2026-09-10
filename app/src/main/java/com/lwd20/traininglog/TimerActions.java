package com.lwd20.traininglog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TimerActions extends BroadcastReceiver {
  public static final String A_STOP = "com.lwd20.traininglog.STOP";
  public static final String A_RESET = "com.lwd20.traininglog.RESET";

  @Override public void onReceive(Context c, Intent i) {
    String a = i.getAction();
    if (a == null) return;
    Intent s = new Intent(c, TimerService.class);
    if (A_STOP.equals(a)) s.setAction(TimerService.ACT_STOP);
    else if (A_RESET.equals(a)) s.setAction(TimerService.ACT_RESET);
    else return;
    try { c.startService(s); } catch (Exception e) {}
  }
}
