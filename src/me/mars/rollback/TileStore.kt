package me.mars.rollback

import arc.func.Boolf
import arc.math.geom.Point2
import arc.struct.ObjectSet
import arc.struct.Seq
import arc.util.Log
import me.mars.rollback.actions.Action
import java.lang.IllegalArgumentException

class TileStore(var width: Int, var height: Int) {
    private val tiles: Seq<TileInfo> = Seq();
    init {
        this.resized();
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

    fun set(x: Int, y: Int, action: Action) {
        if (x < 0 || x >= this.width || y < 0 || y >= this.width) {
            throw IllegalArgumentException("Coordinates $x, $y are out of range (${this.width}, ${this.height})");
        }
        this.tiles.get(y * this.width + x).add(action);
    }

    fun setAction(action: Action, blockSize: Int) {
        val offset: Int = (blockSize-1)/2;
        val sx: Int = Point2.x(action.pos).toInt() - offset;
        val sy: Int = Point2.y(action.pos).toInt() - offset;

        for (x in sx until sx+blockSize) {
            for (y in sy until sy+blockSize) {
                this.set(x, y, action);
            }
        }
    }

    fun clear(x: Int, y: Int, blockSize: Int) {
        val offset: Int = (blockSize-1)/2;
        val sx: Int = x-offset;
        val sy: Int = y-offset;
        for (i in sx until sx+blockSize) {
            for (j in sy until sy+blockSize) {
                this.get(i, j).clear();
            }
        }
    }

    fun collectLatest(check: Boolf<Action>): Seq<Action> {
        val added: ObjectSet<Action> = ObjectSet();
        val selected: Seq<Action> = Seq();
        for (tileInfo: TileInfo in this.tiles) {
            for (pair: ActionPair<*> in tileInfo.all) {
                pair.getLatest().each { latest ->
                    if (check.get(latest) && !added.contains(latest)) {
                        selected.add(latest)
                        added.add(latest)
                    }
                };
            }
        }
        added.clear();
        return selected;
    }

    fun rollback(uuid: String, time: Float) {
        val actions: Seq<Action> = this.collectLatest { it.time > time && it.uuid == uuid }.sort { a -> -a.id.toFloat()}
        Log.info(actions);
        actions.each(Action::undo);
    }
}