# MASTER_REFACTOR_PLAN — FlashcardQuiz Android

Hybrid modernization: XML/ViewBinding frame + Compose theme interop + Room + Hilt + Retrofit + unit tests.

**Stack decisions (locked):**
- UI: Keep XML + ViewBinding; Compose Theme (Color.kt / Theme.kt) + ComposeView for dynamic cards later
- DB: Room mapped 1:1 to existing `flashcardquiz.db` schema (no data loss)
- DI: Hilt
- Network: Retrofit + OkHttp + kotlinx-serialization or Gson, `Result<T>` + injected dispatchers
- Tests: unit tests in `src/test` per phase; existing sources/tests immutable unless phase requires signature updates

**Verification (after every step):**
```
cd android && ./gradlew.bat assembleDebug && ./gradlew.bat test
```

---

## Phase 1: Theme & Design System Setup — `[COMPLETED]`
- [x] `Color.kt` — light/dark sage-teal primitives
- [x] `Theme.kt` — Compose `lightColorScheme` / `darkColorScheme`
- [x] `Type.kt` — Material 3 typography scale
- [x] `res/values/colors.xml` — light palette tokens
- [x] `res/values-night/colors.xml` — dark palette tokens
- [x] `res/values/themes.xml` — M3 theme wired to tokens (status/nav bars)
- [x] `ThemeManager` / `AppTheme` — map to official tokens (keep API stable)
- [x] Replace hardcoded hex in layouts/drawables with `@color/` references (atomic batch)
- [x] Compose BOM + activity-compose deps (theme module compile)
- [x] Unit test: `ThemeTokenTest` (light/dark hex assertions)
- [x] `assembleDebug` + `test` green

## Phase 2: Data Layer (Room + DTOs + Mappers) — `[COMPLETED]`
- [x] Room deps + `@Database` + entities matching `decks` / `flashcards` / `mcqs` columns exactly
- [x] DAOs returning `Flow<T>`
- [x] DTO ↔ Entity ↔ Domain mappers (`toDomain` / `toEntity` / `toDto`)
- [x] DataStore for theme/profile prefs (replace SharedPreferences incrementally)
- [x] Unit tests: mappers (`MappersTest` 6 tests)
- [x] Verify build + tests

## Phase 3: Repository, Network & Hilt — `[COMPLETED]`
- [x] Hilt: `Application` + modules (Network, Database, Dispatcher, Repository)
- [x] Retrofit/OkHttp client replacing callback `ApiClient` surface; suspend + `Result<T>`
- [x] Repository interfaces implemented over Room DAOs + remote API
- [x] Remove trust-all TLS; inject `CoroutineDispatcher`
- [x] Unit tests: repositories (fake DAO / MockWebServer)
- [x] Verify build + tests

## Phase 4: Domain Layer & Use Cases — `[COMPLETED]`
- [x] Use cases: GenerateCards, ReviewCard (SM-2), QuizAnswer, ObserveDecks
- [x] `SpacedRepetitionEngine` pure domain + tests
- [x] Verify build + tests

## Phase 5: Reusable UI Components — `[COMPLETED]`
- [x] Tokenized XML styles/themes + shared drawables
- [x] ComposeView components: FlashcardFace, QuizOptionChip (interoperability)
- [x] Min touch target 48dp audit
- [x] Verify build + tests

## Phase 6: Screens & ViewModel Binding — `[COMPLETED]`
- [x] Hilt ViewModels + StateFlow ViewStates for Home / Generate / Review / Stats
- [x] Fragments observe flows; no business logic in views
- [x] Light/dark theme switch smoke path
- [x] Unit tests: ViewModels (`HomeViewModelTest` 5 tests)
- [x] Final `assembleDebug` + `test`

---

## Status Log
| Phase | Status | Verified |
|-------|--------|----------|
| 1 Theme | COMPLETED | assembleDebug + test (ThemeTokenTest 3/3) |
| 2 Data | COMPLETED | assembleDebug + test (MappersTest 6/6) |
| 3 Repo/Net/DI | COMPLETED | assembleDebug + test (RepositoryTest 7/7) |
| 4 Domain | COMPLETED | assembleDebug + test (SM2 + Quiz 8/8) |
| 5 UI Components | COMPLETED | assembleDebug + test |
| 6 Screens/VM | COMPLETED | assembleDebug + test (HomeViewModelTest 5/5) |

**Final verification (Phase 6):** `assembleDebug` + `test` → BUILD SUCCESSFUL.
**Total unit tests:** 29/29 passing (Theme 3, Mappers 6, Repos 7, SM2 6, Quiz 2, HomeVM 5).
