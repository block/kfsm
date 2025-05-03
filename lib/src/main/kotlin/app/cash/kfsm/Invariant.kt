package app.cash.kfsm

/**
 * Represents an invariant that must hold true for a value in a particular state.
 * 
 * @param V The type of value being validated
 * @param S The type of state that this invariant applies to
 */
interface Invariant<ID, V : Value<ID, V, S>, S : State<ID, V, S>> {
    /**
     * Validates that the given value meets this invariant.
     * 
     * @param value The value to validate
     * @return A [Result] containing either the value if the invariant holds, or an error if it doesn't
     */
    fun validate(value: V): Result<V>
}

// class Invariants<ID, V : Value<ID, V, S>, S : State<S>> private constructor(
//     private val invariants: List<Invariant<ID, V, S>>
// ) : Invariant<ID, V, S> {
//     override fun validate(value: V): Result<Unit> {
//         for (invariant in invariants) {
//             val result = invariant.validate(value)
//             if (result.isFailure) {
//                 return result
//             }
//         }
//         return Result.success(Unit)
//     }
//
//     companion object {
//         fun <ID, V : Value<ID, V, S>, S : State<S>> of(
//             vararg invariants: Invariant<ID, V, S>
//         ): Invariants<ID, V, S> = Invariants(invariants.toList())
//
//         fun <ID, V : Value<ID, V, S>, S : State<S>> empty(): Invariants<ID, V, S> = Invariants(emptyList())
//     }
// }
