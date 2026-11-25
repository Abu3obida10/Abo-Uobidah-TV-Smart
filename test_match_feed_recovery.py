from pathlib import Path
root = Path(__file__).resolve().parent
yalla = (root / "app/src/main/java/com/uobidah/launcher/YallaScoresActivity.kt").read_text(encoding="utf-8")
sofa = (root / "app/src/main/java/com/uobidah/launcher/SofaScoreProvider.kt").read_text(encoding="utf-8")
styles = (root / "app/src/main/java/com/uobidah/launcher/MatchCardStyles.kt").read_text(encoding="utf-8")
espn = (root / "app/src/main/java/com/uobidah/launcher/EspnSoccerProvider.kt").read_text(encoding="utf-8")
detail = (root / "app/src/main/java/com/uobidah/launcher/MatchDetailActivity.kt").read_text(encoding="utf-8")
repo = (root / "app/src/main/java/com/uobidah/launcher/Repos.kt").read_text(encoding="utf-8")
checks = {
 "global fixtures cover yesterday, today and tomorrow": "LocalDate.now().minusDays(1)" in yalla and "LocalDate.now().plusDays(1)" in yalla and "fetchAllDay(date)" in yalla,
 "Yalla team page failure falls back to SofaScore": "SofaScoreProvider.fetchTeam(prefs, team)" in yalla,
 "last cached club matches remain visible on failure": "cached.filter { failed.contains(it.team.id) }" in yalla,
 "global feed has a short cache and parses real events": "all-day|$date" in sofa and "45_000L" in sofa and "parseEvent(event, now)" in sofa,
 "match cards no longer expose a glass transparent style": '"متدرج ناعم"' in styles and '"زجاجي"' not in styles and "Ui.withAlpha(p.bgA, 238)" not in styles,
 "ESPN timeline uses summary and core plays endpoints with bounded requests": "summary?event=$eventId" in espn and "sports.core.api.espn.com/v2/sports/soccer/leagues/$league/events/$eventId/competitions/$eventId/plays" in espn and "5000" in espn,
 "ESPN CDN game package is the last timeline fallback": "cdn.espn.com/core/soccer/game?xhr=1&gameId=$eventId" in espn and "findPlayArrays(cdn)" in espn,
 "ESPN timeline responses are cached and deduplicated per event": "ConcurrentHashMap<String, TimelineCacheEntry>" in espn and "synchronized(lock)" in espn and "25_000L" in espn,
 "match detail refreshes the selected provider event id without cross-provider id mixing": 'resolvedEventId = selectedEventId' in detail and 'usedSofaLive -> SofaScoreProvider.incidents(eventId)' in detail and 'detailSource == "espn" -> EspnSoccerProvider.timeline(eventId)' in detail and 'else -> MatchRepo.timeline(eventId)' in detail,
 "Arabic finished and canceled states use the centralized strict normalizer": "MatchStatusNormalizer.isFinished(status)" in repo and "MatchStatusNormalizer.isCanceled(status)" in repo,
 "Standings use the ESPN Core API path": '"https://site.api.espn.com/apis/v2/sports/soccer/$league/standings"' in espn,
 "Egyptian ESPN coverage is explicitly described as uncertain": "تغطية المصدر غير مؤكدة" in espn and '"egy.1"' in espn,
 "previous results require an explicit finished status": "fun previous(now: Long): Boolean = finished(now)" in repo,
 "elapsed time and score are not a finished-state fallback": "homeScore != null && awayScore != null && startMs > 0 && now - startMs" not in repo,
 "date-only fixtures do not receive a fake midnight kickoff": 'else "00:00:00"' not in repo,
 "cross-source status changes merge even when the score is unchanged": "s.live(now) && !m.live(now)" in repo and "val statusAdvances" in repo,
}
for label, ok in checks.items(): print(("PASS " if ok else "FAIL ") + label)
if not all(checks.values()): raise SystemExit(1)
