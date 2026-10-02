/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.contracts.description.LogicOperationKind
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.impl.FirElseIfTrueCondition
import org.jetbrains.kotlin.fir.expressions.impl.FirUnitExpression
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.getFunctions
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.references.toResolvedSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.description
import org.jetbrains.kotlin.formver.core.embeddings.LabelLink
import org.jetbrains.kotlin.formver.core.embeddings.callables.CallableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.callables.FullySpecialKotlinFunction
import org.jetbrains.kotlin.formver.core.embeddings.callables.insertCall
import org.jetbrains.kotlin.formver.core.embeddings.callables.isVerifyFunction
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.GeCharChar
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.GeIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.GtCharChar
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.GtIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.LeCharChar
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.LeIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.LtCharChar
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.LtIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.Not
import org.jetbrains.kotlin.formver.core.embeddings.toLink
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.core.embeddings.types.equalToType
import org.jetbrains.kotlin.formver.core.functionCallArguments
import org.jetbrains.kotlin.formver.intrinsics.plugin.stringBuilderIntrinsic
import org.jetbrains.kotlin.formver.uniqueness.plugin.isIntArrayElementAccess
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.util.OperatorNameConventions

/**
 * Convert a statement, emitting the resulting Viper statements and
 * declarations into the context, returning a reference to the
 * expression containing the result.  Note that in the FIR, expressions
 * are a subtype of statements.
 *
 * In many cases, we introduce a temporary variable to represent this
 * result (since, for example, a method call is not an expression).
 * When the result is an lvalue, it is important to return an expression
 * that refers to location, not just the same value, and so introducing
 * a temporary variable for the result is not acceptable in those cases.
 */
object StmtConversionVisitor : FirVisitor<ExpEmbedding, StmtConversionContext>() {
    // Note that in some cases we don't expect to ever implement it: we are only
    // translating statements here, after all.  It isn't 100% clear how best to
    // communicate this.
    override fun visitElement(element: FirElement, data: StmtConversionContext): ExpEmbedding =
        throw UnsupportedFeatureException(element.source, element.description)

    /** FIR ends the block of an indexed assignment, such as `a[i] = v` or `a[i] += v`, with a Unit expression. */
    override fun visitExpression(expression: FirExpression, data: StmtConversionContext): ExpEmbedding =
        if (expression is FirUnitExpression) UnitLit else visitElement(expression, data)

    override fun visitReturnExpression(
        returnExpression: FirReturnExpression,
        data: StmtConversionContext,
    ): ExpEmbedding {
        val expr = when (returnExpression.result) {
            is FirUnitExpression -> UnitLit
            else -> data.convert(returnExpression.result)
        }
        // returnTarget is null when it is the implicit return of a lambda
        val returnTargetName = returnExpression.target.labelName
        val target = data.resolveReturnTarget(returnTargetName)
        return Return(expr.withType(target.variable.type), target)
    }

    override fun visitResolvedQualifier(
        resolvedQualifier: FirResolvedQualifier, data: StmtConversionContext
    ): ExpEmbedding {
        if (resolvedQualifier.resolvedType.isUnit) return UnitLit
        throw UnsupportedFeatureException(resolvedQualifier.source, resolvedQualifier.description)
    }

    override fun visitBlock(block: FirBlock, data: StmtConversionContext): ExpEmbedding =
        block.asForRangeLoop()?.let { data.convertForRangeLoop(it) }
            ?: block.statements.map { data.convertReportingUnsupported(it) }.toBlock()

    override fun visitLiteralExpression(
        literalExpression: FirLiteralExpression,
        data: StmtConversionContext,
    ): ExpEmbedding = when (literalExpression.kind) {
        ConstantValueKind.Int -> IntLit((literalExpression.value as Long).toInt())
        ConstantValueKind.Boolean -> BooleanLit(literalExpression.value as Boolean)
        ConstantValueKind.Char -> CharLit(literalExpression.value as Char)
        ConstantValueKind.String -> StringLit(literalExpression.value as String)
        ConstantValueKind.Null -> NullLit
        else -> throw UnsupportedFeatureException(
            literalExpression.source, "constant of kind ${literalExpression.kind}"
        )
    }

