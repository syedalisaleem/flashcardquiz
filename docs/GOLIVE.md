# FlashcardQuiz — Go-Live Runbook (v2.3.0)

## Architecture

- **Web + API**: FastAPI on Modal — https://next-gaming12--flashcardquiz-web.modal.run
- **Android**: Kotlin app (Room local storage) — releases on GitHub
- **Download chain**: `GET /api/download/android` → HTTP redirect → `https://github.com/syedalisaleem/flashcardquiz/releases/latest/download/FlashcardQuizApp.apk`
  - The asset **must** be named exactly `FlashcardQuizApp.apk`.
- **LLM**: Gemini via OpenAI-compatible API (`OPENAI_BASE_URL`, `LLM_MODEL`), key in Modal secret.
- **Textbook fetch chain**: Firecrawl → Gemini knowledge → prompt fallback (see `app/textbook.py`).

## Deploy

```powershell
$env:PYTHONUTF8='1'; $env:PYTHONIOENCODING='utf-8'
modal deploy modal_app.py            # backend (+ uploads dist/)
modal app logs flashcardquiz         # logs
```

- Secrets: `flashcardquiz-secrets` (`OPENAI_API_KEY`, `OPENAI_BASE_URL`, `LLM_MODEL`, `GEMINI_API_KEY`; optional `FIRECRAWL_API_KEY`).
  Update: `modal secret update flashcardquiz-secrets --from-literal KEY=value`.
- Constraints: `max_containers=1`, `scaledown_window=600`, `@modal.concurrent(max_inputs=20)`.

## Android release

```powershell
cd android
.\gradlew.bat :app:assembleRelease :app:bundleRelease --console=plain
Copy-Item app\build\outputs\apk\release\app-release.apk ..\dist\FlashcardQuizApp.apk -Force
Copy-Item app\build\outputs\bundle\release\app-release.aab ..\dist\FlashcardQuiz.aab -Force
gh release create v<new> ..\dist\FlashcardQuizApp.apk ..\dist\FlashcardQuiz.aab --title "..." --notes "..."
```

- Signing: `release-key.jks` + passwords read from `android/local.properties` (untracked; keep a backup).
- Bump `versionCode` + `versionName` in `android/app/build.gradle.kts` every release.
- After the gh release, the site download link serves the new APK automatically (no redeploy needed).

## Feature flags (staged, deliberately off)

- **Ads**: `ADS_ENABLED` default `false` — AdMob main-thread init previously coincided with ANR reports; turn on only after testing cold start on low-RAM devices.
- **Payments**: Google Play Billing present but behind flags — real IAP requires the Play Console account + product setup (owner action before enabling).
- **Firecrawl**: without `FIRECRAWL_API_KEY`, textbook fetch uses Gemini-knowledge → prompt fallback (quality is lower; set the key for best results).

## Known limitations (documented, by design)

1. **Redeploy kills in-flight study jobs** — jobs dict is in-memory per container; a deploy/scaledown makes pending job IDs 404. The app handles this gracefully (shows retry UI). Users mid-generation at deploy time must retry.
2. **Gemini 503 / rate limits** — `_chat` falls back `gemini-3.5-flash` → `gemini-3.5-flash-lite` → `gemini-3.6-flash`; exhausted fallbacks surface as a friendly error. Transient statuses retried: 402/429/500/502/503/504.
3. **Jobs lost on container scale-down** after 600s idle — same client handling as (1).
4. **AdMob / Play Billing untested against real accounts** (no Play Console access yet).

## Tests

```powershell
$env:MOCK_LLM='true'; python test_app.py     # 27 tests (includes Gemini error paths + model fallback)
```

## Monitoring

- `modal app logs flashcardquiz` — watch for `provider=gemini`, `job ... done`, `paused wait=`, `error`.
- GitHub → Releases — APK/AAB artifacts.
- Emulator (dev only): `ANDROID_AVD_HOME=F:\avds` + AVD `fqtest`; after crashes delete stale `hardware-qemu.ini.lock` / `multiinstance.lock` in the AVD dir; host needs ≥4 GB free RAM or QEMU gets OOM-silently-killed.

## Support flow for "app won't update"

1. Check GitHub `releases/latest` has the new version.
2. `GET /api/download/android` must return `302` to `.../latest/download/FlashcardQuizApp.apk`.
3. On device: install new APK over old (same signature `release-key.jks`).
