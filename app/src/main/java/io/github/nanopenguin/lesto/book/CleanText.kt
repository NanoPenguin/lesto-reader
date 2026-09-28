package io.github.nanopenguin.lesto.book

private val Whitespace = Regex("\\s+")

/** Normalises whitespace and removes invisible characters that would break words apart or glue them together. */
internal fun cleanText(text: String): String = text
    .replace('\u00A0', ' ')
    .replace('\u2007', ' ')
    .replace('\u202F', ' ')
    .replace("\u00AD", "")
    .replace("\u200B", "")
    .replace("\uFEFF", "")
    .replace(Whitespace, " ")
    .trim()
