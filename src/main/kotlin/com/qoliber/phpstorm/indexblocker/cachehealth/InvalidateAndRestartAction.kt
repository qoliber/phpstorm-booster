package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class InvalidateAndRestartAction : AnAction("Invalidate Caches and Restart") {

    override fun actionPerformed(e: AnActionEvent) {
        val native = ActionManager.getInstance().getAction("InvalidateCaches")
        if (native != null) {
            native.actionPerformed(e)
        } else {
            Messages.showErrorDialog(
                "The Invalidate Caches action is unavailable in this IDE build.",
                "Invalidate Caches",
            )
        }
    }
}
