package me.mars.rollback

import arc.Events
import arc.func.Boolf
import arc.math.geom.Point2
import arc.struct.ObjectSet
import arc.struct.Seq
import arc.util.Log
import arc.util.Threads
import me.mars.rollback.actions.Action
import mindustry.game.EventType
import java.lang.IllegalArgumentException
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock

class TileStore(var width: Int, var height: Int) {
    private val executor: ExecutorService = Threads.executor("rollback", 1);
    private val taskQueue: Seq<Runnable> = Seq();
    private var lock: ReentrantLock = ReentrantLock();

    private val tiles: Seq<TileInfo> = Seq();
    init {
        this.resized();

        Events.run(EventType.Trigger.update) {
            if (!this.lock.tryLock()) return@run;
            try {
                this.taskQueue.each(Runnable::run);
                this.taskQueue.clear();
            } finally {
                this.lock.unlock();
            }
        }
    }

    fun resized() {
        this.tiles.clear();
        this.tiles.setSize(this.width * this.height);
        for (x in 0  until this.width) {
            for (y in 0 until this.height) {
                val pos = y * width + x;
                this.tiles.set(pos, TileInfo(Point2.pack(x, y)));
            }
        }
    }

    fun get(x: Int, y: Int): TileInfo {
        if (x < 0 || x >= this.width || y < 0 || y >= this.width) {
            throw IllegalArgumentException("Coordinates $x, $y are out of range (${this.width}, ${this.height})");
        }
        return this.tiles.get(y * this.width + x);
    }

    fun get(pos: Int): TileInfo {
        return this.get(Point2.x(pos).toInt(), Point2.y(pos).toInt())
    }

    private fun set(x: Int, y: Int, action: Action) {
        if (x < 0 || x >= this.width || y < 0 || y >= this.width) {
            throw IllegalArgumentException("Coordinates $x, $y are out of range (${this.width}, ${this.height})");
        }
        this.tiles.get(y * this.width + x).add(action);
    }

    fun setAction(action: Action, blockSize: Int) {
        Log.info(action);
        this.taskQueue.add {
            val offset: Int = (blockSize-1)/2;
            val sx: Int = Point2.x(action.pos).toInt() - offset;
            val sy: Int = Point2.y(action.pos).toInt() - offset;

            for (x in sx until sx+blockSize) {
                for (y in sy until sy+blockSize) {
                    this.set(x, y, action);
                }
            }
        }
    }

    fun clear(x: Int, y: Int, blockSize: Int) {
        this.taskQueue.add {
            val offset: Int = (blockSize-1)/2;
            val sx: Int = x-offset;
            val sy: Int = y-offset;
            for (i in sx until sx+blockSize) {
                for (j in sy until sy+blockSize) {
                    this.get(i, j).clear();
                }
            }
        }
    }

    fun collectLatest(check: Boolf<Action>): Seq<Action> {
        try {
            this.lock.lock();
            val added: ObjectSet<Action> = ObjectSet();
            val selected: Seq<Action> = Seq();
            for (tileInfo: TileInfo in this.tiles) {
                for (action: Action in tileInfo.actions) {
                    if (check.get(action) && !added.contains(action)) {
                        selected.add(action);
                        added.add(action);
                    }
                }
                tileInfo.all().each {it.willRollback = false;}
            }
            added.clear();
            return selected;
        } finally {
            this.lock.unlock()
        }
    }

    fun rollback(uuid: String, time: Float) {
        this.executor.submit {
            try {
                this.lock.lock();
                val actions: Seq<Action> = this.collectLatest { it.time > time && it.uuid == uuid }.sort { a -> a.id.toFloat()}
                actions.each(Action::preUndo)
                Log.info("Before removal:");
                Log.info(actions);
                actions.filter(Action::willRollback);
                Log.info(actions);
                for (i in actions.size-1 downTo  0) {
                    actions.get(i).undo();

                }
            } finally {
                this.lock.unlock();
            }
        }
    }
}