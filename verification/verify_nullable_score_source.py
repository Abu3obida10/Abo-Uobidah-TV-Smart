from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / "app/src/main/java/com/uobidah/launcher/DynamicIslandOverlayService.kt"
text = source.read_text(encoding="utf-8")
checks = {
    "current home score read into nullable local": "val currentHomeScore: Int? = live.homeScore" in text,
    "current away score read into nullable local": "val currentAwayScore: Int? = live.awayScore" in text,
    "both current scores checked before use": "if (currentHomeScore != null && currentAwayScore != null)" in text,
    "previous home score parsed into nullable local": "val previousHomeScore: Int? = previousParts.getOrNull(0)?.toIntOrNull()" in text,
    "previous away score parsed into nullable local": "val previousAwayScore: Int? = previousParts.getOrNull(1)?.toIntOrNull()" in text,
    "both previous scores checked before comparison": "if (previousHomeScore != null && previousAwayScore != null)" in text,
    "final home score read into nullable local": "val finalHomeScore: Int? = finishedHit.homeScore" in text,
    "final away score read into nullable local": "val finalAwayScore: Int? = finishedHit.awayScore" in text,
    "final score pair checked before outcome comparison": "if (finalHomeScore != null && finalAwayScore != null" in text,
}
for label, ok in checks.items():
    print(("PASS" if ok else "FAIL") + " " + label)
assert all(checks.values()), "Nullable score source guard check failed"
print(f"Nullable score source guard: {sum(checks.values())}/{len(checks)} passed")
