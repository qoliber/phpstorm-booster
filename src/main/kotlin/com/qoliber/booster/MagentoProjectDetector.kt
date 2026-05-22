package com.qoliber.booster

import com.intellij.openapi.vfs.VirtualFile

object MagentoProjectDetector {

    /** Returns [contentRoot] if it looks like a Magento root (has bin/magento and app/etc), else null. */
    fun magentoRootFor(contentRoot: VirtualFile): VirtualFile? {
        if (!contentRoot.isDirectory) return null
        val binMagento = contentRoot.findFileByRelativePath("bin/magento")
        val appEtc = contentRoot.findFileByRelativePath("app/etc")
        val ok = binMagento != null && !binMagento.isDirectory &&
            appEtc != null && appEtc.isDirectory
        return if (ok) contentRoot else null
    }
}
