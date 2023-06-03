package me.mars.rollback

import arc.func.Boolf
import arc.struct.Seq
import me.mars.rollback.actions.Action
import mindustry.gen.Unit

inline fun <reified T> Seq<in T>.only(): Seq<T> {
    return this.filter {it is T}.`as`()
}

fun Seq<Action>.before(me: Action): Seq<Action> {
    return this.filter {it.id < me.id}
}

fun <T> Seq<T>.popAll(check: Boolf<T>): Seq<T> {
    // The following code hates mobile!
    val res: Seq<T> = Seq()
    val it = this.iterator()
    while (it.hasNext()) {
        val next: T = it.next()
        if (check.get(next)) {
            res.add(next)
            it.remove()
        }
    }
    return res
}

fun Unit.uuidOrEmpty(): String {
    return this.player?.uuid()?: ""
}