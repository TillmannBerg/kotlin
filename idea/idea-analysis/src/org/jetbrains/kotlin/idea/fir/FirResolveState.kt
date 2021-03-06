/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.fir

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionProvider
import org.jetbrains.kotlin.fir.dependenciesWithoutSelf
import org.jetbrains.kotlin.fir.java.FirLibrarySession
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.idea.caches.project.IdeaModuleInfo
import org.jetbrains.kotlin.idea.caches.project.ModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.project.getModuleInfo
import org.jetbrains.kotlin.idea.caches.project.isLibraryClasses
import org.jetbrains.kotlin.idea.caches.resolve.IDEPackagePartProvider
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.utils.addToStdlib.cast

private fun createLibrarySession(moduleInfo: IdeaModuleInfo, project: Project, provider: FirProjectSessionProvider): FirLibrarySession {
    val contentScope = moduleInfo.contentScope()
    return FirLibrarySession.create(moduleInfo, provider, contentScope, project, IDEPackagePartProvider(contentScope))
}

private fun getOrCreateIdeSession(
    sessionProvider: FirProjectSessionProvider,
    project: Project,
    moduleInfo: ModuleSourceInfo
): FirSession {
    sessionProvider.getSession(moduleInfo)?.let { return it }
    return synchronized(moduleInfo.module) {
        sessionProvider.getSession(moduleInfo) ?: FirIdeJavaModuleBasedSession(
            project, moduleInfo, sessionProvider, moduleInfo.contentScope()
        ).also { moduleBasedSession ->
            val ideaModuleInfo = moduleInfo.cast<IdeaModuleInfo>()
            ideaModuleInfo.dependenciesWithoutSelf().forEach {
                if (it is IdeaModuleInfo && it.isLibraryClasses()) {
                    // TODO: consider caching / synchronization here
                    createLibrarySession(it, project, sessionProvider)
                }
            }
            sessionProvider.sessionCache[moduleInfo] = moduleBasedSession
        }
    }
}

interface FirResolveState {
    val sessionProvider: FirSessionProvider

    fun getSession(psi: KtElement): FirSession {
        val sessionProvider = sessionProvider as FirProjectSessionProvider
        val moduleInfo = psi.getModuleInfo() as ModuleSourceInfo
        return getOrCreateIdeSession(sessionProvider, psi.project, moduleInfo)
    }

    operator fun get(psi: KtElement): FirElement?

    fun record(psi: KtElement, fir: FirElement)
}

class FirResolveStateImpl(override val sessionProvider: FirSessionProvider) : FirResolveState {
    private val cache = mutableMapOf<KtElement, FirElement>()

    override fun get(psi: KtElement): FirElement? = cache[psi]

    override fun record(psi: KtElement, fir: FirElement) {
        cache[psi] = fir
    }
}

fun KtElement.firResolveState(): FirResolveState =
    FirIdeResolveStateService.getInstance(project).getResolveState(getModuleInfo())