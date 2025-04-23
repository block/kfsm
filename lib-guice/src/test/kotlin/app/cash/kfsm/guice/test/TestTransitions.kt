package app.cash.kfsm.guice.test

import app.cash.kfsm.States
import app.cash.kfsm.Transition
import app.cash.kfsm.guice.annotations.TransitionDefinition
import com.google.inject.Inject

@TransitionDefinition
class StartToMiddle @Inject constructor() : Transition<String, TestValue, TestState>(
    from = States(TestState.START),
    to = TestState.MIDDLE
) {
    override fun effect(value: TestValue): Result<TestValue> =
        Result.success(value.update(TestState.MIDDLE))
}

@TransitionDefinition
class MiddleToEnd @Inject constructor() : Transition<String, TestValue, TestState>(
    from = States(TestState.MIDDLE),
    to = TestState.END
) {
    override fun effect(value: TestValue): Result<TestValue> =
        Result.success(value.update(TestState.END))
} 
