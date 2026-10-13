package org.jetbrains.kotlin.formver.core.embeddings

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.core.embeddings.types.*
import org.jetbrains.kotlin.formver.viper.NameResolver
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.mangled

/** Source binding metadata survives backend naming and heap extraction. */
data class ModelOrigin(
    val name: String,
    val source: KtSourceElement?,
    val kind: Kind,
    val unique: Boolean = false,
    val parameter: Boolean = false,
    val decoder: SymbolicName? = null,
    val resolvedDecoder: String? = null,
    val resolvedNull: String? = null,
) : org.jetbrains.kotlin.formver.viper.ast.ResolvableInfo {
    context(resolver: NameResolver)
    override fun resolveInfo(): Any = resolved()

    enum class Kind { INT, BOOLEAN, CHAR, REFERENCE }

    context(resolver: NameResolver)
    fun resolved(): ModelOrigin = copy(decoder = null, resolvedDecoder = decoder?.mangled ?: resolvedDecoder,
        resolvedNull = org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain.nullValue.name.mangled)
}

fun TypeEmbedding.modelOrigin(name: String, source: KtSourceElement?, unique: Boolean = false, parameter: Boolean = false): ModelOrigin {
    val kind = when (pretype) {
        IntTypeEmbedding -> ModelOrigin.Kind.INT
        BooleanTypeEmbedding -> ModelOrigin.Kind.BOOLEAN
        CharTypeEmbedding -> ModelOrigin.Kind.CHAR
        else -> ModelOrigin.Kind.REFERENCE
    }
    val decoder = if (kind == ModelOrigin.Kind.REFERENCE) null else getNonNullable().injection.fromRef.name
    return ModelOrigin(name, source, kind, unique, parameter, decoder)
}
