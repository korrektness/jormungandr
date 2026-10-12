package org.jetbrains.kotlin.formver.plugin.services

import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.embeddings.callables.FunctionSignatureImpl
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedFunctionSignatureImpl
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.expression.PlaceholderVariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.FunctionTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.asTypeEmbedding
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.core.names.PredicateName
import org.jetbrains.kotlin.formver.core.names.ScopedName
import org.jetbrains.kotlin.formver.core.names.asScope
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ObserverClassViewsTest {
    private fun type(name: String) = ClassTypeEmbedding(ClassId.topLevel(FqName(name)).embedName())

    private fun signature(owner: ClassTypeEmbedding, resolver: TypeResolver): NonInlineFunctionSignature {
        val receiver = PlaceholderVariableEmbedding(DispatchReceiverName, owner.asTypeEmbedding(), isUnique = true)
        val result = PlaceholderVariableEmbedding(PredicateName("result"), owner.asTypeEmbedding())
        val callable = FunctionTypeEmbedding(receiver.type, null, emptyList(), result.type, returnsUnique = false)
        return NonInlineFunctionSignature(
            NamedFunctionSignatureImpl(
                FunctionSignatureImpl(callable, receiver, null, emptyList(), result, isPure = true),
                ScopedName(owner.name.asScope(), PredicateName("observe")), null,
            ),
            listOf(receiver.uniquePredicateAccessInvariant(resolver)!!), emptyList(), null,
        )
    }

    @Test
    fun testClass_observer_viewsDispatch() {
        val resolver = TypeResolver()
        val base = type("Base")
        val derived = type("Derived")
        val iface = type("Observation")
        resolver.register(base, isInterface = false)
        resolver.register(derived, isInterface = false)
        resolver.register(iface, isInterface = true)
        resolver.markClassFamilyView(base.name)
        resolver.addSubtypeRelation(derived.name, base.name)
        resolver.addSubtypeRelation(base.name, iface.name)
        val observer = signature(iface, resolver)
        val baseBody = signature(base, resolver)
        val derivedBody = signature(derived, resolver)
        resolver.bindObserver(iface, observer.name, observer)
        resolver.bindObserver(base, observer.name, baseBody, dynamicDispatch = true)
        resolver.bindObserver(derived, observer.name, derivedBody)
        resolver.bindObserver(derived, baseBody.name, derivedBody)

        val args = listOf(baseBody.dispatchReceiver!!.toLocalVarUse())
        val view = assertNotNull(resolver.observerBinding(base, observer.name))
        val implementation = assertNotNull(resolver.observerImplementation(base, observer.name))
        assertNotEquals(view.functionName, implementation.functionName)
        assertEquals(baseBody.name, assertIs<Exp.FuncApp>(implementation.application(args)).functionName)
        assertEquals(view.functionName, assertIs<Exp.FuncApp>(view.application(args)).functionName)
        assertEquals(derivedBody.name, resolver.observerBinding(derived, baseBody.name)!!.functionName)
        assertNull(resolver.observerImplementation(iface, observer.name))

        val exact = assertIs<Exp.Implies>(assertNotNull(view.function()).posts.single())
        assertEquals(base.runtimeType, assertIs<Exp.EqCmp>(exact.left).right)
        assertEquals(implementation.application(args), assertIs<Exp.EqCmp>(exact.right).right)

        val pairs = resolver.observerBindings(derived, base)
        assertEquals(1, pairs.size)
        assertEquals(derivedBody.name, pairs.single().first.functionName)
        assertEquals(view.functionName, pairs.single().second.functionName)

        // Restoration preserves the abstract class observation, even when its object is a subclass.
        val restore = resolver.familyRestore(base, iface)
        val equality = assertIs<Exp.EqCmp>(assertIs<Exp.Implies>(restore.posts.last()).right)
        assertEquals(view.application(args), equality.left)
        assertEquals(iface.uniquePredicateName, assertIs<Exp.PredicateAccess>(restore.pres.first()).predicateName)
        val classTransfer = resolver.familyTransfer(derived, base).method
        assertEquals(base.uniquePredicateName, assertIs<Exp.PredicateAccess>(classTransfer.posts.first()).predicateName)
    }

    @Test
    fun testClass_observer_viewsAbstractBinding() {
        val resolver = TypeResolver()
        val abstract = type("AbstractObservation")
        val iface = type("Observation")
        resolver.register(abstract, isInterface = false)
        resolver.register(iface, isInterface = true)
        resolver.addSubtypeRelation(abstract.name, iface.name)
        resolver.markClassFamilyView(abstract.name)
        val observer = signature(iface, resolver)
        resolver.bindObserver(abstract, observer.name, observer, dynamicDispatch = true, hasImplementation = false)

        assertNull(resolver.observerImplementation(abstract, observer.name))
        val function = assertNotNull(resolver.observerBinding(abstract, observer.name)!!.function())
        assertEquals(abstract.uniquePredicateName, assertIs<Exp.PredicateAccess>(function.pres.single()).predicateName)
        assertNull(function.body)
        assertEquals(emptyList(), function.posts)
    }
}
