package org.beeender.comradeneovim

import com.intellij.openapi.actionSystem.*
import org.beeender.comradeneovim.core.NvimInfo
import org.beeender.comradeneovim.core.NvimInstanceManager

class NvimInstanceAction(private val nvimInfo: NvimInfo, private val connected: Boolean) : ToggleAction() {

    init {
        this.templatePresentation.text = nvimInfo.address
        this.templatePresentation.description = nvimInfo.address
        this.templatePresentation.isEnabled = !ComradeNeovimPlugin.autoConnect
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (state) {
            NvimInstanceManager.connect(nvimInfo)
        } else {
            NvimInstanceManager.disconnect(nvimInfo)
        }
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        return connected
    }
}

class NvimToggleAllAction(private val enable: Boolean) : AnAction() {
    init {
        this.templatePresentation.isEnabled = !ComradeNeovimPlugin.autoConnect
        this.templatePresentation.text = when (enable) {
            true -> "Connect All"
            else -> "Disconnect ALl"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (enable) {
            NvimInstanceManager.connectAll()
        }
        else {
            NvimInstanceManager.disconnectAll()
        }
    }
}

class MainAction : ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val list = NvimInstanceManager.list()
        val ret = list.map {
            NvimInstanceAction(it.first, it.second) as AnAction }.toMutableList()
        ret.add(Separator())
        ret.add(NvimToggleAllAction(true))
        ret.add(NvimToggleAllAction(false))
        return ret.toTypedArray()
    }
}

class AutoConnectAction : ToggleAction() {
    override fun isSelected(e: AnActionEvent): Boolean {
        return ComradeNeovimPlugin.autoConnect
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        ComradeNeovimPlugin.autoConnect = state
    }
}

class ShowEditorInSyncAction : ToggleAction() {
    override fun isSelected(e: AnActionEvent): Boolean {
        return ComradeNeovimPlugin.showEditorInSync
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        ComradeNeovimPlugin.showEditorInSync = state
    }
}
