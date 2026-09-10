package com.lwd20.traininglog;

import android.content.Context;
import java.io.PrintWriter;
import java.io.StringWriter;

/** 앱 어디서 죽든 스택트레이스를 남겨 다음 실행 때 볼 수 있게 함 */
public class CrashLog {

  private static boolean installed = false;

  public static synchronized void install(Context c) {
    if (installed) return;
    installed = true;
    final Context app = c.getApplicationContext();
    final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      @Override public void uncaughtException(Thread t, Throwable e) {
        save(app, e);
        if (prev != null) prev.uncaughtException(t, e);
        else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
      }
    });
  }

  public static void save(Context c, Throwable e) {
    try {
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      String head = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
          .format(new java.util.Date());
      String txt = head + "\n" + sw.toString();
      if (txt.length() > 6000) txt = txt.substring(0, 6000);
      c.getSharedPreferences("crash", 0).edit().putString("last", txt).apply();
    } catch (Throwable ignore) {}
  }

  public static String read(Context c) {
    try { return c.getSharedPreferences("crash", 0).getString("last", null); }
    catch (Throwable e) { return null; }
  }

  public static void clear(Context c) {
    try { c.getSharedPreferences("crash", 0).edit().remove("last").apply(); } catch (Throwable e) {}
  }
}
