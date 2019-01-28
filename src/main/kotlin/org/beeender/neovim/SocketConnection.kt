package org.beeender.neovim

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class SocketConnection(private val socket: Socket) : NeovimConnection {
    override val outputStream: OutputStream
        get() = socket.getOutputStream()

    override val inputStream: InputStream
        get() = socket.getInputStream()

    override fun close() {
        outputStream.close()
        inputStream.close()
        socket.close()
    }
}