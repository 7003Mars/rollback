package me.mars.rollback.actions

import me.mars.rollback.before
import me.mars.rollback.lastOpt
import me.mars.rollback.only
import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Player

class ConfigAction(uuid: String, pos: Int, team: Team, val config: Any?) : Action(uuid, pos, team){
    companion object {
        val fakePlayer: Player = Player.create()
    }

    override fun preUndo() {
        // I intend to configure a block, however it is useless if:
        // Another active ConfigAction undoes
        // My target will be removed by an active BuildAction
        // My target will be replaced by an active RemoveAction (Covered by BuildAction?)
        this.willRollback = !this.tileInfo.all().before(this)
            .contains { (it is BuildAction || it is ConfigAction) && it.willRollback }
    }
    override fun undo() {
        val latestRemove: DeleteAction? = this.tileInfo.all().only<DeleteAction>().filter { it.id < this.id}.lastOpt()
        val lowerBound: Int = latestRemove?.id ?: 0

        val prev: ConfigAction? = this.tileInfo.all().filter {
            it is ConfigAction && it.pos == this.pos && it.id < this.id && it. id > lowerBound}.lastOpt() as ConfigAction?

        // TODO: This thing triggers the Config event, use tileConfig__forward (non-public method)
        Call.tileConfig(fakePlayer, Vars.world.build(this.pos), prev?.config)
    }
}