package app.cash.kfsm.guice

import app.cash.kfsm.State
import app.cash.kfsm.Transition
import app.cash.kfsm.Value
import app.cash.kfsm.guice.annotations.TransitionDefinition
import com.google.inject.AbstractModule
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.Multibinder
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder
import kotlin.reflect.KClass

/**
 * Base Guice module for KFSM integration that automatically discovers and binds transitions.
 *
 * This module scans the specified package for classes annotated with [TransitionDefinition]
 * and binds them to a set of transitions that can be injected into the [StateMachine].
 *
 * @param V The type of value being managed by the state machine
 * @param S The type of state, must extend [State]
 * @property basePackage The base package to scan for transitions
 *
 * Example usage:
 * ```kotlin
 * class MyStateMachineModule : KfsmModule<MyValue, MyState>("com.example.myapp")
 * ```
 */
abstract class KfsmModule<V : Value<V, S>, S : State<S>>(
    private val basePackage: String
) : AbstractModule() {

    override fun configure() {
        // Create a multibinder for the transition set
        val transitionBinder = Multibinder.newSetBinder(
            binder(),
            object : TypeLiteral<Transition<V, S>>() {}
        )
        
        // Configure and create the reflections instance for scanning
        val reflections = Reflections(
            ConfigurationBuilder()
                .forPackages(basePackage)
                .setScanners(Scanners.TypesAnnotated)
        )
        
        // Find and bind all transitions
        @Suppress("UNCHECKED_CAST")
        reflections
            .getTypesAnnotatedWith(TransitionDefinition::class.java)
            .asSequence()
            .filter { clazz ->
                Transition::class.java.isAssignableFrom(clazz) &&
                        clazz.kotlin.isSubclassOf(Transition::class)
            }
            .map { it as Class<out Transition<V, S>> }
            .forEach { transitionClass ->
                transitionBinder.addBinding().to(transitionClass)
            }
    }
}

private fun KClass<*>.isSubclassOf(superclass: KClass<*>): Boolean =
    superclass.java.isAssignableFrom(this.java)
