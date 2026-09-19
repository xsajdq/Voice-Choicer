package com.voicechoicer.app.media

/** Amortized-O(1) append buffer for decoded PCM samples, to avoid boxing millions of Shorts in a List. */
internal class GrowableShortBuffer(initialCapacity: Int = 1 shl 16) {
    private var array = ShortArray(initialCapacity)
    private var size = 0

    fun append(source: ShortArray, length: Int = source.size) {
        ensureCapacity(size + length)
        System.arraycopy(source, 0, array, size, length)
        size += length
    }

    fun append(value: Short) {
        ensureCapacity(size + 1)
        array[size] = value
        size++
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= array.size) return
        var newSize = array.size
        while (newSize < needed) newSize *= 2
        array = array.copyOf(newSize)
    }

    fun toShortArray(): ShortArray = array.copyOf(size)
}
