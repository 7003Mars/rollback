package me.mars.rollback.actions

import arc.math.geom.Point2
import arc.struct.Seq
import arc.util.Strings
import arc.util.Time
import me.mars.rollback.RollbackPlugin.Companion.tileStore
import me.mars.rollback.TileInfo
import mindustry.game.Team

abstract class Action(val uuid: String, val pos: Int, val team: Team) {
    companion object {
        var gid: Int = 0;
    }
    val id: Int = gid++;
    val time: Float = Time.time


    abstract fun undo();

    val tileInfo: TileInfo get() {
        return tileStore.get(this.pos);
    }

    override fun toString(): String {
        return this.javaClass.simpleName + "(${Point2.x(this.pos)}, ${Point2.y(this.pos)})#${this.id}@${this.time.toInt()}";
    }
}