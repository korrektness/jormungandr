package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.formver.viper.ast.PermExp

import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.properties.BackingFieldGetter
import org.jetbrains.kotlin.formver.core.embeddings.properties.FieldEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.PathStep
import org.jetbrains.kotlin.formver.core.embeddings.properties.PropertyEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.FamilyTransfer
import org.jetbrains.kotlin.formver.core.embeddings.types.ObserverBinding
import org.jetbrains.kotlin.formver.core.embeddings.types.PretypeEmbedding
import org.jetbrains.kotlin.formver.core.domains.stringLiteralFunction
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.core.names.NameMatcher
import org.jetbrains.kotlin.formver.core.names.NameScope
import org.jetbrains.kotlin.formver.core.names.ScopedName
import org.jetbrains.kotlin.formver.core.names.StringLiteralName
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Declaration
import org.jetbrains.kotlin.formver.viper.ast.Type
import org.jetbrains.kotlin.formver.viper.ast.Method
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Function
import org.jetbrains.kotlin.name.Name

/**
 * Name used to look up properties.
 */
data class PropertyKotlinName(val scope: NameScope, val name: Name)
data class ClassPropertyPair(val className : ScopedName, val propertyName : PropertyKotlinName)

class TypeResolver {
    /**
     * Collection of all ClassTypeEmbeddings and InterfaceTypeEmbeddings
     */
    private val classEmbedding = mutableMapOf<SymbolicName, ClassTypeEmbedding>()
    private val interfaceEmbedding = mutableMapOf<SymbolicName, ClassTypeEmbedding>()

    /**
     * Supertype relation. Key is a subtype, value is a set of supertypes.
     * The transitive closure is not included.
     */
    private val superTypes = mutableMapOf<SymbolicName, MutableSet<SymbolicName>>()

    /**
     * All the properties. Key is the pair of the class name and the field name.
     */
    private val properties = mutableMapOf<ClassPropertyPair, PropertyEmbedding>()

    /**
     * Names of classes marked `@Manual`.
     */
    private val manualClassNames = mutableSetOf<SymbolicName>()

    /**
     * Whether the program embeds `Multiset`. The multiset type and its operations are declared only then, so that
     * Silicon sets up its multiset theory only for programs that use it.
     */
    var usesIterationSequence: Boolean = false
        private set

    fun markIterationSequenceUsed() {
        usesIterationSequence = true
    }

    private val stringLiterals = linkedMapOf<String, Function>()

    /** The function standing for the string literal [value]; equal literals share one. */
    fun stringLiteral(value: String): Function =
        stringLiterals.getOrPut(value) { stringLiteralFunction(StringLiteralName(stringLiterals.size), value) }

    val stringLiteralFunctions: Collection<Function>
        get() = stringLiterals.values

    var usesMultiset: Boolean = false
        private set

    fun markMultisetUsed() {
        usesMultiset = true
    }

    /**
     * Register a class or interface type embedding.
     * This is needed to know which classes were already registered.
     */
    fun register(typeEmbedding: ClassTypeEmbedding, isInterface: Boolean) = when (isInterface) {
        true -> interfaceEmbedding.putIfAbsent(typeEmbedding.name, typeEmbedding)
        false -> classEmbedding.putIfAbsent(typeEmbedding.name, typeEmbedding)
    }

    fun markManual(name: SymbolicName) {
        manualClassNames.add(name)
    }

    val ClassTypeEmbedding.isManual: Boolean
        get() = name in manualClassNames

    private fun toBackingField(property: PropertyEmbedding): FieldEmbedding? =
        (property.getter as? BackingFieldGetter)?.field


    fun isInterface(type: ClassTypeEmbedding): Boolean = type.name in interfaceEmbedding

    fun isImmutableField(name: SymbolicName): Boolean = properties.values.any { property ->
        val field = toBackingField(property)
        property.isVal && field != null && (field.name == name || field.toViper().name == name)
    }

