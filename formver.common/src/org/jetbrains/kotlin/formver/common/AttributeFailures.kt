package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol

fun moveLocations(moves: Iterable<KtSourceElement>): List<RelatedLocation> =
    moves.distinct().sortedBy { it.startOffset }.map { RelatedLocation("move", it) }

fun movedAccessFailure(source: KtSourceElement?, path: KotlinPath, moves: Iterable<KtSourceElement>): Failure =
    pathFailure(source, FailureKind.INVALID_MOVED_ACCESS, path, related = moveLocations(moves))

fun constructionFailure(source: KtSourceElement?, position: FactPosition, escape: ConstructionEscapeFact): Failure =
    Failure(FailureKind.SHARED_CONSTRUCTION_MISMATCH, source, AttributeCondition.Construction(position, escape),
        related = listOfNotNull(escape.source?.let { RelatedLocation("construction escape", it) }))

fun uniqueCaptureFailure(source: KtSourceElement?, path: KotlinPath, boundary: KtSourceElement?): Failure =
    pathFailure(source, FailureKind.INVALID_UNIQUENESS_CAPTURE, path,
        related = listOfNotNull(boundary?.let { RelatedLocation("capture boundary", it) }))

fun localCaptureFailure(
    source: KtSourceElement?, symbol: FirBasedSymbol<*>, boundary: FirBasedSymbol<*>, boundarySource: KtSourceElement?,
): Failure = Failure(FailureKind.INVALID_LOCALITY_CAPTURE, source, AttributeCondition.LocalCapture(symbol, boundary),
    related = listOfNotNull(boundarySource?.let { RelatedLocation("capture boundary", it) }))

fun argumentCollisionFailure(
    source: KtSourceElement?, kind: FailureKind, path: KotlinPath, otherPath: KotlinPath, otherSource: KtSourceElement?,
): Failure {
    require(kind == FailureKind.INVALID_DUPLICATE_UNIQUE_ARGUMENT || kind == FailureKind.INVALID_OVERLAPPING_UNIQUE_ARGUMENTS)
    return pathFailure(source, kind, path, otherPath,
        listOfNotNull(otherSource?.let { RelatedLocation("colliding argument", it) }))
}
