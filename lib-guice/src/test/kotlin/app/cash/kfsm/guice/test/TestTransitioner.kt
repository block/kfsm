package app.cash.kfsm.guice.test

import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner

class TestTransitioner : Transitioner<Transition<TestValue, TestState>, TestValue, TestState>() 