    fun isSubtype(subtype: ClassTypeEmbedding, supertype: ClassTypeEmbedding): Boolean {
        fun reaches(name: SymbolicName): Boolean =
            name == supertype.name || superTypes[name].orEmpty().any { reaches(it) }
        return reaches(subtype.name)
    }

    private val captureFacts = linkedMapOf<SymbolicName, List<Exp>>()

    fun setCaptureFacts(type: ClassTypeEmbedding, facts: List<Exp>) {
        captureFacts[type.name] = facts
    }

    fun captureFactAssertion(type: ClassTypeEmbedding, receiver: Exp): Exp = Exp.LetBinding(
        Declaration.LocalVarDecl(DispatchReceiverName, Type.Ref), receiver,
        captureFacts[type.name].orEmpty().fold<Exp, Exp>(Exp.BoolLit(true)) { acc, fact -> Exp.And(acc, fact) },
    )

    fun hasCaptureFacts(type: ClassTypeEmbedding) = captureFacts[type.name]?.isNotEmpty() == true

    fun captureFacts(type: ClassTypeEmbedding, receiver: Exp): List<Exp> = captureFacts[type.name].orEmpty().map {
        it.rewriteCaptureValues(mapOf(listOf(DispatchReceiverName) to receiver))
    }

    private val classLawBodies = linkedMapOf<SymbolicName, List<Exp>>()
    var classLawExpansion: ClassLawExpansion? = null

    fun registerClassLaws(type: ClassTypeEmbedding) {
        classLawBodies.putIfAbsent(type.name, emptyList())
    }

    fun setClassLaws(type: ClassTypeEmbedding, laws: List<Exp>) {
        classLawBodies[type.name] = laws
    }

    fun hasClassLaws(type: ClassTypeEmbedding) = type.name in classLawBodies

    fun classLawAssertion(type: ClassTypeEmbedding, receiver: Exp, openSuperclasses: Set<ClassTypeEmbedding> = emptySet()): Exp = Exp.LetBinding(
        Declaration.LocalVarDecl(DispatchReceiverName, Type.Ref), receiver,
        generateSequence(superClass(type), ::superClass).filter { it !in openSuperclasses }.toList().foldRight(
            classLawBodies[type.name].orEmpty().fold<Exp, Exp>(Exp.BoolLit(true)) { acc, law -> Exp.And(acc, law) },
        ) { parent, law ->
            Exp.Unfolding(Exp.PredicateAccess(parent.uniquePredicateName,
                listOf(Exp.LocalVar(DispatchReceiverName, Type.Ref)), PermExp.FullPerm()), law)
        },
    )

    private val interfaceLawBodies = linkedMapOf<SymbolicName, List<Exp>>()

    fun setInterfaceLaws(type: ClassTypeEmbedding, laws: List<Exp>) {
        interfaceLawBodies[type.name] = laws
    }

    fun interfaceLaws(type: ClassTypeEmbedding) = interfaceLawBodies[type.name].orEmpty()

    private val observerBindings = linkedMapOf<SymbolicName, MutableMap<SymbolicName, ObserverBinding>>()
    private val observerImplementations = linkedMapOf<SymbolicName, MutableMap<SymbolicName, ObserverBinding>>()

    fun bindObserver(
        type: ClassTypeEmbedding,
        observer: SymbolicName,
        binding: NonInlineFunctionSignature,
        dynamicDispatch: Boolean = false,
        hasImplementation: Boolean = true,
    ) {
        observerBindings.getOrPut(type.name) { linkedMapOf() }[observer] =
            ObserverBinding(type, binding, this, abstractDispatch = isInterface(type) || dynamicDispatch)
        if (!isInterface(type) && hasImplementation) {
            observerImplementations.getOrPut(type.name) { linkedMapOf() }[observer] = ObserverBinding(type, binding, this)
        }
    }

