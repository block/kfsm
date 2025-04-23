package app.cash.kfsm

/**
 * Represents a value that can transition between different states in a finite state machine.
 *
 * This interface defines the core behavior for values that can be managed by a state machine.
 * It provides methods to track the current state and update to a new state.
 *
 * @param V The concrete type of the value implementing this interface
 * @param S The type of state that this value can transition between
 *
 * @see State
 * @see Transition
 * @see Transitioner
 *
 * Example usage:
 * ```kotlin
 * enum class Color { RED, YELLOW, GREEN }
 *
 * data class Light(override val state: Color, override val id: String) : Value<String, Light, Color> {
 *     override fun update(newState: Color): Light = copy(state = newState)
 * }
 * ```
 */
interface Value<ID, V: Value<ID, V, S>, S : State<S>> {
  /**
   * The current state of this value in the state machine.
   *
   * This property represents the value's current position in the state machine's state graph.
   * It is used by the state machine to determine valid transitions and track the value's progress.
   */
  val state: S

  /**
   * Updates this value to a new state.
   *
   * This method creates a new instance of the value with the specified state.
   * The implementation should ensure that the new state is valid according to the state machine's rules.
   *
   * @param newState The state to transition to
   * @return A new instance of the value with the updated state
   */
  fun update(newState: S): V

  /**
   * Returns a unique identifier for this value.
   *
   * This method is used to identify the value within the state machine.
   * By default, it uses the value's string representation, but implementations
   * should override this to provide a more succinct & specific identifier.
   *
   * @return A string that uniquely identifies this value
   */
  val id: ID
}
