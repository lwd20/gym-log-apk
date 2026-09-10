package com.lwd20.traininglog;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** 시스템(One UI) 스타일 위젯 설정 — 라이트/다크·월페이퍼 색 추종 */
public class WidgetConfigActivity extends Activity {

  private int wid = AppWidgetManager.INVALID_APPWIDGET_ID;
  private int bg = 0xFF1A140C;
  private int op = 100;
  private int tx = 0; /* 0 자동, 1 밝은 글자, 2 어두운 글자 */

  private boolean lock = false;

  /* 시스템 팔레트 */
  private int cBg, cCard, cText, cSub, cAccent, cDivider;

  private View pvBox;
  private TextView pvLabel, pvValue, opVal, txVal;
  private SeekBar sbR, sbG, sbB, sbO;
  private EditText edR, edG, edB, edHex;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    setResult(RESULT_CANCELED);
    CrashLog.install(this);
    try {
      Intent it = getIntent();
      if (it != null && it.getExtras() != null) {
        wid = it.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
      }
      if (wid == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
      bg = WCfg.bg(this, wid);
      op = WCfg.op(this, wid);
      tx = WCfg.txMode(this, wid);
      palette();
      setContentView(buildUi());
      apply(null);
    } catch (Throwable t) {
      CrashLog.save(this, t);
      finish();
    }
  }

  /* 배경화면에서 색을 직접 뽑아 팔레트 구성 — 삼성 포함 전 기기 동작 */
  private static int mix(int a, int b, float t) {
    return Color.argb(255,
      Math.round(Color.red(a)   * (1 - t) + Color.red(b)   * t),
      Math.round(Color.green(a) * (1 - t) + Color.green(b) * t),
      Math.round(Color.blue(a)  * (1 - t) + Color.blue(b)  * t));
  }

  private void palette() {
    boolean night = (getResources().getConfiguration().uiMode
        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    int base = 0;
    try {
      if (Build.VERSION.SDK_INT >= 27) {
        android.app.WallpaperColors wc = android.app.WallpaperManager
            .getInstance(this).getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM);
        if (wc != null) base = wc.getPrimaryColor().toArgb();
      }
    } catch (Throwable e) {}
    if (base == 0) base = night ? 0xFF3A3126 : 0xFFB8AD97;
    int W = 0xFFFFFFFF, K = 0xFF000000;
    if (night) {
      cBg      = mix(base, K, 0.78f);
      cCard    = mix(base, K, 0.64f);
      cText    = mix(base, W, 0.88f);
      cSub     = mix(base, W, 0.55f);
      cAccent  = mix(base, W, 0.45f);
      cDivider = mix(cCard, W, 0.10f);
    } else {
      cBg      = mix(base, W, 0.55f);
      cCard    = mix(base, W, 0.86f);
      cText    = mix(base, K, 0.80f);
      cSub     = mix(base, K, 0.45f);
      cAccent  = mix(base, K, 0.42f);
      cDivider = mix(cCard, K, 0.10f);
    }
    try {
      getWindow().setStatusBarColor(cBg);
      getWindow().setNavigationBarColor(cBg);
      if (!night) {
        int f = getWindow().getDecorView().getSystemUiVisibility();
        f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(f);
      }
    } catch (Throwable e) {}
  }

  private int dp(float v) {
    return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
  }

