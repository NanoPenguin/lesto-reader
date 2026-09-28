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
- Tap: play / pause. Swiping pauses playback.
- Paused: the text becomes a page with one sentence per line. The current word sits at the focal point; neighbouring sentences are dimmed above and below, headings in semi-bold with extra space above, as chapter separators. The page is one rigid sheet: all lines start at the same x and lie a fixed distance apart.
  - Drag in any direction: the sheet follows the finger, and keeps moving when flung. Like scrolling, dragging right or down goes back. The word nearest the focal point is highlighted as it changes, with a light tick: on the nearest line, the word closest to the focal point, or its last or first word when the focal point is beyond the line's end or start.
  - On release, the sheet glides so that the highlighted word sits on the focal point, and its focal letter is marked.
- Paused controls: chapter title (opens the chapter list), a slider over the current chapter (the whole book if it has no headings), "Chapter 3 of 24 · 38%", minutes left in the chapter, speed −/+ (steps of 25 wpm), context-words toggle, close.
- Screen stays on while playing. Playback pauses when the app leaves the foreground.
- A book that cannot be opened says why: moved or deleted, no longer accessible (some providers drop the access of deleted files, so this message names both), not a readable book, or no text. If it is in the library, it can be removed from there.

**Settings**
- Theme: System / Light / Dark.
- Text size of the word being read: Small / Medium / Large (32 / 40 / 48 sp).
- Show context words (same toggle as in the reader).
- Reading speed (same value as in the reader).
- About: version, license, source link, third-party licenses.

### Visual language

Monochrome surfaces, generous whitespace, one accent colour, and one typeface: Atkinson Hyperlegible Next, bundled, chosen for letter distinctness at speed. The accent is used only for the focal letter and primary actions. The app icon is the focal point: an accent "o" between the two focal guides.

Text meets WCAG AA contrast, except the deliberately dimmed context words and neighbouring lines. Every action is reachable with TalkBack, including play / pause, and layouts hold up at the largest font scale.

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

Zip → `META-INF/container.xml` → OPF (metadata, manifest, spine) → each linear spine document in order, parsed with Jsoup as XML (HTML as fallback).
- `h1`–`h6` become `Heading`s; an `hgroup` becomes one heading ("I: Down the Rabbit-Hole").
- Table of contents targets (EPUB 3 nav or EPUB 2 NCX) are headings too. A target at a container or document start waits for the first text: if it is short and matches the entry's label it becomes the heading, otherwise the label is inserted as one.
- Block-level elements (`p`, `div`, `li`, `blockquote`, …) become `Paragraph`s. Images, scripts, styles, captions, footnotes and footnote markers are dropped.

### PDF parsing

Uses PdfBox-Android to extract upright text line by line, with font sizes and positions; `PdfLayout` (pure Kotlin) infers the structure.
- Headings: from the PDF outline when it has at least three entries, matched to the printed title nearest its destination, or inserted there. Otherwise, runs of up to three lines set clearly larger than the body size (the most common one), ranked by size. Using both would find most headings twice.
- Removed: among the two top and bottom lines of each page, bare page numbers, lines repeated on three or more pages (digits ignored), and lines starting or ending with the printed page number.
- Paragraphs are split on larger vertical gaps, first-line indents after a sentence end, and short lines ending a sentence; hyphenated line breaks are joined.
- The first parse reports progress per page. A PDF without a text layer shows a clear "no text found" message; password-protected ones cannot be opened.

### RSVP engine (pure Kotlin, no Android)

- **Frames.** A paragraph becomes one frame per word (punctuation stays attached). A heading becomes one frame showing the whole heading, centred, without a focal letter.
- **Focal point (ORP).** The pivot letter index depends on word length: 1 → 0, 2–5 → 1, 6–9 → 2, 10–13 → 3, longer → 4. The pivot is drawn at a fixed horizontal position (slightly left of centre) in the accent colour.
- **Timing.** Measured in word-times of `60 000 / wordsPerMinute` ms. A word takes 1 (1.3 if longer than 8 letters), a heading 2 per word and at least 1.5 s. Pauses add:
  | After | Extra word-times |
  |---|---|
  | `,` `;` `:` `–` `—` | 0.5 |
  | End of sentence | 1.0 |
  | End of paragraph | 1.5 |
  | Heading | 2.0 |
  After pressing play, the first five frames ease in from half speed.
- **Position.** The position is the frame index, saved with the book's frame count once it has been still for a second. If the count differs when the book is reopened (e.g. after a parser update), the position is restored proportionally.
- **Context words.** When enabled, neighbouring words are laid out on the same line around the current word, dimmed and fading towards the edges.

### Storage

- **Files:** Storage Access Framework with persistable URI permissions. No storage permission.
- **Settings:** DataStore holding JSON (kotlinx.serialization) of `{theme, textSize, showContext, wordsPerMinute}`; the reader reads and saves speed and context words there.
- **Library:** DataStore holding a JSON list (kotlinx.serialization) of `{uri, title, author, position, frameCount, lastOpened}`. Removing a book also releases its file permission and cache.
- A damaged settings or library file starts over with defaults rather than failing on every start.
- **Parsed-book cache:** JSON of `Book` in the app cache directory, one file per URI, valid while parser version, file size and modification time match.

### Dependencies

| Library | Why |
|---|---|
| AndroidX Compose, Material 3, Activity, Lifecycle | UI |
| AndroidX DataStore | settings and library |
| kotlinx.serialization | settings, library and cache format |
| Jsoup | tolerant (X)HTML parsing for EPUB |
| PdfBox-Android | PDF text extraction with font information (Apache-2.0) |

Navigation between the three screens is a small state holder with `BackHandler`; no navigation library. The reader's view model is scoped to the reader screen (`rememberViewModelStoreOwner`), so a closed book is released.
