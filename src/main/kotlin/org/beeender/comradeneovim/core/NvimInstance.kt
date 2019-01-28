package org.beeender.comradeneovim.core

import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.beeender.comradeneovim.completion.CompletionHandler
import org.beeender.comradeneovim.ComradeNeovimService
import org.beeender.neovim.Client
import org.beeender.neovim.NeovimConnection
import org.beeender.neovim.SocketConnection
import org.scalasbt.ipcsocket.UnixDomainSocket
import java.io.File
import java.lang.IllegalArgumentException
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths

private const val CONFIG_DIR_NAME = ".IntelliNeovim"
private var HOME = System.getenv("HOME")
private var CONFIG_DIR = File(HOME, CONFIG_DIR_NAME)
private val IPV4_REGEX = "^([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+):([0-9]+)".toRegex()

private val Log = Logger.getInstance(NvimInstance::class.java)

class NvimInstance(private val address: String) {

    private val connection = createRPCConnection(address)
    private val client = Client(connection)
    private val name: String = client.api.callFunction("expand", listOf("%:p")) as String
    val apiInfo = client.api.getApiInfo()
    private val bufManager = SyncedBufferManager(this.client)

    init {
        client.api.setVar("intelliJID", apiInfo.channelId)
        client.api.command("echo 'Intellij connected. ID: ${apiInfo.channelId}'")

        client.registerHandler(bufManager)
        client.registerHandler(CompletionHandler(bufManager))
    }

    fun disconnect() {
    }

    fun register() {

        client.api.setVar("intelliJID", apiInfo.channelId)
        client.api.command("echo 'Intellij connected. ID: ${apiInfo.channelId}'")

        client.registerHandler(bufManager)
        client.registerHandler(CompletionHandler(bufManager))

        registeredInstance?.disconnect()
        registeredInstance = this

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

var registeredInstance: NvimInstance? = null
    private set

fun list(): List<NvimInstance> {
    val list = mutableListOf<NvimInstance>()

    if (!CONFIG_DIR.isDirectory) {
        return list
    }

    CONFIG_DIR.walk().forEach {
        if (it.isFile) {
            val lines = it.readLines()
            if (lines.isEmpty()) return@forEach

            val address = lines.first()
            if (address.isEmpty()) return@forEach

            if (IPV4_REGEX.matches(address) || File(address).exists()) {
                try {
                    list.add(NvimInstance(lines.first()))
                } catch (t: Throwable) {
                    Log.error("Failed to create Neovim instance for ${lines.first()}", t)
                }
            }
        }
    }

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
            i.disconnect()
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

    val matchResult = IPV4_REGEX.find(address)
    if (matchResult != null)
        return SocketConnection(Socket(matchResult.groupValues[1], matchResult.groupValues[2].toInt()))
    else {
        val file = File(address)
        if (file.exists())
            return SocketConnection(UnixDomainSocket(address))
    }
    throw IllegalArgumentException("Cannot create RPC connection from given address: '$address'.")
}
