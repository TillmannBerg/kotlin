/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.config

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.KotlinTypeRefiner
import org.jetbrains.kotlin.types.refinement.TypeRefinement

class KotlinTypeRefinerImpl(
    private val moduleDescriptor: ModuleDescriptor,
    languageVersionSettings: LanguageVersionSettings
) : KotlinTypeRefiner {
    private val isRefinementDisabled = !languageVersionSettings.isTypeRefinementEnabled

    @TypeRefinement
    override fun refineType(type: KotlinType): KotlinType {
        if (isRefinementDisabled) return type
        return type.refine(moduleDescriptor)
    }

    @TypeRefinement
    override fun refineSupertypes(classDescriptor: ClassDescriptor): Collection<KotlinType> {
        return refineSupertypes(classDescriptor, moduleDescriptor)
    }

    @TypeRefinement
    override fun refineSupertypes(classDescriptor: ClassDescriptor, moduleDescriptor: ModuleDescriptor): Collection<KotlinType> {
        if (isRefinementDisabled) return classDescriptor.typeConstructor.supertypes
        return classDescriptor.typeConstructor.supertypes.map { it.refine(moduleDescriptor) }
    }
}

val LanguageVersionSettings.isTypeRefinementEnabled: Boolean
    get() = getFlag(AnalysisFlags.useTypeRefinement) && supportsFeature(LanguageFeature.MultiPlatformProjects)