# Design — Pace

## Product

An RSVP reader that feels calm and premium: one word at a time, a fixed focal point, nothing else competing for attention.

### Scope

In:
- Open EPUB and PDF (with a text layer) via the system file picker.
- Word-by-word playback with adjustable speed.
- Optional context words shown dimmed around the current word.
- Headings shown distinctly from body text.
- Remember the reading position of every opened book.
- Light / dark / system theme.

Out (for now): OCR of scanned PDFs, cloud sync, bookmarks, notes, statistics, cover art, other formats, tablet-specific layouts.

### Screens

**Home** — the menu shown on launch.
- *Continue reading* card: last book, title, author, progress. One tap resumes.
- *Open book* button (system picker, `.epub` / `.pdf`).
- Recent books list (title, author, %). Long-press to remove.
- Settings icon.

**Reader**
- Playing: only the word, its focal guides, and a hairline progress bar.
- Tap anywhere to pause / play.
- Paused overlay: chapter title, time left, seekable progress bar, speed −/+ (steps of 25 wpm), context-words toggle, "back one sentence", close.
- Screen stays on while playing. Playback pauses when the app leaves the foreground.

**Settings**
- Theme: System / Light / Dark.
- Text size: Small / Medium / Large.
- Show context words (same toggle as in the reader).
- Reading speed (same value as in the reader).
- About: version, license, source link, third-party licenses.

### Visual language

Monochrome surfaces, generous whitespace, one accent colour, and one typeface: Atkinson Hyperlegible Next, bundled, chosen for letter distinctness at speed. The accent is used only for the focal letter and primary actions.

## Technical design

### Pipeline

```
Uri ──parser──▶ Book (blocks) ──engine──▶ Frames ──UI──▶ screen
```

**Book model** — the output of every parser:

```kotlin
data class Book(val title: String, val author: String?, val blocks: List<Block>)
sealed interface Block {
    data class Heading(val text: String, val level: Int) : Block
    data class Paragraph(val text: String) : Block
}
```

Parsers only produce blocks. Everything about display and timing belongs to the engine.

### EPUB parsing

Zip → `META-INF/container.xml` → OPF (metadata, manifest, spine) → each spine XHTML in order, parsed with Jsoup.
- `h1`–`h6` become `Heading`s. Elements targeted by the table of contents (EPUB 3 nav or EPUB 2 NCX) are also treated as headings, which catches books that style chapter titles as `<p class="...">`.
- Block-level elements (`p`, `div`, `li`, `blockquote`, …) become `Paragraph`s. Images, scripts and styles are dropped.

### PDF parsing

Uses PdfBox-Android to extract text with font sizes, line by line.
- Headings: short lines whose font size is clearly above the body median, plus titles from the PDF outline when present.
- Removed: running headers/footers (lines repeated at the same position across pages) and bare page numbers.
- Hyphenated line breaks are joined; paragraphs are split on larger vertical gaps.
- A PDF without a text layer shows a clear "no text found" message.

### RSVP engine (pure Kotlin, no Android)

- **Frames.** A paragraph becomes one frame per word (punctuation stays attached). A heading becomes one frame showing the whole heading, centred, without a focal letter.
- **Focal point (ORP).** The pivot letter index depends on word length: 1 → 0, 2–5 → 1, 6–9 → 2, 10–13 → 3, longer → 4. The pivot is drawn at a fixed horizontal position (slightly left of centre) in the accent colour.
- **Timing.** Measured in word-times of `60 000 / wordsPerMinute` ms. A word takes 1 (1.3 if longer than 8 letters), a heading 1 per word. Pauses add:
  | After | Extra word-times |
  |---|---|
  | `,` `;` `:` `–` `—` | 0.5 |
  | End of sentence | 1.0 |
  | End of paragraph | 1.5 |
  | Heading | 2.0 |
  After pressing play, the first five frames ease in from half speed.
- **Position.** The position is the frame index. It is stored together with a parser version; if the version changed, the position is restored proportionally.
- **Context words.** When enabled, neighbouring words are laid out on the same line around the current word, dimmed and fading towards the edges.

### Storage

- **Files:** Storage Access Framework with persistable URI permissions. No storage permission.
- **Settings:** DataStore (Preferences).
- **Library:** DataStore holding a JSON list (kotlinx.serialization) of `{uri, title, author, position, frameCount, lastOpened}`.
- **Parsed-book cache:** JSON of `Book` in the app cache directory, keyed by URI + size + last modified. Large PDFs are parsed once.

### Dependencies

| Library | Why |
|---|---|
| AndroidX Compose, Material 3, Activity, Lifecycle | UI |
| AndroidX DataStore | settings and library |
| kotlinx.serialization | library and cache format |
| Jsoup | tolerant (X)HTML parsing for EPUB |
| PdfBox-Android | PDF text extraction with font information (Apache-2.0) |

Navigation between the three screens is a small state holder with `BackHandler`; no navigation library.
