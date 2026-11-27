/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.context.PersistentCheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.formver.locality.plugin.isTrustedCode
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class TrustedCodeLookaheadTest {
    @TempDir
    lateinit var temp: File

    @Test
    fun `trust depends on the member and accessor present in the declaration chain`() {
        val source = temp.resolve("Trust.kt").apply {
            writeText(
                """
                import org.jetbrains.kotlin.formver.plugin.NeverConvert
                import org.jetbrains.kotlin.formver.plugin.NeverVerify
                import org.jetbrains.kotlin.formver.plugin.preconditions

                class OptedOutConstruction @NeverVerify constructor() {
                    val property = 1
                        get() = field
                    init { }
                }
                class CheckedConstruction { val property = 1 }
                @NeverConvert class OptedOutClass
                @NeverVerify fun specified() { preconditions { true } }
                """.trimIndent(),
            )
        }
        val decisions = mutableMapOf<String, Boolean>()
        val registrar = object : FirExtensionRegistrar() {
            override fun ExtensionRegistrarContext.configurePlugin() {
                +FirAdditionalCheckersExtension.Factory { session ->
                    object : FirAdditionalCheckersExtension(session) {
                        override val declarationCheckers = object : DeclarationCheckers() {
                            override val fileCheckers = setOf(object : FirFileChecker(MppCheckerKind.Common) {
                                context(context: CheckerContext, reporter: DiagnosticReporter)
                                override fun check(declaration: FirFile) {
                                    decisions.putAll(trustDecisions(declaration, context))
                                }
                            })
                        }
                    }
                }
            }
        }
        runFrontend(source, registrar, useLightTree = false)
        assertEquals(
            mapOf(
                "trailing class" to false,
                "trailing property" to true,
                "trailing accessor" to false,
                "initializer" to true,
                "checked constructor" to false,
                "class opt-out" to true,
                "function opt-out" to true,
                "specification" to false,
            ),
            decisions,
        )
    }

    @OptIn(DirectDeclarationsAccess::class)
    private fun trustDecisions(file: FirFile, context: CheckerContext): Map<String, Boolean> {
        val root = PersistentCheckerContext(context.sessionHolder, context.returnTypeCalculator).addDeclaration(file)
        val classes = file.declarations.filterIsInstance<FirRegularClass>().associateBy { it.name.asString() }
        val owner = classes.getValue("OptedOutConstruction")
        val property = owner.declarations.filterIsInstance<FirProperty>().single()
        val initializer = owner.declarations.filterIsInstance<FirAnonymousInitializer>().single()
        val checked = classes.getValue("CheckedConstruction")
        val checkedProperty = checked.declarations.filterIsInstance<FirProperty>().single()
        val specified = file.declarations.filterIsInstance<FirSimpleFunction>().single()
        val specification = checkNotNull(specified.body).statements.single()
        val construction = root.addDeclaration(owner)
        return mapOf(
            "trailing class" to root.isTrustedCode(owner.symbol),
            "trailing property" to construction.isTrustedCode(property.symbol),
            "trailing accessor" to construction.addDeclaration(property).isTrustedCode(checkNotNull(property.getter).symbol),
            "initializer" to construction.isTrustedCode(initializer.symbol),
            "checked constructor" to root.addDeclaration(checked).isTrustedCode(checkedProperty.symbol),
            "class opt-out" to root.isTrustedCode(classes.getValue("OptedOutClass").symbol),
            "function opt-out" to root.isTrustedCode(specified.symbol),
            "specification" to root.addElement(specification).isTrustedCode(specified.symbol),
        )
    }
}
