package com.qoliber.booster.cachehealth

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.JBUI
import java.awt.AWTEvent
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent
import javax.swing.JDialog

class InvalidateAndRestartAction : AnAction("Invalidate Caches and Restart") {

    override fun actionPerformed(e: AnActionEvent) {
        val native = ActionManager.getInstance().getAction("InvalidateCaches")
        if (native == null) {
            Messages.showErrorDialog(
                "The Invalidate Caches action is unavailable in this IDE build.",
                "Invalidate Caches",
            )
            return
        }

        // PhpStorm's native "Invalidate Caches" dialog can render too short on some
        // window managers, clipping its lower options. We can't lay the dialog out,
        // but we can enlarge its window once it opens. Heuristic: while our action is
        // running, watch for a modal dialog titled like "Invalidate Caches" and grow it.
        val toolkit = Toolkit.getDefaultToolkit()
        val listener = AWTEventListener { event ->
            if (event.id != WindowEvent.WINDOW_OPENED) return@AWTEventListener
            val window = (event as WindowEvent).window
            if (window is JDialog && window.title?.contains("invalidate", ignoreCase = true) == true) {
                try {
                    val target = Dimension(
                        maxOf(window.width, JBUI.scale(600)),
                        maxOf(window.height, JBUI.scale(380)),
                    )
                    window.minimumSize = target
                    window.size = target
                    // Re-center so the taller window doesn't run off the screen bottom.
                    window.setLocationRelativeTo(window.owner)
                    window.validate()
                } catch (t: Throwable) {
                    LOG.warn("Failed to enlarge Invalidate Caches dialog", t)
                }
            }
        }

        toolkit.addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK)
        try {
            native.actionPerformed(e)
        } finally {
            toolkit.removeAWTEventListener(listener)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(InvalidateAndRestartAction::class.java)
    }
}
