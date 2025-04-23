package app.cash.kfsm.guice.test

import app.cash.kfsm.guice.StateMachine
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class KfsmGuiceIntegrationTest : StringSpec({
    val injector = Guice.createInjector(TestModule())
    val stateMachine = injector.getInstance(
        Key.get(object : TypeLiteral<StateMachine<String, TestValue, TestState>>() {})
    )
    val startValue = TestValue(id = "test_value_01", state = TestState.START)

    "START state should have exactly one available transition of type StartToMiddle" {
        val transitions = stateMachine.getAvailableTransitions(startValue.state)
        transitions shouldHaveSize 1
        transitions.first().shouldBeInstanceOf<StartToMiddle>()
    }

    "executing StartToMiddle transition should result in MIDDLE state" {
        val middleValue = stateMachine.execute(startValue, stateMachine.getTransition<StartToMiddle>()).getOrThrow()
        middleValue.state shouldBe TestState.MIDDLE
    }

    "MIDDLE state should have exactly one available transition of type MiddleToEnd" {
        val middleValue = stateMachine.execute(startValue, stateMachine.getTransition<StartToMiddle>()).getOrThrow()
        val transitions = stateMachine.getAvailableTransitions(middleValue.state)
        transitions shouldHaveSize 1
        transitions.first().shouldBeInstanceOf<MiddleToEnd>()
    }

    "executing MiddleToEnd transition should result in END state" {
        val middleValue = stateMachine.execute(startValue, stateMachine.getTransition<StartToMiddle>()).getOrThrow()
        val endValue = stateMachine.execute(middleValue, stateMachine.getTransition<MiddleToEnd>()).getOrThrow()
        endValue.state shouldBe TestState.END
    }

    "END state should have no available transitions" {
        val middleValue = stateMachine.execute(startValue, stateMachine.getTransition<StartToMiddle>()).getOrThrow()
        val endValue = stateMachine.execute(middleValue, stateMachine.getTransition<MiddleToEnd>()).getOrThrow()
        val transitions = stateMachine.getAvailableTransitions(endValue.state)
        transitions shouldHaveSize 0
    }
}) 
