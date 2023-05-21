package me.mars.rollback

import arc.struct.Seq
import me.mars.rollback.actions.Action
import me.mars.rollback.actions.BuildAction
import me.mars.rollback.actions.ConfigAction
import me.mars.rollback.actions.DeleteAction

class TileInfo {
    companion object {
        val tmpSeq: Seq<Action> = Seq()
    }

    var prevBuild: BuildAction? = null
    var prevDelete: DeleteAction? = null
    var prevConfig: ConfigAction? = null
    val actions: Seq<Action> = Seq()

    fun add(action: Action) {
        if (action.uuid != this.actions.firstOpt()?.uuid) {
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

    fun clear() {
       this.actions.clear()
        this.prevBuild = null
        this.prevDelete = null
        this.prevConfig = null
    }

    fun all(): Seq<Action> {
        tmpSeq.clear()
        this.prevBuild?.let { tmpSeq.add(it) }
        this.prevDelete?.let { tmpSeq.add(it) }
        this.prevConfig?.let { tmpSeq.add(it) }
        tmpSeq.addAll(this.actions)
        return tmpSeq
    }

}
