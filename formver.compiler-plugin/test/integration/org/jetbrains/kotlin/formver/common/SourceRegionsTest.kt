package org.jetbrains.kotlin.formver.common

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.formver.common.source.RegionId
import org.jetbrains.kotlin.formver.common.source.RegionKind
import org.jetbrains.kotlin.formver.common.source.RegionKind.*
import org.jetbrains.kotlin.formver.common.source.RegionSpan
import org.jetbrains.kotlin.formver.common.source.SourceRegion
import org.jetbrains.kotlin.formver.common.source.sourceRegions
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SourceRegionsTest {
    private val disposable = Disposer.newDisposable()
    private val environment = KotlinCoreEnvironment.createForProduction(
        disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES,
    )
    private val factory = KtPsiFactory(environment.project, false)

    @AfterEach
    fun dispose() = Disposer.dispose(disposable)

    @Test
    fun sharedAnchorsChainAndDescendantsUseInnermostRegion() {
        val file = factory.createFile("fun f(default: () -> Int = { 1 }) = { val nested = { 2 }; nested() }")
        val function = file.declarations.single() as KtNamedFunction
        val parameter = function.valueParameters.single()
        val defaultLambda = parameter.defaultValue as KtLambdaExpression
        val body = function.bodyExpression as KtLambdaExpression
        val nested = body.bodyExpression!!.statements.first() as KtProperty
        val nestedLambda = nested.initializer as KtLambdaExpression

        val functionId = file.id(FUNCTION, "{ val nested = { 2 }; nested() }")
        val defaultId = file.id(DEFAULT_ARGUMENT, "{ 1 }")
        val bodyId = file.id(LAMBDA, "{ val nested = { 2 }; nested() }")
        val propertyId = file.id(PROPERTY_INITIALIZER, "{ 2 }")
        assertEquals(
            listOf(
                SourceRegion(functionId, function, null),
                SourceRegion(defaultId, parameter, null),
                SourceRegion(file.id(LAMBDA, "{ 1 }"), defaultLambda, defaultId),
                SourceRegion(bodyId, body, functionId),
                SourceRegion(propertyId, nested, bodyId),
                SourceRegion(file.id(LAMBDA, "{ 2 }"), nestedLambda, propertyId),
            ),
            sourceRegions(file),
        )
    }

    @Test
    fun constructorAnchorsPrecedeTheirChildrenInOwnerOrder() {
        val file = factory.createFile(
            "class Child : Base { constructor(x: () -> Int = { 3 }) : super(x()) { val y = { 4 } } }",
        )
        val constructor = (file.declarations.single() as KtClass).secondaryConstructors.single()
        val parameter = constructor.valueParameters.single()
        val property = constructor.bodyExpression!!.statements.single() as KtProperty
        val bodyId = file.id(SECONDARY_CONSTRUCTOR, "{ val y = { 4 } }")
        val defaultId = file.id(DEFAULT_ARGUMENT, "{ 3 }")
        val propertyId = file.id(PROPERTY_INITIALIZER, "{ 4 }")
        assertEquals(
            listOf(
                SourceRegion(bodyId, constructor, null),
                SourceRegion(file.id(CONSTRUCTOR_DELEGATION, "super(x())"), constructor, null),
                SourceRegion(defaultId, parameter, null),
                SourceRegion(file.id(LAMBDA, "{ 3 }"), parameter.defaultValue!!, defaultId),
                SourceRegion(propertyId, property, bodyId),
                SourceRegion(file.id(LAMBDA, "{ 4 }"), property.initializer!!, propertyId),
            ),
            sourceRegions(file),
        )
    }

    private fun KtFile.id(kind: RegionKind, snippet: String): RegionId {
        val start = text.indexOf(snippet)
        return RegionId(RegionSpan(kind, start, start + snippet.length), 0)
    }
}
