package org.jetbrains.kotlin.formver.common

/** Violated declaration or use constraint of a specification function. */
enum class SpecOfRule {
    EXTENSION_RECEIVER, RETURN_TYPE, MISSING_MEMBER, OVERLOAD, MULTIPLE_SPECIFICATIONS,
    OWN_SPECIFICATION, PARAMETER_OWNERSHIP, BODY_SHAPE, POSTCONDITIONS_TYPE, CALL,
}
