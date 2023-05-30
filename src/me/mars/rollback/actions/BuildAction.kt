package me.mars.rollback.actions

import arc.util.Log
import me.mars.rollback.RollbackPlugin
import me.mars.rollback.before
import mindustry.Vars.world
import mindustry.content.Blocks
import mindustry.game.Team
import mindustry.world.Block
import mindustry.world.blocks.storage.CoreBlock

class BuildAction(uuid: String, pos: Int, blockSize: Int, team: Team, val block: Block, val rotation: Byte) : Action(uuid, pos, blockSize, team) {

    override fun preUndo() {
        // I intend to remove a block, however it is useless if:
        // A DeleteAction before me runs
        this.willRollback = !this.tileInfo.all().before(this).contains { it is DeleteAction && it.willRollback}
    }

    override fun undo() {
        if (this.block is CoreBlock) return
        if (RollbackPlugin.debug) Log.info("Undo $this to air")
        world.tile(this.pos).setNet(Blocks.air)
    }

    override fun toString(): String {
        return super.toString() + "[build=${this.block}]"
    }
}