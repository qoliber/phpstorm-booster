package com.qoliber.phpstorm.indexblocker

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.pointers.VirtualFilePointer

class IndexBlockerExcludePolicy(private val project: Project) : DirectoryIndexExcludePolicy {

    override fun getExcludeUrlsForProject(): Array<String> = emptyArray()

    override fun getExcludeRootsForModule(rootModel: ModuleRootModel): Array<VirtualFilePointer> =
        emptyArray()
}
