package app.cash.kfsm.guice.annotations

/**
 * Marks a class as a transition that should be automatically discovered and bound by the KFSM Guice integration.
 * 
 * This annotation should be applied to classes that implement [app.cash.kfsm.Transition]. When used in conjunction
 * with [app.cash.kfsm.guice.KfsmModule], transitions marked with this annotation will be automatically discovered
 * and made available for dependency injection.
 *
 * Example usage:
 * ```kotlin
 * @TransitionDefinition
 * class MyTransition @Inject constructor(deps: MyDeps) : Transition<MyValue, MyState>()
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class TransitionDefinition