package app.cash.kfsm.guice.test

import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import app.cash.kfsm.guice.StateMachine
import com.google.inject.AbstractModule
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.Multibinder

class TestModule : AbstractModule() {
    override fun configure() {
        // Create a multibinder for transitions
        val transitionBinder = Multibinder.newSetBinder(
            binder(),
            object : TypeLiteral<Transition<TestValue, TestState>>() {}
        )
        
        // Bind the transitions
        transitionBinder.addBinding().to(StartToMiddle::class.java)
        transitionBinder.addBinding().to(MiddleToEnd::class.java)
        
        // Bind the transitioner
        bind(object : TypeLiteral<Transitioner<Transition<TestValue, TestState>, TestValue, TestState>>() {})
            .toInstance(TestTransitioner())
            
        // Bind the state machine
        bind(object : TypeLiteral<StateMachine<TestValue, TestState>>() {})
    }
} 