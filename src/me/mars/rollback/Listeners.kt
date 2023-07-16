package me.mars.rollback

import arc.func.Boolf
import arc.struct.OrderedMap
import arc.struct.Seq
import arc.util.Log
import arc.util.Time
import me.mars.rollback.RollbackPlugin.Companion.debug
import me.mars.rollback.RollbackPlugin.Companion.tileStore
import me.mars.rollback.actions.Action
import me.mars.rollback.actions.BuildAction
import me.mars.rollback.actions.ConfigAction
import me.mars.rollback.actions.DeleteAction
import mindustry.Vars
import mindustry.game.EventType.*
import mindustry.gen.Building
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.gen.Unit
import mindustry.logic.LAccess
import mindustry.logic.LExecutor
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.world.blocks.ConstructBlock.ConstructBuild
import mindustry.world.blocks.logic.LogicBlock.LogicBuild

typealias Events = Seq<Event>
private val eventStore: OrderedMap<Int, Events> = OrderedMap()
var suppressEvents: Boolean = false

private fun add(pos: Int, event: Event) {
    eventStore.get(pos) { Seq() }.add(event)
}

private var tickStartTime: Long = Time.millis()
private fun Action.claim(uuid: String, expectType: Class<out Action>) {
    if (this.javaClass != expectType) {
        Log.err("$expectType expected, got ${this.javaClass}")
        return
    }
    if (this.uuid.isNotEmpty()) {
        Log.err("$this already has uuid, can't be claimed")
        return
    }
    if (this.time < tickStartTime) {
        Log.err("$this created before time of current tick, $tickStartTime")
    }
    this.uuid = uuid
}

fun addListeners() {
    arc.Events.run(Trigger.update) {
        tickStartTime = Time.millis()
        if (eventStore.isEmpty) return@run
        for (entry in eventStore.entries()) {
            val events: Events = entry.value
            if (events.isEmpty) continue
//            Log.info("Matching for ${Point2.unpack(entry.key)}: $events")
            repeat(events.size) {i ->
                globalMatcher.match(events, i)
            }
            events.clear()
        }
    }

    arc.Events.run(WorldLoadBeginEvent::class.java) {
        suppressEvents = true
    }

    arc.Events.on(WorldLoadEvent::class.java) {
        suppressEvents = false
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
        add(it.tile.pos(), TileChangeE(it.tile, it.tile.build))
        if (it.tile.build == null || it.tile.build.tile != it.tile) return@onEvent
        if (it.tile.build is LogicBuild) {
            val build: LogicBuild = it.tile.build as LogicBuild
            if (build.executor is ModifiedExecutor) return@onEvent
            build.executor = ModifiedExecutor()
        }
    }

    onEvent<TilePreChangeEvent> {
        add(it.tile.pos(), TilePreChangeE(it.tile, it.tile.build))
    }

    onEvent<BlockBuildBeginEvent> {
        if (it.breaking) {
            add(it.tile.pos(), UnitRemoveE(it.tile, it.tile.build, it.unit))
        } else {
            if (it.tile.build is ConstructBuild) {
                val cb: ConstructBuild = it.tile.build as ConstructBuild
                for (prev in cb.prevBuild) {
                    if (prev.pos() != prev.tile.pos()) Log.err("pos mismatch")
                    add(prev.pos(), UnitRemoveE(prev.tile, prev, it.unit))
                }
            }
        }
    }

    onEvent<BlockBuildEndEvent> {
        if (it.breaking) return@onEvent
        add(it.tile.pos(), UnitBuildE(it.tile, it.tile.build, it.unit))
    }

    onEvent<PickupEvent> {
        if (it.build == null) return@onEvent
        add(it.build.pos(), UnitRemoveE(it.build.tile, it.build, it.carrier))
    }

    onEvent<PayloadDropEvent> {
        if (it.build == null) return@onEvent
        add(it.build.pos(), UnitBuildE(it.build.tile, it.build, it.carrier))
    }

    onEvent<ConfigEvent> {
        val build: Building = it.tile
        add(it.tile.pos(), ConfigE(build.tile, build, it.value, it.player))
    }

//    onEvent<CoreChangeEvent> {
//        add(it.t)
//    }


//    Events.on()


}

//  TilePreSet (Any real build) -> TileSet (ConstructBuild or none) = Building gon
//  TilePreSet (Any real build) -> TileSet (Different build that is real) = Building gon + New building
//  TilePreSet (ConstructBuild or none) -> TileSet (Real build) = New building


