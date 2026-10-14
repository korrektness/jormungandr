/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.fir.backend.FirMetadataSource
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrMetadataSourceOwner
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

val verifiedId = ClassId.topLevel(FqName("org.jetbrains.kotlin.formver.plugin.Verified"))

/** The declarations whose programs verified in this compilation. */
class VerifiedDeclarations {
    private val symbols = HashSet<FirBasedSymbol<*>>()

    @Synchronized
    fun record(symbol: FirBasedSymbol<*>) {
        symbols += symbol
    }

    @Synchronized
    operator fun contains(symbol: FirBasedSymbol<*>): Boolean = symbol in symbols

    @Synchronized
    fun isEmpty(): Boolean = symbols.isEmpty()
}

/**
 * Annotates each declaration in [verified] with `@Verified`, so that a compilation that depends on this one can tell
 * that a specification binds a declaration SnaKt verified.
 */
class VerifiedMarker(private val verified: VerifiedDeclarations) : IrGenerationExtension {
    // Plugin IR generation runs once the module's IR is complete.
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        if (verified.isEmpty()) return
        val annotation = checkNotNull(pluginContext.referenceClass(verifiedId)) {
            "SnaKt verified declarations, but $verifiedId is not on the compilation's class path to mark them with."
        }
        val constructor = annotation.owner.constructors.single().symbol
        moduleFragment.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitDeclaration(declaration: IrDeclarationBase) {
                val fir = ((declaration as? IrMetadataSourceOwner)?.metadata as? FirMetadataSource)?.fir
                if (fir != null && fir.symbol in verified) {
                    declaration.annotations += IrConstructorCallImpl.fromSymbolOwner(
                        declaration.startOffset, declaration.endOffset, annotation.defaultType, constructor,
                    )
                }
                super.visitDeclaration(declaration)
            }
        })
    }
}
