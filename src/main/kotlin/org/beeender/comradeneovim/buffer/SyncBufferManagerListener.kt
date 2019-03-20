package org.beeender.comradeneovim.buffer

import java.util.*

interface SyncBufferManagerListener : EventListener {
    fun bufferCreated(syncBuffer: SyncBuffer) {

    }

    fun bufferReleased(syncBuffer: SyncBuffer) {

    }

    /**
     * Triggered when content of both sides (JetBrain and Nvim) get synced.
     */
    fun bufferSynced(syncBuffer: SyncBuffer) {
    }
}