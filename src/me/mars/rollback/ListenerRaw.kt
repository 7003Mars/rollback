package me.mars.rollback

import arc.Events
import arc.math.geom.Point2
import arc.struct.IntMap
import arc.struct.Seq
import arc.util.Log
import mindustry.game.EventType.*

var hadActivity = false
val map: CoordMap = CoordMap()
var i: Int = 0
fun addRaw() {

    Events.run(Trigger.update) {
        map.tick()
    }

    Events.on(TilePreChangeEvent::class.java) {
        hadActivity = true
        map[it.tile.pos()].subSeq.add("Pre change#${i++}:\\n${it.tile.build}")
    }

    Events.on(TileChangeEvent::class.java) {
        hadActivity = true
        map[it.tile.pos()].subSeq.add("Change#${i++}:\\n${it.tile.build}")
    }

    Events.on(BlockBuildBeginEvent::class.java) {
        hadActivity = true
        map[it.tile.build.pos()].subSeq.add("${if (it.breaking) "break" else "build"} begin by ${it.unit}#${i++}:\\n${it.tile.build}")
    }

    Events.on(BlockBuildEndEvent::class.java) {
        hadActivity = true
        map[it.tile.build.pos()].subSeq.add("${if (it.breaking) "break" else "build"} by ${it.unit}#${i++}:\\n${it.tile.build}")
    }

    Events.on(PickupEvent::class.java) {
        if (it.unit != null) return@on
        hadActivity = true
        map[it.build.tileX(), it.build.tileY()].subSeq.add("Pickup by ${it.carrier}#${i++}:\\n${it.build}")
    }

    Events.on(PayloadDropEvent::class.java) {
        if (it.unit != null) return@on
        hadActivity = true
        map[it.build.pos()].subSeq.add("Drop by ${it.carrier}#${i++}:\\n${it.build}")
    }

    Events.on(BlockDestroyEvent::class.java) {
        hadActivity = true
        map[it.tile.pos()].subSeq.add("Destroy#${i++}:\\n${it.tile.build}")
    }
    // ppcpcpcc
    // ppcpcpcc
}



class TileData(val pos: Int) {
    var subSeq: Seq<String> = Seq()
    val clusters: Seq<Seq<String>> = Seq()

    fun transfer() {
        if (subSeq.any()) {
            clusters.add(subSeq)
            subSeq = Seq()
        }
    }

    fun buildGraph(): String {
        val builder: StringBuilder = StringBuilder()
        builder.append("subgraph cluster$pos{\n")
        builder.append("label=\"${Point2.x(pos)},${Point2.y(pos)}\"")
        var prevCluster: Seq<String>? = null
        var i = 0
        for (cluster in clusters) {
            builder.append("\nsubgraph cluster_$i{")
            cluster.each { builder.append("\"$it\"->") }
            builder.delete(builder.length-2, builder.length)
            builder.append("}")
            if (prevCluster != null) builder.append("\n\"${prevCluster.last()}\"->\"${cluster.first()}\"")
            prevCluster = cluster
            i++
        }
        builder.append("}\n")
        return builder.toString()
    }
}

class CoordMap {
    val mapping: IntMap<TileData> = IntMap()

    operator fun get(x: Int, y: Int): TileData {
        return get(Point2.pack(x, y))
    }

    operator fun get(pos: Int): TileData {
        val ret = mapping.get(pos)
        if (ret != null) return ret
        mapping.put(pos, TileData(pos))
        return mapping.get(pos)
    }

    fun tick() {
        this.mapping.values().forEach(TileData::transfer)
    }

    fun clear() {
        this.mapping.clear()
    }

    fun buildGraph(name: String = "Action_name"): String {
        Log.info("Building with size: ${this.mapping.size}")
        val sb: StringBuilder = StringBuilder()
        sb.append("subgraph cluster_$name {")
        sb.append("\nlabel=\"$name\"\n")
        mapping.values().toArray().each { sb.append(it.buildGraph()) }
        sb.append("}")
        return sb.toString()
    }
}


