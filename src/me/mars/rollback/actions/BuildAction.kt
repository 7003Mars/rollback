package me.mars.rollback.actions

import arc.scene.actions.RemoveAction
import arc.util.Log
import me.mars.rollback.RollbackPlugin
import me.mars.rollback.before
import me.mars.rollback.only
import mindustry.Vars.world
import mindustry.content.Blocks
import mindustry.game.Team
import mindustry.world.Block

class BuildAction(uuid: String, pos: Int, team: Team, val block: Block, val rotation: Byte) : Action(uuid, pos, team) {

    override fun preUndo() {
        // I intend to remove a block, however it is useless if:
        // A DeleteAction before me runs
        this.willRollback = !this.tileInfo.all().before(this).contains { it is DeleteAction && it.willRollback};
    }

    override fun undo() {
        if (RollbackPlugin.debug) Log.info("Undo @ to air", this)
        world.tile(this.pos).setNet(Blocks.air);
//        val prev: BuildAction = tileStore.get(this.pos).buildActions.latest()?: return;
//        world.tile(this.pos).setNet(prev.block, this.team, this.rotation.toInt());
    }

    override fun toString(): String {
        return super.toString() + "[build=${this.block}]"
    }
}