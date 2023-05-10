package me.mars.rollback

import arc.struct.Seq
import me.mars.rollback.actions.Action

fun <T> Seq<T>.lastOpt(): T? {
    if (this.size == 0) return null;
    return this.peek();
}

fun <T> Seq<in T>.only(cls: Class<T>): Seq<T> {
    return this.filter {it != null && it::class.java == cls}.`as`();
}

fun Seq<Action>.before(me: Action): Seq<Action> {
    return this.filter {it.id < me.id};
}