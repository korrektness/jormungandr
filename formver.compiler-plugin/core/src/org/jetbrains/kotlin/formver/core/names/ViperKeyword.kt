package org.jetbrains.kotlin.formver.core.names

import org.jetbrains.kotlin.formver.viper.AnyName
import org.jetbrains.kotlin.formver.viper.CandidateName
import org.jetbrains.kotlin.formver.viper.SymbolicName
import scala.jdk.javaapi.CollectionConverters


abstract class ViperKeyword(keyword: String) : AnyName {
    override val inViper: Boolean = true

    override val candidates: List<CandidateName> = nameOnlyCandidates(keyword)

    override val children: List<AnyName> = emptyList()
}

object FunctionResultVariableName : ViperKeyword("result"), SymbolicName {
    override val inViper: Boolean = true
    override val candidates: List<CandidateName> = nameOnlyCandidates("result")
    override val children: List<AnyName> = emptyList()
}

object ViperKeywords {
    val keywords = CollectionConverters.asJava(viper.silver.ast.utility.Consistency.reservedNames())
        .filter { it != "result" }
        .map { object : ViperKeyword(it) {} } + listOf(
        FunctionResultVariableName
    )
}
