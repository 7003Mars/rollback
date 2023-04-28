package me.mars.rollback.actions

import me.mars.rollback.only
import me.mars.rollback.lastOpt
import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.Call

class ConfigAction(uuid: String, pos: Int, team: Team, val config: Any) : Action(uuid, pos, team){
    override fun undo() {
        val latestRemove: DeleteAction? = this.tileInfo.all().only(DeleteAction::class.java).filter { it.id < this.id}.lastOpt();
        val lowerBound: Int = latestRemove?.id ?: 0;

        val prev: ConfigAction? = this.tileInfo.all().filter {
            it is ConfigAction && it.pos == this.pos && it.id < this.id && it. id > lowerBound}.lastOpt() as ConfigAction?;

        // TODO: This thing triggers the Config event, use tileConfig__forward (non-public method)
        Call.tileConfig(null, Vars.world.build(this.pos), prev?.config);
    }
}