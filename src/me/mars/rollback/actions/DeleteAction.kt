package me.mars.rollback.actions

import arc.struct.Seq
import arc.util.Log
import me.mars.rollback.RollbackPlugin
import me.mars.rollback.only
import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.Call

class DeleteAction(uuid: String, pos: Int, team: Team) : Action(uuid, pos, team) {
    override fun undo() {
        val buildSeq: Seq<BuildAction> = this.tileInfo.all().only(BuildAction::class.java).filter { it.id < this.id};
        // REMOVEME: Required?
        Log.info("Old @", buildSeq);
        // TODO: Will this cause issues?
        // The build was overriden by some other player, ignore the undo
//        if (buildSeq.any { it.uuid != this.uuid && it.id > this.id }) return;
        if (buildSeq.isEmpty) return;
        val latestBuild: BuildAction = if (buildSeq.size == 1 ) {
            buildSeq.first();
        } else {
            buildSeq.selectRanked(Comparator.comparingInt { -it.id }, 1)
        };
        val configSeq: Seq<ConfigAction> = this.tileInfo.all().only(ConfigAction::class.java);
        configSeq.filter() { it.id > this.id && it.id < latestBuild.id && it.pos == this.pos};
        val latestConfig: ConfigAction? = if (configSeq.isEmpty) null else
            configSeq.selectRanked(Comparator.comparingInt { -it.id }, 1)
        if (RollbackPlugin.debug) {
            Log.info("Undo @ to @, @", this, latestBuild.block, latestConfig)
        }
        Vars.world.tile(this.pos).setNet(latestBuild.block, this.team, latestBuild.rotation.toInt());
        if (latestConfig != null) Call.tileConfig(null, Vars.world.build(this.pos), latestConfig.config)
    }
}