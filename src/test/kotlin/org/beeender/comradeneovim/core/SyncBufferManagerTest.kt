package org.beeender.comradeneovim.core

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.beeender.neovim.BufDetachEvent

class SyncBufferManagerTest : LightCodeInsightFixtureTestCase() {
    @MockK(relaxed = true)
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
        val params = ComradeBufEnterParams(1, vf.path)
        val notification = ComradeBufEnterParams.toNotification(params)
        bufferManger.comradeBufEnter(notification)
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
}