package com.squareup.cash.kfsm.exemplar

import com.squareup.cash.kfsm.State

/**
 * Base class for all the states a Hamster can embody.
 */
sealed class HamsterState(to: () -> Set<HamsterState>) : State(to)

/** Hamster is awake... and hungry! */
object Awake : HamsterState({ setOf(Eating) })

/** Hamster is eating ... what will they do next? */
object Eating : HamsterState({ setOf(RunningOnWheel, Asleep, OverIt) })

/** Wheeeeeee! */
object RunningOnWheel : HamsterState({ setOf(Asleep, OverIt) })

/** Sits in the corner, chilling */
object OverIt : HamsterState({ setOf(Asleep) })

/** Zzzzzzzzz */
object Asleep : HamsterState({ setOf(Awake) })
