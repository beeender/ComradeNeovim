package org.beeender.comradeneovim.core

import io.mockk.*

fun mockSynchronizerClass() {
    mockkConstructor(Synchronizer::class)
    every { anyConstructed<Synchronizer>().initFromJetBrain() } just Runs
    every { anyConstructed<Synchronizer>().onJetBrainChange(any()) } just Runs
}

fun mockedNvimInstance() :NvimInstance {
   return  mockk(relaxed = true)
}

