package org.beeender.comradeneovim

import com.intellij.notification.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager

object ComradeNeovimService {
    private val balloonGroup = NotificationGroup("ComradeNeovim", NotificationDisplayType.BALLOON, true)

    val instance: ComradeNeovimService
        get() = ServiceManager.getService(ComradeNeovimService::class.java)

    fun showBalloon(msg: String, type: NotificationType) {
        ApplicationManager.getApplication().invokeLater {
            val notification = balloonGroup.createNotification(msg, type)
            Notifications.Bus.notify(notification)
        }
    }
}
