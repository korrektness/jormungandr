/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.domains

import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.embeddings.types.embedClassTypeFunc
import org.jetbrains.kotlin.formver.core.names.DomainName
import org.jetbrains.kotlin.formver.core.names.QualifiedDomainFuncName
import org.jetbrains.kotlin.formver.core.names.UnqualifiedDomainFuncName
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.*
import org.jetbrains.kotlin.name.StandardClassIds


const val RUNTIME_TYPE_DOMAIN_NAME = "rt"


/** Runtime type membership, nullability and primitive value injections. */
class RuntimeTypeDomain(typeResolver: TypeResolver) : BuiltinDomain(DomainName(RUNTIME_TYPE_DOMAIN_NAME)) {
    override val typeVars: List<Type.TypeVar> = emptyList()

    // Define types that are not dependent on the user defined classes in a companion object.
    // That way other classes can refer to them without having an explicit reference to the concrete TypeDomain.
    companion object {
        private val domainName = DomainName(RUNTIME_TYPE_DOMAIN_NAME)
        val RuntimeType: Type.Domain = Type.Domain(domainName, emptyList())
        val Ref = Type.Ref

        fun createDomainFunc(
            funcName: SymbolicName, args: List<Declaration.LocalVarDecl>, type: Type, unique: Boolean = false
        ) = DomainFunc(
            QualifiedDomainFuncName(domainName, funcName), domainName, args, emptyList(), type, unique
        )

        private fun createNewTypeDomainFunc(funcName: SymbolicName) = createDomainFunc(
            funcName,
            emptyList(),
            RuntimeType,
            true,
        )

        private fun createNewTypeDomainFunc(funcName: String) = createNewTypeDomainFunc(
            UnqualifiedDomainFuncName(funcName)
        )
        // variables for readability improving

        private val t = domainVar("t", RuntimeType)
        private val t1 = domainVar("t1", RuntimeType)
        private val t2 = domainVar("t2", RuntimeType)
        private val t3 = domainVar("t3", RuntimeType)

        private val r = domainVar("r", Ref)

        // three basic functions
        /** `isSubtype: (Type, Type) -> Bool` */
        val isSubtype: DomainFunc =
            createDomainFunc(UnqualifiedDomainFuncName("isSubtype"), listOf(t1.decl(), t2.decl()), Type.Bool)
        infix fun Exp.subtype(otherType: Exp) = isSubtype(this, otherType)
        /** `typeOf: Ref -> Type` */
        val typeOf: DomainFunc = createDomainFunc(UnqualifiedDomainFuncName("typeOf"), listOf(r.decl()), RuntimeType)
        /** `nullable: Type -> Type` */
        val nullable: DomainFunc =
            createDomainFunc(UnqualifiedDomainFuncName("nullable"), listOf(t.decl()), RuntimeType)
        // many axioms will use `is` which can be represented as composition of `isSubtype` and `typeOf`
        /** `is: (Ref, Type) -> Bool` */
        infix fun Exp.isOf(elemType: Exp) = isSubtype(typeOf(this), elemType)

        // built-in types function
        val charType: DomainFunc = createNewTypeDomainFunc("charType")
        val intType: DomainFunc = createNewTypeDomainFunc("intType")
        val longType: DomainFunc = createNewTypeDomainFunc("longType")
        val shortType: DomainFunc = createNewTypeDomainFunc("shortType")
        val byteType: DomainFunc = createNewTypeDomainFunc("byteType")
        val boolType: DomainFunc = createNewTypeDomainFunc("boolType")
        val unitType: DomainFunc = createNewTypeDomainFunc("unitType")
        val stringType: DomainFunc = createNewTypeDomainFunc("stringType")
        val multisetType: DomainFunc = createNewTypeDomainFunc("multisetType")
        val iterationSequenceType: DomainFunc = createNewTypeDomainFunc("iterationSequenceType")
        val nothingType: DomainFunc = createNewTypeDomainFunc("nothingType")
        val anyType: DomainFunc = createNewTypeDomainFunc("anyType")
        val functionType: DomainFunc = createNewTypeDomainFunc("functionType")

        // for creation of user types
        fun classTypeFunc(name: SymbolicName) = createDomainFunc(name, emptyList(), RuntimeType, true)

        // bijections to primitive types
        val intInjection = Injection(UnqualifiedDomainFuncName("int"), Type.Int, intType, IntegerBounds::int)
        val longInjection = Injection(UnqualifiedDomainFuncName("long"), Type.Int, longType, IntegerBounds::long)
        val shortInjection = Injection(UnqualifiedDomainFuncName("short"), Type.Int, shortType, IntegerBounds::short)
        val byteInjection = Injection(UnqualifiedDomainFuncName("byte"), Type.Int, byteType, IntegerBounds::byte)
        val boolInjection = Injection(UnqualifiedDomainFuncName("bool"), Type.Bool, boolType)
        val charInjection = Injection(UnqualifiedDomainFuncName("char"), Type.Int, charType, IntegerBounds::char)
        val stringInjection = Injection(UnqualifiedDomainFuncName("string"), Type.Seq(Type.Int), stringType, IntegerBounds::string)
        val multisetInjection =
            Injection(UnqualifiedDomainFuncName("multiset"), Type.Multiset(Type.Int), multisetType)
        val iterationSequenceInjection =
            Injection(UnqualifiedDomainFuncName("iterationSequence"), Type.Seq(Type.Ref), iterationSequenceType, IntegerBounds::sequence)
        val primitiveTypeInjections = listOf(intInjection, longInjection, shortInjection, byteInjection, boolInjection, charInjection, stringInjection)
        // special values
        val nullValue = createDomainFunc(UnqualifiedDomainFuncName("nullValue"), emptyList(), Ref)
        val unitValue = createDomainFunc(UnqualifiedDomainFuncName("unitValue"), emptyList(), Ref)
    }

