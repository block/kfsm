package app.cash.kfsm.guice.test

import app.cash.kfsm.State

sealed class TestState(transitionsFn: () -> Set<TestState>) : State<String, TestValue, TestState>(transitionsFn) {
    data object START : TestState({ setOf(MIDDLE) })
    data object MIDDLE : TestState({ setOf(END) })
    data object END : TestState({ emptySet() })
} 
