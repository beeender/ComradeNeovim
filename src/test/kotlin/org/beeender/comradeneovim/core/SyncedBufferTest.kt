package org.beeender.comradeneovim.core

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.beeender.neovim.BufLinesEvent
import org.junit.Test

class SyncedBufferTest : LightCodeInsightFixtureTestCase() {
    private lateinit var vf: VirtualFile
    private lateinit var buf: SyncedBuffer

    override fun setUp() {
        super.setUp()
        vf = myFixture.copyFileToProject("empty.java")
        buf = SyncedBuffer(0, vf.url)
    }

    override fun tearDown() {
        buf.close()
        super.tearDown()
    }

    override fun getTestDataPath(): String {
        return "tests/testData"
    }

    @Test
    fun test_onBufferChangedTest_newEmptyBuffer() {
        val event = BufLinesEvent(0, 0, 0, -1, emptyList(), false)
        buf.onBufferChanged(event)

        UsefulTestCase.assertEmpty(buf.text)
    }

    @Test
    fun test_onBufferChangedTest_insertEOL() {
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a"), false)
        buf.onBufferChanged(ev1)
        UsefulTestCase.assertEquals("a", buf.text)

        val ev2= BufLinesEvent(0, 1, 0, 1, listOf("a", ""), false)
        buf.onBufferChanged(ev2)
        UsefulTestCase.assertEquals("a\n", buf.text)
    }

    @Test
    fun test_onBufferChangedTest_removeInTheMiddle() {
        // a
        // b
        // c
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a", "b", "c"), false)
        buf.onBufferChanged(ev1)
        UsefulTestCase.assertEquals("a\nb\nc", buf.text)

        // a
        // b {dd}
        // c
        val ev2= BufLinesEvent(0, 1, 1, 3, listOf("c"), false)
        buf.onBufferChanged(ev2)
        UsefulTestCase.assertEquals("a\nc", buf.text)
    }

    @Test
    fun test_onBufferChangedTest_removeMultipleLinesInTheMiddle() {
        // a
        // b
        // c
        // d
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a", "b", "c", "d"), false)
        buf.onBufferChanged(ev1)
        UsefulTestCase.assertEquals("a\nb\nc\nd", buf.text)

        // a
        // {2dd}
        // d
        val ev2= BufLinesEvent(0, 1, 1, 3, listOf(), false)
        buf.onBufferChanged(ev2)
        UsefulTestCase.assertEquals("a\nd", buf.text)
    }

    @Test
    fun test_onBufferChangedTest_removeAll() {
        // a
        // b
        // c
        // d
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a", "b", "c", "d"), false)
        buf.onBufferChanged(ev1)
        UsefulTestCase.assertEquals("a\nb\nc\nd", buf.text)

        // {4dd}
        val ev2= BufLinesEvent(0, 1, 0, 4, listOf(), false)
        buf.onBufferChanged(ev2)
        UsefulTestCase.assertEquals("", buf.text)
    }

    @Test
    fun test_onBufferChangedTest_removeMultipleLinesAtTheEnd() {
        // a
        // b
        // c
        // d
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a", "b", "c", "d"), false)
        buf.onBufferChanged(ev1)
        UsefulTestCase.assertEquals("a\nb\nc\nd", buf.text)

        // a
        // b
        // {2dd}
        val ev2= BufLinesEvent(0, 1, 2, 4, listOf(), false)
        buf.onBufferChanged(ev2)
        UsefulTestCase.assertEquals("a\nb", buf.text)
    }

    @Test
    fun test_onBufferChanged_insertAtTheFirstLine() {
        // b
        // c
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("b", "c"), false)
        buf.onBufferChanged(ev1)
        UsefulTestCase.assertEquals("b\nc", buf.text)

        // a
        // b
        // c
        val ev2= BufLinesEvent(0, 0, 0, 0, listOf("a"), false)
        buf.onBufferChanged(ev2)
        UsefulTestCase.assertEquals("a\nb\nc", buf.text)
    }

    @Test
    fun test_onBufferChanged_insertAtTheLastLine() {
        // a
        // b
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a", "b"), false)
        buf.onBufferChanged(ev1)
        UsefulTestCase.assertEquals("a\nb", buf.text)

        // a
        // b
        // c
        val ev2= BufLinesEvent(0, 0, 2, 2, listOf("c"), false)
        buf.onBufferChanged(ev2)
        UsefulTestCase.assertEquals("a\nb\nc", buf.text)
    }

    @Test
    fun test_onBufferChanged_insertInTheMiddle() {
        // a
        // c
        val ev1= BufLinesEvent(0, 0, 0, -1, listOf("a", "c"), false)
        buf.onBufferChanged(ev1)
        UsefulTestCase.assertEquals("a\nc", buf.text)

        // a
        // b
        // c
        val ev2= BufLinesEvent(0, 1, 1, 1, listOf("b"), false)
        buf.onBufferChanged(ev2)
        UsefulTestCase.assertEquals("a\nb\nc", buf.text)
    }

    @Test
    fun test_onBufferChanged_replace() {
        // a
        // b
        // c
        val ev1= BufLinesEvent(0, 0, 0, 1, listOf("a", "b", "c"), false)
        buf.onBufferChanged(ev1)
        UsefulTestCase.assertEquals("a\nb\nc", buf.text)

        // a!
        // b
        // c
        val ev2= BufLinesEvent(0, 1, 0, 1, listOf("a!"), false)
        buf.onBufferChanged(ev2)
        UsefulTestCase.assertEquals("a!\nb\nc", buf.text)

        // a!
        // b!
        // c
        val ev3= BufLinesEvent(0, 1, 1, 2, listOf("b!"), false)
        buf.onBufferChanged(ev3)
        UsefulTestCase.assertEquals("a!\nb!\nc", buf.text)

        // a!
        // b!
        // c!
        val ev4= BufLinesEvent(0, 1, 2, 3, listOf("c!"), false)
        buf.onBufferChanged(ev4)
        UsefulTestCase.assertEquals("a!\nb!\nc!", buf.text)
    }
}
