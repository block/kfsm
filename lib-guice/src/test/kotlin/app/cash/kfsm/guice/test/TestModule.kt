package app.cash.kfsm.guice.test

import app.cash.kfsm.guice.KfsmModule

class TestModule : KfsmModule<TestValue, TestState>(
  basePackage = "app.cash.kfsm.guice.test",
  types = typeLiteralsFor(TestValue::class.java, TestState::class.java),
)
