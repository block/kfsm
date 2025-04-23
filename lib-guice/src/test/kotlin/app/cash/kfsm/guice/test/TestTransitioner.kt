package app.cash.kfsm.guice.test

import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import app.cash.kfsm.guice.annotations.TransitionerDefinition
import jakarta.inject.Inject
import jakarta.inject.Singleton

@TransitionerDefinition
@Singleton
class TestTransitioner @Inject constructor() : Transitioner<String, Transition<String, TestValue, TestState>, TestValue, TestState>()
