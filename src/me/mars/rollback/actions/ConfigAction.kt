package me.mars.rollback.actions

import arc.util.Log
import me.mars.rollback.RollbackPlugin
import me.mars.rollback.before
import me.mars.rollback.safeConfig
import me.mars.rollback.withSuppress
import mindustry.Vars
import mindustry.game.Team

class ConfigAction(uuid: String, pos: Int, blockSize: Int, team: Team, val config: Any?) : Action(uuid, pos, blockSize, team){

    override fun preUndo() {
        // I intend to configure a block, however it is useless if:
        // Another active ConfigAction undoes
        // My target will be removed by an active BuildAction
        // My target will be replaced by an active RemoveAction (Covered by BuildAction?)
        this.willRollback = !this.tileInfo.all().before(this)
            .contains { (it is BuildAction || it is ConfigAction) && it.willRollback }
    }
    override fun undo(): Run? {
        val latestRemove: DeleteAction? = this.tileInfo.select(-1, DeleteAction::class.java) { it.id < this.id}
        val lowerBound: Int = latestRemove?.id ?: 0

        val prev: ConfigAction? = this.tileInfo.select(-1,
            ConfigAction::class.java) {it.pos == this.pos && it.id < this.id && it.id > lowerBound}
        if (RollbackPlugin.debug) Log.info("Undoing $this to $prev")
        if (prev != null) {
            return withSuppress { safeConfig(null, Vars.world.build(this.pos), prev.config) }
        }
        return null
    }
}