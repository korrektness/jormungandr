/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.formver.common.isExternalSpecificationFile
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirReceiverParameter
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.FirFunctionTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.isAny
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.formver.readonly.plugin.exposesId
import org.jetbrains.kotlin.formver.type.plugin.AttributeTargetJudgment
import org.jetbrains.kotlin.formver.type.plugin.TypeRefAttributeChecker
import org.jetbrains.kotlin.formver.uniqueness.attribute.UniquenessAttribute
import org.jetbrains.kotlin.name.StandardClassIds

private object UniquenessAttributeTargetJudgment : AttributeTargetJudgment {
    @OptIn(SymbolInternals::class)
    private fun acceptsExposesReceiver(type: FirFunctionTypeRef, parents: List<FirElement>): Boolean {
        if (type.receiverTypeRef !== parents.firstOrNull() || !type.isBindingsType()) return false
        val body = parents.firstNotNullOfOrNull { it as? FirValueParameter } ?: return false
        if ((body.returnTypeRef as? FirResolvedTypeRef)?.delegatedTypeRef !== type) return false
        val owner = body.symbol.containingDeclarationSymbol as? FirNamedFunctionSymbol ?: return false
        if (owner.callableId != exposesId) return false
        val function = owner.fir
        return function.isExposesSignature(body, type) && function.returnsUnitWithoutCallingBindings()
    }

    private fun FirFunctionTypeRef.isBindingsType(): Boolean =
        !isMarkedNullable && !isSuspend && parameters.isEmpty() && contextParameterTypeRefs.isEmpty() &&
                returnTypeRef.coneTypeOrNull?.isUnit == true

    private fun FirSimpleFunction.isExposesSignature(body: FirValueParameter, type: FirFunctionTypeRef): Boolean {
        if (receiverParameter != null || contextParameters.isNotEmpty() || status.isSuspend) return false
        if (!symbol.resolvedReturnType.isUnit) return false
        if (valueParameters.size != 2 || valueParameters[1] !== body || body.name.asString() != "body") return false

        val typeParameter = symbol.typeParameterSymbols.singleOrNull() ?: return false
        val receiver = type.receiverTypeRef?.coneTypeOrNull as? ConeTypeParameterType ?: return false
        val spec = valueParameters.first()
        val token = spec.symbol.resolvedReturnType
        val argument = token.typeArguments.singleOrNull() as? ConeTypeParameterType ?: return false
        return spec.name.asString() == "spec" && token.classId == StandardClassIds.KClass && !token.isMarkedNullable &&
                !receiver.isMarkedNullable && !argument.isMarkedNullable && receiver.lookupTag == typeParameter.toLookupTag() &&
                argument.lookupTag == receiver.lookupTag && typeParameter.resolvedBounds.singleOrNull()?.coneType?.isAny == true &&
                spec.defaultValue == null && body.defaultValue == null
    }

    private fun FirSimpleFunction.returnsUnitWithoutCallingBindings(): Boolean {
        val result = (body?.statements?.singleOrNull() as? FirReturnExpression)?.result
        return (result as? FirResolvedQualifier)?.classId == StandardClassIds.Unit
    }

    context(context: CheckerContext)
    override fun accepts(parents: List<FirElement>): Boolean {
        if (context.session.isExternalSpecificationFile(context.containingFilePath)) return true
        val targetElement = parents.getOrNull(1) ?: return false

        return when (targetElement) {
            is FirValueParameter, is FirReceiverParameter, is FirProperty, is FirFunction -> true
            else -> targetElement.source?.kind is KtFakeSourceElementKind.ImplicitTypeArgument ||
                    targetElement is FirFunctionTypeRef && acceptsExposesReceiver(targetElement, parents)
        }
    }
}

val TypeRefUniquenessAttributeChecker = TypeRefAttributeChecker(
    kind = MppCheckerKind.Common,
    attributeClass = UniquenessAttribute::class,
    attributeTargetJudgment = UniquenessAttributeTargetJudgment,
    diagnosticFactory = UniquenessErrors.INVALID_UNIQUENESS_TYPE_TARGET,
)
