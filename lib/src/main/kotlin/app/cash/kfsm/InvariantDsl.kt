package app.cash.kfsm

/**
 * Creates an invariant that checks if the given predicate holds true for the value.
 */
fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> invariant(
  message: String,
  predicate: (V) -> Boolean
): Invariant<ID, V, S> = object : Invariant<ID, V, S> {
  override fun validate(value: V): Result<V> {
    return if (predicate(value)) {
      Result.success(value)
    } else {
      Result.failure(PreconditionNotMet(message))
    }
  }
}

/**
 * Creates an invariant that checks if the given property of the value meets the predicate.
 */
// fun <ID, V : Value<ID, V, S>, S : State<S>, P> invariant(
//   property: (V) -> P,
//   message: String,
//   predicate: (P) -> Boolean
// ): Invariant<ID, V, S> {
//   return object : Invariant<ID, V, S> {
//     override fun validate(value: V): Result<Unit> {
//       return if (predicate(property(value))) {
//         Result.success(Unit)
//       } else {
//         Result.failure(InvalidInvariant(message))
//       }
//     }
//   }
// }

/**
 * Exception thrown when a value fails to meet an invariant.
 */
data class PreconditionNotMet(override val message: String) : Exception(message)
