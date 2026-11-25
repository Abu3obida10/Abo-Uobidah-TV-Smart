#!/usr/bin/env bash
# اختبارات منطق التطبيق البحت دون الحاجة إلى Android SDK أو تثبيت Gradle.
# يستخدم kotlinc المتاح على الجهاز وطبقة JUnit صغيرة داخل مجلد مؤقت فقط.
set -euo pipefail
cd "$(dirname "$0")"

KOTLINC_BIN="${KOTLINC:-$(command -v kotlinc || true)}"
KOTLIN_BIN="${KOTLIN:-$(command -v kotlin || true)}"
JAVAC_BIN="${JAVAC:-$(command -v javac || true)}"
for tool in "$KOTLINC_BIN" "$KOTLIN_BIN" "$JAVAC_BIN"; do
  if [[ -z "$tool" || ! -x "$tool" ]]; then
    echo "Missing required tool: kotlinc, kotlin, or javac" >&2
    exit 2
  fi
done

OUT="$(mktemp -d "${TMPDIR:-/tmp}/uobidah-core-tests.XXXXXX")"
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/src/org/junit/function" "$OUT/src/org/junit" "$OUT/classes"

cat > "$OUT/src/org/junit/Test.java" <<'JAVA'
package org.junit;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Test {}
JAVA
cat > "$OUT/src/org/junit/function/ThrowingRunnable.java" <<'JAVA'
package org.junit.function;
public interface ThrowingRunnable { void run() throws Throwable; }
JAVA
cat > "$OUT/src/org/junit/Assert.java" <<'JAVA'
package org.junit;
import java.util.Objects;
import org.junit.function.ThrowingRunnable;
public class Assert {
  public static void fail(String message) { throw new AssertionError(message); }
  public static void assertTrue(boolean value) { if (!value) fail("expected true"); }
  public static void assertTrue(String message, boolean value) { if (!value) fail(message); }
  public static void assertFalse(boolean value) { if (value) fail("expected false"); }
  public static void assertFalse(String message, boolean value) { if (value) fail(message); }
  public static void assertSame(Object expected, Object actual) { if (expected != actual) fail("expected same instance"); }
  public static void assertNull(Object actual) { if (actual != null) fail("expected null but was <" + actual + ">"); }
  public static void assertEquals(String message, Object expected, Object actual) {
    if (!Objects.equals(expected, actual)) fail(message + ": expected <" + expected + "> but was <" + actual + ">");
  }
  public static void assertEquals(Object expected, Object actual) {
    if (!Objects.equals(expected, actual)) fail("expected <" + expected + "> but was <" + actual + ">");
  }
  public static void assertEquals(long expected, long actual) {
    if (expected != actual) fail("expected <" + expected + "> but was <" + actual + ">");
  }
  public static void assertEquals(double expected, double actual, double delta) {
    if (!(Double.compare(expected, actual) == 0 || Math.abs(expected - actual) <= delta))
      fail("expected <" + expected + "> but was <" + actual + ">");
  }
  public static <T extends Throwable> T assertThrows(Class<T> expected, ThrowingRunnable action) {
    try { action.run(); } catch (Throwable actual) {
      if (expected.isInstance(actual)) return expected.cast(actual);
      fail("expected " + expected.getName() + " but caught " + actual);
    }
    fail("expected exception " + expected.getName());
    return null;
  }
}
JAVA
cat > "$OUT/src/Runner.kt" <<'KOTLIN'
import org.junit.Test
import java.lang.reflect.InvocationTargetException

fun main() {
    val classes = listOf(
        "com.uobidah.launcher.core.MiniJsonTest",
        "com.uobidah.launcher.core.NotificationPolicyTest",
        "com.uobidah.launcher.core.AlertGateTest",
        "com.uobidah.launcher.core.FavoriteListTest",
        "com.uobidah.launcher.core.BackupCodecTest",
        "com.uobidah.launcher.core.MatchSourceTest",
        "com.uobidah.launcher.core.MatchStatusNormalizerTest",
        "com.uobidah.launcher.core.PerfLimitsTest",
        "com.uobidah.launcher.core.CityClockTest",
        "com.uobidah.launcher.core.IslandGeometryTest",
        "com.uobidah.launcher.core.HomeLayoutsTest",
        "com.uobidah.launcher.core.IptvContentClassifierTest",
        "com.uobidah.launcher.core.ContrastTest",
        "com.uobidah.launcher.core.RecitersTest",
        "com.uobidah.launcher.core.CardGridTest",
        "com.uobidah.launcher.core.AdhkarQuranTest",
        "com.uobidah.launcher.core.TimelineKindTest",
        "com.uobidah.launcher.core.MatchIdentityTest",
        "com.uobidah.launcher.core.MatchScoreReconcilerTest",
        "com.uobidah.launcher.core.MatchRefreshPlanTest",
        "com.uobidah.launcher.core.LatestRequestGateTest",
        "com.uobidah.launcher.core.IptvStreamTypeResolverTest",
        "com.uobidah.launcher.core.TimelineIntegrityTest",
        "com.uobidah.launcher.core.UpdatePolicyTest",
        "com.uobidah.launcher.core.YallaScoresHtmlParserTest",
        "com.uobidah.launcher.core.M3uParserTest",
        "com.uobidah.launcher.core.QiblaFormStandingsTest",
        "com.uobidah.launcher.core.NotesOccasionsTest",
        "com.uobidah.launcher.core.FavoriteChannelsTest",
        "com.uobidah.launcher.core.M3uLogoTest"
    )
    var passed = 0
    val failures = mutableListOf<String>()
    for (name in classes) {
        val type = Class.forName(name)
        for (method in type.declaredMethods.filter { it.isAnnotationPresent(Test::class.java) }.sortedBy { it.name }) {
            val instance = type.getDeclaredConstructor().newInstance()
            try {
                method.invoke(instance)
                passed++
                println("PASS ${type.simpleName}.${method.name}")
            } catch (e: InvocationTargetException) {
                val cause = e.targetException
                failures += "${type.simpleName}.${method.name}: ${cause.javaClass.simpleName}: ${cause.message}"
                System.err.println("FAIL ${failures.last()}")
            }
        }
    }
    println("\nCore tests: $passed passed, ${failures.size} failed")
    if (failures.isNotEmpty()) error("Core test suite failed")
}
KOTLIN

"$JAVAC_BIN" -d "$OUT/classes" "$OUT/src/org/junit/Test.java" "$OUT/src/org/junit/function/ThrowingRunnable.java" "$OUT/src/org/junit/Assert.java"
"$KOTLINC_BIN" -jvm-target 17 -classpath "$OUT/classes" \
  app/src/main/java/com/uobidah/launcher/core/*.kt \
  app/src/test/java/com/uobidah/launcher/core/*.kt \
  "$OUT/src/Runner.kt" -d "$OUT/classes"
"$KOTLIN_BIN" -classpath "$OUT/classes" RunnerKt
