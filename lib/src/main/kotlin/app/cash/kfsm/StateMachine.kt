package app.cash.kfsm

import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.superclasses

object StateMachine {

  /** Check your state machine covers all subtypes */
  fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> verify(head: S): Result<Set<State<ID, V, S>>> = verify(head, baseType(head))

  /** Render a state machine in Mermaid markdown */
  fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> mermaid(head: S): Result<String> = walkTree(head).map { states ->
    listOf("stateDiagram-v2", "[*] --> ${head::class.simpleName}").plus(
      states.toSet().flatMap { from ->
        from.subsequentStates.map { to -> "${from::class.simpleName} --> ${to::class.simpleName}" }
      }.toList().sorted()
    ).joinToString("\n    ")
  }

  private fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> verify(head: S, type: KClass<out S>): Result<Set<State<ID, V, S>>> =
    walkTree(head).mapCatching { seen ->
      val notSeen = type.sealedSubclasses.minus(seen.map { it::class }.toSet()).toList().sortedBy { it.simpleName }
      when {
        notSeen.isEmpty() -> seen
        else -> throw InvalidStateMachine(
          "Did not encounter [${notSeen.map { it.simpleName }.joinToString(", ")}]"
        )
      }
    }

  private fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> walkTree(
    current: S,
    statesSeen: Set<S> = emptySet()
  ): Result<Set<S>> = runCatching {
    when {
      statesSeen.contains(current) -> statesSeen
      current.subsequentStates.isEmpty() -> statesSeen.plus(current)
      else -> current.subsequentStates.flatMap {
        walkTree(it, statesSeen.plus(current)).getOrThrow()
      }.toSet()
    }
  }

  @Suppress("UNCHECKED_CAST") 
  private fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> baseType(s: S): KClass<out S> = s::class.allSuperclasses
    .find { it.superclasses.contains(State::class) }!! as KClass<out S>
}

data class InvalidStateMachine(override val message: String) : Exception(message)
