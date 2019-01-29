package org.beeender.comradeneovim.core

import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import org.beeender.comradeneovim.ComradeNeovimService
import org.beeender.comradeneovim.completion.CompletionHandler
import org.beeender.comradeneovim.parseIPV4String
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
    private val client = Client(connection, onClose)
    private val name: String = client.api.callFunction("expand", listOf("%:p")) as String
    val apiInfo = client.api.getApiInfo()
    val bufManager = SyncedBufferManager(this.client)

    init {
        client.api.setVar("ComradeNeovimId", apiInfo.channelId)
        client.api.command("echo 'ComradeNeovim connected. ID: ${apiInfo.channelId}'")

        client.registerHandler(bufManager)
        client.registerHandler(CompletionHandler(bufManager))
        log.info("NvimInstance has been created for connection '$connection'")
        ComradeNeovimService.instance.showBalloon("Connected to Neovim instance $address",
                NotificationType.INFORMATION)
    }

    override fun close() {
        connection.close()
    }

    override fun toString(): String {
        return "$name ($address)"
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
