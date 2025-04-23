package app.cash.kfsm.guice.test

import app.cash.kfsm.guice.KfsmModule

class TestModule : KfsmModule<String, TestValue, TestState>(
  types = typeLiteralsFor(String::class.java, TestValue::class.java, TestState::class.java)
)
