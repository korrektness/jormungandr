// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class Node

class Box<T>(var item: T)

fun <T> `shared type parameter`(x: T, y: @Borrowed T): T {
    val z: T = x
    return z
}

fun <T> `unique generic class`(box: @Unique Box<T>): @Unique Box<T> = box

fun <T> `unique concrete type beside a type parameter`(node: @Unique Node, item: T): @Unique Node = node
