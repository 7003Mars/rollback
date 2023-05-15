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
        val tmpSeq: Seq<Action> = Seq();
    }

    var prevBuild: BuildAction? = null;
    var prevDelete: DeleteAction? = null;
    var prevConfig: ConfigAction? = null;
    val actions: Seq<Action> = Seq();

    fun add(action: Action) {
        if (action.uuid != this.actions.firstOpt()?.uuid) {
            this.actions.each{
                when (it) {
                    is BuildAction -> this.prevBuild = it
                    is DeleteAction -> this.prevDelete = it
                    is ConfigAction -> this.prevConfig = it
                }
            }
            this.actions.clear();
        }
        this.actions.add(action);
    }

    fun clear() {
       this.actions.clear();
        this.prevBuild = null;
        this.prevDelete = null;
        this.prevConfig = null;
    }

    fun all(): Seq<Action> {
        tmpSeq.clear();
        this.prevBuild?.let { tmpSeq.add(it) }
        this.prevDelete?.let { tmpSeq.add(it) }
        this.prevConfig?.let { tmpSeq.add(it) }
        tmpSeq.addAll(this.actions);
        return tmpSeq;
    }

}

//class ActionPair<T: Action>() {
//    private var actions: Seq<T> = Seq();
////    private var action1: T? = null;
//    private var prev: T? = null;
//
//    fun add(action: T) {
//        if (action.uuid == this.actions.firstOpt()?.uuid) {
//            this.actions.add(action);
//        } else {
//            this.prev = if (this.actions.any()) actions.peek() else null;
//            this.actions.clear().add(action);
//        }
//        Log.info("[@], @, @",action.javaClass.simpleName, this.prev, this.actions);
//    }
//
//    fun getLatest(): Seq<T> {
//        return this.actions;
//    }
//
//    fun getPrev(): T? {
//        return this.prev;
//    }
//
//    /**
//     * Beware, the array is reused
//     */
//    @Suppress("UNCHECKED_CAST")
//    fun asArray(): Seq<T> {
//        val seq: Seq<T> = (tmpSeq.clear() as Seq<T>).addAll(this.actions);
//        if (this.prev != null) seq.add(this.prev)
//        return seq;
//    }
//
//    fun clear() {
//        this.actions.clear();
//        this.prev = null;
//    }
//}
