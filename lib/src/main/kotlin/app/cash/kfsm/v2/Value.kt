package app.cash.kfsm.v2

/**
 * Represents a value that can transition between different states in a finite state machine.
 *
 * Values are the domain objects that transition between states. They carry:
 * - A unique identifier
 * - The current state
 * - Domain data relevant to the entity
 *
 * Values should be immutable. State transitions produce new value instances
 * with the updated state.
 *
 * Example:
 * ```kotlin
 * data class Order(
 *   override val id: String,
 *   override val state: OrderState,
 *   val customerId: String,
 *   val items: List<OrderItem>,
 *   val total: Money
 * ) : Value<String, Order, OrderState> {
 *   override fun update(newState: OrderState): Order = copy(state = newState)
 * }
 * ```
 *
 * @param ID The type of unique identifier for this value
 * @param V The concrete value type (for self-referential typing)
 * @param S The state type
 */
interface Value<ID, V : Value<ID, V, S>, S : State<S>> {
  /**
   * Unique identifier for this value.
   */
  val id: ID

  /**
   * The current state of this value.
   */
  val state: S

  /**
   * Creates a new instance of this value with the given state.
   *
   * @param newState The new state
   * @return A new value instance with the updated state
   */
  fun update(newState: S): V
}
