package me.mars.rollback
import arc.Events
import arc.util.*
import mindustry.Vars
import mindustry.game.EventType.GameOverEvent
import mindustry.game.Team
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.mod.Plugin

class RollbackPlugin : Plugin() {
    companion object {
        @JvmStatic val tileStore: TileStore = TileStore(0, 0)
        @JvmStatic val debug: Boolean = OS.hasEnv("rollback.debug")
        @JvmStatic val internalName: String = "rollback"
        val sel: Select = Select()
    }

    override fun init() {
        Log.info("Rollback running version ${Vars.mods.getMod(internalName).meta.version}")
        if (debug) Log.info("Debug mode is enabled, expect more logs")
        addListeners()
        addRaw()
    }

    override fun registerClientCommands(handler: CommandHandler) {
        if (!debug) return
        handler.register("rollback", "<name> [time]", "Rollback the actions of a player") {
            val player: Player = Groups.player.find { p -> p.name == it[0] } ?: return@register
            Log.info("Rollback for @", player.name)
            val uuid: String = player.uuid()
            val millis: Int = Strings.parseInt(it.getOrElse(1) {"1"}, 1) * 1000
            tileStore.rollback(uuid, Time.millis()-millis)
        }

        handler.register("fake", "fake") {
            tileStore.collectLatest { true }.each { it.uuid = "" }
        }

        handler.register("bg", "[name...]", "log graph to console") {
            Log.info("\n"+map.buildGraph(if (it.isNotEmpty()) it[0] else "nameME"))
            map.clear()
        }
        handler.register("c", "clear") {
            map.clear()
        }

        handler.register("rtv", "rtv") {
//            Vars.world.loadMap(Vars.maps.getNextMap(Gamemode.sandbox, null))
            Events.fire(GameOverEvent(Team.crux))
        }

//        Events.on(EventType.PlayerLeave::class.java) {
//            Core.app.exit();
//        }
    }
}