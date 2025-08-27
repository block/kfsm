package app.cash.kfsm

/**
 * Represents an effect that can be applied during a state transition.
 *
 * An effect is a function that transforms a value during a state transition, with access to both
 * the source and destination states through a [TransitionContext]. This allows effects to make
 * decisions based on the full transition context rather than just the value being transformed.
 *
 * Example usage:
 * ```kotlin
 * // Simple effect that just transforms the value
 * val simpleEffect = Effect { (value) ->
 *   Result.success(value.copy(count = value.count + 1))
 * }
 *
 * // Effect that uses state information
 * val logOnlyEffect = Effect { (value, from, to) ->
 *    logger.info { "${value.id} was $from but becoming $to" }
 *    value
 * }
 * ```
 *
 * @param ID The type of identifier used in the state machine
 * @param V The type of value being transformed
 * @param S The type of state in the state machine
 */
fun interface Effect<ID, V : Value<ID, V, S>, S : State<ID, V, S>> {
  /**
   * Applies this effect to transform a value during a state transition.
   *
   * @param context The transition context containing the value being transformed and the states involved
   * @return A [Result] containing either the transformed value or an error if the transformation failed
   */
  fun apply(context: TransitionContext<ID, V, S>): Result<V>
}
