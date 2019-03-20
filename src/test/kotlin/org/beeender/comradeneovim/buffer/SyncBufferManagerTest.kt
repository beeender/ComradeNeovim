package org.beeender.comradeneovim.buffer

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import io.mockk.unmockkAll
import org.beeender.comradeneovim.buffer.SyncBufferManager
import org.beeender.comradeneovim.core.ComradeBufEnterParams
import org.beeender.comradeneovim.core.ComradeBufWriteParams
import org.beeender.comradeneovim.core.NvimInstance
import org.beeender.neovim.BufChangedtickEvent
import org.beeender.neovim.BufDetachEvent
import org.beeender.neovim.BufLinesEvent

class SyncBufferManagerTest : LightCodeInsightFixtureTestCase() {
    private lateinit var nvimInstance: NvimInstance
    private lateinit var vf: VirtualFile
    private lateinit var bufferManger: SyncBufferManager

    override fun setUp() {
        super.setUp()
        nvimInstance = mockedNvimInstance()
        mockSynchronizerClass()

        vf = myFixture.copyFileToProject("empty.java")
        bufferManger = SyncBufferManager(nvimInstance)
    }

    override fun tearDown() {
        bufferManger.cleanUp(myFixture.project)
        unmockkAll()
        super.tearDown()
    }

    override fun getTestDataPath(): String {
        return "tests/testData"
    }

    fun test_loadBuffer() {
        bufferManger.loadBuffer(1, vf.path)
        val buf = bufferManger.findBufferById(1)
        assertNotNull(buf)
        assertEquals(buf!!.id,  1)
    }

    fun test_comradeBufEnter() {
        bufferManger.comradeBufEnter(ComradeBufEnterParams(1, vf.path))
        val buf = bufferManger.findBufferById(1)
        assertNotNull(buf)
        assertEquals(buf!!.id,  1)
    }

    fun test_nvimBufferDetachEvent() {
        bufferManger.loadBuffer(1, vf.path)
        val buf = bufferManger.findBufferById(1)!!
        bufferManger.nvimBufDetachEvent(BufDetachEvent(1))
        assertNull(bufferManger.findBufferById(buf.id))
        assertTrue(buf.isReleased())
    }

    fun test_nvimBufLines() {
        bufferManger.loadBuffer(1, vf.path)
        val buf = bufferManger.findBufferById(1)!!
        bufferManger.nvimBufLinesEvent(BufLinesEvent(buf.id, 1,
                0, -1, listOf("foo", "bar"), false))
        assertEquals(buf.document.text, "foo\nbar")
        assertEquals(buf.synchronizer.changedtick, 1)
    }

    fun test_nvimBufChangedtick() {
        bufferManger.loadBuffer(1, vf.path)
        val buf = bufferManger.findBufferById(1)!!
        bufferManger.nvimBufChangedtickEvent(BufChangedtickEvent(buf.id, 42))
        assertEquals(buf.synchronizer.changedtick, 42)
    }

    fun test_comradeBufWrite() {
        bufferManger.loadBuffer(1, vf.path)
        val buf = bufferManger.findBufferById(1)!!

        val docMan = FileDocumentManager.getInstance()

        buf.setText("42")
        assertTrue(docMan.isDocumentUnsaved(buf.document))
        bufferManger.comradeBufWrite(ComradeBufWriteParams(buf.id))
        assertFalse(docMan.isDocumentUnsaved(buf.document))
    }
}