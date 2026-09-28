package io.github.nanopenguin.pace.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BookCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val book = Book("Title", null, listOf(Block.Heading("One", 1), Block.Paragraph("Text.")))

    @Test
    fun `returns a book only for the source it was parsed from`() {
        val cache = BookCache(folder.root)
        cache.write("content://book", "v1", book)

        assertEquals(book, cache.read("content://book", "v1"))
        assertNull(cache.read("content://book", "v2"))
        assertNull(cache.read("content://other", "v1"))
    }

    @Test
    fun `deleted and corrupt entries are ignored`() {
        val cache = BookCache(folder.root)
        cache.write("content://book", "v1", book)
        cache.delete("content://book")
        assertNull(cache.read("content://book", "v1"))

        cache.write("content://book", "v1", book)
        folder.root.listFiles()!!.single().writeText("{ not json")
        assertNull(cache.read("content://book", "v1"))
    }
}
