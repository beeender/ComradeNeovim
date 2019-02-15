package org.beeender.comradeneovim

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.messages.MessageBusConnection
import org.beeender.comradeneovim.core.NvimInstanceManager

class ApplicationComponent : BaseComponent {
    private val projectManagerListener =  object : ProjectManagerListener {
        override fun projectOpened(project: Project) {
            NvimInstanceManager.refresh()
        }
    }

    lateinit var msgBusConnection : MessageBusConnection

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
}