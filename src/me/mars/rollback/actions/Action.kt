package me.mars.rollback.actions

import arc.math.geom.Point2
import arc.util.Time
import me.mars.rollback.RollbackPlugin.Companion.tileStore
import me.mars.rollback.TileInfo
import mindustry.game.Team

// TODO: uuid should be val, not var
abstract class Action(var uuid: String, val pos: Int, val blockSize: Int, val team: Team): Comparable<Action> {
    companion object {
        var gid: Int = 0
    }
    val id: Int = gid++
    val time: Long = Time.millis()
    var willRollback = false

    /**
     * Used to check if this action will be used or not
     */
    abstract fun preUndo()

    /**
     * Returns a runnable [Run] to be invoked on the main thread
     */
    abstract fun undo(): Run?

    val tileInfo: TileInfo get() {
        return tileStore.get(this.pos)
    }

    override fun toString(): String {
        return this.javaClass.simpleName + "@${this.uuid}:(${Point2.x(this.pos)}, ${Point2.y(this.pos)})#${this.id}"
    }

    override fun compareTo(other: Action): Int {
        return this.id - other.id
    }
}

typealias Run = () -> Unit