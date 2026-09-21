package de.d88.platform.model

/** Zeitquelle – abstrahiert, damit Kernlogik deterministisch getestet werden kann. */
fun interface Clock {
    fun now(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}
