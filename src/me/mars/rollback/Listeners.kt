package me.mars.rollback

import arc.func.Boolf
import arc.func.Prov
import arc.math.geom.Point2
import arc.struct.OrderedMap
import arc.struct.Seq
import arc.util.Log
import me.mars.rollback.RollbackPlugin.Companion.debug
import me.mars.rollback.RollbackPlugin.Companion.tileStore
import me.mars.rollback.actions.BuildAction
import me.mars.rollback.actions.ConfigAction
import me.mars.rollback.actions.DeleteAction
import mindustry.Vars
import mindustry.game.EventType.*
import mindustry.gen.Building
import mindustry.gen.Groups
import mindustry.gen.Unit
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.world.blocks.ConstructBlock.ConstructBuild

typealias Events = Seq<Event>
val eventStore: OrderedMap<Int, Events> = OrderedMap()

// TODO: That is ugly
fun OrderedMap<Int, Events>.getOrPut(key: Int, prov: Prov<Events> = Prov<Events> { Seq() }): Events {
    if (!this.containsKey(key)) this.put(key, prov.get())
    return this.get(key)
}
fun addListeners() {
    arc.Events.run(Trigger.update) {
        for (entry in eventStore.entries()) {
            val events: Events = entry.value
            if (events.isEmpty) continue
            Log.info("Matching for ${Point2.unpack(entry.key)}: $events")
            repeat(events.size) {i ->
                globalMatcher.match(events, i)
            }
            events.clear()
        }
    }

    onEvent<WorldLoadEvent> {
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

    onEvent<TileChangeEvent> {
        eventStore.getOrPut(it.tile.pos()).add(TileChangeE(it.tile, it.tile.build))
    }

    onEvent<TilePreChangeEvent> {
        eventStore.getOrPut(it.tile.pos()).add(TilePreChangeE(it.tile, it.tile.build))
    }

    onEvent<BlockBuildBeginEvent> {
        if (it.breaking) {
            eventStore.getOrPut(it.tile.pos()).add(UnitRemoveE(it.tile, it.tile.build, it.unit))
        } else {
            if (it.tile.build is ConstructBuild) {
                val cb: ConstructBuild = it.tile.build as ConstructBuild
                for (prev in cb.prevBuild) {
                    if (prev.pos() != prev.tile.pos()) Log.err("pos mistmatch")
                    eventStore.getOrPut(prev.pos()).add(UnitRemoveE(prev.tile, prev, it.unit))
                }
            }
        }
    }

    onEvent<BlockBuildEndEvent> {
        if (it.breaking) return@onEvent
        eventStore.getOrPut(it.tile.pos()).add(UnitBuildE(it.tile, it.tile.build, it.unit))
    }

//    Events.on()


}

//  TilePreSet (Any real build) -> TileSet (ConstructBuild or none) = Building gon
//  TilePreSet (Any real build) -> TileSet (Different build that is real) = Building gon + New building
//  TilePreSet (ConstructBuild or none) -> TileSet (Real build) = New building


val globalMatcher: Matcher<Event> = with(Matcher(Event::class.java)) {
    increment = false
    // TODO: Should ignore event if its build pos isnt this tile pos?
    addMatcher(TilePreChangeE::class.java, { it.build != null && it.build !is ConstructBuild } ) {
        //  TilePreSet (Any real build) -> TileSet (ConstructBuild or none) = Building gon
        addMatcher(TileChangeE::class.java, { it.build is ConstructBuild || it.build == null}) {
            success { it, events ->
                val prev: TilePreChangeE = events[events.indexOf(it)-1] as TilePreChangeE
                Log.info("Success: ${prev.build} -> None")
                tileStore.setAction(DeleteAction("", it.tile.pos(), prev.build!!.block.size, prev.build.team))
            }
        }
        //  TilePreSet (Any real build) -> TileSet (Different build that is real) = Building gon + New building
        // TODO: Check if the build is not the same
        addMatcher(TileChangeE::class.java, { it.build != null && it.build !is ConstructBuild }) {
            success { it, events ->
                Log.info("Success: Real -> Real")
                val prev: TileChangeE = events[events.indexOf(it)] as TileChangeE
                tileStore.setAction(DeleteAction("", prev.build!!.pos(), prev.build.block.size, prev.build.team))
                tileStore.setAction(BuildAction("", it.build!!.pos(), it.build.block.size, it.build.team,
                    it.build.block, it.build.rotation.toByte()))
            }
        }
    }
    //  TilePreSet (ConstructBuild or none) -> TileSet (Real build) = New building
    addMatcher(TilePreChangeE::class.java, { it.build is ConstructBuild || it.build == null }) {
        addMatcher(TileChangeE::class.java, { it.build != null && it.build !is ConstructBuild }) {
            success { it, _ ->
                Log.info("Success: None -> Real")
                tileStore.setAction(BuildAction("", it.build!!.pos(), it.build.block.size, it.build.team,
                    it.build.block(), it.build.rotation.toByte()))
            }
        }
    }

    addMatcher(UnitBuildE::class.java, { it.unit.player != null }) {
        success { it, _ ->
            Log.info("Success of $it")
            tileStore.taskQueue.add { (tileStore.get(it.build.pos()).actions.peek() as BuildAction).uuid = it.unit.player.uuid() }
        }
    }

    addMatcher(UnitRemoveE::class.java, { it.unit.player != null }) {
        success { it, _->
            Log.info("Success of $it")
//            tileStore.taskQueue.add { Log.info("Tilelog: @", tileStore.get(it.build.pos()).actions) }
            tileStore.taskQueue.add { (tileStore.get(it.build.pos()).actions.peek() as DeleteAction).uuid = it.unit.player.uuid() }
        }
    }

    return@with this
}

abstract class Event(val tile: Tile) {
    override fun toString(): String {
        return "@[$tile]"
    }
}

class TilePreChangeE(tile: Tile, val build: Building?): Event(tile) {
    //    override fun yieldAction(iterator: Iterator<Event>): Action? {
//        TODO("Not yet implemented")
//    }
    override fun toString(): String {
        return "TilePreChangeE(build=$build)" + super.toString()
    }
}

class TileChangeE(tile: Tile, val build: Building?): Event(tile) {
    //    override fun yieldAction(iterator: Iterator<Event>): Action? {
//        TODO("Not yet implemented")
//    }
    override fun toString(): String {
        return "TileChangeE(build=$build)" + super.toString()
    }

}

/**
 * Used for the following: End of unit building, units dropping builds
 */
class UnitBuildE(tile: Tile, val build: Building, val unit: Unit): Event(tile) {
    override fun toString(): String {
        return "UnitBuildE(build=$build, unit=$unit)" + super.toString()
    }
}

/**
 * Used for the following: Start of unit deconstruction, units picking up builds
 */
class UnitRemoveE(tile: Tile, val build: Building, val unit: Unit): Event(tile) {
    override fun toString(): String {
        return "UnitRemoveE(build=$build, unit=$unit)" + super.toString()
    }
}

// TODO
//class ConfigE(tile: Tile)

open class Matcher<T : Event>(val matchClass: Class<T>, val check: Boolf<T>? = null) {
    val matchers: Seq<Matcher<*>> = Seq()
    var onSuccess: (T, Seq<Event>) -> kotlin.Unit = { it, _ ->
        Log.warn("Match success: $it. No further action done")
    }
    var increment: Boolean = true

    fun <R : Event>addMatcher(cls: Class<R>, check: Boolf<R>? = null, block: Matcher<R>.() -> kotlin.Unit) {
        val matcher = Matcher(cls, check)
        this.matchers.add(matcher)
        matcher.block()
    }

    @Suppress("UNCHECKED_CAST")
    private fun matches(event: Event): Boolean {
        if (event.javaClass != this.matchClass) return false
        return this.check?.get(event as T) ?: true
    }

    fun success(cons: (T, Events) -> kotlin.Unit) {
        this.onSuccess = cons
    }

    @Suppress("UNCHECKED_CAST")
    open fun match(events: Events, index: Int): Boolean {
        if (this.matchers.isEmpty) {
            this.onSuccess(events.get(index) as T, events)
            return true
        }
        val nextIndex: Int = index + if (this.increment) 1 else 0
        val next: Event = if (index < events.size) events.get(nextIndex) else return false
        var success = false
        for (matcher in this.matchers) {
            if (!matcher.matches(next)) continue
            if (matcher.match(events, nextIndex)) {
                if (success) Log.warn("Multiple matches found")
                success = true
                if (!debug) break
            }
        }
        return success
    }
}

//class GlobalMatcher() : Matcher<Event>(Event::class.java) {
//    init {
//        this.onSuccess = {Log.warn("Matcher failed?")}
//    }
//
//    override fun match(iterator: Iterator<Event>) {
//        super.match(iterator)
//    }
//}