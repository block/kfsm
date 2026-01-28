package app.cash.kfsm

import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * DSL for building an awaitable workflow with all components wired together.
 *
 * This is a thin convenience layer over the existing components - it doesn't
 * replace the class-based approach, just simplifies wiring.
 *
 * Example:
 * ```kotlin
 * val workflow = workflow<String, Document, DocState, DocEffect> {
 *     repository(documentRepository)
 *     valueLoader { id -> documentRepository.findById(id) }
 *     outbox(documentOutbox)
 *
 *     settledWhen { state ->
 *         state is DocState.Accepted || state is DocState.Rejected
 *     }
 *
 *     effects {
 *         on<DocEffect.UploadFile> { id, effect ->
 *             val fileId = uploadService.upload(effect.fileName, effect.content)
 *             EffectOutcome.TransitionProduced(id, UploadCompleted(fileId))
 *         }
 *         on<DocEffect.ScanFile> { id, effect ->
 *             val result = scanService.scan(effect.fileId)
 *             if (result.clean) {
 *                 EffectOutcome.TransitionProduced(id, ScanSucceeded(result.report))
 *             } else {
 *                 EffectOutcome.TransitionProduced(id, ScanFailed(result.reason))
 *             }
 *         }
 *         on<DocEffect.NotifyUser> { _, effect ->
 *             notificationService.notify(effect.userId, effect.message)
 *             EffectOutcome.Completed
 *         }
 *     }
 * }
 *
 * // Usage
 * val result = workflow.awaitable.transitionAndAwait(document, RequestUpload(...), 30.seconds)
 * workflow.processor.processAll()
 * ```
 *
 * @param ID The type of unique identifier for values
 * @param V The value type
 * @param S The state type
 * @param Ef The effect type
 */
class WorkflowBuilder<ID, V : Value<ID, V, S>, S : State<S>, Ef : Effect> {
    private var repository: Repository<ID, V, S, Ef>? = null
    private var valueLoader: ((ID) -> Result<V?>)? = null
    private var outbox: Outbox<ID, Ef>? = null
    private var pendingRequestStore: PendingRequestStore<ID, V>? = null
    private var isSettled: ((S) -> Boolean)? = null
    private var effectHandler: EffectHandler<ID, V, S, Ef>? = null
    private var pollInterval: Duration = 100.milliseconds
    private var effectTypes: Set<String>? = null
    private var maxAttempts: Int? = null

    fun repository(repository: Repository<ID, V, S, Ef>) {
        this.repository = repository
    }

    fun valueLoader(loader: (ID) -> Result<V?>) {
        this.valueLoader = loader
    }

    fun outbox(outbox: Outbox<ID, Ef>) {
        this.outbox = outbox
    }

    fun pendingRequestStore(store: PendingRequestStore<ID, V>) {
        this.pendingRequestStore = store
    }

    fun settledWhen(predicate: (S) -> Boolean) {
        this.isSettled = predicate
    }

    fun settledStates(vararg states: S) {
        val stateSet = states.toSet()
        this.isSettled = { state -> stateSet.contains(state) }
    }

    fun pollInterval(interval: Duration) {
        this.pollInterval = interval
    }

    fun effectTypes(vararg types: String) {
        this.effectTypes = types.toSet()
    }

    fun maxAttempts(attempts: Int) {
        this.maxAttempts = attempts
    }

    fun effects(block: EffectHandlerBuilder<ID, V, S, Ef>.() -> Unit) {
        val builder = EffectHandlerBuilder<ID, V, S, Ef>()
        builder.block()
        this.effectHandler = builder.build()
    }

    fun effectHandler(handler: EffectHandler<ID, V, S, Ef>) {
        this.effectHandler = handler
    }

    fun build(): Workflow<ID, V, S, Ef> {
        val repo = requireNotNull(repository) { "repository must be configured" }
        val loader = requireNotNull(valueLoader) { "valueLoader must be configured" }
        val outboxImpl = requireNotNull(outbox) { "outbox must be configured" }
        val store = requireNotNull(pendingRequestStore) { "pendingRequestStore must be configured" }
        val settled = requireNotNull(isSettled) { "settledWhen or settledStates must be configured" }
        val handler = requireNotNull(effectHandler) { "effects or effectHandler must be configured" }

        val stateMachine = StateMachine(repo)
        val awaitable = AwaitableStateMachine(
            stateMachine = stateMachine,
            pendingRequestStore = store,
            isSettled = settled,
            pollInterval = pollInterval
        )
        val processor = EffectProcessor(
            outbox = outboxImpl,
            handler = handler,
            stateMachine = stateMachine,
            valueLoader = loader,
            awaitable = awaitable,
            effectTypes = effectTypes,
            maxAttempts = maxAttempts
        )

        return Workflow(
            stateMachine = stateMachine,
            awaitable = awaitable,
            processor = processor
        )
    }
}

/**
 * Builder for creating an [EffectHandler] using a DSL.
 */
class EffectHandlerBuilder<ID, V : Value<ID, V, S>, S : State<S>, Ef : Effect> {
    @PublishedApi
    internal val handlers = mutableMapOf<KClass<out Ef>, (ID, Ef) -> Result<EffectOutcome<ID, V, S, Ef>>>()

    inline fun <reified E : Ef> on(noinline handler: (ID, E) -> EffectOutcome<ID, V, S, Ef>) {
        onCatching<E> { id, effect -> Result.success(handler(id, effect)) }
    }

    inline fun <reified E : Ef> onCatching(noinline handler: (ID, E) -> Result<EffectOutcome<ID, V, S, Ef>>) {
        @Suppress("UNCHECKED_CAST")
        handlers[E::class] = { id, effect -> handler(id, effect as E) }
    }

    fun build(): EffectHandler<ID, V, S, Ef> = EffectHandler { valueId, effect ->
        val handler = handlers[effect::class]
            ?: return@EffectHandler Result.failure(
                IllegalArgumentException("No handler registered for effect type: ${effect::class.simpleName}")
            )
        runCatching { handler(valueId, effect) }.getOrElse { Result.failure(it) }
    }
}

/**
 * A fully wired workflow containing all components.
 */
data class Workflow<ID, V : Value<ID, V, S>, S : State<S>, Ef : Effect>(
    val stateMachine: StateMachine<ID, V, S, Ef>,
    val awaitable: AwaitableStateMachine<ID, V, S, Ef>,
    val processor: EffectProcessor<ID, V, S, Ef>
)

/**
 * Build a workflow using the DSL.
 */
fun <ID, V : Value<ID, V, S>, S : State<S>, Ef : Effect> workflow(
    block: WorkflowBuilder<ID, V, S, Ef>.() -> Unit
): Workflow<ID, V, S, Ef> {
    val builder = WorkflowBuilder<ID, V, S, Ef>()
    builder.block()
    return builder.build()
}
