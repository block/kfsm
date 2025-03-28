package app.cash.kfsm.guice.test

import app.cash.kfsm.guice.StateMachine
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KfsmGuiceIntegrationTest {
    private val injector = Guice.createInjector(TestModule())
    private val stateMachine = injector.getInstance(
        Key.get(object : TypeLiteral<StateMachine<TestValue, TestState>>() {})
    )

    @Test
    fun `test state machine integration`() {
        // Start with a value in START state
        val startValue = TestValue(TestState.START)
        
        // Get available transitions
        val availableTransitions = stateMachine.getAvailableTransitions(startValue.state)
        assertEquals(1, availableTransitions.size)
        assertTrue(availableTransitions.any { it is StartToMiddle })
        
        // Execute the transition
        val middleValue = stateMachine.execute(startValue, stateMachine.getTransition<StartToMiddle>()).getOrThrow()
        assertEquals(TestState.MIDDLE, middleValue.state)
        
        // Get next available transitions
        val nextTransitions = stateMachine.getAvailableTransitions(middleValue.state)
        assertEquals(1, nextTransitions.size)
        assertTrue(nextTransitions.any { it is MiddleToEnd })
        
        // Execute final transition
        val endValue = stateMachine.execute(middleValue, stateMachine.getTransition<MiddleToEnd>()).getOrThrow()
        assertEquals(TestState.END, endValue.state)
        
        // Verify no more transitions available
        val finalTransitions = stateMachine.getAvailableTransitions(endValue.state)
        assertTrue(finalTransitions.isEmpty())
    }
} 