    /**
     * Converts a string template to a chain of `String.plus` calls, one per part, merging adjacent literal parts.
     * The chain starts from the leading literal, or from `""` when the template starts with an expression.
     */
    override fun visitStringConcatenationCall(
        stringConcatenationCall: FirStringConcatenationCall, data: StmtConversionContext
    ): ExpEmbedding {
        val stringClass = data.session.symbolProvider.getClassLikeSymbolByClassId(StandardClassIds.String) as FirClassSymbol<*>
        val plus = stringClass.declaredMemberScope(data.session, memberRequiredPhase = null)
            .getFunctions(OperatorNameConventions.PLUS).single()
        val plusEmbedding = data.embedAnyFunction(plus)
        val parts = buildList {
            val literal = StringBuilder()
            for (arg in stringConcatenationCall.arguments) {
                if (arg is FirLiteralExpression) {
                    literal.append(arg.value.toString())
                    continue
                }
                if (literal.isNotEmpty()) add(StringLit(literal.toString()))
                literal.clear()
                add(data.convert(arg))
            }
            if (literal.isNotEmpty()) add(StringLit(literal.toString()))
        }
        val start = parts.firstOrNull() as? StringLit
        val stringType = buildType { string() }
        return (if (start == null) parts else parts.drop(1)).fold<ExpEmbedding, ExpEmbedding>(start ?: StringLit("")) { acc, part ->
            plusEmbedding.insertCall(listOf(acc, part), data, stringType)
        }
    }

    override fun visitIntegerLiteralOperatorCall(
        integerLiteralOperatorCall: FirIntegerLiteralOperatorCall,
        data: StmtConversionContext,
    ): ExpEmbedding {
        return visitFunctionCall(integerLiteralOperatorCall, data)
    }

    override fun visitWhenSubjectExpression(
        whenSubjectExpression: FirWhenSubjectExpression,
        data: StmtConversionContext,
    ): ExpEmbedding = data.whenSubject!!

    private fun convertWhenBranches(
        whenBranches: Iterator<FirWhenBranch>,
        type: TypeEmbedding,
        data: StmtConversionContext,
    ): ExpEmbedding {
        if (!whenBranches.hasNext()) return UnitLit

        val branch = whenBranches.next()

        // Note that only the last condition can be a FirElseIfTrue
        return if (branch.condition is FirElseIfTrueCondition) {
            data.withNewScope { convert(branch.result) }
        } else {
            val cond = data.convert(branch.condition).withType { boolean() }
            val thenExp = data.withNewScope { convert(branch.result) }
            val elseExp = convertWhenBranches(whenBranches, type, data)
            If(cond, thenExp.withType(type), elseExp.withType(type), type)
        }
    }

    override fun visitWhenExpression(whenExpression: FirWhenExpression, data: StmtConversionContext): ExpEmbedding =
        data.withNewScope {
            val type = data.embedType(whenExpression)
            val subj: Declare? = whenExpression.subjectVariable?.let { firSubjVar ->
                val subjExp = convert(firSubjVar.initializer!!)
                if (firSubjVar.name.isSpecial)
                    declareAnonVar(subjExp.type, subjExp)
                else
                    declareLocalVariable(firSubjVar.symbol, subjExp)
            }
            val body = withWhenSubject(subj?.variable) {
                convertWhenBranches(whenExpression.branches.iterator(), type, this)
            }
            subj?.let { blockOf(it, body) } ?: body
        }

