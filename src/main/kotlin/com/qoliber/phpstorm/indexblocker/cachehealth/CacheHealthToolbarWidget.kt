package com.qoliber.phpstorm.indexblocker.cachehealth

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel

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
        val group = DefaultActionGroup()
        ActionManager.getInstance().getAction("com.qoliber.indexblocker.cachehealth.Trim")?.let { group.add(it) }
        ActionManager.getInstance().getAction("com.qoliber.indexblocker.cachehealth.Invalidate")?.let { group.add(it) }
        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "Cache Health", group, DataContext.EMPTY_CONTEXT,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
            )
            .showUnderneathOf(anchor)
    }
}
