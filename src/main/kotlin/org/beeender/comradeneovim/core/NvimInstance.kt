package org.beeender.comradeneovim.core

import com.intellij.openapi.diagnostic.Logger
import org.beeender.comradeneovim.completion.CompletionHandler
import org.beeender.comradeneovim.parseIPV4String
import org.beeender.neovim.ApiInfo
import org.beeender.neovim.Client
import org.beeender.neovim.NeovimConnection
import org.beeender.neovim.SocketConnection
import org.scalasbt.ipcsocket.UnixDomainSocket
import java.io.Closeable
import java.io.File
import java.net.Socket

private val Log = Logger.getInstance(NvimInstance::class.java)

class NvimInstance(private val address: String, onClose: (Throwable?) -> Unit) : Closeable {

    private val log = Logger.getInstance(NvimInstance::class.java)
    private val connection = createRPCConnection(address)
    val client = Client(connection, onClose)
    lateinit var apiInfo:ApiInfo
    val bufManager = SyncedBufferManager(this)
    @Volatile var connected = false
        private set

    suspend fun connect() {
        apiInfo = client.api.getApiInfo()

        client.api.callFunction("ComradeRegisterIntelliJ", listOf(apiInfo.channelId))

        client.registerHandler(bufManager)
        client.registerHandler(CompletionHandler(bufManager))
        log.info("NvimInstance has been created for connection '$connection'")
        connected = true
    }

    suspend fun getCurrentBufName() : String {
        return client.api.callFunction("expand", listOf("%:p")) as String
    }

    override fun close() {
        connected = false
        connection.close()
    }

    override fun toString(): String {
        return address
    }
}

private fun createRPCConnection(address: String): NeovimConnection {
    Log.info("Creating RPC connection from '$address'")

    val ipInfo = parseIPV4String(address)
    if (ipInfo!= null)
        return SocketConnection(Socket(ipInfo.first, ipInfo.second))
    else {
        val file = File(address)
        if (file.exists())
            return SocketConnection(UnixDomainSocket(address))
    }
    throw IllegalArgumentException("Cannot create RPC connection from given address: '$address'.")
}
