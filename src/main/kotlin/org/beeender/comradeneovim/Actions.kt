package org.beeender.comradeneovim

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

class MainAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
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
