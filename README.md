# Pace

A minimal RSVP reader for Android. Pace shows your EPUB and PDF books one word at a time,
around a fixed focal point, so you can read faster without moving your eyes.

- Opens EPUB and PDF files from your device
- Adjustable reading speed, optional context words
- Chapter headings shown distinctly
- Remembers where you left off
- No permissions, no network, no tracking

**Status:** early development, see [docs/PLAN.md](docs/PLAN.md).

## Building

Requires the Android SDK and Java 25 (Android Studio includes both; Gradle downloads Java if missing).

```
./gradlew assembleDebug
```

## Contributing

Read [AGENTS.md](AGENTS.md) for conventions and [docs/DESIGN.md](docs/DESIGN.md) for scope.
Run `./gradlew spotlessApply lint test` before opening a pull request.

## License

[MIT](LICENSE). The bundled Atkinson Hyperlegible Next font is licensed under the
[SIL Open Font License](licenses/AtkinsonHyperlegibleNext-OFL.txt), and the Material Symbols icons
under the [Apache License 2.0](licenses/MaterialSymbols-Apache-2.0.txt). Libraries and their licenses
are listed in the app under Settings › Third-party licenses.
