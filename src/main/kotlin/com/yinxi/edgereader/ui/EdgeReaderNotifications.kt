package com.yinxi.edgereader.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object EdgeReaderNotifications {
    fun error(project: Project, title: String, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Edge Reader")
            .createNotification(title, message, NotificationType.ERROR)
            .notify(project)
    }

    fun info(project: Project, title: String, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Edge Reader")
            .createNotification(title, message, NotificationType.INFORMATION)
            .notify(project)
    }
}
