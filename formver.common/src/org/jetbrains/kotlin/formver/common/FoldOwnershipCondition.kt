package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.KtSourceElement

enum class FoldOwnershipRule {
    NOT_HELD, HOLE_BELOW, NOT_SUPERTYPE, LOOP_INVARIANT_HOLE, UNTRACKED_WRITE_RECEIVER, BORROWED_ASSIGNMENT, DOWNCAST,
}

enum class OwnershipAbsenceReason { NEVER_HELD, CONSUMED, MOVED, JOINED, FRAMED }

data class OwnershipAbsence(
    val reason: OwnershipAbsenceReason,
    val target: ConversionOwnershipPath? = null,
    val site: KtSourceElement? = null,
)

data class FoldOwnershipCondition(
    val rule: FoldOwnershipRule,
    val path: ConversionOwnershipPath? = null,
    val absent: ConversionOwnershipPath? = null,
    val absence: OwnershipAbsence? = null,
    val typeName: String? = null,
    val writeSite: KtSourceElement? = null,
) : ConversionOwnershipCondition
