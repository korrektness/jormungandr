package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.text
import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.ContextIndependentParameterRenderer
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirDiagnosticRenderers
import org.jetbrains.kotlin.fir.declarations.utils.memberDeclarationNameOrNull
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.types.renderReadable

/** The FIR and test adapters share this rendering boundary. */
object FailureToFirRenderer : BaseDiagnosticRendererFactory() {
    override val MAP: KtDiagnosticFactoryToRendererMap by KtDiagnosticFactoryToRendererMap("SnaKtFailures") { map ->
        val renderer = object : ContextIndependentParameterRenderer<Failure> {
            override fun render(obj: Failure): String = message(obj, obj.errorStyle)
        }
        FailureKind.entries.forEach { map.put(it.factory, "{0}", renderer) }
    }

    @OptIn(InternalDiagnosticFactoryMethod::class)
    fun diagnostic(failure: Failure, settings: LanguageVersionSettings): KtDiagnostic? =
        failure.primary?.let {
            failure.kind.factory.on(it, failure,
                positioningStrategy = if (failure.kind.declarationName) SourceElementPositioningStrategies.DECLARATION_NAME
                    else SourceElementPositioningStrategies.DEFAULT,
                languageVersionSettings = settings,
            )
        }

    fun message(failure: Failure, style: ErrorStyle = failure.errorStyle): String {
        val friendly = when (val condition = failure.condition) {
            is KotlinCondition.SpecOf -> specOfMessage(condition)
            is KotlinCondition.Law -> lawMessage(condition)
            is KotlinCondition.Observer -> observerMessage(condition)
            is KotlinCondition.Exposes -> exposesMessage(condition)
            is KotlinCondition.ExternalSpec -> externalSpecMessage(condition)
            else -> obligationMessage(failure)
        }
        val backend = failure.backend
        val message = when (style) {
            ErrorStyle.USER_FRIENDLY -> friendly
            ErrorStyle.ORIGINAL_VIPER -> backend?.let { "Viper verification error [${it.errorId}/${it.reasonId}]: ${it.detail}" } ?: friendly
            ErrorStyle.BOTH -> if (backend == null || failure.condition == KotlinCondition.Unclassified) friendly
                else "$friendly\nBackend [${backend.errorId}/${backend.reasonId}]: ${backend.detail}"
        }
        val bindings = failure.counterexample?.bindings.orEmpty()
        val heading = if (failure.counterexample?.unchecked == true) "Counterexample (unchecked)" else "Counterexample"
        return if (bindings.isEmpty()) message else message + "\n$heading:\n" + bindings.joinToString("\n") {
            val path = when (val label = it.oldState) {
                null -> it.path
                "old" -> "old(${it.path})"
                else -> "old[$label](${it.path})"
            }
            val location = it.source?.let(::counterexampleLocation)?.let { location -> " ($location)" }.orEmpty()
            "  $path = ${counterexampleValue(it.value)}$location"
        }
    }

    private fun counterexampleLocation(source: org.jetbrains.kotlin.KtSourceElement): String? {
        val text = when (source) {
            is org.jetbrains.kotlin.KtPsiSourceElement -> source.psi.containingFile.text
            is org.jetbrains.kotlin.KtLightSourceElement -> source.treeStructure.toString(source.treeStructure.root).toString()
            else -> return null
        }
        val before = text.take(source.startOffset)
        val line = before.count { it == '\n' } + 1
        val column = before.length - before.lastIndexOf('\n')
        val file = (source as? org.jetbrains.kotlin.KtPsiSourceElement)?.psi?.containingFile?.name
        return "${file?.let { "$it:" }.orEmpty()}$line:$column"
    }

    private fun counterexampleValue(value: CounterexampleValue): String = when (value) {
        is CounterexampleValue.Integer -> value.value.toString()
        is CounterexampleValue.BooleanValue -> value.value.toString()
        is CounterexampleValue.Character -> "'" + when (value.value) {
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            '\'' -> "\\'"
            '\\' -> "\\\\"
            else -> if (value.value.isISOControl()) "\\u%04x".format(value.value.code) else value.value.toString()
        } + "'"
        CounterexampleValue.Null -> "null"
        is CounterexampleValue.EnumLike -> value.names.joinToString(" / ")
    }

