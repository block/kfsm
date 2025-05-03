package app.cash.kfsm.guice

import app.cash.kfsm.State
import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import app.cash.kfsm.Value
import app.cash.kfsm.guice.annotations.TransitionDefinition
import app.cash.kfsm.guice.annotations.TransitionerDefinition
import com.google.inject.AbstractModule
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Types
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder

/**
 * Base Guice module for KFSM integration that automatically discovers and binds transitions.
 *
 * This module scans the specified package for classes annotated with [TransitionDefinition]
 * and binds them to a set of transitions that can be injected into the [StateMachine].
 * If no package is specified, it will scan the package of the concrete module class.
 *
 * @param ID The type of the ID for the value
 * @param V The type of value being managed by the state machine
 * @param S The type of state, must extend [State]
 * @property basePackage The base package to scan for transitions. If not provided, uses the package of the concrete module class.
 * @property types The type literals for the state machine, transitions, and transitioner
 *
 * Example usage:
 * ```kotlin
 * // With explicit package
 * class MyStateMachineModule : KfsmModule<MyValue, MyState>(
 *   basePackage = "com.example.myapp",
 *   types = typeLiteralsFor(MyValue::class.java, MyState::class.java)
 * )
 *
 * // Using default package (scans the package of MyStateMachineModule)
 * class MyStateMachineModule : KfsmModule<MyValue, MyState>(
 *   types = typeLiteralsFor(MyValue::class.java, MyState::class.java)
 * )
 * ```
 */
abstract class KfsmModule<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(
  private val types: KfsmMachineTypes<ID, V, S>,
  private val basePackage: String? = null,
) : AbstractModule() {

  @Suppress("UNCHECKED_CAST")
  override fun configure() {
    // Use the concrete module's package if basePackage is not provided
    val packageToScan = basePackage ?: this::class.java.`package`.name

    // Create a multibinder for the transition set
    val transitionBinder = Multibinder.newSetBinder(binder(), types.transition)

    // Configure and create the reflections instance for scanning
    val reflections = Reflections(
      ConfigurationBuilder()
        .forPackages(packageToScan)
        .setScanners(Scanners.TypesAnnotated)
    )

    // Find and bind all transitions
    reflections
      .getTypesAnnotatedWith(TransitionDefinition::class.java)
      .asSequence()
      .filter { clazz -> Transition::class.java.isAssignableFrom(clazz) }
      .map { it as Class<out Transition<ID, V, S>> }
      .forEach { transitionClass ->
        transitionBinder.addBinding().to(transitionClass)
      }

    // Find and bind the transitioner
    reflections.getTypesAnnotatedWith(TransitionerDefinition::class.java)
      .asSequence()
      .filter { clazz -> Transitioner::class.java.isAssignableFrom(clazz) }
      .map { it as Class<out Transitioner<*, *, *, *>> }
      .forEach { transitionerClass ->
        bind(types.transitioner)
          .to(transitionerClass as Class<out Transitioner<ID, Transition<ID, V, S>, V, S>>)
      }

    // Bind the state machine
    bind(types.stateMachine)
  }

  companion object {

    data class KfsmMachineTypes<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(
      val stateMachine: TypeLiteral<StateMachine<ID, V, S>>,
      val transition: TypeLiteral<Transition<ID, V, S>>,
      val transitioner: TypeLiteral<Transitioner<ID, Transition<ID, V, S>, V, S>>,
    )

    @Suppress("UNCHECKED_CAST")
    fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> typeLiteralsFor(
      idType: Class<ID>,
      valueType: Class<V>,
      stateType: Class<S>
    ): KfsmMachineTypes<ID, V, S> {
      val stateMachineType = Types.newParameterizedType(StateMachine::class.java, idType, valueType, stateType)
      val transitionType = Types.newParameterizedType(Transition::class.java, idType, valueType, stateType)
      val transitionerType = Types.newParameterizedType(Transitioner::class.java, idType, transitionType, valueType, stateType)

      return KfsmMachineTypes(
        TypeLiteral.get(stateMachineType) as TypeLiteral<StateMachine<ID, V, S>>,
        TypeLiteral.get(transitionType) as TypeLiteral<Transition<ID, V, S>>,
        TypeLiteral.get(transitionerType) as TypeLiteral<Transitioner<ID, Transition<ID, V, S>, V, S>>,
      )
    }
  }
}


