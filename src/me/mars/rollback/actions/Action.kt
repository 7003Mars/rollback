package me.mars.rollback.actions

import arc.math.geom.Point2
import arc.util.Time
import me.mars.rollback.RollbackPlugin.Companion.tileStore
import me.mars.rollback.TileInfo
import mindustry.game.Team

// TODO: uuid should be val, not var
abstract class Action(var uuid: String, val pos: Int, val team: Team) {
    companion object {
        var gid: Int = 0
    }
    val id: Int = gid++
    val time: Float = Time.time
    var willRollback = false

    abstract fun preUndo()

    abstract fun undo()

    val tileInfo: TileInfo get() {
        return tileStore.get(this.pos)
    }

    override fun toString(): String {
        return this.javaClass.simpleName + "(${Point2.x(this.pos)}, ${Point2.y(this.pos)})#${this.id}@${this.time.toInt()}"
    }
}