    private fun specOfMessage(condition: KotlinCondition.SpecOf): String = "Invalid @SpecOf: " + when (condition.rule) {
                SpecOfRule.EXTENSION_RECEIVER -> "extension receiver."
                SpecOfRule.RETURN_TYPE -> "return type."
                SpecOfRule.MISSING_MEMBER -> "no member '${condition.targetName}'."
                SpecOfRule.OVERLOAD -> "no overload of '${condition.targetName}' with these parameter types."
                SpecOfRule.MULTIPLE_SPECIFICATIONS -> "several @SpecOf functions for '${condition.targetName}'."
                SpecOfRule.OWN_SPECIFICATION -> "target has its own specification."
                SpecOfRule.PARAMETER_OWNERSHIP -> "parameter ownership."
                SpecOfRule.BODY_SHAPE -> "body shape."
                SpecOfRule.POSTCONDITIONS_TYPE -> "postconditions type."
                SpecOfRule.CALL -> "call."
            }

    private fun lawMessage(condition: KotlinCondition.Law): String = when (condition.rule) {
                LawRule.OVERRIDE -> "Overridden law."
                LawRule.OWNER -> "Law declared outside a class or interface."
                LawRule.PURITY -> "Missing @Pure on law."
                LawRule.RESULT_TYPE -> "Non-Boolean law result."
                LawRule.BODY -> "Bodiless law."
                LawRule.PARAMETERS -> "Law parameters."
                LawRule.IMPLICIT_OVERRIDE -> "Implicit law override."
                LawRule.MUTABLE_LOCAL -> "Mutable local in law."
                LawRule.ASSIGNMENT -> "Assignment in law."
                LawRule.NON_VALUE_ACCESS -> "Non-value read or call in law."
                LawRule.INHERITED_VAR_READ -> "Invalid inherited var read; see Class laws."
                LawRule.ACCESSOR_PROPERTY_READ -> "Read of a property with accessor methods; see Class laws."
                LawRule.ABSTRACT_OBSERVER -> "Abstract observer in class law."
                LawRule.RECURSIVE_OBSERVER -> "Recursive observer in class law."
                LawRule.DELEGATED_PROPERTY -> "Delegated property; see Class laws."
                LawRule.CONSTRUCTION_THIS_USE -> "Phase-one this use; see Class laws."
                LawRule.CONSTRUCTION_CALL -> "Phase-one member call; see Class laws."
                LawRule.CONSTRUCTION_READ -> "Phase-one property read; see Class laws."
                LawRule.CONSTRUCTION_VIRTUAL_DISPATCH -> "Overridable constructor dispatch; see Class laws."
                LawRule.CONSTRUCTION_INCOMPLETE -> "Incomplete constructor assignment; see Class laws."
            }

    private fun observerMessage(condition: KotlinCondition.Observer): String = when (condition.rule) {
                ObserverRule.GHOST_OVERRIDE -> "Ghost observer override: '${memberSpelling(requireNotNull(condition.member))}'."
                ObserverRule.MUTABLE_PURE_PROPERTY -> "Invalid @Pure property: mutable property."
                ObserverRule.IMPURE_OVERRIDE -> "Invalid observer override: not @Pure."
                ObserverRule.NON_OBSERVER_OVERRIDE -> "Invalid @Pure override: '${memberSpelling(requireNotNull(condition.member))}' is not an observer."
                ObserverRule.DELEGATED_IMPLEMENTATION -> "Invalid observer override: '${memberSpelling(requireNotNull(condition.member))}' is delegated, so it has no body."
                ObserverRule.IMPURE_IMPLEMENTATION -> "Invalid observer override: '${memberSpelling(requireNotNull(condition.member))}' is implemented by '${memberSpelling(requireNotNull(condition.implementation))}', which is not @Pure."
                ObserverRule.GHOST_OWNER -> "Ghost observer outside a fun interface."
                ObserverRule.GHOST_PURITY -> "Missing @Pure on ghost observer."
                ObserverRule.GHOST_SUSPEND -> "Suspend ghost observer."
                ObserverRule.GHOST_LAW -> "Ghost observer marked @Law."
                ObserverRule.GHOST_PARAMETERS -> "Ghost observer parameters."
                ObserverRule.GHOST_BODY -> "Non-placeholder ghost observer body."
                ObserverRule.GHOST_REFERENCE -> "Ghost observer reference."
                ObserverRule.GHOST_PLACEHOLDER_USE -> "Ghost placeholder use."
                ObserverRule.EXECUTABLE_GHOST_USE -> "Executable ghost observer use."
            }