    private fun bindingIn(
        bindings: Map<SymbolicName, MutableMap<SymbolicName, ObserverBinding>>,
        view: ClassTypeEmbedding,
        observer: SymbolicName,
    ): ObserverBinding? = bindings[view.name]?.let {
        it[observer] ?: it.values.firstOrNull { binding -> binding.signature.name == observer }
    }

    /** An observation through the caller's ownership presentation. */
    fun observerBinding(view: ClassTypeEmbedding, observer: SymbolicName): ObserverBinding? =
        bindingIn(observerBindings, view, observer)

    /** The concrete body selected by a runtime class, for implementation proof obligations. */
    fun observerImplementation(runtimeClass: ClassTypeEmbedding, observer: SymbolicName): ObserverBinding? =
        bindingIn(observerImplementations, runtimeClass, observer)

    private val classFamilyViews = mutableSetOf<SymbolicName>()

    fun markClassFamilyView(name: SymbolicName) {
        classFamilyViews += name
    }

    fun isFamilyView(type: ClassTypeEmbedding): Boolean = isInterface(type) || type.name in classFamilyViews

    fun observerBindings(from: ClassTypeEmbedding, to: ClassTypeEmbedding) =
        observerBindings[to.name].orEmpty().map { (observer, target) ->
            val source = observerBindings[from.name]?.get(observer)
                ?: error("Missing observer binding for ${from.name}: $observer")
            source to target
        }.distinctBy { it.second.functionName }

    fun hasRuntimeObserverDispatch(type: ClassTypeEmbedding): Boolean = !isInterface(type) &&
        observerBindings[type.name].orEmpty().values.any { it.functionName != it.signature.name }

    fun observerViewFunctions() = observerBindings.values.flatMap { it.values }
        .mapNotNull { it.function() }.distinctBy { it.name }

    private val familyTransfers = linkedMapOf<Pair<ClassTypeEmbedding, ClassTypeEmbedding>, FamilyTransfer>()

    fun familyTransfer(from: ClassTypeEmbedding, to: ClassTypeEmbedding): FamilyTransfer {
        require(isFamilyView(to) && isSubtype(from, to) && from != to)
        return familyTransfers.getOrPut(from to to) { FamilyTransfer(from, to, this) }
    }

    private val familyRestores = linkedSetOf<Pair<ClassTypeEmbedding, ClassTypeEmbedding>>()

    fun familyRestore(from: ClassTypeEmbedding, to: ClassTypeEmbedding): Method {
        val transfer = familyTransfer(from, to)
        familyRestores += from to to
        return transfer.restore
    }

    fun familyTransferMethods() = familyTransfers.values.map { it.method } +
        familyRestores.map { familyTransfers.getValue(it).restore }

    fun classTypeEmbeddings() = (classEmbedding.values.toList() + interfaceEmbedding.values.toList()).distinctBy { it.name }

    fun predicates() = classTypeEmbeddings().flatMap { listOfNotNull(it.footprintPredicate(), it.uniquePredicate()) }

    fun getEmbeddingOrExecute(name: ScopedName, action: () -> ClassTypeEmbedding) = classEmbedding[name] ?: action()

    fun lookupClassTypeEmbedding(name: SymbolicName) = classEmbedding[name] ?: interfaceEmbedding[name]

    /**
     * Extends the subtype relation with [subtype] <: [supertype]
     */
    fun addSubtypeRelation(subtype: SymbolicName, supertype: SymbolicName) = superTypes.getOrPut(subtype) {
        mutableSetOf()
    }.add(supertype)


    /**
     * Returns the set of super types of a given class or interface.
     * These are only the direct super types, transitive closure is not included.
     */
    fun lookupSuperTypes(name: SymbolicName) =
        superTypes.getOrDefault(name, emptySet()).mapNotNull { lookupClassTypeEmbedding(it) }

    /**
     * Get or Put a property to the class.
     */
    fun getOrPutProperty(name: ClassPropertyPair, create: () -> PropertyEmbedding) = properties.getOrPut(name, create)


