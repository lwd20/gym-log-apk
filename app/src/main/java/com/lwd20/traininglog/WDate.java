package com.lwd20.traininglog;

import android.content.Context;
import java.util.Calendar;
import org.json.JSONObject;

/**
 * 위젯이 그려지는 시점의 날짜를 기준으로 값을 다시 계산하기 위한 유틸.
 *
 * 웹에서 내려준 JSON은 마지막 앱 실행 시점에 고정된 스냅샷이라,
 * "N일 전"이나 "이번 주" 같은 상대 표현을 그대로 쓰면 날이 바뀌어도 갱신되지 않음.
 * 그래서 동기화 시점의 절대 날짜(sd/mon)와 각 항목의 절대 날짜를 함께 받아
 * 여기서 오늘 기준으로 다시 계산함.
 */
public class WDate {

  /** 오늘 자정(로컬) */
  static Calendar midnight() {
    Calendar c = Calendar.getInstance();
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    return c;
  }

  public static String iso(Calendar c) {
    return String.format("%04d-%02d-%02d",
        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
  }

  public static String todayIso() { return iso(midnight()); }

  /** 월=0 … 일=6 */
  public static int dow() {
    return (midnight().get(Calendar.DAY_OF_WEEK) + 5) % 7;
  }

  /** 이번 주 월요일 */
  public static String mondayIso() {
    Calendar c = midnight();
    c.add(Calendar.DAY_OF_MONTH, -dow());
    return iso(c);
  }

  public static Calendar parse(String s) {
    try {
      if (s == null || s.length() < 10) return null;
      Calendar c = Calendar.getInstance();
      c.clear();
      c.set(Integer.parseInt(s.substring(0, 4)),
            Integer.parseInt(s.substring(5, 7)) - 1,
            Integer.parseInt(s.substring(8, 10)));
      return c;
    } catch (Exception e) { return null; }
  }

  /** isoFrom 부터 오늘까지 며칠 지났는지. 파싱 실패 시 -1 */
  public static int daysAgo(String isoFrom) {
    Calendar f = parse(isoFrom);
    if (f == null) return -1;
    long ms = midnight().getTimeInMillis() - f.getTimeInMillis();
    return (int) Math.round(ms / 86400000.0);
  }

  static JSONObject data(Context c) {
    try {
      String raw = c.getSharedPreferences("widget", 0).getString("data", null);
      if (raw != null) return new JSONObject(raw);
    } catch (Exception e) {}
    return null;
  }

  /** 동기화된 스냅샷이 만들어진 날짜 */
  public static String syncIso(Context c) {
    JSONObject o = data(c);
    return o == null ? "" : o.optString("sd", "");
  }

  /** 스냅샷이 오늘 만들어진 것인지 — 오늘 기록 위젯의 유효성 판단용 */
  public static boolean syncedToday(Context c) {
    String sd = syncIso(c);
    return sd.length() >= 10 && sd.equals(todayIso());
  }

  /**
   * 스냅샷의 주간 데이터가 지난 주 것인지.
   * mon 필드가 없는 구버전 스냅샷은 판정하지 않음(false).
   */
  public static boolean weekStale(Context c) {
    JSONObject o = data(c);
    if (o == null) return false;
    String mon = o.optString("mon", "");
    if (mon.length() < 10) return false;
    return !mon.equals(mondayIso());
  }

  private static final String[] DOW = {"월", "화", "수", "목", "금", "토", "일"};

  /** 오늘 요일 한 글자 */
  public static String dayLabel() { return DOW[dow()]; }

  /** 헤더에 붙일 경과 표시 — 최신이면 빈 문자열.
      250dp 위젯에서도 헤더가 잘리지 않게 짧게 유지함. */
  public static String staleTag(Context c) {
    return weekStale(c) ? "  · 지난주" : "";
  }
}
