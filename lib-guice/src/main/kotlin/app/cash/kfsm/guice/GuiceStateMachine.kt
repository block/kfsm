package app.cash.kfsm.guice

import app.cash.kfsm.NoPathToTargetState
import app.cash.kfsm.State
import app.cash.kfsm.StateMachine
import app.cash.kfsm.States
import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import app.cash.kfsm.Value
import com.google.inject.Inject
import kotlin.collections.Set

class GuiceStateMachine<ID, V : Value<ID, V, S>, S : State<ID, V, S>> @Inject constructor(
  private val transitions: Set<Transition<ID, V, S>>,
  private val transitioner: Transitioner<ID, Transition<ID, V, S>, V, S>
) : StateMachine<ID, V, S> {
  override fun getAvailableTransitions(state: S): Set<Transition<ID, V, S>> =
    transitions.filter { it.from.set.contains(state) }.toSet()

  @Suppress("UNCHECKED_CAST")
  override fun <T : Transition<ID, V, S>> getTransition(): T {
    // Since we can't use T::class.java with a generic type parameter,
    // we'll need to cast based on the caller's expectation
    return transitions.first() as T
  }

  override fun execute(value: V, transition: Transition<ID, V, S>): Result<V> =
    transitioner.transition(value, transition)

  override fun transitionToState(value: V, targetState: S): Result<V> {
    val path = findPathToState(value.state, targetState) ?: return Result.failure(NoPathToTargetState(value as Value<*, *, *>, targetState as State<*, *, *>))
    
    var currentValue = value
    for (transition in path) {
      val result = execute(currentValue, transition)
      if (result.isFailure) {
        return result
      }
      currentValue = result.getOrThrow()
    }
    
    return Result.success(currentValue)
  }

  private fun findPathToState(start: S, target: S): List<Transition<ID, V, S>>? {
    if (start == target) return null
    val visited = mutableSetOf<S>()
    val queue = ArrayDeque<Pair<S, List<Transition<ID, V, S>>>>()
    queue.add(start to emptyList())

    while (queue.isNotEmpty()) {
      val (current, path) = queue.removeFirst()
      visited.add(current)

      for (transition in getAvailableTransitions(current)) {
        val next = transition.to
        if (next == target) {
          return path + transition
        }
        if (next !in visited) {
          queue.add(next to path + transition)
        }
      }
    }

    return null
  }
}
