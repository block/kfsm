package app.cash.kfsm.guice.test

import app.cash.kfsm.Value

data class TestValue(
    override val state: TestState,
    override val id: String
) : Value<String, TestValue, TestState> {
    override fun update(newState: TestState): TestValue = copy(state = newState)
} 