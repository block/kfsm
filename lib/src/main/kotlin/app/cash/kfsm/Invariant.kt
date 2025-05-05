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
