package io.github.nanopenguin.pace.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class FocalPointTest {
    @Test
    fun `focal letter moves right as words get longer`() {
        assertEquals(0, focalIndex("a"))
        assertEquals(1, focalIndex("to"))
        assertEquals(1, focalIndex("reads"))
        assertEquals(2, focalIndex("reader"))
        assertEquals(3, focalIndex("remarkable"))
        assertEquals(3, focalIndex("conversations"))
        assertEquals(4, focalIndex("extraordinarily"))
        assertEquals(4, focalIndex("incomprehensibilities"))
    }

    @Test
    fun `surrounding punctuation is ignored`() {
        assertEquals(2, focalIndex("“and"))
        assertEquals(1, focalIndex("book,”"))
        assertEquals(2, focalIndex("(she"))
    }

    @Test
    fun `words without letters use the first character`() {
        assertEquals(0, focalIndex("—"))
        assertEquals(0, focalIndex(""))
    }
}
