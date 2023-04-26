package me.mars.rollback

import arc.struct.Seq
import arc.util.Log
import me.mars.rollback.TileInfo.Companion.tmpSeq
import me.mars.rollback.actions.Action
import me.mars.rollback.actions.BuildAction
import me.mars.rollback.actions.ConfigAction
import me.mars.rollback.actions.DeleteAction

class TileInfo(val pos: Int) {
    companion object {
        val tmpSeq: Seq<out Action> = Seq();
    }

    val buildActions: ActionPair<BuildAction> = ActionPair();
    val deleteActions: ActionPair<DeleteAction> = ActionPair();
    val configActions: ActionPair<ConfigAction> = ActionPair();
    val all: Array<ActionPair<*>> = arrayOf(this.buildActions, this.deleteActions, this.configActions);

    fun add(action: Action) {
        when (action) {
            is BuildAction -> this.buildActions.add(action)
            is DeleteAction -> this.deleteActions.add(action)
            is ConfigAction -> this.configActions.add(action)
        }
    }

    fun clear() {
       this.all.forEach { it.clear() };
    }

}

class ActionPair<T: Action>() {
    private var actions: Seq<T> = Seq();
//    private var action1: T? = null;
    private var prev: T? = null;

    fun add(action: T) {
        if (action.uuid == this.actions.firstOpt()?.uuid) {
            this.actions.add(action);
        } else {
            this.prev = if (this.actions.any()) actions.peek() else null;
            this.actions.clear().add(action);
        }
        Log.info("[@], @, @",action.javaClass.simpleName, this.prev, this.actions);
    }

    fun getLatest(): Seq<T> {
        return this.actions;
    }

    fun getPrev(): T? {
        return this.prev;
    }

    @Suppress("UNCHECKED_CAST")
    fun asArray(): Seq<T> {
        val seq: Seq<T> = (tmpSeq.clear() as Seq<T>).addAll(this.actions);
        if (this.prev != null) seq.add(this.prev)
        return seq;
    }

    fun clear() {
        this.actions.clear();
        this.prev = null;
    }
}