val globalMatcher: Matcher<Event> = with(Matcher(Event::class.java)) {
    increment = false
    addMatcher(TilePreChangeE::class.java, { it.build != null && it.build.tile == it.tile && it.build !is ConstructBuild} ) {
        //  TilePreSet (Any real build) -> TileSet (ConstructBuild or none) = Building gon
        addMatcher(TileChangeE::class.java, { it.build is ConstructBuild || it.build == null}) {
            success { it, events ->
                val prev: TilePreChangeE = events[events.indexOf(it)-1] as TilePreChangeE
//                Log.info("Success: ${prev.build} -> None")
                tileStore.setAction(DeleteAction("", it.tile.pos(), prev.build!!.block.size, prev.build.team))
            }
        }
        // TilePreSet (Any real build) -> TileSet (Different build that is real) = Building gon + New building
        // This should never be triggered by player actions
        addMatcher(TileChangeE::class.java, { it.build != null && it.build !is ConstructBuild }) {
            success { it, events ->
                // If nothing actually changed, do nothing
                val prev: TilePreChangeE = events[events.indexOf(it)-1] as TilePreChangeE
                if (prev.build == it.build) return@success
//                Log.info("Success: Real -> Real")
                tileStore.setAction(DeleteAction("", prev.build!!.pos(), prev.build.block.size, prev.build.team))
                val b: Building = it.build!!
                tileStore.setAction(BuildAction("", b.pos(), b.block.size, b.team,
                    b.block, b.rotation.toByte()))
            }
        }
    }
    // TODO: Should ignore event if its build pos isnt this tile pos?
    //  TilePreSet (ConstructBuild or none) -> TileSet (Real build) = New building
    addMatcher(TilePreChangeE::class.java, { it.build is ConstructBuild || it.build == null }) {
        addMatcher(TileChangeE::class.java, { it.build != null && it.build !is ConstructBuild }) {
            success { it, _ ->
//                Log.info("Success: None -> Real")
                tileStore.setAction(BuildAction("", it.build!!.pos(), it.build.block.size, it.build.team,
                    it.build.block(), it.build.rotation.toByte()))
            }
        }
    }

    addMatcher(UnitBuildE::class.java, { it.unit.player != null }) {
        success { it, _ ->
            val uuid: String = it.unit.player.uuid()
            tileStore.taskQueue.add {
                tileStore.get(it.build.pos()).actions.peek().claim(uuid, BuildAction::class.java)
            }
            if (it.build.block.configurable) tileStore.setAction(ConfigAction(uuid, it.build.pos(),
                it.build.block.size, it.unit.team, it.build.config()))
        }
    }

    addMatcher(UnitRemoveE::class.java, { it.unit.player != null }) {
        success { it, _->
//            tileStore.taskQueue.add { Log.info("Tilelog: @", tileStore.get(it.build.pos()).actions) }
            tileStore.taskQueue.add {
                tileStore.get(it.build.pos()).actions.peek().claim(it.unit.player.uuid(), DeleteAction::class.java)
            }
        }
    }

    addMatcher(ConfigE::class.java) {
        success { it, _ ->
            // TODO: Verify referenced block from tile / building is valid
            val uuid: String = it.player?.uuid() ?: ""
            tileStore.setAction(ConfigAction(uuid, it.tile.pos(), it.build.block.size,
                it.player?.team() ?: it.tile.team(), it.value))
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
    override fun toString(): String {
        return "TilePreChangeE(build=$build)" + super.toString()
    }
}

class TileChangeE(tile: Tile, val build: Building?): Event(tile) {
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

/**
 * Used for the following: Buildings getting configured
 */
class ConfigE(tile: Tile, val build: Building, val value: Any?, val player: Player?): Event(tile)

class Matcher<T : Event>(val matchClass: Class<T>, val check: Boolf<T>? = null) {
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
    fun match(events: Events, index: Int): Boolean {
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

class ModifiedExecutor : LExecutor() {
    override fun runOnce() {
        var index: Int = this.counter.numval.toInt()
        if (index >= this.instructions.size || index < 0) index = 0
        val instr: LInstruction = this.instructions[index]
        if (instr is ControlI && instr.type == LAccess.config) {
            val build: Building = this.obj(instr.target).takeIf { it is Building } as Building? ?: return
            if (!(this.privileged || (build.team == this.team && this.linkIds.contains(build.id)))) return
            if (!this.`var`(instr.p1).isobj) return
            val value: Any? = this.obj(instr.p1)
            tileStore.setAction(ConfigAction("", build.pos(), build.block.size, this.team, value))
        }
        super.runOnce()
    }
}