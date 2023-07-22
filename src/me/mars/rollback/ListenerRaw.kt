package me.mars.rollback

import arc.Events
import arc.math.geom.Point2
import arc.struct.IntMap
import arc.struct.OrderedMap
import arc.struct.Seq
import arc.util.Log
import mindustry.Vars
import mindustry.game.EventType.*
import mindustry.gen.Building
import mindustry.world.blocks.ConstructBlock.ConstructBuild

val map: CoordMap = CoordMap()
private val tilePreSets: OrderedMap<Int, TilePreChange> = OrderedMap()

var i: Int = 0
fun addRaw() {

    onEvent<TilePreChangeEvent> {
        tilePreSets.put(it.tile.pos(), TilePreChange(it.tile, it.tile.build))
    }

    onEvent<TileChangeEvent> {
        val prev: TilePreChange = tilePreSets.get(it.tile.pos())!!
        val prevBuild: Building? = prev.build?.takeIf { b -> b.pos() ==  it.tile.pos()}
        val curBuild: Building? = it.tile.build?.takeIf { b -> b.pos() == it.tile.pos() }
        fun addDelete(build: Building) {
            val offset: Int = build.block.sizeOffset
            val size: Int = build.block.size
            val sx: Int = build.tileX() - offset
            val sy: Int = build.tileY() - offset
            var n = 0
            val event = "Delete#${i++}:\\n$build"
            for (x in sx until sx+size) {
                for (y in sy until sy+size) {
                    map[Point2.pack(x, y)].subSeq.add("\u200B".repeat(n++) + event)
                }
            }
        }

        if (prevBuild != null && prevBuild !is ConstructBuild && (curBuild == null || curBuild is ConstructBuild)) {
            addDelete(prevBuild)
        }
        if ((prevBuild == null || prevBuild is ConstructBuild) && curBuild != null && curBuild !is ConstructBuild) {
            map[curBuild.pos()].subSeq.add("Create#${i++}:\\n${curBuild}")
            // Building built
        }
        if (prevBuild != null && prevBuild !is ConstructBuild && curBuild != null && curBuild !is ConstructBuild) {
            addDelete(prevBuild)
            map[curBuild.pos()].subSeq.add("Create#${i++}:\\n${curBuild}")
        }
        tilePreSets.remove(it.tile.pos())
    }

    Events.run(Trigger.update) {
        map.tick()
    }

//    Events.on(TilePreChangeEvent::class.java) {
//        map[it.tile.pos()].subSeq.add("Pre change#${i++}:\\n${it.tile.build}")
//    }
//
//    Events.on(TileChangeEvent::class.java) {
//        map[it.tile.pos()].subSeq.add("Change#${i++}:\\n${it.tile.build}")
//    }

    Events.on(BlockBuildBeginEvent::class.java) {
        if (it.breaking) {
            map[it.tile.build.pos()].subSeq.add("Break begin by ${it.unit}#${i++}:\\n${it.tile.build}")
        } else {
            if (it.tile.build is ConstructBuild) {
                val cb: ConstructBuild = it.tile.build as ConstructBuild
                for (prev in cb.prevBuild) {
                    if (prev.pos() != prev.tile.pos()) Log.err("pos mismatch")
                    map[prev.pos()].subSeq.add("Break begin by ${it.unit}#${i++}:\\n$prev")

                }
            }
        }
    }

    Events.on(BlockBuildEndEvent::class.java) {
        map[it.tile.build.pos()].subSeq.add("${if (it.breaking) "break" else "build"} by ${it.unit}#${i++}:\\n${it.tile.build}")
    }

    Events.on(PickupEvent::class.java) {
        if (it.unit != null) return@on
        map[it.build.tileX(), it.build.tileY()].subSeq.add("Pickup by ${it.carrier}#${i++}:\\n${it.build}")
    }

    Events.on(PayloadDropEvent::class.java) {
        if (it.unit != null) return@on
        map[it.build.pos()].subSeq.add("Drop by ${it.carrier}#${i++}:\\n${it.build}")
    }

    Events.on(BlockDestroyEvent::class.java) {
        map[it.tile.pos()].subSeq.add("Destroy#${i++}:\\n${it.tile.build}")
    }

    Events.on(ConfigEvent::class.java) {
        map[it.tile.pos()].subSeq.add("Config of ${it.value} to ${it.tile.config()}${i++}:\\n${it.tile}")
    }

//    Events.on(CoreChangeEvent)
    // ppcpcpcc
    // ppcpcpcc
}



class TileData(val pos: Int) {
    var subSeq: Seq<String> = Seq()
    val clusters: Seq<Seq<String>> = Seq()
    val tickTimes: Seq<Long> = Seq()

    fun transfer() {
        if (subSeq.any()) {
            clusters.add(subSeq)
            tickTimes.add(Vars.state.updateId)
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
            builder.append("label=\"${tickTimes[i]}\"")
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


