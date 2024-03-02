package me.mars.rollback

import arc.Core
import arc.Events
import arc.func.Boolf
import arc.struct.Seq
import me.mars.rollback.actions.Action
import me.mars.rollback.actions.Run
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.gen.Unit

inline fun <reified T> Seq<in T>.only(): Seq<T> {
    return this.retainAll {it is T}.`as`()
}

fun Seq<Action>.before(me: Action): Seq<Action> {
    return this.retainAll {it.id < me.id}
}

fun <T> Seq<T>.lastOpt(): T? {
    if (this.size == 0) return null
    return this.peek()
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

/**
 * Registers an event listener that can be suppressed by [suppressEvents]
 */
inline fun <reified T> onEvent(crossinline cons: (T) -> kotlin.Unit) {
    Events.on(T::class.java) {
        if (suppressEvents) return@on
        cons(it)
    }
}

fun withSuppress(runnable: Runnable): Run {
    return {
        suppressEvents = true
        try {
            runnable.run()
        } finally {
            suppressEvents = false
        }
    }
}

fun safeConfig(player: Player?, building: Building, value: Any?) {
    if (building.config() == value) return
    Core.app.post { suppressEvents = true }
    Call.tileConfig(player, building, value)
    Core.app.post { suppressEvents = false }
}