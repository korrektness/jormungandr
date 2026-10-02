package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.KtSourceElement

/** A failure raised by SnaKt itself, positioned at the construct it concerns. */
abstract class SnaktException(val source: KtSourceElement?, override val message: String, cause: Throwable?) :
    Exception(message, cause)

/** An invariant of SnaKt broke: a bug in the plugin. */
class SnaktInternalException(source: KtSourceElement?, message: String, cause: Throwable? = null) :
    SnaktException(source, message, cause)

/** The construct at [source] is outside the fragment of Kotlin that SnaKt translates. */
class UnsupportedFeatureException(source: KtSourceElement?, message: String) : SnaktException(source, message, null)

/**
 * Runs [action], turning any failure that is not a [SnaktException] into a [SnaktInternalException] at [source].
 * Nested calls leave the innermost position in place, so the error points at the most specific construct.
 */
inline fun <R> attributingFailuresTo(source: KtSourceElement?, action: () -> R): R =
    try {
        action()
    } catch (e: SnaktException) {
        throw e
    } catch (e: Exception) {
        throw e.asInternalErrorAt(source)
    } catch (e: NotImplementedError) {
        throw e.asInternalErrorAt(source)
    }

fun Throwable.asInternalErrorAt(source: KtSourceElement?): SnaktInternalException =
    SnaktInternalException(source, listOfNotNull(this::class.simpleName, message).joinToString(": "), this)
