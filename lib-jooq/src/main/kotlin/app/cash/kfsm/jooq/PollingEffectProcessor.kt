package app.cash.kfsm.jooq

import app.cash.kfsm.AwaitableStateMachine
import app.cash.kfsm.Effect
import app.cash.kfsm.EffectHandler
import app.cash.kfsm.EffectProcessor
import app.cash.kfsm.State
import app.cash.kfsm.StateMachine
import app.cash.kfsm.Value
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A background processor that polls the outbox and executes effects.
 *
 * This processor wraps [EffectProcessor] and provides:
 * - Configurable polling interval
 * - Exponential backoff when outbox is empty
 * - Graceful shutdown
 * - Optional immediate trigger after transitions
 *
 * Example:
 * ```kotlin
 * val polling = PollingEffectProcessor(
 *   outbox = jooqOutbox,
 *   handler = orderEffectHandler,
 *   stateMachine = orderStateMachine,
 *   valueLoader = { id -> orderRepository.findById(id) },
 *   config = PollingConfig(
 *     baseInterval = Duration.ofMillis(100),
 *     maxInterval = Duration.ofSeconds(5),
 *     batchSize = 50
 *   )
 * )
 *
 * polling.start()
 * // ... application runs ...
 * polling.stop()
 * ```
 *
 * For priority-based processing, run separate processors for different effect types:
 * ```kotlin
 * // High-priority processor for ledger operations (polls frequently)
 * val ledgerProcessor = PollingEffectProcessor(
 *   outbox = outbox,
 *   effectTypes = setOf("ChargeLedger", "RefundLedger"),
 *   config = PollingConfig(baseInterval = Duration.ofMillis(50))
 * )
 *
 * // Low-priority processor for notifications (polls less frequently)
 * val notificationProcessor = PollingEffectProcessor(
 *   outbox = outbox,
 *   effectTypes = setOf("SendEmail", "SendPush"),
 *   config = PollingConfig(baseInterval = Duration.ofSeconds(1))
 * )
 * ```
 *
 * @param ID The type of unique identifier for values
 * @param V The value type
 * @param S The state type
 * @param Ef The effect type
 * @param effectTypes If provided, only process messages with these effect types.
 * @param maxAttempts Maximum attempts before dead-lettering. If null, failed messages stay as FAILED.
 */
class PollingEffectProcessor<ID : Any, V : Value<ID, V, S>, S : State<S>, Ef : Effect>(
  outbox: JooqOutbox<ID, Ef>,
  handler: EffectHandler<ID, V, S, Ef>,
  stateMachine: StateMachine<ID, V, S, Ef>,
  valueLoader: (ID) -> Result<V?>,
  awaitable: AwaitableStateMachine<ID, V, S, Ef>? = null,
  effectTypes: Set<String>? = null,
  maxAttempts: Int? = null,
  private val config: PollingConfig = PollingConfig(),
  private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
    Thread(r, "kfsm-effect-processor").apply { isDaemon = true }
  }
) {
  private val processor = EffectProcessor(
    outbox = outbox,
    handler = handler,
    stateMachine = stateMachine,
    valueLoader = valueLoader,
    awaitable = awaitable,
    effectTypes = effectTypes,
    maxAttempts = maxAttempts
  )

  private val running = AtomicBoolean(false)
  private val hasWork = AtomicBoolean(false)
  private var scheduledTask: ScheduledFuture<*>? = null
  private var currentInterval = config.baseInterval

  /**
   * Start the background polling loop.
   */
  fun start() {
    if (!running.compareAndSet(false, true)) {
      return // Already running
    }
    scheduleNext(Duration.ZERO)
  }

  /**
   * Stop the background polling loop.
   *
   * @param timeout Maximum time to wait for current processing to complete
   */
  fun stop(timeout: Duration = Duration.ofSeconds(30)) {
    running.set(false)
    scheduledTask?.cancel(false)
    executor.shutdown()
    executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)
  }

  /**
   * Trigger immediate processing. Call this after a successful transition
   * to reduce latency for the current request.
   *
   * Thread-safe and idempotent—multiple calls will not cause duplicate processing.
   */
  fun triggerNow() {
    hasWork.set(true)
    if (running.get()) {
      executor.submit { poll() }
    }
  }

  /**
   * Process all pending messages once (for testing or manual triggering).
   */
  fun processOnce(): Int = processor.processAll(config.batchSize)

  private fun scheduleNext(delay: Duration) {
    if (!running.get()) return

    scheduledTask = executor.schedule(
      { poll() },
      delay.toMillis(),
      TimeUnit.MILLISECONDS
    )
  }

  private fun poll() {
    if (!running.get()) return

    try {
      val processedCount = processor.processAll(config.batchSize)

      // Adjust interval based on whether we found work
      currentInterval = if (processedCount > 0 || hasWork.getAndSet(false)) {
        config.baseInterval
      } else {
        minOf(
          Duration.ofMillis((currentInterval.toMillis() * config.backoffMultiplier).toLong()),
          config.maxInterval
        )
      }
    } catch (e: Exception) {
      config.errorHandler?.invoke(e)
      currentInterval = config.maxInterval
    }

    scheduleNext(currentInterval)
  }
}

/**
 * Configuration for [PollingEffectProcessor].
 */
data class PollingConfig(
  /**
   * Base polling interval when work is available.
   */
  val baseInterval: Duration = Duration.ofMillis(100),

  /**
   * Maximum polling interval when no work is available.
   */
  val maxInterval: Duration = Duration.ofSeconds(5),

  /**
   * Multiplier for exponential backoff.
   */
  val backoffMultiplier: Double = 2.0,

  /**
   * Maximum messages to process per poll.
   */
  val batchSize: Int = 100,

  /**
   * Optional error handler for processing failures.
   */
  val errorHandler: ((Throwable) -> Unit)? = null
)
