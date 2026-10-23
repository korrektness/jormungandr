package org.jetbrains.kotlin.formver.core.domains

import org.jetbrains.kotlin.formver.core.names.DomainAssociatedFuncName
import org.jetbrains.kotlin.formver.viper.ast.*

/** Resource-neutral identities whose applications check runtime definedness. */
object CastDefinedness {
    private val value = Var(DomainAssociatedFuncName("castValue"), Type.Ref)
    private val target = Var(DomainAssociatedFuncName("castTarget"), RuntimeTypeDomain.RuntimeType)

    val checkedType = UserFunction(
        DomainAssociatedFuncName("checkedType"), listOf(value.decl(), target.decl()), Type.Ref,
        pres = listOf(RuntimeTypeDomain.isSubtype(RuntimeTypeDomain.typeOf(value.use()), target.use())),
        posts = listOf(Exp.Result(Type.Ref) eq value.use()),
        body = value.use(),
    )
    val nonNull = UserFunction(
        DomainAssociatedFuncName("nonNull"), listOf(value.decl()), Type.Ref,
        pres = listOf(value.use() ne RuntimeTypeDomain.nullValue()),
        posts = listOf(Exp.Result(Type.Ref) eq value.use()),
        body = value.use(),
    )
}
