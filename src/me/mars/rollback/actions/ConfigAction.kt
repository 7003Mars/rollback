package me.mars.rollback.actions

import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.Call

class ConfigAction(uuid: String, pos: Int, team: Team, val config: Any) : Action(uuid, pos, team){
    override fun undo() {
        val prev: Any? = this.tileInfo.configActions.getPrev()?.config;
        // TODO: This thing triggers the Config event, use tileConfig__forward (non-public method)
        Call.tileConfig(null, Vars.world.build(this.pos), prev);
    }
}