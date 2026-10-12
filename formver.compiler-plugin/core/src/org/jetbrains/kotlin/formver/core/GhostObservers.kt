/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.formver.uniqueness.plugin.isGhostObserver as hasGhostObserverAnnotation

fun FirCallableSymbol<*>.isGhostObserver(session: FirSession): Boolean = hasGhostObserverAnnotation(session)
