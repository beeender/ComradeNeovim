package org.beeender.comradeneovim.core

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase.assertThrows
import io.mockk.unmockkAll
import junit.framework.Assert
import org.beeender.neovim.BufLinesEvent
import org.junit.Test

class SyncBufferTest : LightCodeInsightFixtureTestCase() {
    private lateinit var vf: VirtualFile
    private lateinit var buf: SyncBuffer
    private lateinit var nvimInstance: NvimInstance
    private lateinit var synchronizer: Synchronizer

    override fun setUp() {
        super.setUp()
        nvimInstance = mockedNvimInstance()
        mockSynchronizerClass()

        vf = myFixture.copyFileToProject("empty.java")
        buf = SyncBuffer(0, vf.path, nvimInstance)
        synchronizer = Synchronizer(buf)
        buf.attachSynchronizer(synchronizer)
    }

    override fun tearDown() {
        buf.release()
        unmockkAll()
        super.tearDown()
    }

    override fun getTestDataPath(): String {
        return "tests/testData"
    }

    private fun runOnChange(event: BufLinesEvent) {
        val change = BufferChange.NeovimChangeBuilder(buf, event).build()
        buf.synchronizer.onChange(change)
    }

    @Test
    fun test_onBufferChangedTest_newEmptyBuffer() {
        val event = BufLinesEvent(0, 0, 0, -1, emptyList(), false)
        runOnChange(event)

        UsefulTestCase.assertEmpty(buf.text)
    }

    @Test
    fun test_onBufferChangedTest_insertEOL() {
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a"), false)
        runOnChange(ev1)
        UsefulTestCase.assertEquals("a", buf.text)

        val ev2= BufLinesEvent(0, 1, 0, 1, listOf("a", ""), false)
        runOnChange(ev2)
        UsefulTestCase.assertEquals("a\n", buf.text)
    }

    @Test
    fun test_onBufferChangedTest_removeInTheMiddle() {
        // a
        // b
        // c
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a", "b", "c"), false)
        runOnChange(ev1)
        UsefulTestCase.assertEquals("a\nb\nc", buf.text)

        // a
        // b {dd}
        // c
        val ev2= BufLinesEvent(0, 1, 1, 3, listOf("c"), false)
        runOnChange(ev2)
        UsefulTestCase.assertEquals("a\nc", buf.text)
    }

    @Test
    fun test_onBufferChangedTest_removeMultipleLinesInTheMiddle() {
        // a
        // b
        // c
        // d
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a", "b", "c", "d"), false)
        runOnChange(ev1)
        UsefulTestCase.assertEquals("a\nb\nc\nd", buf.text)

        // a
        // {2dd}
        // d
        val ev2= BufLinesEvent(0, 1, 1, 3, listOf(), false)
        runOnChange(ev2)
        UsefulTestCase.assertEquals("a\nd", buf.text)
    }

    @Test
    fun test_onBufferChangedTest_removeAll() {
        // a
        // b
        // c
        // d
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a", "b", "c", "d"), false)
        runOnChange(ev1)
        UsefulTestCase.assertEquals("a\nb\nc\nd", buf.text)

        // {4dd}
        val ev2= BufLinesEvent(0, 1, 0, 4, listOf(), false)
        runOnChange(ev2)
        UsefulTestCase.assertEquals("", buf.text)
    }

    @Test
    fun test_onBufferChangedTest_removeMultipleLinesAtTheEnd() {
        // a
        // b
        // c
        // d
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a", "b", "c", "d"), false)
        runOnChange(ev1)
        UsefulTestCase.assertEquals("a\nb\nc\nd", buf.text)

        // a
        // b
        // {2dd}
        val ev2= BufLinesEvent(0, 1, 2, 4, listOf(), false)
        runOnChange(ev2)
        UsefulTestCase.assertEquals("a\nb", buf.text)
    }

    @Test
    fun test_onBufferChanged_insertAtTheFirstLine() {
        // b
        // c
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("b", "c"), false)
        runOnChange(ev1)
        UsefulTestCase.assertEquals("b\nc", buf.text)