    private fun exposesMessage(condition: KotlinCondition.Exposes): String = when (condition.rule) {
                ExposesRule.REFERENCE -> "Observer binding reference."
                ExposesRule.MISSING_BINDINGS -> "Missing observer bindings."
                ExposesRule.OUTSIDE_SAM_LAMBDA -> "Observer bindings outside a SAM lambda."
                ExposesRule.PLACEMENT -> "Misplaced observer bindings."
                ExposesRule.OUTSIDE_SAM_CONVERSION -> "Observer bindings outside a SAM conversion."
                ExposesRule.CALLABLE_SPECIFICATION -> "Invalid callable specification."
                ExposesRule.SPECIFICATION_TOKEN -> "Invalid specification token."
                ExposesRule.NON_LITERAL_TOKEN -> "Non-literal specification token."
                ExposesRule.MISMATCHED_SPECIFICATION -> "Mismatched callable specification."
                ExposesRule.NON_LITERAL_BINDINGS -> "Non-literal observer bindings."
                ExposesRule.NON_LAMBDA_BINDINGS -> "Non-lambda observer bindings."
                ExposesRule.EMPTY_BINDINGS -> "Empty observer bindings."
                ExposesRule.INCOMPLETE_BINDINGS -> "Incomplete observer bindings."
                ExposesRule.BINDING_SHAPE -> "Invalid observer binding."
                ExposesRule.BINDING_OPERATOR -> "Invalid observer binding operator."
                ExposesRule.BINDING_TARGET -> "Invalid binding target."
                ExposesRule.FOREIGN_BINDING -> "Foreign observer binding."
                ExposesRule.BINDING_VALUE -> "Invalid observer binding value."
                ExposesRule.PARAMETER_DEPENDENT_VALUE -> "Observer binding value reads a lambda parameter."
                ExposesRule.DUPLICATE_BINDING -> "Duplicate observer binding."
            }

    private fun externalSpecMessage(condition: KotlinCondition.ExternalSpec): String {
        val target = condition.target
        return when (condition.rule) {
            ExternalSpecRule.MISSING_TARGET_ARGUMENT -> "Unresolved external target argument: $target"
            ExternalSpecRule.INVALID_TARGET_NAME -> "Invalid external target name: $target"
            ExternalSpecRule.OUTSIDE_SPECIFICATION_INPUTS -> "External model outside loaded specification inputs: $target"
            ExternalSpecRule.MODEL_NOT_TOP_LEVEL -> "An external function or property model must be top-level."
            ExternalSpecRule.MEMBER_OUTSIDE_CLASS_MODEL -> "@ExternalMember outside an external class model."
            ExternalSpecRule.CLASS_MODEL_NOT_INTERFACE -> "An external class model must be an interface."
            ExternalSpecRule.AMBIGUOUS_CLASS -> "Ambiguous external class: $target"
            ExternalSpecRule.UNRESOLVED_CLASS -> "Unresolved external class: $target"
            ExternalSpecRule.SOURCE_TARGET -> "External targets must come from a dependency: $target"
            ExternalSpecRule.DUPLICATE_CLASS -> "Duplicate external class binding: $target"
            ExternalSpecRule.TYPE_PARAMETERS -> "Incompatible external class type parameters: $target"
            ExternalSpecRule.TYPE_PARAMETER_BOUNDS -> "Incompatible external class type parameter bounds: $target"
            ExternalSpecRule.UNSUPPORTED_MEMBER -> "Unsupported external member kind."
            ExternalSpecRule.NO_COMPATIBLE_TARGET -> "External binding requires one compatible target: $target"
            ExternalSpecRule.DUPLICATE_MEMBER -> "Duplicate external member binding: $target"
            ExternalSpecRule.SOURCE_SUBTYPE -> "Source subtype of externally modeled class: $target"
        }
    }

