package org.beeender.comradeneovim.buffer

import io.mockk.*
import org.beeender.comradeneovim.buffer.Synchronizer
import org.beeender.comradeneovim.core.NvimInstance

fun mockSynchronizerClass() {
    mockkConstructor(Synchronizer::class)
    every { anyConstructed<Synchronizer>().initFromJetBrain() } just Runs
    every { anyConstructed<Synchronizer>().onJetBrainChange(any()) } just Runs
}

fun mockedNvimInstance() : NvimInstance {
   return  mockk(relaxed = true)
}

