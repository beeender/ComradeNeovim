package org.beeender.neovim

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

interface NeovimConnection: Closeable {
    val inputStream: InputStream
    val outputStream: OutputStream
}