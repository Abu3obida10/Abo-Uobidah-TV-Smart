from pathlib import Path

root = Path(__file__).resolve().parents[1]
intent_test = (root / "app/src/test/java/com/uobidah/launcher/TvInputsIntentTest.kt").read_text()
render_test = (root / "app/src/test/java/com/uobidah/launcher/ReferenceLayoutRenderTest.kt").read_text()
main = (root / "app/src/main/java/com/uobidah/launcher/TvInputsActivity.kt").read_text()
policy = (root / "app/src/main/java/com/uobidah/launcher/TvInputRoutingPolicy.kt").read_text()

checks = {
    "RobolectricTestRunner imported explicitly": "import org.robolectric.RobolectricTestRunner" in intent_test,
    "test runner annotation resolves imported class": "@RunWith(RobolectricTestRunner::class)" in intent_test,
    "prayer time is safely cast to TextView": "find(root, \"r9-prayer-time-$prayer\") as? TextView" in render_test,
    "typed prayer time reads TextView.text only after null check": "time != null && time.visibility == View.VISIBLE && time.text.isNotBlank()" in render_test,
    "HDMI activity uses the standalone routing policy": "TvInputRoutingPolicy.selectDirectRoute(" in main,
    "saved TV app route handled before HDMI fallback": "TvInputRoutingPolicy.DirectRoute.SAVED_TV_APP" in main,
    # الحارس يعيش داخل السياسة نفسها منذ توحيد المسار في selectDirectRoute.
    "disconnected HDMI never enters direct fallback": "TvInputRoutingPolicy.isDirectlyAvailableInputState(" in main or "isDirectlyAvailableInputState(state" in policy,
    "route tests cover disconnected HDMI": "directRouteNeverAutoSelectsDisconnectedHdmi" in intent_test,
    # قرار المستخدم (27 نوفمبر): المدخل المتصل أولًا، ويُفضَّل HDMI 1 بين المتصلة؛ الاختبار بهذا الاسم.
    "route tests cover connected HDMI priority": "directRoutePrefersConnectedHdmiOverResolvableSavedTvApplication" in intent_test,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
print(f"RESULT: {len(checks)-len(failed)}/{len(checks)}")
if failed:
    raise SystemExit(1)
