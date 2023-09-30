package me.mars.rollback

import arc.func.Boolf
import arc.math.geom.Point2
import arc.struct.ObjectMap
import arc.struct.OrderedMap
import arc.struct.Seq
import arc.util.Log
import arc.util.Time
import arc.util.pooling.Pool
import arc.util.pooling.Pool.Poolable
import arc.util.pooling.Pools
import me.mars.rollback.RollbackPlugin.Companion.tileStore
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

/**
 * All these are cleared every tick
 */
private val eventStore: ObjectMap<Int, Events> = ObjectMap()
private val allEvents: Events = Seq()
private val tilePreSets: OrderedMap<Int, TilePreChange> = OrderedMap()
private val tilePool: Pool<TilePreChange> = Pools.get(TilePreChange::class.java, ::TilePreChange, 100)
var suppressEvents: Boolean = false

private fun add(pos: Int, event: Event) {
    eventStore.get(pos) { Seq() }.add(event)
    allEvents.add(event)
}

private var tickStartTime: Long = Time.millis()

fun addListeners() {
    Log.info("Adding the sus")
    arc.Events.run(Trigger.update) {
        tickStartTime = Time.millis()
//        allEvents.each { globalMatcher.match()}
        for (event: Event in allEvents) {
//             Log.info("\nNew match started for $event")
            globalMatcher.match(event, eventStore.get(event.tile.pos()))
        }
        eventStore.clear()
        allEvents.clear()
    }

    arc.Events.run(WorldLoadBeginEvent::class.java) {
        suppressEvents = true
        eventStore.clear()
        allEvents.clear()
    }

    arc.Events.on(WorldLoadEvent::class.java) {
        suppressEvents = false
        tileStore.width = Vars.world.width()
        tileStore.height = Vars.world.height()
        tileStore.resized()

        Groups.build.each {
            val block: Block = it.block
            tileStore.setAction(BuildAction("world", it.pos(), block.size, it.team, block, it.rotation.toByte()))
            if (block.configurable) {
                tileStore.setAction(ConfigAction("world", it.pos(), block.size, it.team, it.config()))
            }
        }
    }

    onEvent<TilePreChangeEvent> {
        tilePreSets.put(it.tile.pos(), tilePool.obtain().set(it.tile.build))
    }

    onEvent<TileChangeEvent> {
        val prev: TilePreChange = tilePreSets.get(it.tile.pos())!!
        val prevBuild: Building? = prev.build?.takeIf { b -> b.pos() == it.tile.pos() }
        val curBuild: Building? = it.tile.build?.takeIf { b -> b.pos() == it.tile.pos() }

        if (prevBuild != null && prevBuild !is ConstructBuild && (curBuild == null || curBuild is ConstructBuild)) {
            // Building gon
            add(prevBuild.pos(), RemoveE(prevBuild.tile, prevBuild))
        }
        if ((prevBuild == null || prevBuild is ConstructBuild) && curBuild != null && curBuild !is ConstructBuild) {
            add(curBuild.pos(), BuildE(curBuild.tile, curBuild))
//            Log.info("built")
            // Building built
        }
        if (prevBuild != null && prevBuild !is ConstructBuild && curBuild != null && curBuild !is ConstructBuild) {
//            Log.info("both")
            add(prevBuild.pos(), RemoveE(prevBuild.tile, prevBuild))
            add(curBuild.pos(), BuildE(curBuild.tile, curBuild))
        }
        tilePreSets.remove(it.tile.pos())
        tilePool.free(prev)

        if (curBuild is LogicBuild) {
            if (curBuild.executor !is ModifiedExecutor) curBuild.executor = ModifiedExecutor()
        }
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

    onEvent<BlockDestroyEvent> {
        if (it.tile.build == null) {
            Log.err("Entity of BlockDestroyEvent should not be null!")
            return@onEvent
        }
        add(it.tile.pos(), DestroyE(it.tile, it.tile.build))
    }
}


val globalMatcher: Matcher<Event> = with(Matcher(Event::class.java)) {
//    addMatcher(DestroyE::class.java) {
//        desc = "Match destroy"
//        addMatcher(RemoveE::class.java) {
//            desc = "Match destroy -> remove"
//            success { removeE: RemoveE, _: Events ->
//                val build: Building = removeE.build
//                tileStore.setAction(DeleteAction("destroy", build.pos(), build.block.size, build.team))
//            }
//        }
//    }

    addMatcher(RemoveE::class.java) {
        optional = true
        desc = "Match remove"
        addMatcher(UnitRemoveE::class.java) {
            desc = "Match remove -> unit remove"
            success { unitRemoveE: UnitRemoveE, _: Events ->
                val build: Building = unitRemoveE.build
                val unit: Unit = unitRemoveE.unit
                tileStore.setAction(DeleteAction(unit.uuidOrEmpty(), build.pos(), build.block.size, unit.team))
            }
        }
        success { removeE: RemoveE, _: Events ->
            val build: Building = removeE.build
            tileStore.setAction(DeleteAction("world", build.pos(), build.block.size, build.team))
        }
    }

    addMatcher(BuildE::class.java) {
        desc = "Match build"
        optional = true
        addMatcher(UnitBuildE::class.java) {
            desc = "Match build -> unit build"
            success { unitBuildE: UnitBuildE, _: Events ->
                val build: Building = unitBuildE.build
                val unit: Unit = unitBuildE.unit
                tileStore.setAction(BuildAction(unit.uuidOrEmpty(), build.pos(), build.block.size, unit.team, build.block, build.rotation.toByte()))
            }
        }
        success { buildE: BuildE, _: Events ->
            val build: Building = buildE.build
            tileStore.setAction(BuildAction("world", build.pos(), build.block.size, build.team, build.block, build.rotation.toByte()))
        }
    }

    addMatcher(ConfigE::class.java) {
        desc = "Match config"
        success { it, _ ->
            // TODO: Verify referenced block from tile / building is valid
            val uuid: String = it.player?.uuid() ?: ""
            tileStore.setAction(ConfigAction(uuid, it.tile.pos(), it.build.block.size,
                it.player?.team() ?: it.tile.team(), it.value))
        }
    }

    return@with this
}

class Matcher<T>(val matchClass: Class<T>, val check: Boolf<T>? = null) {
    /**
     * [desc] is used for debug only, null otherwise
     */
    var desc: String = "Global matcher"
    val matchers: Seq<Matcher<*>> = Seq()
    var optional: Boolean = false
    var onSuccess: (T, Seq<Event>) -> kotlin.Unit = { it, _ ->
        Log.warn("Match success: $it. No further action done")
    }

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

    fun match(cur: Event, events: Events) {
        this.match(cur, events, Int.MIN_VALUE)
    }

    @Suppress("UNCHECKED_CAST")
    private fun match(cur: Event?, events: Events, index: Int): Boolean {
        // If there is nothing left, we have reached an endpoint
        if (this.matchers.isEmpty || (cur == null && this.optional)) {
//            Log.info("$this has no more matchers or cur is null")
            this.onSuccess(events.get(index-1) as T, events)
            return true
        }
        if (cur == null) {
            Log.err("$this Skipped null event")
            return false
        }
//        Log.info("Cur: $cur\n$events")
        var success = false
        var nextIndex: Int = -1
        var next: Event? = null
        for (matcher in this.matchers) {
//            Log.info("Match against $matcher")
            if (!matcher.matches(cur)) continue
            if (next == null) {
                nextIndex = (if (index >= 0) index else events.indexOf(cur)) + 1
                // However, if there is nothing left, we failed
                next = if (nextIndex < events.size) events.get(nextIndex) else null
            }
            if (matcher.match(next, events, nextIndex)) {
                success = true
                break
            }
        }
        if (!success && this.optional) {
            val prevIndex: Int = if (index >= 0) index-1 else events.indexOf(cur)-1
//            Log.info("optional from failure, prev was ${events.get(prevIndex)}")
            this.onSuccess(events.get(prevIndex) as T, events)
            return true
        }
        return success
    }

    override fun toString(): String {
        return if (RollbackPlugin.debug) "\"${this.desc}\"" else super.toString()
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

class TilePreChange: Poolable {
    var build: Building? = null

    fun set(build: Building?): TilePreChange {
        this.build = build
        return this
    }

    override fun reset() {
        this.build = null
    }
}

abstract class Event(val tile: Tile) {
    override fun toString(): String {
        return "@[$tile]"
    }
}

class RemoveE(tile: Tile, val build: Building): Event(tile) {
    override fun toString(): String {
        return "RemoveE(${Point2.unpack(this.tile.pos())}, build=$build)" + super.toString()
    }
}
class BuildE(tile: Tile, val build: Building): Event(tile) {
    override fun toString(): String {
        return "BuildE(${Point2.unpack(this.tile.pos())}, build=$build)" + super.toString()
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
class ConfigE(tile: Tile, val build: Building, val value: Any?, val player: Player?): Event(tile) {
    override fun toString(): String {
        return "ConfigE(build=$build, player=$player)" + super.toString()
    }
}

/**
 * A building was destroyed
 */
class DestroyE(tile: Tile, val build: Building): Event(tile) {
    override fun toString(): String {
        return "DestroyE(build=$build)" + super.toString()
    }
}