    private fun obligationMessage(failure: Failure): String = when (val condition = failure.condition) {
            is KotlinCondition.IntegerOverflow ->
                "Could not establish that ${operator(condition.operation)} stays within the ${condition.resultType} range [${condition.lower}, ${condition.upper}]."
            is KotlinCondition.DivisionByZero -> "Could not establish a nonzero divisor for ${operator(condition.operation)}."
            is KotlinCondition.Proof -> proofMessage(condition)
            is PurityCondition -> when (condition.rule) {
                PurityRule.FUNCTION_BODY -> "Impure function body detected in pure function"
                PurityRule.LAW_BODY -> "Impure law body"
                PurityRule.ASSERT_CONDITION -> "Assert condition is impure"
                PurityRule.REFUTE_CONDITION -> "Refute condition is impure"
            }
            is ConversionOwnershipCondition -> conversionOwnershipMessage(condition)
            is TerminationCondition -> terminationMessage(condition)
            KotlinCondition.Termination -> "Could not establish that this recursive call decreases the termination measure."
            is AttributeCondition -> attributeMessage(failure.kind, condition)
            is KotlinCondition.Returns -> "Could not establish that the function never returns ${article(opposite(condition.effect))}."
            is KotlinCondition.Conditional -> {
                val value = if (condition.effect == KotlinCondition.ReturnValue.ANY) "" else " ${returnValue(condition.effect)}"
                "Could not establish `${predicate(condition.predicate)}` when the function returns$value."
            }
            is KotlinCondition.Index -> {
                val bound = when (condition.bound) {
                    KotlinCondition.Bound.NEGATIVE -> "is not negative"
                    KotlinCondition.Bound.NOT_BELOW_SIZE, KotlinCondition.Bound.ABOVE_SIZE -> "is less than its size"
                }
                "Could not establish that the index into ${collection(condition.collection, condition.symbol)} $bound."
            }
            is KotlinCondition.SubList -> {
                val bound = if (condition.negative) "has no negative index" else "ends within its size"
                "Could not establish that the sub-list range of ${collection(KotlinCondition.Collection.LIST, condition.symbol)} $bound."
            }
            is KotlinCondition.Ownership -> ownershipMessage(condition)
            is KotlinCondition.Override -> {
                val reason = when (condition.clause) {
                    KotlinCondition.OverrideClause.PRECONDITION -> "its precondition may not follow from the overridden one"
                    KotlinCondition.OverrideClause.POSTCONDITION -> "its postcondition may not establish the overridden one"
                }
                "Could not establish that this override satisfies the contract of '${condition.overridden}': $reason."
            }
            is KotlinCondition.Skipped -> skippedMessage(condition)
            is KotlinCondition.Translation -> translationMessage(failure.kind, condition)
            is KotlinCondition.Tool -> toolMessage(failure.kind, condition)
            is KotlinCondition.Timeout ->
                "Verification of '${condition.declaration}' did not finish within ${condition.seconds} seconds, so it is not verified."
            KotlinCondition.UntrackedWrite -> "Write is not tracked because the local it goes through is shared."
            is KotlinCondition.UncheckedLibraryCall ->
                "Call to '${condition.member}' is not checked against its specification because its receiver is not unique. It may throw, and its result is unknown."
            KotlinCondition.Unclassified -> {
                val evidence = requireNotNull(failure.backend)
                "Could not establish a generated Kotlin obligation [${evidence.errorId}/${evidence.reasonId}]: ${evidence.detail}"
            }
            is KotlinCondition.SpecOf, is KotlinCondition.Law, is KotlinCondition.Observer, is KotlinCondition.Exposes,
            is KotlinCondition.ExternalSpec ->
                error("Specification conditions must use their specification renderer")
    }

    private fun proofMessage(condition: KotlinCondition.Proof): String {
        val message = when (condition.clause) {
            KotlinCondition.ProofClause.ASSERTION -> "Could not establish the assertion."
            KotlinCondition.ProofClause.REFUTATION -> "Could not refute the condition."
            KotlinCondition.ProofClause.LAW -> "Could not establish law '${FirDiagnosticRenderers.DECLARATION_NAME.render(requireNotNull(condition.declaration))}' for this implementation."
            KotlinCondition.ProofClause.CLASS_LAW -> "Could not establish class law '${memberSpelling(requireNotNull(condition.declaration) as org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol<*>)}' for ${requireNotNull(condition.subject)}."
            KotlinCondition.ProofClause.PRECONDITION ->
                "Could not establish the precondition${condition.declaration?.let { " of '${FirDiagnosticRenderers.DECLARATION_NAME.render(it)}'" }.orEmpty()} at this call."
            KotlinCondition.ProofClause.POSTCONDITION -> "Could not establish the postcondition on return."
            KotlinCondition.ProofClause.POSTCONDITION_AT_RETURN -> "Could not establish the postcondition on this return."
            KotlinCondition.ProofClause.POSTCONDITION_AT_END -> "Could not establish the postcondition at the end of this body."
            KotlinCondition.ProofClause.LOOP_INVARIANT -> when (condition.loopPhase) {
                KotlinCondition.LoopPhase.ENTRY -> "Could not establish the loop invariant on entry to the loop."
                KotlinCondition.LoopPhase.PRESERVATION -> "Could not establish that an iteration preserves the loop invariant."
                KotlinCondition.LoopPhase.EXIT -> "Could not establish the loop invariant where the loop exits."
                null -> "Could not establish the loop invariant."
            }
            KotlinCondition.ProofClause.EXHAUSTIVE_WHEN -> "Could not establish that a branch of this exhaustive 'when' matches."
        }
        val clause = condition.clauseSource ?: return message
        val text = clause.text?.toString()?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return message
        val shown = if (text.length > MAX_CLAUSE_LENGTH) text.take(MAX_CLAUSE_LENGTH) + "..." else text
        val location = counterexampleLocation(clause)?.let { " ($it)" }.orEmpty()
        return "$message\nFailing clause: `$shown`$location"
    }

