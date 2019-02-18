package org.beeender.comradeneovim

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.xmlb.XmlSerializerUtil
import org.beeender.comradeneovim.core.NvimInstanceManager

@State(name = "ComradeNeovim",
        storages = [Storage(file = "\$APP_CONFIG\$/comrade_neovim_settings.xml")])
class ComradeNeovimPlugin : BaseComponent, PersistentStateComponent<Settings> {
    companion object {
        private val instance: ComradeNeovimPlugin by lazy {
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
    }

    private var settings = Settings()
    private lateinit var msgBusConnection : MessageBusConnection

    private val projectManagerListener =  object : ProjectManagerListener {
        override fun projectOpened(project: Project) {
            NvimInstanceManager.refresh()
        }
    }

    override fun initComponent() {
        NvimInstanceManager.start()
        msgBusConnection = ApplicationManager.getApplication().messageBus.connect()
        msgBusConnection.subscribe(ProjectManager.TOPIC, projectManagerListener)
    }

    override fun disposeComponent() {
        NvimInstanceManager.stop()
        msgBusConnection.disconnect()
        super.disposeComponent()
    }

    override fun getState(): Settings {
        return settings
    }

    override fun loadState(state: Settings) {
        XmlSerializerUtil.copyBean(state, settings)
    }
}