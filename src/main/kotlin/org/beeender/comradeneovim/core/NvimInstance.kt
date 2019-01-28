package org.beeender.comradeneovim.core

import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.beeender.comradeneovim.completion.CompletionHandler
import org.beeender.comradeneovim.ComradeNeovimService
import org.beeender.comradeneovim.parseIPV4String
import org.beeender.neovim.Client
import org.beeender.neovim.NeovimConnection
import org.beeender.neovim.SocketConnection
import org.scalasbt.ipcsocket.UnixDomainSocket
import java.io.Closeable
import java.io.File
import java.lang.IllegalArgumentException
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths

private val Log = Logger.getInstance(NvimInstance::class.java)

class NvimInstance(private val address: String, onClose: (Throwable?) -> Unit) : Closeable {

    private val log = Logger.getInstance(NvimInstance::class.java)
    private val connection = createRPCConnection(address)
    private val client = Client(connection, onClose)
    private val name: String = client.api.callFunction("expand", listOf("%:p")) as String
    val apiInfo = client.api.getApiInfo()
    private val bufManager = SyncedBufferManager(this.client)

    init {
        client.api.setVar("intelliJID", apiInfo.channelId)
        client.api.command("echo 'Intellij connected. ID: ${apiInfo.channelId}'")

        client.registerHandler(bufManager)
        client.registerHandler(CompletionHandler(bufManager))
        log.info("NvimInstance has been created for connection '$connection'")
    }

    override fun close() {
        connection.close()
    }

    fun register() {

        client.api.setVar("intelliJID", apiInfo.channelId)
        client.api.command("echo 'Intellij connected. ID: ${apiInfo.channelId}'")

        client.registerHandler(bufManager)
        client.registerHandler(CompletionHandler(bufManager))

        bufManager.loadCurrentBuffer()

        ComradeNeovimService.instance.showBalloon("Connected to Neovim instance $address",
                NotificationType.INFORMATION)
    }

    fun containsInProject(projects: Array<Project>): Boolean {
        val cwd = client.api.callFunction("getcwd", listOf()) as String
        val curPath = client.api.callFunction("expand", listOf("%:p")) as String

        for (pro in projects) {
            val proPath = Paths.get(pro.basePath)

            // Same root dir.
            if (Files.isSameFile(proPath, Paths.get(cwd))) {
                return true
            }

            // One project root dir contains the current opened buffer.
            if (Paths.get(curPath).startsWith(proPath)) {
                return true
            }
        }
        return false
    }

    override fun toString(): String {
        return "$name ($address)"
    }
}

fun list(): List<NvimInstance> {
    val list = mutableListOf<NvimInstance>()

    return list
}

fun autoConnect() {
    Log.info("autoConnect")


    val instances = list()
    // There is only one Neovim instance. Just connect to it
    if (instances.size == 1) {
        instances.first().register()
        return
    }

    var instanceToConnect: NvimInstance? = null
    val projects = ProjectManager.getInstance().openProjects

    for (i in instances) {
        try {
            if (i.containsInProject(projects)) {
                instanceToConnect = i
                break
            }
        } catch (t: Throwable) {
            Log.error("Failed to probe Neovim instance.", t)
        } finally {
        }
    }

    if (instanceToConnect != null) {
        instanceToConnect.register()
    } else {
        ComradeNeovimService.instance.showBalloon("There is no Neovim instance can be connected automatically.",
                NotificationType.ERROR)
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