    private fun translationMessage(kind: FailureKind, condition: KotlinCondition.Translation): String = when (kind) {
        FailureKind.UNSUPPORTED_FEATURE -> "Unsupported construct: ${condition.detail} [${condition.reason}]"
        FailureKind.VERIFICATION_SKIPPED -> "Verification skipped: ${condition.detail} [${condition.reason}]"
        else -> "${condition.detail} [${condition.reason}]"
    }

    private fun toolMessage(kind: FailureKind, condition: KotlinCondition.Tool): String = when (kind) {
        FailureKind.VERIFIER_UNAVAILABLE -> "The verifier cannot run: ${condition.detail} [${condition.reason}]"
        FailureKind.INVALID_VIPER_PROGRAM -> "Invalid generated Viper program: ${condition.detail} [${condition.reason}]"
        else -> "An internal error has occurred: ${condition.detail} [${condition.reason}]\nPlease report this at https://github.com/jesyspa/SnaKt/issues"
    }

    private fun ownershipMessage(condition: KotlinCondition.Ownership): String {
        val (path, site) = when (condition.site) {
            KotlinCondition.OwnershipSite.UNFOLD -> condition.path to "to unfold it"
            KotlinCondition.OwnershipSite.FOLD -> condition.path to "to fold it"
            KotlinCondition.OwnershipSite.CONSTRUCTION -> "the arguments stored in a new ${condition.path}" to "to fold it"
            KotlinCondition.OwnershipSite.HAVOC -> condition.path to "to havoc it after a call"
            KotlinCondition.OwnershipSite.LOOP_HEAD -> condition.path to "at the loop head"
            KotlinCondition.OwnershipSite.PRECONDITION -> "the argument for parameter ${condition.path}" to "at the call to `${condition.function}`"
            KotlinCondition.OwnershipSite.POSTCONDITION -> condition.path to "when `${condition.function}` returns"
        }
        return "Could not establish ownership of $path $site."
    }

    private fun skippedMessage(condition: KotlinCondition.Skipped): String {
        val reason = when (condition.reason) {
            KotlinCondition.SkipReason.CONVERSION_ERRORS -> "errors in its declaration"
            KotlinCondition.SkipReason.UNIQUENESS_OR_LOCALITY_ERRORS -> "uniqueness or locality errors"
            KotlinCondition.SkipReason.UNSUPPORTED_OWNERSHIP -> "unsupported ownership"
            KotlinCondition.SkipReason.INTERNAL_ERROR -> "an internal error"
            KotlinCondition.SkipReason.MISSING_TERMINATION_MEASURE -> "recursion without a termination measure"
        }
        val name = when (val function = condition.function) {
            null -> "<anonymous>"
            is FirPropertyAccessorSymbol -> "<${if (function.isGetter) "get" else "set"}-${function.propertySymbol.name}>"
            else -> FirDiagnosticRenderers.DECLARATION_NAME.render(function)
        }
        return "Verification skipped: function '$name' was not verified because of $reason [${condition.reason.name}]"
    }

    private fun terminationMessage(condition: TerminationCondition): String = when (condition.rule) {
        TerminationRule.MISSING_MEASURE ->
            "Recursive @Pure function has no termination measure: ${condition.cycle.joinToString(" -> ")}. Add decreases(...) to its preconditions."
        TerminationRule.IMPURE_FUNCTION -> "A termination measure is allowed only on a @Pure function."
        TerminationRule.DUPLICATE -> "A function has at most one decreases(...)."
        TerminationRule.MEASURE_TYPE -> "A termination measure must be an Int or a reference to an object of a class."
    }

