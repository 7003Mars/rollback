package me.mars.rollback

import arc.Events
import arc.struct.ObjectSet
import arc.util.Log
import me.mars.rollback.RollbackPlugin.Companion.tileStore
import me.mars.rollback.actions.BuildAction
import me.mars.rollback.actions.ConfigAction
import me.mars.rollback.actions.DeleteAction
import mindustry.Vars
import mindustry.game.EventType.*
import mindustry.gen.Building
import mindustry.gen.Groups
import mindustry.world.Block
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.ConstructBlock.ConstructBuild
import mindustry.world.blocks.storage.CoreBlock.CoreBuild

fun addListeners() {
    Events.on(WorldLoadEvent::class.java) {
        tileStore.width = Vars.world.width();
        tileStore.height = Vars.world.height();
        tileStore.resized();

        Groups.build.each {
            val block: Block = it.block;
            Log.info(it);
            tileStore.setAction(BuildAction("", it.pos(), it.team, block, it.rotation.toByte()), block.size);
            if (block.configurable && it.config() != null) {
                tileStore.setAction(ConfigAction("", it.pos(), it.team, it.config()), block.size);
            }
        }
    }

    Events.on(BlockDestroyEvent::class.java) {
        tileStore.clear(it.tile.x.toInt(), it.tile.y.toInt(), it.tile.block().size);
    }

    Events.on(BlockBuildBeginEvent::class.java) {
        if (it.unit.player == null) {
            Log.info("no player")
            return@on
        };
        val uuid: String = it.unit.player.uuid();
        // Instant core upgrade
        if (it.tile.build is CoreBuild) {
            val build: CoreBuild = it.tile.build as CoreBuild;
            val size: Int = build.block.size;
            val sx: Int = build.tileX() + build.block.sizeOffset;
            val sy: Int = build.tileY() + build.block.sizeOffset;
            // Find all builds I replaced - including the previous core
            val added: ObjectSet<BuildAction> = ObjectSet();
            for (x in sx until sx+size) {
                for (y in sy until sy+size) {
                    val lastBuild: BuildAction = tileStore.get(x, y).actions.lastOpt()
                        .takeIf { action -> action is BuildAction } as BuildAction? ?: continue;
                    if (added.contains(lastBuild)) continue;
                    Log.info("Core replaced: @", lastBuild)
                    tileStore.setAction(DeleteAction(uuid, lastBuild.pos, it.team), lastBuild.block.size);
                    added.add(lastBuild);
                }
            }
            tileStore.setAction(BuildAction(uuid, build.pos(), it.team, build.block, 0), size);
            return@on;
        }
        if (it.tile.build !is ConstructBuild) {
            Log.warn("Build is @, not ConstructBuild", it.tile.build)
            return@on;
        }
        val size: Int = it.tile.build.block.size;
        for (prev: Building in (it.tile.build as ConstructBuild).prevBuild) {
            tileStore.setAction(DeleteAction(uuid, prev.pos(), it.team), size);
        }
    }

    Events.on(BlockBuildEndEvent::class.java) {
        if (it.unit.player == null) {
            Log.info("no player")
            return@on
        };
        val uuid: String = it.unit.player.uuid();
        // Deconstruct finished, ignore it
        // Not actually sure why it would result in the build being null though.
        if (it.tile.build == null || it.tile.build.block is ConstructBlock) return@on;
        val pos: Int = it.tile.build.pos();
        val block: Block = it.tile.build.block;
        tileStore.setAction(BuildAction(uuid, pos, it.team, block, it.tile.build.rotation.toByte()), block.size);
        val config: Any? = it.tile.build.config();
        if (config != null)  tileStore.setAction(ConfigAction(uuid, pos, it.team, config), block.size);
    }

    Events.on(ConfigEvent::class.java) {
        if (it.player == null) {
            Log.info("no player")
            return@on
        };
        val pos: Int = it.tile.pos();
        val size: Int = it.tile.block.size;
        tileStore.setAction(ConfigAction(it.player.uuid(), pos, it.player.team(), it.value), size);
    }

    Events.on(BlockDestroyEvent::class.java) {
        tileStore.clear(it.tile.x.toInt(), it.tile.y.toInt(), it.tile.build.block.size);
    }



}