    override fun visitPropertyAccessExpression(
        propertyAccessExpression: FirPropertyAccessExpression,
        data: StmtConversionContext,
    ): ExpEmbedding {
        propertyAccessExpression.calleeReference.symbol?.let(::indexedArrayAlias)?.let { return data.convert(it) }
        propertyAccessExpression.stringBuilderIntrinsic(data.session)?.let {
            return data.convertStringBuilderIntrinsic(propertyAccessExpression, it)
        }
        propertyAccessExpression.topLevelPropertySymbol()?.let { return data.convertTopLevelPropertyRead(it) }
        val propertyAccess = data.embedPropertyAccess(propertyAccessExpression)
        return propertyAccess.getValue(data)
    }

    private fun FirPropertyAccessExpression.topLevelPropertySymbol(): FirPropertySymbol? =
        (calleeReference.symbol as? FirPropertySymbol)?.takeIf {
            !it.isLocal && dispatchReceiver == null && extensionReceiver == null
        }

    /**
     * A `const val` reads as the literal of its initializer. Any other property without a receiver is read as an
     * unconstrained value of its type.
     */
    private fun StmtConversionContext.convertTopLevelPropertyRead(symbol: FirPropertySymbol): ExpEmbedding {
        if (symbol.resolvedStatus.isConst) return convert(symbol.resolvedInitializer!!)
        val declaration = declareAnonVar(embedType(symbol.resolvedReturnType), null)
        val value = declaration.variable.withInvariants(typeResolver) {
            proven = true
            access = true
        }
        return blockOf(declaration, value)
    }

    override fun visitEqualityOperatorCall(
        equalityOperatorCall: FirEqualityOperatorCall,
        data: StmtConversionContext,
    ): ExpEmbedding {
        if (equalityOperatorCall.arguments.size != 2) {
            throw SnaktInternalException(
                equalityOperatorCall.source,
                "Invalid equality comparison ${equalityOperatorCall.description}, can only compare 2 elements."
            )
        }
        val left = data.convert(equalityOperatorCall.arguments[0])
        val right = data.convert(equalityOperatorCall.arguments[1])

        return when (equalityOperatorCall.operation) {
            FirOperation.EQ -> convertEqCmp(left, right)
            FirOperation.NOT_EQ -> Not(convertEqCmp(left, right))
            FirOperation.IDENTITY -> IdentityCmp(left, right)
            FirOperation.NOT_IDENTITY -> Not(IdentityCmp(left, right))
            else -> throw UnsupportedFeatureException(
                equalityOperatorCall.source, "equality operation ${equalityOperatorCall.operation}"
            )
        }
    }

    private fun convertEqCmp(left: ExpEmbedding, right: ExpEmbedding): ExpEmbedding {
        //TODO: replace with call to left.equals()
        return EqCmp(left, right)
    }

    override fun visitComparisonExpression(
        comparisonExpression: FirComparisonExpression,
        data: StmtConversionContext,
    ): ExpEmbedding {

        val dispatchReceiver: FirExpression =
            comparisonExpression.compareToCall.dispatchReceiver ?: throw SnaktInternalException(
                comparisonExpression.compareToCall.source, "found 'compareTo' call with null receiver"
            )
        val arg =
            comparisonExpression.compareToCall.argumentList.arguments.firstOrNull() ?: throw SnaktInternalException(
                comparisonExpression.compareToCall.source, "found `compareTo` call with no argument at position 0"
            )
        val left = data.convert(dispatchReceiver)
        val right = data.convert(arg)

        val functionSymbol = comparisonExpression.compareToCall.toResolvedCallableSymbol()

        val functionType = data.embedFunctionPretype(functionSymbol as FirFunctionSymbol)

        val comparisonTemplate = when {
            functionType.formalArgTypes.all { it.equalToType { int() } } -> IntComparisonExpEmbeddingsTemplate
            functionType.formalArgTypes.all { it.equalToType { char() } } -> CharComparisonExpEmbeddingsTemplate
            else -> {
                val result = data.convert(comparisonExpression.compareToCall)
                return IntComparisonExpEmbeddingsTemplate.retrieve(comparisonExpression.operation)(result, IntLit(0))
            }
        }
        return comparisonTemplate.retrieve(comparisonExpression.operation)(left, right)
    }

