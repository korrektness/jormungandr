package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.formver.core.asPosition
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Exp.Companion.toConjunction
import org.jetbrains.kotlin.formver.viper.ast.Stmt
import org.jetbrains.kotlin.formver.viper.ast.Type

/**
 * Stores [value] at [index] of the sequence field [contents]. Without [pointwise] the field becomes the update
 * `contents[index := value]`. With [pointwise] it becomes a fresh sequence whose length, element [index] and every other
 * element are equated with those of the update, so no update term reaches the verifier.
 */
fun LinearizationContext.writeElement(contents: Exp.FieldAccess, index: Exp, value: Exp, pointwise: Boolean) {
    val pos = source.asPosition
    if (!pointwise) {
        addStatement { Stmt.FieldAssign(contents, Exp.SeqUpdate(contents, index, value, pos), pos) }
        return
    }
    val type = contents.field.type as Type.Seq
    val before = freshViperVar(type)
    val after = freshViperVar(type)
    addDeclaration(before.decl())
    addDeclaration(after.decl())
    addStatement { Stmt.LocalVarAssign(before.use(), contents, pos) }
    addStatement { Stmt.FieldAssign(contents, after.use(), pos) }
    val k = freshViperVar(Type.Int)
    val frame = Exp.forall(k) { k ->
        simpleTrigger { Exp.SeqIndex(contents, k) }
        simpleTrigger { Exp.SeqIndex(before.use(), k) }
        assumption { Exp.LeCmp(Exp.IntLit(0), k) }
        assumption { Exp.LtCmp(k, Exp.SeqLength(before.use())) }
        assumption { Exp.NeCmp(k, index) }
        Exp.EqCmp(Exp.SeqIndex(contents, k), Exp.SeqIndex(before.use(), k))
    }
    val facts = listOf(
        Exp.EqCmp(Exp.SeqLength(contents), Exp.SeqLength(before.use())),
        Exp.EqCmp(Exp.SeqIndex(contents, index), value),
        frame,
    )
    addStatement { Stmt.Inhale(facts.toConjunction(), pos) }
}
