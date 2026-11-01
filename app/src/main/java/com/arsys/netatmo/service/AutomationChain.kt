package com.arsys.netatmo.service

/**
 * Cycle/depth guard for the "run another automation" action. The chain holds the ids of the
 * automations already executing in this run (its first entry is the one whose trigger fired),
 * so following a reference can never loop: a cycle would re-enter an id already present.
 */
object AutomationChain {

    /** Longest automation chain a single run may follow before refusing to go deeper. */
    const val MAX_DEPTH = 10

    /**
     * The chain to execute [targetId] with, or null when it must not run: either it is already
     * in this chain (a cycle — running it again would loop) or the chain is at [MAX_DEPTH].
     */
    fun follow(chain: Set<Long>, targetId: Long): Set<Long>? = when {
        targetId in chain -> null
        chain.size >= MAX_DEPTH -> null
        else -> chain + targetId
    }
}
