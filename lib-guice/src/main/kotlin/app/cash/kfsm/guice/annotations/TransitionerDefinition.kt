package app.cash.kfsm.guice.annotations

/**
 * Annotation used to mark a class as a transitioner that should be automatically discovered and bound by [KfsmModule].
 *
 * Classes annotated with this annotation will be automatically discovered and bound to the appropriate
 * [Transitioner] type when used with [KfsmModule]. This allows for automatic dependency injection
 * of transitioners without manual binding.
 *
 * Example usage:
 * ```kotlin
 * @TransitionerDefinition
 * class MyTransitioner @Inject constructor(deps: MyDeps): Transitioner<MyTransition, MyValue, MyState> {
 *     override fun transition(value: MyValue, transition: MyTransition): Result<MyValue> {
 *         // Implementation
 *     }
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class TransitionerDefinition
