package com.qoliber.booster.cachehealth

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSeparator
import javax.swing.SwingConstants

class CacheHealthToolbarWidget : DumbAwareAction(), CustomComponentAction {

    override fun actionPerformed(e: AnActionEvent) { /* interaction handled by the component */ }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val label = JLabel("Index …").apply {
            border = JBUI.Borders.empty(0, 8)
            toolTipText = "PhpStorm index cache size — click for actions"
        }
        val service = CacheSizeService.getInstance()
        val repaint = Runnable {
            val sizes = service.lastResult()
            if (sizes != null) label.text = "Index ${SizeFormat.humanReadable(sizes.indexBytes)}"
        }
        service.addListener { UIUtil.invokeLaterIfNeeded(repaint) }
        service.refreshAsync()
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(ev: MouseEvent) = showPopup(label)
        })
        return label
    }

    private fun showPopup(anchor: JComponent) {
        val service = CacheSizeService.getInstance()
        val result = service.lastResult()

        val panel = javax.swing.JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8)
        }

        // --- breakdown section ---
        if (result != null) {
            for ((name, bytes) in result.perDir) {
                panel.add(JLabel("$name: ${SizeFormat.humanReadable(bytes)}").apply {
                    alignmentX = JComponent.LEFT_ALIGNMENT
                    border = JBUI.Borders.empty(1, 0)
                })
            }
            panel.add(JLabel("total: ${SizeFormat.humanReadable(result.totalBytes)}").apply {
                alignmentX = JComponent.LEFT_ALIGNMENT
                border = JBUI.Borders.emptyTop(4)
                font = font.deriveFont(java.awt.Font.BOLD)
            })
        } else {
            panel.add(JLabel("Computing…").apply {
                alignmentX = JComponent.LEFT_ALIGNMENT
            })
            service.refreshAsync()
        }

        panel.add(Box.createRigidArea(Dimension(0, 8)))
        panel.add(JSeparator(SwingConstants.HORIZONTAL).apply {
            alignmentX = JComponent.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 2)
        })
        panel.add(Box.createRigidArea(Dimension(0, 8)))

        // placeholder to capture popup reference after creation
        var popup: JBPopup? = null

        fun makeButton(text: String, action: () -> Unit): JButton =
            JButton(text).apply {
                alignmentX = JComponent.LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                addActionListener {
                    popup?.cancel()
                    action()
                }
            }

        val refreshButton = makeButton("Refresh") {
            service.refreshAsync()
        }

        val trimButton = makeButton("Trim Caches") {
            invokeAction("com.qoliber.booster.cachehealth.Trim", anchor)
        }

        val invalidateButton = makeButton("Invalidate & Restart") {
            invokeAction("com.qoliber.booster.cachehealth.Invalidate", anchor)
        }

        val settingsButton = makeButton("Open Settings") {
            ShowSettingsUtil.getInstance().showSettingsDialog(null, "Cache Health")
        }

        panel.add(refreshButton)
        panel.add(Box.createRigidArea(Dimension(0, 4)))
        panel.add(trimButton)
        panel.add(Box.createRigidArea(Dimension(0, 4)))
        panel.add(invalidateButton)
        panel.add(Box.createRigidArea(Dimension(0, 4)))
        panel.add(settingsButton)

        val scrollPane = JBScrollPane(panel).apply {
            border = JBUI.Borders.empty()
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scrollPane, refreshButton)
            .setTitle("Cache Health")
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()

        popup!!.showUnderneathOf(anchor)
    }

    private fun invokeAction(id: String, anchor: JComponent) {
        val action = ActionManager.getInstance().getAction(id) ?: return
        val ctx = DataManager.getInstance().getDataContext(anchor)
        val event = AnActionEvent.createEvent(action, ctx, Presentation(), ActionPlaces.POPUP, ActionUiKind.NONE, null)
        action.actionPerformed(event)
    }
}