    private interface ComparisonExpEmbeddingsTemplate {
        fun retrieve(operation: FirOperation): BinaryOperatorExpEmbeddingTemplate
    }

    private object IntComparisonExpEmbeddingsTemplate : ComparisonExpEmbeddingsTemplate {
        override fun retrieve(operation: FirOperation) = when (operation) {
            FirOperation.LT -> LtIntInt
            FirOperation.LT_EQ -> LeIntInt
            FirOperation.GT -> GtIntInt
            FirOperation.GT_EQ -> GeIntInt
            else -> throw IllegalArgumentException("Expected comparison operation but found ${operation}.")
        }
    }

    private object CharComparisonExpEmbeddingsTemplate : ComparisonExpEmbeddingsTemplate {
        override fun retrieve(operation: FirOperation) = when (operation) {
            FirOperation.LT -> LtCharChar
            FirOperation.LT_EQ -> LeCharChar
            FirOperation.GT -> GtCharChar
            FirOperation.GT_EQ -> GeCharChar
            else -> throw IllegalArgumentException("Expected comparison operation but found ${operation}.")
        }
    }

    private fun List<FirExpression>.withVarargsHandled(data: StmtConversionContext, function: CallableEmbedding?) =
        flatMap { arg ->
            when (arg) {
                is FirVarargArgumentsExpression -> {
                    if (function == null || !function.isVerifyFunction) {
                        throw UnsupportedFeatureException(
                            arg.source, "vararg arguments to a function other than `verify`"
                        )
                    }
                    data.withNoScope {
                        arg.arguments.map { this.convert(it) }
                    }
                }

                else -> listOf(data.convert(arg))
            }
        }

    override fun visitFunctionCall(functionCall: FirFunctionCall, data: StmtConversionContext): ExpEmbedding {
        val symbol = functionCall.toResolvedCallableSymbol() as? FirFunctionSymbol<*>
            ?: throw NotImplementedError("Only functions are expected as callables of function calls, got ${functionCall.toResolvedCallableSymbol()}")
        if (functionCall.isIntArrayElementAccess()) return data.convertIntArrayElementAccess(functionCall)
        if (functionCall.isIntArrayInit()) return data.convertIntArrayInit(functionCall)
        data.convertMultisetIntrinsic(functionCall)?.let { return it }
        functionCall.stringBuilderIntrinsic(data.session)?.let { return data.convertStringBuilderIntrinsic(functionCall, it) }

        val callee = data.embedAnyFunction(symbol)
        val returnType = data.embedType(functionCall.resolvedType)
        val mappedParameters = functionCall.resolvedArgumentMapping?.values?.map { it.symbol }
        val passedPositionally = mappedParameters == null || mappedParameters == symbol.valueParameterSymbols ||
                // A special function reads omitted trailing arguments as absent.
                callee is FullySpecialKotlinFunction &&
                mappedParameters == symbol.valueParameterSymbols.take(mappedParameters.size)
        if (!passedPositionally) {
            return data.insertCallWithMappedArguments(functionCall, symbol, callee, returnType)
        }
        return callee.insertCall(
            functionCall.functionCallArguments.withVarargsHandled(data, callee),
            data,
            returnType,
        )
    }