    private fun conversionOwnershipMessage(condition: ConversionOwnershipCondition): String = when (condition) {
        is ConversionOwnershipCondition.Read -> {
            val (read, owner) = when (condition.kind) {
                ConversionOwnershipCondition.ReadKind.VAR_PROPERTY -> "this var property" to "receiver"
                ConversionOwnershipCondition.ReadKind.ARRAY_ELEMENT -> "an array element" to "array"
                ConversionOwnershipCondition.ReadKind.ARRAY_CONTENTS -> "the contents of an array" to "array"
                ConversionOwnershipCondition.ReadKind.STRING_BUILDER -> "a StringBuilder" to "builder"
            }
            when (condition.reason) {
                ConversionOwnershipCondition.ReadReason.NEEDS_UNIQUE_RECEIVER -> "Reading $read needs a @Unique $owner."
                ConversionOwnershipCondition.ReadReason.CONSUMED_ROOT -> {
                    val root = if (condition.receiver?.root?.kind == ConversionOwnershipPath.Kind.DISPATCH_RECEIVER) "`this`"
                        else "this @Unique parameter"
                    "Reading $read in a postcondition needs a @Borrowed root; the function consumes $root."
                }
            }
        }
        is ConversionOwnershipCondition.ConsumedArgument ->
            "Passing this @Unique parameter to a pure function in a postcondition needs a @Borrowed root; the function consumes it."
        is ConversionOwnershipCondition.MissingFacts -> when (condition.rule) {
            ConversionOwnershipCondition.MissingFactsRule.INLINE_FUNCTION ->
                "The uniqueness checker has no facts for the inline function '${condition.declaration.name}'."
            ConversionOwnershipCondition.MissingFactsRule.LAMBDA_BODY -> "The uniqueness checker has no state for this lambda body."
        }
        ConversionOwnershipCondition.TryWithOwnership -> "`try` is not supported in a function that holds ownership."
        is FoldOwnershipCondition -> foldOwnershipMessage(condition)
    }

    fun ownershipPath(value: ConversionOwnershipPath): String {
        val root = when (value.root.kind) {
            ConversionOwnershipPath.Kind.DISPATCH_RECEIVER -> "this"
            ConversionOwnershipPath.Kind.RESULT -> "the result"
            else -> value.root.name ?: value.root.symbol?.memberDeclarationNameOrNull?.toString() ?: "a temporary value"
        }
        val members = value.members.joinToString("") { ".${it.name ?: it.symbol?.name}" }
        return if (value.root.kind == ConversionOwnershipPath.Kind.RESULT) root + members else "`$root$members`"
    }

    private const val MAX_SITE_SNIPPET_LENGTH = 40
    private const val MAX_CLAUSE_LENGTH = 80

    private fun siteSnippet(site: KtSourceElement?): String? {
        val text = site?.text?.lineSequence()?.firstOrNull()?.trim() ?: return null
        return "`${if (text.length > MAX_SITE_SNIPPET_LENGTH) text.take(MAX_SITE_SNIPPET_LENGTH) + "..." else text}`"
    }

    private fun ownershipAbsence(value: OwnershipAbsence): String {
        val site = siteSnippet(value.site)
        return when (value.reason) {
            OwnershipAbsenceReason.NEVER_HELD -> "was not tracked as owned before this point"
            OwnershipAbsenceReason.CONSUMED -> "was consumed" + (site?.let { " by $it" } ?: "")
            OwnershipAbsenceReason.MOVED -> "was moved to ${ownershipPath(requireNotNull(value.target))}" + (site?.let { " by $it" } ?: "")
            OwnershipAbsenceReason.JOINED -> "was dropped where control flow joins" + (site?.let { " at $it" } ?: "")
            OwnershipAbsenceReason.FRAMED -> "is kept outside the loop" + (site?.let { " at $it" } ?: "")
        }
    }

    private fun foldOwnershipMessage(value: FoldOwnershipCondition): String {
        fun path() = ownershipPath(requireNotNull(value.path))
        fun absent() = ownershipPath(requireNotNull(value.absent))
        fun type() = value.typeName ?: "anonymous class"
        val message = when (value.rule) {
            FoldOwnershipRule.NOT_HELD -> {
                val subject = if (value.absent == value.path) "it" else absent()
                "Ownership of ${path()} is needed here, but $subject ${ownershipAbsence(requireNotNull(value.absence))}."
            }
            FoldOwnershipRule.HOLE_BELOW -> "Ownership of all of ${path()} is needed here, but ${absent()} ${ownershipAbsence(requireNotNull(value.absence))}."
            FoldOwnershipRule.NOT_SUPERTYPE -> "Ownership of ${path()} as `${type()}` is needed here, but `${type()}` is not a supertype of its type."
            FoldOwnershipRule.LOOP_INVARIANT_HOLE -> "Ownership of all of ${path()} is needed by a loop invariant, but ${absent()} is not held at the loop head."
            FoldOwnershipRule.UNTRACKED_WRITE_RECEIVER -> {
                val written = siteSnippet(value.writeSite)?.let { " by $it" } ?: ""
                "Ownership of the receiver written$written is needed here, but the receiver is not a variable followed by `@Unique` properties, so its permissions are not tracked."
            }
            FoldOwnershipRule.BORROWED_ASSIGNMENT -> "${path()} is assigned while an inline function borrows it, so the borrowed value cannot be handed back."
            FoldOwnershipRule.DOWNCAST -> "Ownership cannot be downcast to `${type()}`."
        }
        return "$message [${value.rule.name}]"
    }

