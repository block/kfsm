package app.cash.kfsm.guice.test

import app.cash.kfsm.guice.KfsmModule

class TestModule : KfsmModule<TestValue, TestState>(
  types = typeLiteralsFor(TestValue::class.java, TestState::class.java)
)
