package me.mars.rollback.actions

import arc.Core
import arc.struct.Seq
import arc.util.Log
import me.mars.rollback.RollbackPlugin
import me.mars.rollback.before
import me.mars.rollback.only
import mindustry.Vars.world
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.world.blocks.storage.CoreBlock
import mindustry.world.blocks.storage.CoreBlock.CoreBuild
import mindustry.world.modules.ItemModule

class DeleteAction(uuid: String, pos: Int, team: Team) : Action(uuid, pos, team) {
    override fun preUndo() {
        // I intend to place a block, however it is useless if:
        // My target is removed again by an active BuildAction
        this.willRollback = !this.tileInfo.all().before(this).contains { it is BuildAction && it.willRollback }
    }
    override fun undo() {
        val buildSeq: Seq<BuildAction> = this.tileInfo.all().before(this).only()
        if (buildSeq.isEmpty) {
            Log.warn("@ has no previous build logs. Should not happen!")
            return
        }
        val latestBuild: BuildAction = buildSeq.selectRanked(Comparator.comparingInt { -it.id }, 1)
        val configSeq: Seq<ConfigAction> = this.tileInfo.all().only()
        configSeq.filter { it.id < this.id && it.id > latestBuild.id && it.pos == this.pos}
        val latestConfig: ConfigAction? = if (configSeq.isEmpty) null else
            configSeq.selectRanked(Comparator.comparingInt { -it.id }, 1)
        if (RollbackPlugin.debug) {
            Log.info("Undo @ to @, @", this, latestBuild.block, latestConfig)
        }
        if (latestBuild.block is CoreBlock) {
            // Since core undos run first, the current building *should* be a core
            Core.app.post {
                // TODO: Not sure if ItemModules are shared among cores.
                /** @see mindustry.world.blocks.storage.CoreBlock.CoreBuild.onRemoved in the future*/
                val items: ItemModule? = world.build(this.pos)?.takeIf { it is CoreBuild }?.items?.copy()
//                Log.info("Current items: @", items)
                world.tile(this.pos).setNet(latestBuild.block, this.team, latestBuild.rotation.toInt())
//                Log.info("Current is @", world.build(this.pos))
                if (items != null) world.build(this.pos).items.set(items)

            }
        } else {
            world.tile(this.pos).setNet(latestBuild.block, this.team, latestBuild.rotation.toInt())
        }
        if (latestConfig != null) Call.tileConfig(null, world.build(this.pos), latestConfig.config)
    }
    fun undoToCore(): Boolean {
        val buildSeq: Seq<BuildAction> = this.tileInfo.all().before(this).only()
        if (buildSeq.isEmpty) {
            return false
        }
        val latestBuild: BuildAction = if (buildSeq.size == 1 ) {
            buildSeq.first()
        } else {
            buildSeq.selectRanked(Comparator.comparingInt { -it.id }, 1)
        }
        return latestBuild.block is CoreBlock
    }
}