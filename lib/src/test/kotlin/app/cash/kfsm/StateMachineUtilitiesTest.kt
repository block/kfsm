package app.cash.kfsm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe

class StateMachineUtilitiesTest : StringSpec({
    "mermaidStateDiagramMarkdown generates correct diagram" {
        // Given a machine with multiple transitions
        val machine = fsm<String, Letter, Char> {
            A.becomes {
                B.via { it.copy(id = "banana") }
            }
            B.becomes {
                C.via { it.copy(id = "cinnamon") }
                D.via { it.copy(id = "durian") }
                B.via { it.copy(id = "berry") }
            }
            D.becomes {
                E.via { it.copy(id = "eggplant") }
            }
        }.getOrThrow()

        // When generating a diagram starting from A
        val diagram = machine.mermaidStateDiagramMarkdown(A)

        // Then the diagram contains all expected elements
        diagram shouldBe """
            |stateDiagram-v2
            |    [*] --> A
            |    A --> B
            |    B --> B
            |    B --> C
            |    B --> D
            |    D --> E
            """.trimMargin()
    }
})