        // a
        // b
        // c
        val ev2= BufLinesEvent(0, 1, 0, 0, listOf("a"), false)
        runOnChange(ev2)
        UsefulTestCase.assertEquals("a\nb\nc", buf.text)
    }

    @Test
    fun test_onBufferChanged_insertAtTheLastLine() {
        // a
        // b
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a", "b"), false)
        runOnChange(ev1)
        UsefulTestCase.assertEquals("a\nb", buf.text)

        // a
        // b
        // c
        val ev2= BufLinesEvent(0, 1, 2, 2, listOf("c"), false)
        runOnChange(ev2)
        UsefulTestCase.assertEquals("a\nb\nc", buf.text)
    }

    @Test
    fun test_onBufferChanged_insertInTheMiddle() {
        // a
        // c
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a", "c"), false)
        runOnChange(ev1)
        UsefulTestCase.assertEquals("a\nc", buf.text)

        // a
        // b
        // c
        val ev2= BufLinesEvent(0, 1, 1, 1, listOf("b"), false)
        runOnChange(ev2)
        UsefulTestCase.assertEquals("a\nb\nc", buf.text)
    }

    @Test
    fun test_onBufferChanged_replace() {
        // a
        // b
        // c
        val ev1= BufLinesEvent(0, 0, 0, 1, listOf("a", "b", "c"), false)
        runOnChange(ev1)
        UsefulTestCase.assertEquals("a\nb\nc", buf.text)

        // a!
        // b
        // c
        val ev2= BufLinesEvent(0, 1, 0, 1, listOf("a!"), false)
        runOnChange(ev2)
        UsefulTestCase.assertEquals("a!\nb\nc", buf.text)

        // a!
        // b!
        // c
        val ev3= BufLinesEvent(0, 2, 1, 2, listOf("b!"), false)
        runOnChange(ev3)
        UsefulTestCase.assertEquals("a!\nb!\nc", buf.text)

        // a!
        // b!
        // c!
        val ev4= BufLinesEvent(0, 3, 2, 3, listOf("c!"), false)
        runOnChange(ev4)
        UsefulTestCase.assertEquals("a!\nb!\nc!", buf.text)
    }

    @Test
    fun test_onBufferChanged_replaceWithBlankLine() {
        // a
        // b
        // c
        val ev1 = BufLinesEvent(0, 0, 0, 1, listOf("a", "b", "c"), false)
        runOnChange(ev1)
        UsefulTestCase.assertEquals("a\nb\nc", buf.text)

        // (blank line)
        // b
        // c
        val ev2 = BufLinesEvent(0, 1, 0, 1, listOf(""), false)
        runOnChange(ev2)
        UsefulTestCase.assertEquals("\nb\nc", buf.text)

        // (blank line)
        // (blank line)
        // c
        val ev3 = BufLinesEvent(0, 2, 1, 2, listOf(""), false)
        runOnChange(ev3)
        UsefulTestCase.assertEquals("\n\nc", buf.text)

        // (blank line)
        // (blank line)
        // (blank line)
        val ev4 = BufLinesEvent(0, 3, 2, 3, listOf(""), false)
        runOnChange(ev4)
        UsefulTestCase.assertEquals("\n\n", buf.text)
    }

    @Test
    fun test_onBufferChanged_removeFirstBlankLine() {
        // (blank line)
        // b
        // c
        val ev1 = BufLinesEvent(0, 0, 0, -1, listOf("", "b", "c"), false)
        runOnChange(ev1)
        UsefulTestCase.assertEquals("\nb\nc", buf.text)

        // b
        // c
        val ev2 = BufLinesEvent(0, 1, 0, 1, listOf(), false)
        runOnChange(ev2)
        UsefulTestCase.assertEquals("b\nc", buf.text)
    }

    @Test
    fun test_onBufferChange_mismatchChangedtickThrows() {
        synchronizer.exceptionHandler = { _ -> fail() }
        val ev1 = BufLinesEvent(0, 0, 0, -1, listOf("", "b", "c"), false)
        runOnChange(ev1)
        UsefulTestCase.assertEquals("\nb\nc", buf.text)

        var called = false
        synchronizer.exceptionHandler = { t ->
            assertTrue(t is BufferOutOfSyncException)
            called = true
        }
        val ev2 = BufLinesEvent(0, 2, 0, 1, listOf(), false)
        runOnChange(ev2)
        assertTrue(called)
    }
}
