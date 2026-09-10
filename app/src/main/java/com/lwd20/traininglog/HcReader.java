package com.lwd20.traininglog;

import android.app.Activity;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.MealType;
import android.health.connect.datatypes.NutritionRecord;
import android.health.connect.datatypes.WeightRecord;
import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Mass;
import android.os.OutcomeReceiver;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 헬스 커넥트에서 섭취 영양을 읽어 날짜별로 합친다.
 *
 * 안드로이드 14부터 헬스 커넥트가 OS 안에 들어와서 라이브러리 없이 쓸 수 있다.
 * 이 앱은 그래들 의존성이 하나도 없는 순수 프레임워크 앱이라 이 길을 택했다.
 *
 * 여기 나오는 클래스는 전부 API 34 이므로 이 파일은 SDK_INT 검사를 통과한
 * 뒤에만 건드려야 한다 — 이름만 스쳐도 옛 기기에서 클래스 로딩이 터진다.
 * 그래서 HealthBridge 와 파일을 나눠 두었다.
 */
final class HcReader {

  private HcReader() {}

  /**
   * 에너지 단위를 실측으로 정한다.
   *
   * 플랫폼 Energy 에는 getInCalories() 하나뿐이고 킬로칼로리 메서드가 없다.
   * 문서만 봐서는 738kcal 짜리 저녁이 738 로 오는지 738000 으로 오는지 알 수 없어
   * 값의 크기로 가른다. 사람이 하루에 20000kcal 를 넘길 수는 없으므로
   * 그 위로 나오면 단위가 cal 인 것이고 1000 으로 나눈다. 여유가 아주 크다.
   */
  private static double kcal(double raw) {
    return raw > 20000 ? raw / 1000.0 : raw;
  }

  /** 질량도 같은 이유로. 하루 단백질이 5000g 일 수는 없다 */
  private static double grams(double raw) {
    return raw > 5000 ? raw / 1000.0 : raw;
  }

  private static double e(Energy v) { return v == null ? 0 : kcal(v.getInCalories()); }
  private static double m(Mass v) { return v == null ? 0 : grams(v.getInGrams()); }

  interface Done {
    void ok(String json);
    void fail(String why);
  }

  /** 하루치 또는 한 끼니의 누적 */
  private static final class Sum {
    double kcal, p, c, f;
    int n;

    void add(NutritionRecord r) {
      kcal += e(r.getEnergy());
      p += m(r.getProtein());
      c += m(r.getTotalCarbohydrate());
      f += m(r.getTotalFat());
      n++;
    }

    String json() {
      return "{\"kcal\":" + Math.round(kcal)
          + ",\"p\":" + Math.round(p)
          + ",\"c\":" + Math.round(c)
          + ",\"f\":" + Math.round(f)
          + ",\"n\":" + n + "}";
    }
  }

  /** 하루 = 합계 + 끼니별 */
  private static final class Day {
    final Sum all = new Sum();
    final Map<String, Sum> meals = new LinkedHashMap<>();

    Sum meal(String k) {
      Sum v = meals.get(k);
      if (v == null) { v = new Sum(); meals.put(k, v); }
      return v;
    }
  }

  /* 상수를 이름으로 짚는다 — 숫자를 짐작하면 틀려도 조용히 넘어간다 */
  private static String mealKey(int t) {
    if (t == MealType.MEAL_TYPE_BREAKFAST) return "breakfast";
    if (t == MealType.MEAL_TYPE_LUNCH) return "lunch";
    if (t == MealType.MEAL_TYPE_DINNER) return "dinner";
    if (t == MealType.MEAL_TYPE_SNACK) return "snack";
    return "other";
  }

  static void read(final Activity act, int days, final Done cb) {
    HealthConnectManager hcm = act.getSystemService(HealthConnectManager.class);
    if (hcm == null) { cb.fail("서비스 없음"); return; }

    final ZoneId zone = ZoneId.systemDefault();
    Instant from = LocalDate.now(zone).minusDays(Math.max(0, days - 1))
        .atStartOfDay(zone).toInstant();
    Instant to = Instant.now();

    ReadRecordsRequestUsingFilters<NutritionRecord> req =
        new ReadRecordsRequestUsingFilters.Builder<>(NutritionRecord.class)
            .setTimeRangeFilter(new TimeInstantRangeFilter.Builder()
                .setStartTime(from).setEndTime(to).build())
            .build();

    hcm.readRecords(req, act.getMainExecutor(),
        new OutcomeReceiver<ReadRecordsResponse<NutritionRecord>, HealthConnectException>() {
          @Override public void onResult(ReadRecordsResponse<NutritionRecord> res) {
            final String food;
            try { food = fold(res.getRecords(), zone); }
            catch (Throwable t) { cb.fail(String.valueOf(t.getMessage())); return; }
            readWeight(act, hcm, zone, from, to, food, cb);
          }
          @Override public void onError(HealthConnectException ex) {
            cb.fail(ex.getClass().getSimpleName() + ": " + ex.getMessage());
          }
        });
  }

