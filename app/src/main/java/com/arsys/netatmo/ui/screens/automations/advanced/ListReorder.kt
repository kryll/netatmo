package com.arsys.netatmo.ui.screens.automations.advanced

/**
 * Returns a copy of this list with the element at [index] moved [delta] positions
 * (negative = up, positive = down). Out-of-range moves return the list unchanged,
 * so the first item cannot go up nor the last one down.
 */
internal fun <T> List<T>.movedWithin(index: Int, delta: Int): List<T> {
    val target = index + delta
    if (index !in indices || target !in indices) return this
    return toMutableList().also { list ->
        val item = list.removeAt(index)
        list.add(target, item)
    }
}