    override fun visitImplicitInvokeCall(
        implicitInvokeCall: FirImplicitInvokeCall,
        data: StmtConversionContext,
    ): ExpEmbedding {
        val receiver =
            implicitInvokeCall.dispatchReceiver as? FirPropertyAccessExpression ?: throw UnsupportedFeatureException(
                implicitInvokeCall.source, "implicit invoke on a receiver that is not a variable"
            )
        val returnType = data.embedType(implicitInvokeCall.resolvedType)
        val receiverSymbol = receiver.calleeReference.toResolvedSymbol<FirBasedSymbol<*>>()!!
        val args = implicitInvokeCall.argumentList.arguments.withVarargsHandled(data, function = null)
        return when (val exp = data.embedLocalSymbol(receiverSymbol).ignoringMetaNodes()) {
            is LambdaExp -> {
                // The lambda is already the receiver, so we do not need to convert it.
                // TODO: do this more uniformly: convert the receiver, see it is a lambda, use insertCall on it.
                exp.insertCall(args, data, returnType)
            }

            else -> {
                InvokeFunctionObject(data.convert(receiver), args, returnType)
            }
        }
    }

    override fun visitProperty(property: FirProperty, data: StmtConversionContext): ExpEmbedding {
        val symbol = property.symbol
        if (indexedArrayAlias(symbol) != null) return UnitLit
        if (!symbol.isLocal) {
            throw SnaktInternalException(
                property.source,
                "StmtConversionVisitor should not encounter non-local properties."
            )
        }

        // The declaration stands even when its initializer is unsupported, so later statements can use the name.
        val declaration =
            data.declareLocalProperty(symbol, property.initializer?.let { data.convertReportingUnsupported(it) })
        val targetOwned = data.uniquenessAnalysis?.ownsAfter(property, listOf(symbol)) ?: return declaration
        return declaration.copy(targetOwned = targetOwned)
    }

    override fun visitWhileLoop(whileLoop: FirWhileLoop, data: StmtConversionContext): ExpEmbedding {
        val condition = data.convert(whileLoop.condition).withType { boolean() }
        return data.convertLoop(whileLoop, condition, extractLoopInvariants(whileLoop.block.statements)) {
            convert(whileLoop.block)
        }
    }

    override fun visitBreakExpression(
        breakExpression: FirBreakExpression,
        data: StmtConversionContext,
    ): ExpEmbedding {
        val targetName = breakExpression.target.labelName
        val breakLabel = LabelLink(data.breakLabelName(targetName))
        return Goto(breakLabel)
    }

    override fun visitContinueExpression(
        continueExpression: FirContinueExpression,
        data: StmtConversionContext,
    ): ExpEmbedding {
        val targetName = continueExpression.target.labelName
        val continueLabel = LabelLink(data.continueLabelName(targetName))
        return Goto(continueLabel)
    }

    override fun visitDesugaredAssignmentValueReferenceExpression(
        desugaredAssignmentValueReferenceExpression: FirDesugaredAssignmentValueReferenceExpression,
        data: StmtConversionContext
    ): ExpEmbedding {
        return data.convert(desugaredAssignmentValueReferenceExpression.expressionRef.value)
    }

    override fun visitVariableAssignment(
        variableAssignment: FirVariableAssignment,
        data: StmtConversionContext,
    ): ExpEmbedding {
        val lValue = when (val lValue = variableAssignment.lValue) {
            is FirPropertyAccessExpression -> lValue
            is FirDesugaredAssignmentValueReferenceExpression -> lValue.expressionRef.value as FirPropertyAccessExpression
            else -> throw SnaktInternalException(
                variableAssignment.source, "Lvalue must be either property access or desugared assignment."
            )
        }
        val embedding = data.embedPropertyAccess(lValue, variableAssignment)
        val convertedRValue = data.convert(variableAssignment.rValue)
        val assignment = embedding.setValue(convertedRValue, data)
        if (assignment is FieldModification && assignment.dropsWrite) {
            lValue.dispatchReceiver?.let { data.warnIfUntrackedWrite(variableAssignment, it, assignment.receiverOwned) }
        }
        val analysis = data.uniquenessAnalysis
        if (assignment !is Assign || analysis == null) return assignment
        val lValuePath = analysis.pathOf(lValue) ?: return assignment
        return assignment.copy(targetOwned = analysis.ownsAfter(variableAssignment, lValuePath))
    }

