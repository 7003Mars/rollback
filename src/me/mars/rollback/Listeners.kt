package me.mars.rollback

import arc.Events
import arc.struct.ObjectSet
import arc.util.Log
import me.mars.rollback.RollbackPlugin.Companion.debug
import me.mars.rollback.RollbackPlugin.Companion.tileStore
import me.mars.rollback.actions.Action
import me.mars.rollback.actions.BuildAction
import me.mars.rollback.actions.ConfigAction
import me.mars.rollback.actions.DeleteAction
import mindustry.Vars
import mindustry.game.EventType.*
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Groups
import mindustry.world.Block
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.ConstructBlock.ConstructBuild
import mindustry.world.blocks.storage.CoreBlock.CoreBuild

fun addListeners() {
    Events.on(WorldLoadEvent::class.java) {
        tileStore.width = Vars.world.width()
        tileStore.height = Vars.world.height()
        tileStore.resized()

        Groups.build.each {
            val block: Block = it.block
            Log.info(it)
            tileStore.setAction(BuildAction("", it.pos(), it.team, block, it.rotation.toByte()), block.size)
            if (block.configurable) {
                tileStore.setAction(ConfigAction("", it.pos(), it.team, it.config()), block.size)
            }
        }
    }

    Events.on(PickupEvent::class.java) {
        if (it.build == null) return@on
        // Thankfully the build tile doesn't update before the event is fired.. for now
        val latestBuild: BuildAction? = tileStore.get(it.build.tileX(), it.build.tileY())
            .all().only<BuildAction>().sort(Comparator.comparing(Action::id)).lastOpt()
        if (latestBuild?.block != it.build.block) {
            Log.warn("Build mismatch: @ and @", latestBuild, it.build.block)
            return@on
        }
        tileStore.setAction(DeleteAction(it.carrier.player?.uuid()?: "", latestBuild!!.pos, it.carrier.team), latestBuild.block.size)
    }

    Events.on(PayloadDropEvent::class.java) {
        if (it.build == null) return@on
        val block: Block = it.build.block
        tileStore.setAction(BuildAction(it.carrier.uuidOrEmpty(), it.build.pos(), it.carrier.team,
            block, it.build.rotation.toByte()), block.size)
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
                    val latestBuild: BuildAction = tileStore.get(x, y).all().only<BuildAction>()
                        .sort(Comparator.comparing(Action::id)).lastOpt() ?: continue
                    if (added.contains(latestBuild)) continue
                    if (debug) Log.info("Core replaced: @", latestBuild)
                    tileStore.setAction(DeleteAction(uuid, latestBuild.pos, it.team), latestBuild.block.size)
                    added.add(latestBuild)
                }
            }
            tileStore.setAction(BuildAction(uuid, build.pos(), it.team, build.block, 0), size)
            return@on
        }
        if (it.tile.build !is ConstructBuild) {
            Log.warn("Build is @, not ConstructBuild", it.tile.build)
            return@on
        }
        val size: Int = it.tile.build.block.size
        for (prev: Building in (it.tile.build as ConstructBuild).prevBuild) {
            tileStore.setAction(DeleteAction(uuid, prev.pos(), it.team), size)
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
        tileStore.setAction(BuildAction(uuid, pos, it.team, block, it.tile.build.rotation.toByte()), block.size)
        val config: Any? = it.tile.build.config()
        if (config != null)  tileStore.setAction(ConfigAction(uuid, pos, it.team, config), block.size)
    }

    Events.on(ConfigEvent::class.java) {
        if (it.player == ConfigAction.fakePlayer) return@on
        val pos: Int = it.tile.pos()
        val size: Int = it.tile.block.size
        val team: Team = it.player?.team()?: it.tile.team
        tileStore.setAction(ConfigAction(it.player?.uuid()?: "", pos, team, it.tile.config()), size)
    }

    Events.on(BlockDestroyEvent::class.java) {
        tileStore.clear(it.tile.x.toInt(), it.tile.y.toInt(), it.tile.build.block.size)
    }



}
