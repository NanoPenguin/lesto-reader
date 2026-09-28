# AGENTS.md

Guidance for AI agents and human contributors working on this repository.

## What this is

**Pace** — a minimal, open-source (MIT) RSVP (Rapid Serial Visual Presentation) reader for Android.
It opens EPUB and PDF files from device storage and shows them one word at a time.

- Product and technical design: [docs/DESIGN.md](docs/DESIGN.md)
- Roadmap and current status: [docs/PLAN.md](docs/PLAN.md)

## Principles

1. **Small scope.** Anything not listed in `docs/DESIGN.md` is out of scope. Propose it before building it.
2. **Readable over clever.** Code is written to be reviewed by strangers. Obvious beats concise.
3. **Every dependency must earn its place.** Prefer the platform and AndroidX. Justify new libraries in the PR.
4. **Private by default.** No network permission, analytics, ads, or Google Play Services. No runtime permissions (files are opened through the system picker).
5. **It just works.** Handle bad files and edge cases gracefully; never crash on user content.

## Stack

Kotlin · Jetpack Compose · Material 3 · AGP 9 (built-in Kotlin) · single `app` module · Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`).
Minimum SDK 26. Gradle runs on Java 25, pinned in `gradle/gradle-daemon-jvm.properties`; Gradle finds or downloads it.

## Code layout

```
app/src/main/java/io/github/nanopenguin/pace/
  PaceApp.kt  Root composable and navigation
  book/       Book model, EPUB/PDF parsers, parsed-book cache
  reader/     RSVP engine (pure Kotlin) and the reader screen
  library/    Recently opened books and reading positions
  settings/   User preferences and the settings screen
  home/       Home screen
  ui/         Theme, shared composables
licenses/     Licenses of bundled third-party assets
```

## Conventions

- Kotlin coding conventions, enforced by ktlint (`intellij_idea` style, matching Android Studio's formatter) via Spotless.
- One concept per file. Keep files short; split when a file stops fitting in one's head.
- Icons are vector drawables in `res/drawable` (Material Symbols); no icon library.
- Screens follow `XScreen` (stateless composable) + `XViewModel` (exposes one `StateFlow<XUiState>`).
- No business logic in composables. The RSVP engine and parsers must be unit-testable without an emulator.
- Dependencies are wired by hand in `AppContainer`; no DI framework.
- Comments explain *why*, not *what*. No commented-out code, no TODOs without an issue link.
- Name things fully (`wordsPerMinute`, not `wpm`) except in widely known cases.
- User-facing strings live in `strings.xml`.

## Commands

```
./gradlew assembleDebug             # build
./gradlew test                      # unit tests
./gradlew spotlessApply lint        # format, then static checks
```

## Documentation rules

- Keep docs short and current. Update them in the same change as the code they describe.
- Prefer editing an existing section over adding a new one. Do not add new doc files without a clear need.
- `docs/PLAN.md` is the single source of truth for status; tick items as they land.

## Workflow for agents

- Read `docs/PLAN.md` first and work on the current milestone only.
- Make small, focused changes. Build and run tests before declaring work done.
- Ask before adding dependencies, permissions, screens, or settings.
