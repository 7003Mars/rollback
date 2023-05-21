package me.mars.rollback

import arc.struct.Seq
import me.mars.rollback.actions.Action
import mindustry.gen.Unit

fun <T> Seq<T>.lastOpt(): T? {
    if (this.size == 0) return null
    return this.peek()
}

inline fun <reified T> Seq<in T>.only(): Seq<T> {
    return this.filter {it is T}.`as`()
}

fun Seq<Action>.before(me: Action): Seq<Action> {
    return this.filter {it.id < me.id}
}

fun Unit.uuidOrEmpty(): String {
    return this.player?.uuid()?: ""
}