    // The interfaces `String` implements. They are class types, in the domain only when the program refers to them.
    private val stringSuperTypeNames = setOf(StandardClassIds.CharSequence.embedName(), StandardClassIds.Comparable.embedName())

    private val usesMultiset = typeResolver.usesMultiset
    private val allInjections: List<Injection> =
        primitiveTypeInjections + listOfNotNull(
            multisetInjection.takeIf { usesMultiset },
            iterationSequenceInjection.takeIf { typeResolver.usesIterationSequence },
        )
    val builtinTypes: List<DomainFunc> =
        listOf(intType, longType, shortType, byteType, boolType, charType, unitType, nothingType, anyType, functionType, stringType) +
                listOfNotNull(multisetType.takeIf { usesMultiset }, iterationSequenceType.takeIf { typeResolver.usesIterationSequence })
    private val userTypes: List<DomainFunc> =
        typeResolver.classTypeEmbeddings().map { it.embedClassTypeFunc() }
    val nonNullableTypes: List<DomainFunc> = (builtinTypes + userTypes).distinctBy { it.name }
    override val functions: List<DomainFunc> = nonNullableTypes + listOf(
        nullValue, unitValue, isSubtype, typeOf, nullable
    ) + allInjections.flatMap { listOf(it.toRef, it.fromRef) }
    override val axioms: List<DomainAxiom> = AxiomListBuilder.build(this) {
        axiom("subtypeReflexive") {
            Exp.forall(t) { t -> t subtype t }
        }
        axiom("subtypeTransitive") {
            Exp.forall(t1, t2, t3) { t1, t2, t3 ->
                assumption {
                    compoundTrigger {
                        subTrigger { t1 subtype t2 }
                        subTrigger { t2 subtype t3 }
                    }
                }
                compoundTrigger {
                    subTrigger { t1 subtype t2 }
                    subTrigger { t1 subtype t3 }
                }
                compoundTrigger {
                    subTrigger { t2 subtype t3 }
                    subTrigger { t1 subtype t3 }
                }
                t1 subtype t3
            }
        }
        axiom("subtypeAntisymmetric") {
            Exp.forall(t1, t2) { t1, t2 ->
                assumption {
                    compoundTrigger {
                        subTrigger { t1 subtype t2 }
                        subTrigger { t2 subtype t1 }
                    }
                }
                t1 eq t2
            }
        }
        axiom("nullableIdempotent") {
            Exp.forall(t) { t ->
                simpleTrigger { nullable(nullable(t)) } eq nullable(t)
            }
        }
        axiom("nullableSupertype") {
            Exp.forall(t) { t ->
                t subtype simpleTrigger { nullable(t) }
            }
        }
        axiom("nullablePreservesSubtype") {
            Exp.forall(t1, t2) { t1, t2 ->
                assumption { t1 subtype t2 }
                simpleTrigger { nullable(t1) subtype nullable(t2) }
            }
        }
        axiom("nullableAnySupertype") {
            Exp.forall(t) { t ->
                t subtype nullable(anyType())
            }
        }
        nonNullableTypes.forEach {
            axiom { it() subtype anyType() }
        }
        axiom("supertypeOfNothing") {
            Exp.forall(t) { t ->
                nothingType() subtype t
            }
        }
        axiom("anyNotNullableTypeLevel") {
            Exp.forall(t) { t ->
                !isSubtype(nullable(t), anyType())
            }
        }
        axiom("nullSmartcastValueLevel") {
            Exp.forall(r, t) { r, t ->
                assumption {
                    simpleTrigger { r isOf nullable(t) }
                }
                (r eq nullValue()) or (r isOf t)
            }
        }
        axiom("nothingEmpty") {
            Exp.forall(r) { r ->
                !(r isOf nothingType())
            }
        }
        axiom("nullSmartcastTypeLevel") {
            Exp.forall(t1, t2) { t1, t2 ->
                assumption {
                    compoundTrigger {
                        subTrigger { t1 subtype anyType() }
                        subTrigger { t1 subtype nullable(t2) }
                    }
                }
                t1 subtype t2
            }
        }
        axiom("typeOfNull") {
            nullValue() isOf nullable(nothingType())
        }
        axiom("anyNotNullableValueLevel") {
            !(nullValue() isOf anyType())
        }
        axiom("typeOfUnit") {
            unitValue() isOf unitType()
        }
        axiom("uniquenessOfUnit") {
            Exp.forall(r) { r ->
                assumption {
                    simpleTrigger { r isOf unitType() }
                }
                r eq unitValue()
            }
        }
        allInjections.forEach {
            it.apply { injectionAxioms() }
        }
        typeResolver.classTypeEmbeddings().filter { it.name in stringSuperTypeNames }.forEach { superType ->
            axiom {
                stringType() subtype superType.runtimeType
            }
        }
        typeResolver.classTypeEmbeddings().forEach { type ->
            typeResolver.lookupSuperTypes(type.name).forEach { superType ->
                axiom {
                    type.runtimeType subtype superType.runtimeType
                }
            }
        }
    }
}
