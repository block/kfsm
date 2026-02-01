package app.cash.kfsm.v2

/**
 * A Decision represents either:
 * - A successful transition to a new state with optional effects to emit
 * - A rejection of the event (the event is not valid for the current state)
 *
 * The separation of decision-making (pure) from effect execution (impure) enables:
 * - Easy testing of business logic without side effects
 * - Transactional outbox pattern for reliable effect processing
 * - Clear audit trail of state changes and their causes
 *
 * @param S The state type
 * @param E The effect type
 */
sealed class Decision<S : State<S>, E : Effect> {
  /**
   * The event was accepted and resulted in a state transition.
   *
   * @param state The new state after the transition
   * @param emittedEffects Effects to be stored in the outbox and processed after commit.
   *                       Use [Effects.parallel] for concurrent execution or [Effects.ordered]
   *                       for sequential execution with dependencies.
   */
  data class Accept<S : State<S>, E : Effect>(
    val state: S,
    val emittedEffects: List<Effects<E>> = emptyList()
  ) : Decision<S, E>() {
    constructor(state: S, vararg effects: E) : this(state, effects.map { Effects.parallel(it) })

    /**
     * Returns all effects as a flat list (for backward compatibility and simple access).
     */
    val effects: List<E>
      get() = emittedEffects.flatMap { it.allEffects() }
  }

  /**
   * The event was rejected and no state change occurred.
   *
   * @param reason A description of why the event was rejected
   */
  data class Reject<S : State<S>, E : Effect>(val reason: String) : Decision<S, E>()

  companion object {
    /**
     * Accept with effects that can run in parallel (no ordering constraints).
     */
    fun <S : State<S>, E : Effect> accept(state: S, vararg effects: E): Decision<S, E> =
      Accept(state, effects.map { Effects.parallel(it) })

    /**
     * Accept with effects that can run in parallel (no ordering constraints).
     */
    fun <S : State<S>, E : Effect> accept(state: S, effects: List<E> = emptyList()): Decision<S, E> =
      Accept(state, effects.map { Effects.parallel(it) })

    /**
     * Accept with effects that may include ordering constraints.
     *
     * Example:
     * ```kotlin
     * Decision.accept(
     *   state = NewState,
     *   Effects.ordered(ChargeLedger(amount), SendReceipt(email)),  // SendReceipt waits for ChargeLedger
     *   Effects.parallel(SendAnalytics(event))  // Runs independently
     * )
     * ```
     */
    fun <S : State<S>, E : Effect> accept(state: S, vararg effects: Effects<E>): Decision<S, E> =
      Accept(state, effects.toList())

    fun <S : State<S>, E : Effect> reject(reason: String): Decision<S, E> = Reject(reason)
  }
}

/**
 * Specifies how effects should be processed - either in parallel or in a specific order.
 *
 * Use [parallel] for effects that have no dependencies and can run concurrently.
 * Use [ordered] for effects that must run sequentially, where each effect waits
 * for the previous one to complete before starting.
 *
 * Example:
 * ```kotlin
 * // These can run in parallel
 * Effects.parallel(SendEmail(...))
 * Effects.parallel(SendPushNotification(...))
 *
 * // These run in order: first charge, then send receipt
 * Effects.ordered(ChargeLedger(amount), SendReceipt(email))
 * ```
 */
sealed class Effects<E : Effect> {
  /**
   * Returns all effects in this spec as a flat list.
   */
  abstract fun allEffects(): List<E>

  /**
   * A single effect with no dependencies (can run in parallel with others).
   */
  data class Parallel<E : Effect>(val effect: E) : Effects<E>() {
    override fun allEffects(): List<E> = listOf(effect)
  }

  /**
   * A chain of effects that must be processed in order.
   * Each effect waits for the previous one to complete before starting.
   */
  data class Ordered<E : Effect>(val effects: List<E>) : Effects<E>() {
    init {
      require(effects.isNotEmpty()) { "Ordered effects must have at least one effect" }
    }

    override fun allEffects(): List<E> = effects
  }

  companion object {
    /**
     * Create a parallel effect specification (no dependencies).
     */
    fun <E : Effect> parallel(effect: E): Effects<E> = Parallel(effect)

    /**
     * Create an ordered effect chain where each effect depends on the previous.
     *
     * @param first The first effect to execute
     * @param then Subsequent effects, each waiting for the previous to complete
     */
    fun <E : Effect> ordered(first: E, vararg then: E): Effects<E> =
      Ordered(listOf(first) + then.toList())

    /**
     * Create an ordered effect chain from a list.
     */
    fun <E : Effect> ordered(effects: List<E>): Effects<E> = Ordered(effects)
  }
}
