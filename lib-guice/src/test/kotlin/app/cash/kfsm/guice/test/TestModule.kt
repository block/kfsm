package app.cash.kfsm.guice.test

import app.cash.kfsm.Transitioner
import app.cash.kfsm.guice.KfsmModule
import com.google.inject.TypeLiteral

class TestModule : KfsmModule<TestValue, TestState>("app.cash.kfsm.guice.test") {
    override fun configure() {
        super.configure()
        
        // Bind the transitioner
        bind(object : TypeLiteral<Transitioner<Transition<TestValue, TestState>, TestValue, TestState>>() {})
            .toInstance(TestTransitioner())
    }
} 