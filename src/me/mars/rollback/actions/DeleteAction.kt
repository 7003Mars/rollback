package me.mars.rollback.actions

import arc.struct.Seq
import arc.util.Log
import me.mars.rollback.*
import mindustry.Vars.world
import mindustry.game.Team
import mindustry.world.blocks.storage.CoreBlock
import mindustry.world.blocks.storage.CoreBlock.CoreBuild
import mindustry.world.modules.ItemModule

class DeleteAction(uuid: String, pos: Int, blockSize: Int, team: Team) : Action(uuid, pos, blockSize, team) {
    override fun preUndo() {
        // I intend to place a block, however it is useless if:
        // My target is removed again by an active BuildAction
        this.willRollback = !this.tileInfo.all().before(this).contains { it is BuildAction && it.willRollback }
    }
    override fun undo(): Run? {
        val latestBuild: BuildAction? = this.tileInfo.select(-1, BuildAction::class.java) {it.id < this.id}
        if (latestBuild == null) {
            Log.warn("$this has no previous build logs. Should not happen!")
            return null
        }
        val latestConfig: ConfigAction? =  this.tileInfo.select(-1,
            ConfigAction::class.java) {it.pos == this.pos && it.id < this.id && it.id > latestBuild.id}
        if (RollbackPlugin.debug) {
            Log.info("Undo $this to ${latestBuild.block}, $latestConfig")
        }
        if (latestBuild.block is CoreBlock) {
            // Since core undos run first, the current building *should* be a core
            val items: ItemModule? = world.build(this.pos)?.takeIf { it is CoreBuild }?.items?.copy()
            return withSuppress {
                world.tile(this.pos).setNet(latestBuild.block, this.team, latestBuild.rotation.toInt())
                if (items != null) world.build(this.pos).items.set(items)
            }
        }
        return withSuppress {
            world.tile(this.pos).setNet(latestBuild.block, this.team, latestBuild.rotation.toInt())
            if (latestConfig != null) safeConfig(null, world.build(this.pos), latestConfig.config)
        }
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