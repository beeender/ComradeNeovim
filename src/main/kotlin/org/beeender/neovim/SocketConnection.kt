package org.beeender.neovim

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class SocketConnection(val socket: Socket) : NeovimConnection {
    override val outputStream: OutputStream
        get() = socket.getOutputStream()

    override val inputStream: InputStream
        get() = socket.getInputStream()

    override fun close() {
        socket.close()
    }
}