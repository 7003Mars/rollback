package me.mars.rollback

import arc.Events
import arc.math.geom.Point2
import arc.struct.ObjectSet
import arc.util.Log
import me.mars.rollback.RollbackPlugin.Companion.debug
import me.mars.rollback.RollbackPlugin.Companion.tileStore
import me.mars.rollback.actions.BuildAction
import me.mars.rollback.actions.ConfigAction
import me.mars.rollback.actions.DeleteAction
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.game.EventType.*
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Groups
import mindustry.logic.LAccess
import mindustry.logic.LExecutor
import mindustry.logic.TileLayer
import mindustry.world.Block
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.ConstructBlock.ConstructBuild
import mindustry.world.blocks.logic.LogicBlock.LogicBuild
import mindustry.world.blocks.storage.CoreBlock.CoreBuild

fun addListeners() {
    Events.on(WorldLoadEvent::class.java) {
        tileStore.width = Vars.world.width()
        tileStore.height = Vars.world.height()
        tileStore.resized()

        Groups.build.each {
            val block: Block = it.block
            tileStore.setAction(BuildAction("", it.pos(), block.size, it.team, block, it.rotation.toByte()))
            if (block.configurable) {
                tileStore.setAction(ConfigAction("", it.pos(), block.size, it.team, it.config()))
            }
        }
    }

    Events.on(PickupEvent::class.java) {
        if (it.build == null) return@on
        // Thankfully the build tile doesn't update before the event is fired... for now
        val latestBuild: BuildAction? = tileStore.get(it.build.tileX(), it.build.tileY())
            .select(-1, BuildAction::class.java) { true }
        if (latestBuild?.block != it.build.block) {
            Log.warn("Build mismatch: @ and @", latestBuild, it.build.block)
            return@on
        }
        tileStore.setAction(DeleteAction(it.carrier.player?.uuid()?: "", latestBuild!!.pos, latestBuild.block.size, it.carrier.team))
    }

    Events.on(PayloadDropEvent::class.java) {
        if (it.build == null) return@on
        val block: Block = it.build.block
        tileStore.setAction(BuildAction(it.carrier.uuidOrEmpty(), it.build.pos(), block.size, it.carrier.team,
            block, it.build.rotation.toByte()))
    }

    Events.on(BlockBuildBeginEvent::class.java) {
        if (debug && it.unit.player == null) {
            Log.info("no player")
        }
        val uuid: String = it.unit.uuidOrEmpty()
        // Instant core upgrade
        if (it.tile.build is CoreBuild) {
            val build: CoreBuild = it.tile.build as CoreBuild
            val size: Int = build.block.size
            val sx: Int = build.tileX() + build.block.sizeOffset
            val sy: Int = build.tileY() + build.block.sizeOffset
            // Find all builds I replaced - including the previous core
            val added: ObjectSet<BuildAction> = ObjectSet()
            for (x in sx until sx+size) {
                for (y in sy until sy+size) {
                    val latestBuild: BuildAction = tileStore.get(x, y).select(-1, BuildAction::class.java) {true} ?: continue
                    if (added.contains(latestBuild)) continue
                    if (debug) Log.info("Core replaced: @", latestBuild)
                    tileStore.setAction(DeleteAction(uuid, latestBuild.pos, latestBuild.block.size, it.team))
                    added.add(latestBuild)
                }
            }
            tileStore.setAction(BuildAction(uuid, build.pos(), size, it.team, build.block, 0))
            return@on
        }
        if (it.tile.build !is ConstructBuild) {
            Log.warn("Build is @, not ConstructBuild", it.tile.build)
            return@on
        }
        // TODO: Why'd I use this?
//        val size: Int = it.tile.build.block.size
        for (prev: Building in (it.tile.build as ConstructBuild).prevBuild) {
            tileStore.setAction(DeleteAction(uuid, prev.pos(), prev.block.size, it.team))
        }
    }

    Events.on(BlockBuildEndEvent::class.java) {
        if (debug && it.unit.player == null) {
            Log.info("no player")
        }
        val uuid: String = it.unit.uuidOrEmpty()
        // Deconstruct finished, ignore it
        // Not actually sure why it would result in the build being null though.
        if (it.tile.build == null || it.tile.build.block is ConstructBlock) return@on
        val pos: Int = it.tile.build.pos()
        val block: Block = it.tile.build.block
        tileStore.setAction(BuildAction(uuid, pos, block.size, it.team, block, it.tile.build.rotation.toByte()))
        val config: Any? = it.tile.build.config()
        if (config != null)  tileStore.setAction(ConfigAction(uuid, pos, block.size, it.team, config))
    }

    Events.on(ConfigEvent::class.java) {
        if (it.player == ConfigAction.fakePlayer) return@on
        val pos: Int = it.tile.pos()
        val size: Int = it.tile.block.size
        val team: Team = it.player?.team()?: it.tile.team
        tileStore.setAction(ConfigAction(it.player?.uuid()?: "", pos, size, team, it.tile.config()))
    }

    Events.on(BlockDestroyEvent::class.java) {
        val size: Int = it.tile.build.block.size
        tileStore.taskQueue.add {
            tileStore.clear(
                it.tile.x.toInt(),
                it.tile.y.toInt(),
                size,
                0
            )
        }
    }

    Events.on(TileChangeEvent::class.java) {
        val build: LogicBuild = it.tile.build.takeIf { b -> b is LogicBuild } as LogicBuild? ?: return@on
        if (build.executor !is Executor) {
            // Should happen on the tick the block is set, therefore no need to clone LExecutor fields
            build.executor = Executor()
        }
    }

}

class Executor : LExecutor() {
    override fun runOnce() {
        // TODO: Coded while half awake, please check through
        var index: Int = (this.counter.numval+1).toInt()
        if (index >= this.instructions.size || index < 0) index = 0
        val instr: LInstruction = this.instructions[index]
        // TOOD: Check for configs too
        when (instr) {
            is SetBlockI -> {
                if (instr.layer == TileLayer.block) {
                    val b: Block = this.obj(instr.block).takeIf { it is Block } as Block? ?: return
                    val x: Int = this.numi(instr.x)
                    val y: Int = this.numi(instr.y)
                    val sx: Int = x+b.sizeOffset
                    val sy: Int = y+b.sizeOffset
                    val added: ObjectSet<Building> = ObjectSet()
                    for (_x in sx until sx+b.size) {
                        for (_y in sy until sy+b.size) {
                            val build: Building = Vars.world.build(_x, _y) ?: continue
                            if (build is ConstructBuild || added.contains(build)) continue
                            tileStore.setAction(DeleteAction("", build.pos(), build.block.size, this.team))
                            added.add(build)
                        }
                    }

                    if (b != Blocks.air) {
                        val team: Team = this.team(instr.team) ?: Team.derelict
                        tileStore.setAction(BuildAction("", Point2.pack(x, y), b.size, team, b, 0))
                    }
                }
            }

            is ControlI -> {
                if (instr.type == LAccess.config) {
                    val build: Building = this.obj(instr.target).takeIf { it is Building } as Building? ?: return
                    if (!(this.privileged || (build.team == this.team && this.linkIds.contains(build.id)))) return
                    if (!this.`var`(instr.p1).isobj) return
                    val value: Any? = this.obj(instr.p1)
                    tileStore.setAction(ConfigAction("", build.pos(), build.block.size, this.team, value))
                }
            }
        }
        super.runOnce()
    }
}