  /* 체중 권한만 따로 거부할 수 있다 — 실패해도 영양은 살려서 내보낸다 */
  private static void readWeight(Activity act, HealthConnectManager hcm, final ZoneId zone,
                                 Instant from, Instant to, final String food, final Done cb) {
    try {
      ReadRecordsRequestUsingFilters<WeightRecord> wr =
          new ReadRecordsRequestUsingFilters.Builder<>(WeightRecord.class)
              .setTimeRangeFilter(new TimeInstantRangeFilter.Builder()
                  .setStartTime(from).setEndTime(to).build())
              .build();
      hcm.readRecords(wr, act.getMainExecutor(),
          new OutcomeReceiver<ReadRecordsResponse<WeightRecord>, HealthConnectException>() {
            @Override public void onResult(ReadRecordsResponse<WeightRecord> res) {
              try { cb.ok(merge(food, foldW(res.getRecords(), zone))); }
              catch (Throwable t) { cb.ok(food); }
            }
            @Override public void onError(HealthConnectException ex) { cb.ok(food); }
          });
    } catch (Throwable t) { cb.ok(food); }
  }

  /** 영양 JSON 뒤에 체중을 덧붙인다 — 끝의 '}' 를 떼고 이어 붙인다 */
  private static String merge(String food, String w) {
    int i = food.lastIndexOf('}');
    if (i < 0) return food;
    return food.substring(0, i) + ",\"w\":" + w + "}";
  }

  /* 하루에 여러 번 잰 값이 전부 개별 레코드로 온다. 어느 것을 쓸지는 웹이
     정하므로 [분, kg] 로 시각을 붙여 그대로 넘긴다.
     Mass 는 그램이라 72.4kg 이 72400 으로 온다 — 영양 쪽 grams() 의 크기 짐작을
     여기에 쓰면 단위가 뒤섞이므로 따로 나눈다. */
  private static String foldW(List<WeightRecord> recs, ZoneId zone) {
    Map<String, StringBuilder> by = new LinkedHashMap<>();
    for (WeightRecord r : recs) {
      if (r.getWeight() == null) continue;
      double kg = r.getWeight().getInGrams() / 1000.0;
      if (!(kg >= 20 && kg <= 300)) continue;      /* 사람 몸무게가 아니면 버린다 */
      ZoneOffset off = r.getZoneOffset();
      java.time.LocalDateTime lt = (off != null ? r.getTime().atOffset(off).toLocalDateTime()
                                                : r.getTime().atZone(zone).toLocalDateTime());
      String k = lt.toLocalDate().toString();
      int mins = lt.getHour() * 60 + lt.getMinute();
      StringBuilder b = by.get(k);
      if (b == null) { b = new StringBuilder("["); by.put(k, b); }
      else b.append(',');
      b.append('[').append(mins).append(',').append(Math.round(kg * 10) / 10.0).append(']');
    }
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, StringBuilder> en : by.entrySet()) {
      if (!first) sb.append(',');
      first = false;
      sb.append('"').append(en.getKey()).append("\":").append(en.getValue()).append(']');
    }
    return sb.append('}').toString();
  }

  /** 기록을 그 기록이 적힌 지역 날짜로 묶는다 — 밤 11시 간식이 다음 날로 새면 안 된다 */
  private static String fold(List<NutritionRecord> recs, ZoneId zone) {
    Map<String, Day> by = new LinkedHashMap<>();
    double rawFirst = -1;
    for (NutritionRecord r : recs) {
      ZoneOffset off = r.getStartZoneOffset();
      LocalDate d = (off != null ? r.getStartTime().atOffset(off).toLocalDate()
                                 : r.getStartTime().atZone(zone).toLocalDate());
      String k = d.toString();
      Day day = by.get(k);
      if (day == null) { day = new Day(); by.put(k, day); }
      if (rawFirst < 0 && r.getEnergy() != null) rawFirst = r.getEnergy().getInCalories();
      day.all.add(r);
      day.meal(mealKey(r.getMealType())).add(r);
    }
    StringBuilder sb = new StringBuilder("{\"ok\":true,\"raw\":").append(rawFirst)
        .append(",\"days\":{");
    boolean first = true;
    for (Map.Entry<String, Day> en : by.entrySet()) {
      if (!first) sb.append(',');
      first = false;
      Day v = en.getValue();
      String a = v.all.json();
      /* 합계 뒤에 끼니별을 덧붙인다 — 합계만 있으면 '저녁을 안 먹었다'를 알 수 없다 */
      StringBuilder ms = new StringBuilder(",\"meals\":{");
      boolean f2 = true;
      for (Map.Entry<String, Sum> mm : v.meals.entrySet()) {
        if (!f2) ms.append(',');
        f2 = false;
        ms.append('"').append(mm.getKey()).append("\":").append(mm.getValue().json());
      }
      ms.append('}');
      sb.append('"').append(en.getKey()).append("\":")
        .append(a, 0, a.length() - 1).append(ms).append('}');
    }
    return sb.append("}}").toString();
  }
}
