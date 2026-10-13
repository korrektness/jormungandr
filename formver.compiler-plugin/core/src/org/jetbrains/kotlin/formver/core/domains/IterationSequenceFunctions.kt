/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.domains

import org.jetbrains.kotlin.formver.core.names.DomainAssociatedFuncName
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Function
import org.jetbrains.kotlin.formver.viper.ast.Type
import org.jetbrains.kotlin.formver.viper.ast.ge
import org.jetbrains.kotlin.formver.viper.ast.lt
import org.jetbrains.kotlin.formver.viper.ast.eq
import org.jetbrains.kotlin.formver.viper.ast.toExp

object IterationSequenceElementFunction : Function by FunctionBuilder.build(DomainAssociatedFuncName("iterationSequenceGet"), {
    val sequence = RuntimeTypeDomain.iterationSequenceInjection.fromRef(argument(Type.Ref))
    val index = RuntimeTypeDomain.intInjection.fromRef(argument(Type.Ref))
    returns(Type.Ref)
    precondition { index ge 0.toExp() }
    precondition { index lt Exp.SeqLength(sequence) }
    postcondition { result eq Exp.SeqIndex(sequence, index) }
})
