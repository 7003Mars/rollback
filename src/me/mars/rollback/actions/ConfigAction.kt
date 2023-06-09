package me.mars.rollback.actions

import arc.util.Log
import arc.util.Ratekeeper
import me.mars.rollback.RollbackPlugin
import me.mars.rollback.before
import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Player

class ConfigAction(uuid: String, pos: Int, blockSize: Int, team: Team, val config: Any?) : Action(uuid, pos, blockSize, team){
    companion object {
        val fakePlayer: Player = Player.create().also {
            it.info.rate = object : Ratekeeper() {
                override fun allow(spacing: Long, cap: Int): Boolean {
                    return true
                }
            }
            it.admin = true
        }
    }

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
            return { Call.tileConfig(fakePlayer, Vars.world.build(this.pos), prev.config) }
        }
        return null
        // TODO: This thing triggers the Config event, use tileConfig__forward (non-public method)
    }
}