package me.mars.rollback

import arc.Core
import arc.Events
import arc.func.Boolf
import arc.math.geom.Point2
import arc.struct.ObjectSet
import arc.struct.Seq
import arc.util.Log
import arc.util.Threads
import me.mars.rollback.actions.Action
import me.mars.rollback.actions.DeleteAction
import me.mars.rollback.actions.Run
import mindustry.game.EventType
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock

class TileStore(var width: Int, var height: Int) {
    private val executor: ExecutorService = Threads.executor("rollback", 1)
    val taskQueue: Seq<Runnable> = Seq()
    private var lock: ReentrantLock = ReentrantLock()

    private val tiles: Seq<TileInfo?> = Seq()

    init {
        this.resized()

        Events.run(EventType.Trigger.update) {
            if (!this.lock.tryLock()) return@run
            try {
                this.taskQueue.each(Runnable::run)
                this.taskQueue.clear()
            } finally {
                this.lock.unlock()
            }
        }
    }

    /**
     * Called on map change
     */
    fun resized() {
        try {
            this.lock.lock()
            this.taskQueue.clear()
            this.tiles.clear()
            this.tiles.setSize(this.width * this.height)
        } finally {
            this.lock.unlock()
        }
    }

    /**
     * Get the [TileInfo] at ([x], [y])
     */
    fun get(x: Int, y: Int): TileInfo {
        if (x < 0 || x >= this.width || y < 0 || y >= this.height) {
            throw IllegalArgumentException("Coordinates $x, $y are out of range (${this.width}, ${this.height})")
        }
        val index: Int = y * this.width + x
        if (this.tiles.get(index) == null) {
            this.tiles.set(index, TileInfo())
        }
        return this.tiles.get(y * this.width + x)!!
    }

    fun get(pos: Int): TileInfo {
        return this.get(Point2.x(pos).toInt(), Point2.y(pos).toInt())
    }

    private fun set(x: Int, y: Int, action: Action) {
        if (x < 0 || x >= this.width || y < 0 || y >= this.height) {
            throw IllegalArgumentException("Coordinates $x, $y are out of range (${this.width}, ${this.height})")
        }
        this.get(x, y).add(action)
    }

    /**
     * Sets an [action]
     */
    fun setAction(action: Action) {
        if (RollbackPlugin.debug) Log.info("Add: $action")
        this.taskQueue.add {
            val offset: Int = (action.blockSize-1)/2
            val sx: Int = Point2.x(action.pos).toInt() - offset
            val sy: Int = Point2.y(action.pos).toInt() - offset

            for (x in sx until sx+action.blockSize) {
                for (y in sy until sy+action.blockSize) {
                    this.set(x, y, action)
                }
            }
        }
    }

    /**
     * Clear all action logs from [id] onwards, assuming a block at ([x], [y]) of size [blockSize]
     */
    fun clear(x: Int, y: Int, blockSize: Int, id: Int) {
        val offset: Int = (blockSize-1)/2
        val sx: Int = x-offset
        val sy: Int = y-offset
        for (_x in sx until sx+blockSize) {
            for (_y in sy until sy+blockSize) {
                val index: Int = _y * this.width + _x
                this.tiles.get(index)?.clear(id)
            }
        }
    }

    /**
     * Collect all actions that match the [check]
     * Note that this method is blocking
     */
    fun collectLatest(check: Boolf<Action>): Seq<Action> {
        try {
            this.lock.lock()
            val added: ObjectSet<Action> = ObjectSet()
            val selected: Seq<Action> = Seq()
            for (tileInfo: TileInfo? in this.tiles) {
                if (tileInfo == null) continue
                for (action: Action in tileInfo.actions) {
                    if (check.get(action) && !added.contains(action)) {
                        selected.add(action)
                        added.add(action)
                    }
                }
                tileInfo.all().each {it.willRollback = false;}
            }
            added.clear()
            return selected
        } finally {
            this.lock.unlock()
        }
    }

    /**
     * Calling this submits a task to undo the action of a player with [uuid], from [time] millis onwards
     */
    fun rollback(uuid: String, time: Long) {
        this.executor.submit {
            try {
                this.lock.lock()
                val actions: Seq<Action> = this.collectLatest { it.time > time && it.uuid == uuid }.sort()
//                Log.info("Collected: \n@", actions);
                actions.each(Action::preUndo)
                actions.retainAll(Action::willRollback)
//                Log.info("filtered:\n@", actions)
                // Rollback cores first to prevent game over
                val coreUndo = actions.popAll { it is DeleteAction && it.undoToCore() }.`as`<DeleteAction>()
                coreUndo.each { it.undo()?.also { r -> Core.app.post(r) } }
                for (i in actions.size-1 downTo  0) {
                    val runnable: Run? = actions.get(i).undo()
                    if (runnable != null) Core.app.post(runnable)
                }
                coreUndo.each { this.clear(Point2.x(it.pos).toInt(), Point2.y(it.pos).toInt(), it.blockSize, it.id) }
                actions.each { this.clear(Point2.x(it.pos).toInt(), Point2.y(it.pos).toInt(), it.blockSize, it.id) }
                Log.info("Rollback for $uuid from time $time done")
            } catch (e: Exception) {
              Log.err("Something went wrong during the rollback!", e)
            } finally {
                this.lock.unlock()
            }
        }
    }
}