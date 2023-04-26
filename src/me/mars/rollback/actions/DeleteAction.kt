package me.mars.rollback.actions

import arc.struct.Seq
import arc.util.Log
import me.mars.rollback.ActionPair
import me.mars.rollback.RollbackPlugin
import mindustry.Vars
import mindustry.game.Team

class DeleteAction(uuid: String, pos: Int, team: Team) : Action(uuid, pos, team) {
    override fun undo() {
        val builds: ActionPair<BuildAction> = this.tileInfo.buildActions;
        val buildSeq: Seq<BuildAction> = builds.asArray();
        buildSeq.removeAll {it.id >= this.id}
        Log.info("Old @", buildSeq);
        // The build was overriden by some other player, ignore the undo
//        if (buildSeq.any { it.uuid != this.uuid && it.id > this.id }) return;
        val latest: BuildAction = if (buildSeq.size == 2 ) {
            buildSeq.selectRanked(Comparator.comparingInt { -it.id }, 1)
        } else {
            buildSeq.firstOpt()?: return;
        };
        if (RollbackPlugin.debug) {
            Log.info("Undo @ to @", this, latest.block)
        }
        Vars.world.tile(this.pos).setNet(latest.block, this.team, latest.rotation.toInt());
    }
}