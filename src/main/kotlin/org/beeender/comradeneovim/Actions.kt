package org.beeender.comradeneovim

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.popup.JBPopupFactory
import javax.swing.ListSelectionModel.SINGLE_SELECTION

class MainAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent?) {
        /*
        val instances = list()
        val popup = JBPopupFactory.getInstance().createPopupChooserBuilder(instances)
                .setSelectionMode(SINGLE_SELECTION)
                .setItemChosenCallback {
                    it.register()
                }
                .createPopup()
        popup.showInFocusCenter()
        */
    }
}

class AutoConnectAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent?) {
    }
}
