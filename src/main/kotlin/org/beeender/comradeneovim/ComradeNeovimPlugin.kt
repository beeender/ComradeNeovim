package org.beeender.comradeneovim

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.beeender.comradeneovim.core.NvimInstanceManager
import org.beeender.comradeneovim.insight.InsightProcessor

val ComradeScope = ComradeNeovimPlugin.instance.coroutineScope

object Version {
    val versionString = PluginManager.getPlugin(PluginId.getId("beeender.ComradeNeovim"))!!.version
    val major: Int
    val minor: Int
    val patch: Int
    val prerelese: String

    init {
        val vers = versionString.split('.', '-')
        major = vers[0].toInt()
        minor = vers[1].toInt()
        patch = vers[2].toInt()
        prerelese = when (vers.size > 3) {
            true -> vers[3]
            else -> ""
        }
    }

    fun toMap() : Map<String, String> {
        return mapOf(
                "major" to major.toString(),
                "minor" to minor.toString(),
                "patch" to patch.toString(),
                "prerelease" to prerelese
        )
    }
}


@State(name = "ComradeNeovim",
        storages = [Storage(file = "\$APP_CONFIG\$/comrade_neovim_settings.xml")])
class ComradeNeovimPlugin : BaseComponent, PersistentStateComponent<Settings>, Disposable {
    companion object {
        val instance: ComradeNeovimPlugin by lazy {
            ApplicationManager.getApplication().getComponent(ComradeNeovimPlugin::class.java)
        }

        var autoConnect: Boolean
            get() { return instance.settings.autoConnect }
            set(value) {
                if (value) {
                    instance.settings.autoConnect = true
                    NvimInstanceManager.connectAll()
                } else {
                    instance.settings.autoConnect = false
                }
            }
        var showEditorInSync: Boolean
            get() { return instance.settings.showEditorInSync }
            set(value) { instance.settings.showEditorInSync = value }
    }

    private var settings = Settings()
    private lateinit var msgBusConnection: MessageBusConnection
    private lateinit var job: Job
    // Retain a reference to make sure the singleton get initialized
    @Suppress("unused")

    val coroutineScope by lazy {  CoroutineScope(job + Dispatchers.Default) }

    private val projectManagerListener =  object : ProjectManagerListener {
        override fun projectOpened(project: Project) {
            NvimInstanceManager.refresh()
            // Start the singleton InsightProcessor here to avoid cyclic initialization
            InsightProcessor.start()
        }

        override fun projectClosing(project: Project) {
            NvimInstanceManager.cleanUp(project)
        }
    }

    override fun initComponent() {
        job = Job()
        NvimInstanceManager.start()
        Disposer.register(this, NvimInstanceManager)
        msgBusConnection = ApplicationManager.getApplication().messageBus.connect(this)
        msgBusConnection.subscribe(ProjectManager.TOPIC, projectManagerListener)
    }

    override fun disposeComponent() {
        Disposer.dispose(this)
    }

    override fun getState(): Settings {
        return settings
    }

    override fun loadState(state: Settings) {
        XmlSerializerUtil.copyBean(state, settings)
    }

    override fun dispose() {
        job.cancel()
        super.disposeComponent()
    }
}

