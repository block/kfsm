package app.cash.kfsm.guice.test

import app.cash.kfsm.State

sealed class TestState(transitionsFn: () -> Set<TestState>) : State<TestState>(transitionsFn) {
    object START : TestState({ setOf(MIDDLE) })
    object MIDDLE : TestState({ setOf(END) })
    object END : TestState({ emptySet() })
} 