  private GradientDrawable rounded(int fill, float rad) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(fill);
    g.setCornerRadius(dp(rad));
    return g;
  }

  /* ── UI ─────────────────────────────── */

  private View buildUi() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(cBg);

    ScrollView sv = new ScrollView(this);
    sv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    sv.setFillViewport(true);
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(20), dp(8), dp(20), dp(16));
    sv.addView(body);
    root.addView(sv);

    /* 제목 */
    TextView title = new TextView(this);
    title.setText("\uc704\uc82f \uc124\uc815");
    title.setTextSize(24);
    title.setTextColor(cText);
    title.setTypeface(Typeface.DEFAULT_BOLD);
    title.setPadding(dp(4), dp(28), 0, dp(20));
    body.addView(title);

    /* 미리보기 — 실제 위젯 색으로 */
    LinearLayout pv = new LinearLayout(this);
    pv.setOrientation(LinearLayout.VERTICAL);
    pv.setGravity(Gravity.CENTER);
    /* 자식 정렬까지 명시 — 일부 기기에서 컨테이너 gravity만으론 좌측 정렬됨 */
    pvLabel = new TextView(this);
    pvLabel.setText("\uc5b4\uae68");
    pvLabel.setTextSize(14);
    pvLabel.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    plp.gravity = Gravity.CENTER_HORIZONTAL;
    pvLabel.setLayoutParams(plp);
    pvValue = new TextView(this);
    pvValue.setText("20 / 20 \uc138\ud2b8");
    pvValue.setTextSize(19);
    pvValue.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
    pvValue.setPadding(0, dp(5), 0, 0);
    pvValue.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams vlp2 = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    vlp2.gravity = Gravity.CENTER_HORIZONTAL;
    pvValue.setLayoutParams(vlp2);
    pv.addView(pvLabel);
    pv.addView(pvValue);
    LinearLayout.LayoutParams pvp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(170));
    pvp.bottomMargin = dp(18);
    pv.setLayoutParams(pvp);
    pvBox = pv;
    body.addView(pv);

    /* 카드 1 — 배경 색 */
    LinearLayout c1 = cardBox(body);
    rowHeader(c1, "\ubc30\uacbd \uc0c9");
    edHex = valueEdit(false);
    valueRow(c1, "HEX", edHex);
    divider(c1);
    sbR = channelRow(c1, "R", 0);
    sbG = channelRow(c1, "G", 1);
    sbB = channelRow(c1, "B", 2, true);
    edHex.addTextChangedListener(new Watch() {
      @Override void changed(String t) {
        if (lock) return;
        String h = t.trim().replace("#", "");
        if (h.length() != 6) return;
        try {
          bg = (int) Long.parseLong(h, 16) | 0xFF000000;
          apply(edHex);
        } catch (Exception e) {}
      }
    });

    /* 카드 2 — 투명도 */
    LinearLayout c2 = cardBox(body);
    LinearLayout oh = new LinearLayout(this);
    oh.setOrientation(LinearLayout.HORIZONTAL);
    oh.setGravity(Gravity.CENTER_VERTICAL);
    oh.setPadding(dp(20), dp(16), dp(20), dp(2));
    TextView ot = new TextView(this);
    ot.setText("\ud22c\uba85\ub3c4");
    ot.setTextSize(17);
    ot.setTextColor(cText);
    ot.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    oh.addView(ot);
    opVal = new TextView(this);
    opVal.setTextSize(14);
    opVal.setTextColor(cAccent);
    opVal.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
    oh.addView(opVal);
    c2.addView(oh);
    sbO = new SeekBar(this);
    sbO.setMax(100);
    tintSeek(sbO);
    LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    olp.setMargins(dp(14), dp(2), dp(14), dp(14));
    sbO.setLayoutParams(olp);
    sbO.setOnSeekBarChangeListener(new Seek() {
      @Override void moved(int v, boolean user) {
        if (!user || lock) return;
        op = v;
        apply(sbO);
      }
    });
    c2.addView(sbO);

    /* 카드 3 — 글자 색상 (탭하면 자동→밝게→어둡게 순환) */
    LinearLayout c3 = cardBox(body);
    LinearLayout tr = new LinearLayout(this);
    tr.setOrientation(LinearLayout.VERTICAL);
    tr.setPadding(dp(20), dp(14), dp(20), dp(14));
    tr.setClickable(true);
    TextView tt = new TextView(this);
    tt.setText("\uae00\uc790 \uc0c9\uc0c1");
    tt.setTextSize(17);
    tt.setTextColor(cText);
    txVal = new TextView(this);
    txVal.setTextSize(14);
    txVal.setTextColor(cAccent);
    txVal.setPadding(0, dp(3), 0, 0);
    tr.addView(tt);
    tr.addView(txVal);
    tr.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        tx = (tx + 1) % 3;
        apply(null);
      }
    });
    c3.addView(tr);

    /* 최근 오류 — 있을 때만 */
    final String crash = CrashLog.read(this);
    if (crash != null) {
      final LinearLayout cE = cardBox(body);
      rowHeader(cE, "\ucd5c\uadfc \uc624\ub958 (\uae38\uac8c \ub20c\ub7ec \ubcf5\uc0ac)");
      TextView tvE = new TextView(this);
      tvE.setText(crash);
      tvE.setTextSize(10);
      tvE.setTextColor(0xFFB05050);
      tvE.setTypeface(Typeface.MONOSPACE);
      tvE.setTextIsSelectable(true);
      tvE.setPadding(dp(20), 0, dp(20), dp(6));
      cE.addView(tvE);
      TextView clr = new TextView(this);
      clr.setText("\uc9c0\uc6b0\uae30");
      clr.setTextSize(14);
      clr.setTextColor(cAccent);
      clr.setPadding(dp(20), dp(4), dp(20), dp(14));
      clr.setClickable(true);
      clr.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
          CrashLog.clear(WidgetConfigActivity.this);
          cE.setVisibility(View.GONE);
        }
      });
      cE.addView(clr);
    }

    /* 하단 취소|저장 알약 */
    LinearLayout bar = new LinearLayout(this);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER);
    bar.setPadding(0, dp(8), 0, dp(14));
    LinearLayout pill = new LinearLayout(this);
    pill.setOrientation(LinearLayout.HORIZONTAL);
    pill.setGravity(Gravity.CENTER_VERTICAL);
    pill.setBackground(rounded(cCard, 30));
    pill.setElevation(dp(2));
    TextView btnCancel = pillBtn("\ucde8\uc18c");
    btnCancel.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) { finish(); }
    });
    View pd = new View(this);
    pd.setBackgroundColor(cDivider);
    pd.setLayoutParams(new LinearLayout.LayoutParams(dp(1), dp(22)));
    TextView btnSave = pillBtn("\uc800\uc7a5");
    btnSave.setTypeface(Typeface.DEFAULT_BOLD);
    btnSave.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) { saveAndFinish(); }
    });
    pill.addView(btnCancel);
    pill.addView(pd);
    pill.addView(btnSave);
    bar.addView(pill);
    root.addView(bar);

    return root;
  }

  private TextView pillBtn(String t) {
    TextView b = new TextView(this);
    b.setText(t);
    b.setTextSize(16);
    b.setTextColor(cText);
    b.setGravity(Gravity.CENTER);
    b.setClickable(true);
    b.setPadding(dp(34), dp(15), dp(34), dp(15));
    return b;
  }

  private LinearLayout cardBox(LinearLayout parent) {
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setBackground(rounded(cCard, 24));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.bottomMargin = dp(14);
    c.setLayoutParams(lp);
    parent.addView(c);
    return c;
  }

  private void rowHeader(LinearLayout card, String t) {
    TextView h = new TextView(this);
    h.setText(t);
    h.setTextSize(17);
    h.setTextColor(cText);
    h.setPadding(dp(20), dp(16), dp(20), dp(10));
    card.addView(h);
  }

  private void divider(LinearLayout card) {
    View d = new View(this);
    d.setBackgroundColor(cDivider);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
    lp.setMargins(dp(20), dp(4), dp(20), dp(6));
    d.setLayoutParams(lp);
    card.addView(d);
  }

  private void valueRow(LinearLayout card, String label, View val) {
    LinearLayout r = new LinearLayout(this);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setPadding(dp(20), 0, dp(20), dp(4));
    TextView lb = new TextView(this);
    lb.setText(label);
    lb.setTextSize(14);
    lb.setTextColor(cSub);
    lb.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    r.addView(lb);
    val.setLayoutParams(new LinearLayout.LayoutParams(dp(118), ViewGroup.LayoutParams.WRAP_CONTENT));
    r.addView(val);
    card.addView(r);
  }

  private SeekBar channelRow(LinearLayout card, String name, int ch) { return channelRow(card, name, ch, false); }

  private SeekBar channelRow(LinearLayout card, String name, final int ch, boolean last) {
    LinearLayout r = new LinearLayout(this);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setPadding(dp(20), dp(2), dp(20), last ? dp(14) : dp(4));
    TextView lb = new TextView(this);
    lb.setText(name);
    lb.setTextSize(13);
    lb.setTextColor(cSub);
    lb.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
    lb.setMinWidth(dp(22));
    r.addView(lb);
    final SeekBar sb = new SeekBar(this);
    sb.setMax(255);
    tintSeek(sb);
    sb.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    r.addView(sb);
    final EditText ed = valueEdit(true);
    LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT);
    elp.leftMargin = dp(10);
    ed.setLayoutParams(elp);
    r.addView(ed);
    card.addView(r);
    if (ch == 0) edR = ed; else if (ch == 1) edG = ed; else edB = ed;
    sb.setOnSeekBarChangeListener(new Seek() {
      @Override void moved(int v, boolean user) {
        if (!user || lock) return;
        setChannel(ch, v);
        apply(sb);
      }
    });
    ed.addTextChangedListener(new Watch() {
      @Override void changed(String t) {
        if (lock) return;
        try {
          int v = Integer.parseInt(t.trim());
          if (v < 0) v = 0; if (v > 255) v = 255;
          setChannel(ch, v);
          apply(ed);
        } catch (Exception e) {}
      }
    });
    return sb;
  }

  private void tintSeek(SeekBar sb) {
    try {
      ColorStateList a = ColorStateList.valueOf(cAccent);
      sb.setProgressTintList(a);
      sb.setThumbTintList(a);
    } catch (Exception e) {}
  }

  private EditText valueEdit(boolean number) {
    EditText ed = new EditText(this);
    ed.setSingleLine(true);
    ed.setTextSize(14);
    ed.setTextColor(cAccent);
    ed.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
    ed.setGravity(Gravity.CENTER);
    ed.setBackground(rounded((cText & 0x00FFFFFF) | 0x14000000, 10));
    ed.setPadding(dp(10), dp(7), dp(10), dp(7));
    ed.setInputType(number ? InputType.TYPE_CLASS_NUMBER
        : (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
    return ed;
  }

  private void setChannel(int ch, int v) {
    int r = Color.red(bg), g = Color.green(bg), b2 = Color.blue(bg);
    if (ch == 0) r = v; else if (ch == 1) g = v; else b2 = v;
    bg = Color.argb(255, r, g, b2);
  }

  /* 상태 → 화면 일괄 반영 */
  private void apply(View src) {
    lock = true;
    try {
      int r = Color.red(bg), g = Color.green(bg), b2 = Color.blue(bg);
      if (sbR != null && src != sbR) sbR.setProgress(r);
      if (sbG != null && src != sbG) sbG.setProgress(g);
      if (sbB != null && src != sbB) sbB.setProgress(b2);
      if (edR != null && src != edR) edR.setText(String.valueOf(r));
      if (edG != null && src != edG) edG.setText(String.valueOf(g));
      if (edB != null && src != edB) edB.setText(String.valueOf(b2));
      if (edHex != null && src != edHex)
        edHex.setText(String.format("#%02X%02X%02X", r, g, b2));
      if (sbO != null && src != sbO) sbO.setProgress(op);
      if (opVal != null) opVal.setText(op + "%");
      if (txVal != null)
        txVal.setText(tx == 0 ? "\uc790\ub3d9" : tx == 1 ? "\ubc1d\uac8c" : "\uc5b4\ub461\uac8c");

      if (pvBox != null) {
        pvBox.setBackground(roundedPv(Color.argb(Math.round(op * 255f / 100f), r, g, b2)));
        boolean lightText = WCfg.lightText(bg, op, tx);
        if (pvLabel != null) pvLabel.setTextColor(lightText ? 0xFFC9BFA8 : 0xFF433A22);
        if (pvValue != null) pvValue.setTextColor(lightText ? 0xFFE3B95A : 0xFF5A3E00);
      }
    } catch (Exception e) {
    } finally {
      lock = false;
    }
  }

  private GradientDrawable roundedPv(int fill) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(fill);
    g.setCornerRadius(dp(24));
    g.setStroke(dp(1), (cText & 0x00FFFFFF) | 0x22000000);
    return g;
  }

  private void saveAndFinish() {
    try {
      getSharedPreferences("widget", 0).edit()
        .putInt("bg_" + wid, bg)
        .putInt("op_" + wid, op)
        .putInt("tx_" + wid, tx)
        .apply();
      RefreshReceiver.pushAll(this);
    } catch (Throwable e) { CrashLog.save(this, e); }
    Intent out = new Intent();
    out.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
    setResult(RESULT_OK, out);
    finish();
  }

  private abstract static class Seek implements SeekBar.OnSeekBarChangeListener {
    abstract void moved(int v, boolean user);
    @Override public void onProgressChanged(SeekBar s, int v, boolean u) { try { moved(v, u); } catch (Exception e) {} }
    @Override public void onStartTrackingTouch(SeekBar s) {}
    @Override public void onStopTrackingTouch(SeekBar s) {}
  }
  private abstract static class Watch implements TextWatcher {
    abstract void changed(String t);
    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
    @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
    @Override public void afterTextChanged(Editable e) { try { changed(e == null ? "" : e.toString()); } catch (Exception ex) {} }
  }
}
