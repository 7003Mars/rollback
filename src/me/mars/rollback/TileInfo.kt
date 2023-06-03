package me.mars.rollback

import arc.func.Boolf
import arc.struct.Seq
import me.mars.rollback.RollbackPlugin.Companion.sel
import me.mars.rollback.actions.Action
import me.mars.rollback.actions.BuildAction
import me.mars.rollback.actions.ConfigAction
import me.mars.rollback.actions.DeleteAction
import java.util.Comparator

class TileInfo {
    companion object {
        val tmpSeq: Seq<Action> = Seq()
    }

    var prevBuild: BuildAction? = null
    var prevDelete: DeleteAction? = null
    var prevConfig: ConfigAction? = null
    val actions: Seq<Action> = Seq()

    fun add(action: Action) {
        if (this.actions.any() && action.uuid != this.actions.first().uuid) {
            this.actions.each{
                when (it) {
                    is BuildAction -> this.prevBuild = it
                    is DeleteAction -> this.prevDelete = it
                    is ConfigAction -> this.prevConfig = it
                }
            }
            this.actions.clear()
        }
        this.actions.add(action)
    }

    /**
     * Clears all actions with an id >= [id]
     */
    fun clear(id: Int) {
       this.actions.filter { it.id < id }
        this.prevBuild = this.prevBuild.takeIf { it != null && it.id < id }
        this.prevDelete = this.prevDelete.takeIf { it != null && it.id < id }
        this.prevConfig = this.prevConfig.takeIf { it != null && it.id < id }
    }

    fun all(): Seq<Action> {
        tmpSeq.clear()
        this.prevBuild?.let { tmpSeq.add(it) }
        this.prevDelete?.let { tmpSeq.add(it) }
        this.prevConfig?.let { tmpSeq.add(it) }
        tmpSeq.addAll(this.actions)
        return tmpSeq
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Action> select(index: Int, cls: Class<T>, check: Boolf<in Action>): T? {
        val seq: Seq<Action> = this.all()
        seq.filter { check.get(it) && it.javaClass == cls }
        if (seq.isEmpty) return null
        if (seq.size == 1) return seq.first() as T
        val i: Int = if (index < 0) seq.size - index+1 else index+1
        return sel.select(seq.toArray(cls), Comparator.comparingInt(Action::id), i, seq.size)
    }

}
