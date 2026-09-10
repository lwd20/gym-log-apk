package com.lwd20.traininglog;

import android.widget.RemoteViews;

/** 위젯 색 테마 — 밝은 배경화면에서 글자가 묻히는 문제 대응 */
public class WTheme {
  /* 역할별 색: [0]=다크판, [1]=라이트판 */
  public static int head(boolean l)  { return l ? 0xFF433A22 : 0xFF9A8A63; }
  public static int label(boolean l) { return l ? 0xFF1A160E : 0xFFC9BFA8; }
  public static int value(boolean l) { return l ? 0xFF5A3E00 : 0xFFE3B95A; }
  public static int dim(boolean l)   { return l ? 0xFF3E382A : 0xFF8F887C; }
  public static int mute(boolean l)  { return l ? 0xFF6A6252 : 0xFF5C5648; }

  public static int dotOn(boolean l)  { return l ? R.drawable.wg_dot_on_l : R.drawable.wg_dot_on; }
  public static int dotOff(boolean l) { return l ? R.drawable.wg_dot_off_l : R.drawable.wg_dot_off; }
  public static int today(boolean l)  { return l ? R.drawable.wg_today_l : R.drawable.wg_today; }
  public static int todayText(boolean l) { return l ? 0xFFF2E7C8 : 0xFF1A1200; }

  public static void tint(RemoteViews rv, int[] ids, int color) {
    for (int i = 0; i < ids.length; i++) rv.setTextColor(ids[i], color);
  }
}
