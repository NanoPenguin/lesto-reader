package io.github.nanopenguin.lesto.library

import androidx.datastore.core.DataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var now = 0L

    private fun TestScope.store() = LibraryStore(
        DataStoreFactory.create(LibrarySerializer, scope = backgroundScope) { folder.root.resolve("library.json") },
        clock = { now },
    )

    @Test
    fun `remembers positions and lists the most recent book first`() = runTest {
        val store = store()
        now = 1
        assertEquals(0, store.open("a", "Book A", "Author", frameCount = 100))
        store.savePosition("a", 40)
        now = 2
        store.open("b", "Book B", null, frameCount = 10)

        assertEquals(listOf("b", "a"), store.entries.first().map { it.uri })
        now = 3
        assertEquals(40, store.open("a", "Book A", "Author", frameCount = 100))
        assertEquals(listOf("a", "b"), store.entries.first().map { it.uri })
    }

    @Test
    fun `removed books are forgotten`() = runTest {
        val store = store()
        store.open("a", "Book A", null, frameCount = 100)
        store.savePosition("a", 40)
        store.remove("a")

        assertEquals(emptyList<LibraryEntry>(), store.entries.first())
        assertEquals(0, store.open("a", "Book A", null, frameCount = 100))
    }

    @Test
    fun `positions move proportionally when the frame count changes`() {
        assertEquals(50, restoredPosition(50, savedFrameCount = 101, frameCount = 101))
        assertEquals(100, restoredPosition(50, savedFrameCount = 101, frameCount = 201))
        assertEquals(9, restoredPosition(50, savedFrameCount = 51, frameCount = 10))
        assertEquals(0, restoredPosition(5, savedFrameCount = 10, frameCount = 0))
    }
}
