package app.cash.kfsm.guice

import app.cash.kfsm.State
import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import app.cash.kfsm.Value
import com.google.inject.Inject
import com.google.inject.Singleton
import kotlin.reflect.KClass

/**
 * A Guice-managed wrapper around KFSM's [Transitioner] that provides convenient access to discovered transitions.
 *
 * This class is responsible for:
 * 1. Managing the set of available transitions
 * 2. Providing type-safe access to specific transitions
 * 3. Executing transitions using the underlying [Transitioner]
 *
 * @param V The type of value being managed by the state machine
 * @param S The type of state, must extend [State]
 * @property transitions The set of all available transitions, injected by Guice
 * @property transitioner The underlying KFSM transitioner that executes the transitions
 *
 * Example usage:
 * ```kotlin
 * @Inject
 * lateinit var stateMachine: StateMachine<MyValue, MyState>
 *
 * fun processTransition(value: MyValue) {
 *     // Get available transitions for current state
 *     val transitions = stateMachine.getAvailableTransitions(value.state)
 *
 *     // Get a specific transition by its type
 *     val transition = stateMachine.getTransition<MySpecificTransition>()
 *
 *     // Execute a transition
 *     val result = stateMachine.execute(value, transition)
 * }
 * ```
 */
@Singleton
class StateMachine<ID, V : Value<ID, V, S>, S : State<S>> @Inject constructor(
    private val transitions: Set<Transition<ID, V, S>>,
    private val transitioner: Transitioner<ID, Transition<ID, V, S>, V, S>
) {
    // Cache transitions by their class for faster lookup
    private val transitionsByType: Map<Class<out Transition<ID, V, S>>, Transition<ID, V, S>> =
        transitions.associateBy { it::class.java }

    /**
     * Returns all transitions that are valid for the given state.
     *
     * A transition is considered valid if its 'from' states include the current state.
     *
     * @param state The current state to check transitions for
     * @return Set of valid transitions for the given state
     */
    fun getAvailableTransitions(state: S): Set<Transition<ID, V, S>> =
      transitions.filter { transition ->
            transition.from.set.contains(state)
        }.toSet()

    /**
     * Gets a specific transition by its type.
     *
     * @param T The specific transition type to retrieve
     * @return The requested transition instance
     * @throws IllegalArgumentException if no transition of the specified type is found
     */
    inline fun <reified T : Transition<ID, V, S>> getTransition(): T =
        getTransition(T::class)

    /**
     * Gets a specific transition by its [KClass].
     *
     * @param klass The [KClass] of the transition to retrieve
     * @return The requested transition instance
     * @throws IllegalArgumentException if no transition of the specified type is found
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Transition<ID, V, S>> getTransition(klass: KClass<T>): T =
      transitionsByType[klass.java] as? T
        ?: error("No transition found for type ${klass.simpleName}")

    /**
     * Executes the given transition on the provided value.
     *
     * @param value The value to transition
     * @param transition The transition to execute
     * @return A [Result] containing either the transitioned value or an error
     */
    fun execute(value: V, transition: Transition<ID, V, S>): Result<V> =
        transitioner.transition(value, transition)
}
