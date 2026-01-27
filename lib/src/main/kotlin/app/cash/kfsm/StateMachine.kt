package app.cash.kfsm

/**
 * Orchestrates state transitions using transitions and a transactional outbox.
 *
 * The StateMachine coordinates the flow:
 * 1. Receive a transition for a value
 * 2. Verify the transition can apply to the current state
 * 3. Call the transition's decide function (pure)
 * 4. Validate the new state's invariants
 * 5. Persist the new state and effects atomically via the repository
 * 6. Return the result
 *
 * Effect execution happens separately via an [EffectProcessor] that reads
 * from the outbox after the transaction commits.
 *
 * Example:
 * ```kotlin
 * val machine = StateMachine(OrderRepository(database))
 *
 * val result = machine.transition(order, ConfirmOrder(paymentId))
 * ```
 *
 * @param ID The type of unique identifier for values
 * @param V The value type
 * @param S The state type
 * @param Ef The effect type
 */
class StateMachine<ID, V : Value<ID, V, S>, S : State<S>, Ef : Effect>(
  private val repository: Repository<ID, V, S, Ef>
) {
  /**
   * Apply a transition to a value.
   *
   * This method:
   * 1. Verifies the transition can apply to the current state
   * 2. Invokes the transition's decide function
   * 3. Validates the decision's state matches the transition's target
   * 4. Validates invariants of the new state
   * 5. Persists the value and outbox messages atomically
   *
   * @param value The current value
   * @param transition The transition to apply
   * @return Success with the updated value, or failure with the rejection/error
   */
  fun transition(value: V, transition: Transition<ID, V, S, Ef>): Result<V> {
    // Check if transition can apply to current state
    if (!transition.canApplyTo(value.state)) {
      // If already at target state, this is a no-op (idempotent)
      if (value.state == transition.to) {
        return Result.success(value)
      }
      return Result.failure(
        InvalidStateForTransition(
          transition = transition,
          currentState = value.state,
          valueId = value.id
        )
      )
    }

    // Get the decision from the transition
    return when (val decision = transition.decide(value)) {
      is Decision.Accept -> {
        // Validate the decision's state matches the transition's declared target
        if (decision.state != transition.to) {
          return Result.failure(
            DecisionStateMismatch(
              expected = transition.to,
              actual = decision.state,
              transition = transition
            )
          )
        }

        val updatedValue = value.update(decision.state)

        // Validate invariants of the new state
        val invariantResult = decision.state.validateInvariants(updatedValue)
        if (invariantResult.isFailure) {
          return Result.failure(invariantResult.exceptionOrNull()!!)
        }

        val outboxMessages = decision.effects.map { effect ->
          OutboxMessage(valueId = value.id, effect = effect)
        }

        repository.saveWithOutbox(updatedValue, outboxMessages)
      }

      is Decision.Reject -> Result.failure(RejectedTransition(decision.reason))
    }
  }
}

/**
 * Repository for persisting values and outbox messages atomically.
 *
 * Implementations must ensure that the value state change and outbox messages
 * are persisted in the same transaction (or with equivalent atomicity guarantees).
 *
 * @param ID The type of unique identifier for values
 * @param V The value type
 * @param S The state type
 * @param Ef The effect type
 */
interface Repository<ID, V : Value<ID, V, S>, S : State<S>, Ef : Effect> {
  /**
   * Persist the value and outbox messages atomically.
   *
   * @param value The value with updated state
   * @param outboxMessages Effects to store in the outbox
   * @return The persisted value
   */
  fun saveWithOutbox(value: V, outboxMessages: List<OutboxMessage<ID, Ef>>): Result<V>
}

/**
 * Exception thrown when a transition is rejected by its decide function.
 */
class RejectedTransition(val reason: String) : Exception(reason)

/**
 * Exception thrown when a transition cannot be applied to the current state.
 */
class InvalidStateForTransition(
  val transition: Transition<*, *, *, *>,
  val currentState: State<*>,
  val valueId: Any?
) : Exception("Cannot apply ${transition::class.simpleName} to state $currentState for value $valueId")

/**
 * Exception thrown when a decision's state doesn't match the transition's declared target.
 */
class DecisionStateMismatch(
  val expected: State<*>,
  val actual: State<*>,
  val transition: Transition<*, *, *, *>
) : Exception("${transition::class.simpleName} declared target $expected but decided $actual")
