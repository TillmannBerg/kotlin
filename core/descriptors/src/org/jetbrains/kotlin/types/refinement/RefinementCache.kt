/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types.refinement

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.TypeConstructor

sealed class RefinementCache(protected val moduleDescriptor: ModuleDescriptor) {
    @TypeRefinement
    abstract fun isRefinementNeededForTypeConstructor(typeConstructor: TypeConstructor): Boolean

    @TypeRefinementInternal
    abstract fun refineOrGetType(type: SimpleType, refinedTypeFactory: (ModuleDescriptor) -> SimpleType?): SimpleType

    @TypeRefinement
    abstract fun <S : MemberScope> getOrPutScopeForClass(classDescriptor: ClassDescriptor, compute: () -> S): S
}

internal class RefinementCacheImpl(moduleDescriptor: ModuleDescriptor, storageManager: StorageManager) : RefinementCache(moduleDescriptor) {
    private val _isRefinementNeededForTypeConstructor =
        storageManager.createMemoizedFunction(TypeConstructor::areThereExpectSupertypesOrTypeArguments)
    private val refinedTypeCache = storageManager.createCacheWithNotNullValues<SimpleType, SimpleType>()
    private val scopes = storageManager.createCacheWithNotNullValues<ClassDescriptor, MemberScope>()

    @TypeRefinement
    override fun isRefinementNeededForTypeConstructor(typeConstructor: TypeConstructor): Boolean {
        return _isRefinementNeededForTypeConstructor.invoke(typeConstructor)
    }

    @TypeRefinementInternal
    override fun refineOrGetType(type: SimpleType, refinedTypeFactory: (ModuleDescriptor) -> SimpleType?): SimpleType {
        return refinedTypeCache.computeIfAbsent(type) {
            refinedTypeFactory(moduleDescriptor) ?: type
        }
    }

    @TypeRefinement
    override fun <S : MemberScope> getOrPutScopeForClass(classDescriptor: ClassDescriptor, compute: () -> S): S {
        @Suppress("UNCHECKED_CAST")
        return scopes.computeIfAbsent(classDescriptor, compute) as S
    }
}

/**
 * We can think that if there is no RefinementCache cache in module descriptor and EmptyRefinementCache
 *   is using, then type refinement is disabled
 */
private class EmptyRefinementCache(moduleDescriptor: ModuleDescriptor) : RefinementCache(moduleDescriptor) {
    @TypeRefinement
    override fun isRefinementNeededForTypeConstructor(typeConstructor: TypeConstructor): Boolean = false

    @TypeRefinementInternal
    override fun refineOrGetType(type: SimpleType, refinedTypeFactory: (ModuleDescriptor) -> SimpleType?): SimpleType = type

    @TypeRefinement
    override fun <S : MemberScope> getOrPutScopeForClass(classDescriptor: ClassDescriptor, compute: () -> S): S = compute()
}

internal val refinementCacheCapability = ModuleDescriptor.Capability<RefinementCache>("RefinementCache")

@TypeRefinement
private val ModuleDescriptor.refinementCache: RefinementCache
    get() = getCapability(refinementCacheCapability) ?: EmptyRefinementCache(this)

@TypeRefinement
fun <S : MemberScope> ModuleDescriptor.getOrPutScopeForClass(classDescriptor: ClassDescriptor, compute: () -> S): S =
    refinementCache.getOrPutScopeForClass(classDescriptor, compute)

@TypeRefinement
@TypeRefinementInternal
fun ModuleDescriptor.refineOrGetType(type: SimpleType, refinedTypeFactory: (ModuleDescriptor) -> SimpleType?): SimpleType {
    return refinementCache.refineOrGetType(type, refinedTypeFactory)
}

@TypeRefinement
fun ModuleDescriptor.isRefinementNeededForTypeConstructor(typeConstructor: TypeConstructor): Boolean {
    return refinementCache.isRefinementNeededForTypeConstructor(typeConstructor)
}