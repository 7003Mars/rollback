package me.mars.rollback

import arc.struct.Seq

fun <T> Seq<T>.lastOpt(): T? {
    if (this.size == 0) return null;
    return this.peek();
}

fun <T> Seq<*>.only(cls: Class<T>): Seq<T> {
    return this.filter {it != null && it::class.java == cls}.`as`();
}