    fun locality(value: Locality): String = value.name.lowercase()
    fun uniqueness(value: Uniqueness): String = value.name.lowercase()

    fun path(value: KotlinPath): String = value.joinToString(".") {
        when (it) {
            is org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol,
            is org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol<*> -> "this"
            else -> it.memberDeclarationNameOrNull.toString()
        }
    }

    private fun type(value: org.jetbrains.kotlin.fir.types.ConeKotlinType): String =
        FirDiagnosticRenderers.RENDER_TYPE.render(value, org.jetbrains.kotlin.diagnostics.rendering.RenderingContext.of(value))

    private fun position(value: FactPosition): String = value.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun contract(value: FunctionFact<Locality>?): String = value?.let { fact ->
        fact.parameters.joinToString(", ", "[", "]") { parameter ->
            parameter.function?.let { "(${locality(parameter.fact)}, ${contract(it)})" } ?: locality(parameter.fact)
        } + (fact.result?.let { " -> ${contract(it)}" } ?: "")
    } ?: "unknown"

    private fun mismatch(target: FactTarget, label: String, expected: String, actual: String): String = when (target) {
        is FactTarget.Value -> "${position(target.position)} $label mismatch: expected '$expected', actual '$actual'."
        is FactTarget.Context -> "${label.replaceFirstChar { it.uppercase() }} mismatch for context parameter of type '${type(target.type)}': expected '$expected', actual '$actual'."
    }

    private fun boundary(value: FirBasedSymbol<*>): String {
        val kind = when (value) {
            is org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousObjectSymbol -> return "anonymous object"
            is org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol -> return "anonymous function"
            is org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol<*> -> if (value.classId.isLocal) "local class" else "class"
            is FirFunctionSymbol<*> -> if (value.callableId.isLocal) "local function" else "function"
            else -> "declaration"
        }
        return "$kind '${FirDiagnosticRenderers.DECLARATION_NAME.render(value)}'"
    }

    private fun attributeMessage(kind: FailureKind, condition: AttributeCondition): String = when (condition) {
        is AttributeCondition.LocalityMismatch -> mismatch(condition.target, "locality", locality(condition.expected), locality(condition.actual))
        is AttributeCondition.UniquenessMismatch -> mismatch(condition.target, "uniqueness", uniqueness(condition.expected), uniqueness(condition.actual))
        is AttributeCondition.ContractMismatch -> mismatch(condition.target, "locality contract", contract(condition.expected), contract(condition.actual))
        is AttributeCondition.LocalCapture -> "Unable to capture local '${FirDiagnosticRenderers.DECLARATION_NAME.render(condition.symbol)}' across ${boundary(condition.boundary)}."
        is AttributeCondition.Construction -> {
            val escape = condition.escape
            val owner = escape.escapingClass.name.asString()
            val reason = when (escape.kind) {
                ConstructionEscapeFact.Kind.MemberCall -> "the construction of '$owner' calls member '${escape.member?.memberDeclarationNameOrNull}' on 'this'"
                ConstructionEscapeFact.Kind.Use -> "the construction of '$owner' lets 'this' escape"
                ConstructionEscapeFact.Kind.Capture -> "the construction of '$owner' captures 'this' in a declaration that is not called in place"
            }
            "${position(condition.position)} uniqueness mismatch: expected 'unique', actual 'shared', because $reason."
        }
        is AttributeCondition.Path -> when (kind) {
            FailureKind.INVALID_MOVED_ACCESS -> "'${path(condition.path)}' is accessed after it was moved."
            FailureKind.INVALID_UNIQUENESS_CAPTURE -> "Unique or borrowed reference '${path(condition.path)}' is captured by a declaration that is not called in place."
            FailureKind.ESCAPE_UNIQUENESS_INCONSISTENCY -> "Escaping value has moved field: '${path(condition.path)}'."
            FailureKind.CONTEXT_ESCAPE_UNIQUENESS_INCONSISTENCY -> "Escaping context parameter of type '${type(requireNotNull(condition.contextType))}' has moved field: '${path(condition.path)}'."
            FailureKind.EXIT_UNIQUENESS_INCONSISTENCY -> "Borrowed local path is moved at exit: '${path(condition.path)}'."
            FailureKind.INVALID_DUPLICATE_UNIQUE_ARGUMENT -> "Attempt to pass the same unique argument '${path(condition.path)}' twice."
            FailureKind.INVALID_OVERLAPPING_UNIQUE_ARGUMENTS -> "Attempt to pass unique argument '${path(condition.path)}' with overlapping unique argument '${path(requireNotNull(condition.other))}'."
            else -> error("Unexpected path failure kind: $kind")
        }
        is AttributeCondition.Rule -> when (condition.kind) {
            FailureKind.INVALID_LOCALITY_TYPE_TARGET -> "Locality can only be specified on types of function parameters, extension receivers, or local variables, and on member functions, where it describes `this`."
            FailureKind.INVALID_UNIQUENESS_TYPE_TARGET -> "Uniqueness can only be specified on values, properties, functions, and compatible type positions, and on member functions, where it describes `this`."
            FailureKind.INVALID_TYPE_PARAMETER_UNIQUENESS -> "A declaration whose type is a type parameter cannot be unique."
            FailureKind.INVALID_VALUE_TYPE_UNIQUENESS -> "A declaration of a value type cannot be unique."
            FailureKind.OVERRIDE_UNIQUENESS_MISMATCH -> "Uniqueness or borrowing annotations differ from those of the overridden declaration."
            FailureKind.ACTUAL_UNIQUENESS_MISMATCH -> "Uniqueness or borrowing annotations differ from those of the expect declaration."
            FailureKind.INVALID_PURE_UNIQUE_RESULT -> "A pure function cannot return a unique value."
            FailureKind.INVALID_PURE_REFERENCE_RESULT -> "A pure function with a unique parameter cannot return a reference."
            FailureKind.INVALID_UNIQUE_PRED_PLACEMENT -> "'UniquePred' constructed outside a specification builtin."
            else -> error("Unexpected attribute rule: ${condition.kind}")
        }
    }

