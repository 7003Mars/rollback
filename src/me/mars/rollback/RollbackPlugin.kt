package me.mars.rollback
import arc.util.CommandHandler
import arc.util.Log
import arc.util.Strings
import arc.util.Time
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.mod.Plugin

class RollbackPlugin(): Plugin() {
    companion object {
        @JvmStatic val tileStore: TileStore = TileStore(0, 0);
        @JvmStatic val debug: Boolean = true;
    }

    override fun init() {
        addListeners();
    }

    override fun registerClientCommands(handler: CommandHandler) {
        handler.register("rollback", "<name> [time]", "Rollback the actions of a player") {
            val player: Player = Groups.player.find { p -> p.name == it[0] } ?: return@register;
            Log.info("Rollback for @", player.name);
            val uuid: String = player.uuid();
            val ticks: Int = Strings.parseInt(it.getOrElse(1) {"1"}, 1) * Time.toMinutes.toInt() /60;
            tileStore.rollback(uuid, Time.time-ticks);
        }
    }
}