    override fun visitSmartCastExpression(
        smartCastExpression: FirSmartCastExpression,
        data: StmtConversionContext,
    ): ExpEmbedding {
        val exp = data.convert(smartCastExpression.originalExpression)
        val newType = data.embedType(smartCastExpression.smartcastType.coneType)
        // If the smart-cast is from A? to A, then is not necessary to inhale invariants
        return if (exp.type.getNonNullable() == newType) {
            exp.withType(newType)
        } else {
            // TODO: when there is a cast from B to A, only inhale invariants of A - invariants of B
            exp.withNewTypeInvariants(newType, data.typeResolver) {
                access = true
            }
        }
    }

    override fun visitBooleanOperatorExpression(
        booleanOperatorExpression: FirBooleanOperatorExpression,
        data: StmtConversionContext,
    ): ExpEmbedding {
        val left = data.convert(booleanOperatorExpression.leftOperand)
        val right = data.convert(booleanOperatorExpression.rightOperand)
        return when (booleanOperatorExpression.kind) {
            LogicOperationKind.AND -> SequentialAnd(left, right)
            LogicOperationKind.OR -> SequentialOr(left, right)
        }
    }

    override fun visitThisReceiverExpression(
        thisReceiverExpression: FirThisReceiverExpression,
        data: StmtConversionContext,
    ): ExpEmbedding {
        // `thisReceiverExpression` has a bound symbol which can be used for lookup
        // for extensions `this`es the bound symbol is the function they originate from
        // for member functions the bound symbol is a class they're defined in
        //
        // since dispatch receiver can only originate from non-anonymous function we do not specify its name here
        // as we have only one candidate to resolve it
        fun tryResolve(symbol: FirBasedSymbol<*>): ExpEmbedding? {
            val resolved = when (symbol) {
                is FirClassSymbol<*> -> data.resolveDispatchReceiver()
                is FirAnonymousFunctionSymbol -> data.resolveExtensionReceiver(symbol.label!!.name)
                is FirFunctionSymbol<*> -> data.resolveExtensionReceiver(symbol.name.asString())
                else -> return null
            }

            return resolved
                ?: throw SnaktInternalException(
                    thisReceiverExpression.source,
                    "Can't resolve the 'this' receiver since the function does not have one."
                )
        }

        val symbol = thisReceiverExpression.calleeReference.boundSymbol
        tryResolve(symbol as FirBasedSymbol<*>)?.let { return it }
        val declSymbol = when (symbol) {
            is FirReceiverParameterSymbol -> symbol.containingDeclarationSymbol
            is FirValueParameterSymbol -> symbol.containingDeclarationSymbol
            else -> throw SnaktInternalException(symbol.source, "Unsupported receiver expression type.")
        }
        tryResolve(declSymbol)?.let { return it }

        throw SnaktInternalException(thisReceiverExpression.source, "No resolution approach to this symbol worked.")
    }

    override fun visitTypeOperatorCall(
        typeOperatorCall: FirTypeOperatorCall,
        data: StmtConversionContext,
    ): ExpEmbedding {
        val argument = data.convert(typeOperatorCall.arguments[0])
        val conversionType = data.embedType(typeOperatorCall.conversionTypeRef.coneType)
        return when (typeOperatorCall.operation) {
            FirOperation.IS -> Is(argument, conversionType)
            FirOperation.NOT_IS -> Not(Is(argument, conversionType))
            FirOperation.AS -> Cast(argument, conversionType).withInvariants(data.typeResolver) {
                proven = true
                access = true
            }

            FirOperation.SAFE_AS -> SafeCast(argument, conversionType).withInvariants(data.typeResolver) {
                proven = true
                access = true
            }

            else -> throw UnsupportedFeatureException(
                typeOperatorCall.source, "type operator ${typeOperatorCall.operation}"
            )
        }
    }

