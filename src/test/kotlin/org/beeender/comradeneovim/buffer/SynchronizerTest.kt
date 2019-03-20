package org.beeender.comradeneovim.buffer

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import io.mockk.*
import org.beeender.comradeneovim.buffer.SyncBufferManager
import org.beeender.comradeneovim.buffer.Synchronizer
import org.beeender.comradeneovim.core.NvimInstance

class SynchronizerTest : LightCodeInsightFixtureTestCase() {
    private lateinit var vf: VirtualFile
    private lateinit var nvimInstance: NvimInstance

    override fun setUp() {
        super.setUp()
        nvimInstance = mockedNvimInstance()
        mockkConstructor(Synchronizer::class)

        vf = myFixture.copyFileToProject("empty.java")
    }

    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    override fun getTestDataPath(): String {
        return "tests/testData"
    }

    fun test_throwsIn_OnJetBrainChanges() {
        every { anyConstructed<Synchronizer>().initFromJetBrain() } just Runs
        every { anyConstructed<Synchronizer>().onJetBrainChange(any()) } throws IllegalArgumentException()
        vf = myFixture.copyFileToProject("empty.java")
        val bufferManger = SyncBufferManager(nvimInstance)
        bufferManger.loadBuffer(1, vf.path)
        val buf = bufferManger.findBufferById(1)!!
        buf.setText("abc")
        assertTrue(buf.isReleased())
        assertNull(bufferManger.findBufferById(buf.id))
    }
}