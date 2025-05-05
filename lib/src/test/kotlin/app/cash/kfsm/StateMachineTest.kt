package app.cash.kfsm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess

data class ValidValue(override val state: ValidState, override val id: String) : Value<String, ValidValue, ValidState> {
  override fun update(newState: ValidState): ValidValue = copy(state = newState)
}

data class UniCycleValue(override val state: UniCycleState, override val id: String) : Value<String, UniCycleValue, UniCycleState> {
  override fun update(newState: UniCycleState): UniCycleValue = copy(state = newState)
}

data class BiCycleValue(override val state: BiCycleState, override val id: String) : Value<String, BiCycleValue, BiCycleState> {
  override fun update(newState: BiCycleState): BiCycleValue = copy(state = newState)
}

data class TriCycleValue(override val state: TriCycleState, override val id: String) : Value<String, TriCycleValue, TriCycleState> {
  override fun update(newState: TriCycleState): TriCycleValue = copy(state = newState)
}

class StateMachineTest : StringSpec({

  "Returns the states when the machine is valid" {
    StateMachine.verify(Valid1) shouldBeSuccess setOf(
      Valid1,
      Valid2,
      Valid3,
      Valid4,
      Valid5
    )
  }

  "Can verify machines with self-loops" {
    StateMachine.verify(UniCycle1).shouldBeSuccess()
  }

  "Can verify machines with 2 party loops" {
    StateMachine.verify(BiCycle1).shouldBeSuccess()
  }

  "Can verify machines with 3+ party loops" {
    StateMachine.verify(TriCycle1).shouldBeSuccess()
  }

  "Returns failure when not all states are encountered" {
    StateMachine.verify(Valid3) shouldBeFailure InvalidStateMachine("Did not encounter [Valid1, Valid2]")
  }

  "produces mermaid diagram source for non-cyclical state machine" {
    StateMachine.mermaid(Valid1) shouldBeSuccess """
      |stateDiagram-v2
      |    [*] --> Valid1
      |    Valid1 --> Valid2
      |    Valid1 --> Valid3
      |    Valid2 --> Valid3
      |    Valid3 --> Valid4
      |    Valid3 --> Valid5
    """.trimMargin()
  }

  "produces mermaid diagram source for cyclical state machine" {
    StateMachine.mermaid(TriCycle3) shouldBeSuccess """
      |stateDiagram-v2
      |    [*] --> TriCycle3
      |    TriCycle1 --> TriCycle2
      |    TriCycle2 --> TriCycle3
      |    TriCycle3 --> TriCycle1
    """.trimMargin()
  }
})

sealed class ValidState(to: () -> Set<ValidState>) : State<String, ValidValue, ValidState>(to)
data object Valid1 : ValidState({ setOf(Valid2, Valid3) })
data object Valid2 : ValidState({ setOf(Valid3) })
data object Valid3 : ValidState({ setOf(Valid4, Valid5) })
data object Valid4 : ValidState({ setOf() })
data object Valid5 : ValidState({ setOf() })

sealed class UniCycleState(to: () -> Set<UniCycleState>) : State<String, UniCycleValue, UniCycleState>(to)
data object UniCycle1 : UniCycleState({ setOf(UniCycle1) })

sealed class BiCycleState(to: () -> Set<BiCycleState>) : State<String, BiCycleValue, BiCycleState>(to)
data object BiCycle1 : BiCycleState({ setOf(BiCycle2) })
data object BiCycle2 : BiCycleState({ setOf(BiCycle1) })

sealed class TriCycleState(to: () -> Set<TriCycleState>) : State<String, TriCycleValue, TriCycleState>(to)
data object TriCycle1 : TriCycleState({ setOf(TriCycle2) })
data object TriCycle2 : TriCycleState({ setOf(TriCycle3) })
data object TriCycle3 : TriCycleState({ setOf(TriCycle1) })