    override fun visitAnonymousFunctionExpression(
        anonymousFunctionExpression: FirAnonymousFunctionExpression,
        data: StmtConversionContext,
    ): ExpEmbedding {
        val function = anonymousFunctionExpression.anonymousFunction
        val (signature, _) = with(data) { function.symbol.toFunctionSignature() }
        return LambdaExp(signature, function, data, function.symbol.label!!.name)
    }


    override fun visitThrowExpression(throwExpression: FirThrowExpression, data: StmtConversionContext): ExpEmbedding =
        Block {
            add(data.convert(throwExpression.exception))
            add(data.exceptionalExit())
        }

    override fun visitTryExpression(tryExpression: FirTryExpression, data: StmtConversionContext): ExpEmbedding {
        if (tryExpression.finallyBlock != null) {
            throw UnsupportedFeatureException(tryExpression.source, "`finally` block")
        }
        if (data.holdsOwnership()) {
            data.reportUnsupportedOwnership(tryExpression.source, "`try` is not supported in a function that holds ownership.")
        }
        val (catchData, tryBody) = data.withCatches(tryExpression.catches) { catchData ->
            withNewScope {
                val jumps =
                    catchData.blocks.map { catchBlock -> NonDeterministically(Goto(catchBlock.entryLabel.toLink())) }
                val body = convert(tryExpression.tryBlock)
                GotoChainNode(
                    null,
                    Block {
                        addAll(jumps)
                        add(body)
                        addAll(jumps)
                    },
                    catchData.exitLabel.toLink()
                )
            }
        }
        val catches = catchData.blocks.map { catchBlock ->
            data.withNewScope {
                val parameter = catchBlock.firCatch.parameter
                // The value is the thrown exception, which we do not know, hence we do not initialise the exception variable.
                val paramDecl = declareLocalProperty(parameter.symbol, null)
                GotoChainNode(
                    catchBlock.entryLabel,
                    blockOf(
                        paramDecl,
                        convert(catchBlock.firCatch.block)
                    ),
                    catchData.exitLabel.toLink()
                )
            }
        }
        return Block {
            add(tryBody)
            addAll(catches)
            add(LabelExp(catchData.exitLabel))
        }
    }

    override fun visitElvisExpression(
        elvisExpression: FirElvisExpression,
        data: StmtConversionContext,
    ): ExpEmbedding {
        val lhs = data.convert(elvisExpression.lhs)
        val rhs = data.convert(elvisExpression.rhs)
        val expType = data.embedType(elvisExpression.resolvedType)
        return Elvis(lhs, rhs, expType)
    }

    override fun visitSafeCallExpression(
        safeCallExpression: FirSafeCallExpression,
        data: StmtConversionContext,
    ): ExpEmbedding {
        val selector = safeCallExpression.selector
        val receiver = data.convert(safeCallExpression.receiver)
        val expType = data.embedType(safeCallExpression.resolvedType)
        val checkedSafeCallSubjectType = data.embedType(safeCallExpression.checkedSubjectRef.value.resolvedType)

        return share(receiver) { sharedReceiver ->
            If(
                sharedReceiver.notNullCmp(),
                data.withCheckedSafeCallSubject(sharedReceiver.withType(checkedSafeCallSubjectType)) { convert(selector) }.withType(expType),
                NullLit.withType(expType),
                expType
            )
        }
    }

    override fun visitCheckedSafeCallSubject(
        checkedSafeCallSubject: FirCheckedSafeCallSubject,
        data: StmtConversionContext,
    ): ExpEmbedding = data.checkedSafeCallSubject ?: throw SnaktInternalException(
        checkedSafeCallSubject.source,
        "Trying to resolve checked subject ${checkedSafeCallSubject.description} which was not captured in StmtConversionContext"
    )
}