    /** Kotlin spells built-in integer operations as operators; conversions are calls. */
    private fun operator(operation: String): String = when (operation) {
        "plus" -> "+"
        "minus" -> "-"
        "times" -> "*"
        "div" -> "/"
        "rem" -> "%"
        "inc" -> "++"
        "dec" -> "--"
        "unaryMinus" -> "unary -"
        else -> if (operation.startsWith("to")) "$operation()" else operation
    }

    private fun article(value: String): String = "${if (value.first() in "aeiou") "an" else "a"} $value value"

    private fun returnValue(value: KotlinCondition.ReturnValue): String = when (value) {
        KotlinCondition.ReturnValue.TRUE -> "true"
        KotlinCondition.ReturnValue.FALSE -> "false"
        KotlinCondition.ReturnValue.NULL -> "null"
        KotlinCondition.ReturnValue.NON_NULL -> "non-null"
        KotlinCondition.ReturnValue.ANY -> "unconstrained"
    }

    private fun opposite(effect: KotlinCondition.ReturnValue): String = returnValue(when (effect) {
        KotlinCondition.ReturnValue.TRUE -> KotlinCondition.ReturnValue.FALSE
        KotlinCondition.ReturnValue.FALSE -> KotlinCondition.ReturnValue.TRUE
        KotlinCondition.ReturnValue.NULL -> KotlinCondition.ReturnValue.NON_NULL
        KotlinCondition.ReturnValue.NON_NULL -> KotlinCondition.ReturnValue.NULL
        KotlinCondition.ReturnValue.ANY -> KotlinCondition.ReturnValue.ANY
    })

    private fun memberSpelling(symbol: org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol<*>): String =
        symbol.callableId?.className?.let { "$it.${symbol.name}" } ?: symbol.name.asString()

    private fun symbol(symbol: FirBasedSymbol<*>): String = if (symbol is FirFunctionSymbol<*>) "this"
        else FirDiagnosticRenderers.DECLARATION_NAME.render(symbol)

    private fun predicate(value: KotlinPredicate): String = when (value) {
        is KotlinPredicate.Symbol -> symbol(value.symbol)
        is KotlinPredicate.Constant -> value.value.toString()
        is KotlinPredicate.IsNull -> "${symbol(value.symbol)} ${if (value.negated) "!=" else "=="} null"
        is KotlinPredicate.IsType -> "${symbol(value.symbol)} ${if (value.negated) "!is" else "is"} ${value.type.renderReadable()}"
        is KotlinPredicate.Negation -> "!${predicate(value.predicate)}"
        is KotlinPredicate.Conjunction -> "(${predicate(value.left)} && ${predicate(value.right)})"
        is KotlinPredicate.Disjunction -> "(${predicate(value.left)} || ${predicate(value.right)})"
    }

    private fun collection(kind: KotlinCondition.Collection, symbol: FirBasedSymbol<*>?): String {
        val name = kind.name.lowercase()
        return symbol?.let { "$name '${FirDiagnosticRenderers.DECLARATION_NAME.render(it)}'" }
            ?: "the following $name sub-expression"
    }
}
