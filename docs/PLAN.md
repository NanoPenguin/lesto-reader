# Plan

Milestones are done in order. Each ends with a building, testable app.

## Open decisions

- [x] Name: **Pace**
- [x] Application ID: `io.github.nanopenguin.pace`
- [x] License: MIT
- [x] Typeface: bundled Atkinson Hyperlegible Next (OFL)
- [x] Build environment: Android Studio SDK; Gradle daemon pinned to Java 25

## M0 — Scaffold
- [x] Gradle project, version catalog, single `app` module, R8 for release
- [x] Theme (light/dark), empty Home / Reader / Settings screens, state-based navigation
- [x] ktlint (Spotless), Android lint, unit test setup
- [ ] GitHub Actions: build, test, lint on every PR (written, not yet run on GitHub)
- [x] README, LICENSE, `.gitignore`

## M1 — RSVP core (with built-in sample text)
- [x] `Book` model and engine: frames, ORP, timing, ramp-up
- [x] Unit tests for ORP, timing, frames and navigation
- [x] Reader screen: focal-point word rendering, play/pause, paused overlay
- [x] Speed control and context-words toggle

*Goal: the reading experience feels right before any file handling exists.*

## M2 — EPUB and library
- [x] Open file via system picker, persist URI permission; replace `SampleBook`
- [x] Reader view model scoped to the reader screen
- [x] EPUB parser incl. ToC-based heading detection, with tests (also checked on Gutenberg and Standard Ebooks books)
- [x] Parsed-book cache
- [x] Library store: recent books, reading positions, resume
- [x] Home screen: continue card, recent list, remove
- [x] Verify on a device: picker, loading, resume after restart, removing, error messages

## M3 — PDF
- [x] PdfBox-Android text extraction with font sizes
- [x] Heading detection, header/footer and page-number removal, de-hyphenation
- [x] "No text found" handling; progress indicator on first parse
- [x] Verify on a device: papers, a dissertation, a 150 MB report, a designed book, encrypted and text-less files

## M4 — Settings and polish
- [ ] Settings screen and persistence
- [ ] Error states: missing file, lost permission, corrupt book
- [ ] Accessibility: TalkBack labels, font scaling, contrast
- [ ] App icon, APK size check

## M5 — Release 1.0
- [ ] Test on a range of real books and devices
- [ ] Signed release build, F-Droid-friendly (no proprietary dependencies)
- [ ] Changelog and tagged release
