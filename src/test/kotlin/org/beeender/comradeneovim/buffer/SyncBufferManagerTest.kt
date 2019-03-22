package org.beeender.comradeneovim.buffer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
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

        val all = SyncBufferManager.listAllBuffers()
        assertTrue(all.contains(buf))
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
        assertTrue(buf.isReleased)
        val all = SyncBufferManager.listAllBuffers()
        assertFalse(all.contains(buf))
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

    fun test_listener_createAndRelease() {
        val listener = spyk<SyncBufferManagerListener>()
        val busConnection = ApplicationManager.getApplication().messageBus.connect()
        busConnection.subscribe(SyncBufferManager.TOPIC, listener)

        verify(exactly = 0) { listener.bufferCreated(any()) }
        verify(exactly = 0) { listener.bufferReleased(any()) }

        bufferManger.loadBuffer(1, vf.path)
        val buf = bufferManger.findBufferById(1)!!
        verify(exactly = 1) { listener.bufferCreated(buf) }

        bufferManger.releaseBuffer(buf)
        verify(exactly = 1) { listener.bufferReleased(buf) }

        busConnection.disconnect()
    }

    fun test_allBuffers() {
        val nvimInstance2 = mockedNvimInstance()
        val bufferManger2 = SyncBufferManager(nvimInstance2)

        bufferManger.loadBuffer(1, vf.path)
        bufferManger2.loadBuffer(2, vf.path)

        val buf1 = bufferManger.findBufferById(1)!!
        val buf2 = bufferManger2.findBufferById(2)!!

        var all = SyncBufferManager.listAllBuffers()
        assertEquals(2, all.size)
        assertTrue(all.contains(buf1))
        assertTrue(all.contains(buf2))

        bufferManger.releaseBuffer(buf1)
        all = SyncBufferManager.listAllBuffers()
        assertFalse(all.contains(buf1))
        bufferManger2.releaseBuffer(buf2)
        all = SyncBufferManager.listAllBuffers()
        assertFalse(all.contains(buf2))
    }
}