    /**
     * Returns all the fields belonging directly to a class.
     */
    fun lookupClassBackingFields(name: SymbolicName) =
        properties.filterKeys { it.className == name }.values.mapNotNull { toBackingField(it) }

    fun lookupBackingField(name: ClassPropertyPair): FieldEmbedding? = properties[name]?.let { toBackingField(it) }

    fun lookupClassDefaultBehavingProperties(name: SymbolicName): List<PropertyEmbedding> =
        lookupClassProperties(name).filter { it.hasDefaultBehaviour }

    fun lookupClassProperties(name: SymbolicName): List<PropertyEmbedding> =
        properties.filterKeys { it.className == name }.values.toList()

    fun lookupDefaultBehavingProperties(name: ClassPropertyPair): PropertyEmbedding? =
        properties[name]?.takeIf { it.hasDefaultBehaviour }


    fun backingFields(): List<FieldEmbedding> = properties.values.mapNotNull { toBackingField(it) }


    /**
     * Collects all the fields belonging to a class and its super types.
     * They are unique with regard to their name.
     */
    private fun collectFields(className: SymbolicName): List<FieldEmbedding> {
        return lookupSuperTypes(className).fold(lookupClassBackingFields(className)) { acc, type ->
            acc + collectFields(type.name)
        }.distinctBy { it.name }
    }

    fun <R> flatMapUniqueFields(
        className: SymbolicName,
        action: (FieldEmbedding) -> List<R>
    ): List<R> = collectFields(className).flatMap { action(it) }


    /** The direct superclass of [classType], `null` when it has none or is an interface. */
    fun superClass(classType: ClassTypeEmbedding): ClassTypeEmbedding? =
        lookupSuperTypes(classType.name).firstOrNull { !interfaceEmbedding.containsKey(it.name) }

    /** Whether [field] is declared in [classType] or one of its superclasses. */
    fun declaresOnChain(classType: ClassTypeEmbedding, field: PathStep): Boolean =
        generateSequence(classType, ::superClass).any { it.name == field.containingClass?.name }

    /**
     * Returns the sequence of class types that are between the [typeEmbedding] and the [field].
     */
    fun hierarchyPathTo(
        typeEmbedding: PretypeEmbedding,
        field: PathStep
    ): Sequence<ClassTypeEmbedding> = sequence {
        val classType = (typeEmbedding as? ClassTypeEmbedding) ?: return@sequence
        val className = field.containingClass?.name
        require(className != null) { "Cannot find hierarchy unfold path of a field with no class information" }
        if (className == typeEmbedding.name) {
            yield(classType)
        } else {
            val sup = superClass(classType) ?: throw IllegalArgumentException("Reached top of the hierarchy without finding the field")

            yield(classType)
            yieldAll(hierarchyPathTo(sup, field))
        }
    }

    /**
     * Returns true if the [pretypeEmbedding] is a subtype of Collection with [name]
     */
    fun isInheritorOfCollectionTypeNamed(pretypeEmbedding: PretypeEmbedding, name: String): Boolean {
        val classEmbedding = pretypeEmbedding as? ClassTypeEmbedding ?: return false
        return classEmbedding.isCollectionTypeNamed(name) || lookupSuperTypes(classEmbedding.name).any {
            isInheritorOfCollectionTypeNamed(it, name)
        }
    }

    fun isCollectionInheritor(pretype: PretypeEmbedding) = isInheritorOfCollectionTypeNamed(pretype, "Collection")

    fun PretypeEmbedding.isCollectionTypeNamed(name: String): Boolean {
        val classEmbedding = this as? ClassTypeEmbedding ?: return false
        NameMatcher.matchGlobalScope(classEmbedding.name) {
            ifInCollectionsPkg {
                ifClassName(name) {
                    return true
                }
            }
            return false
        }
    }

}
