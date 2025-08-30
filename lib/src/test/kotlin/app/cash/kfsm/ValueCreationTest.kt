package app.cash.kfsm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValueCreationTest {
  @Test fun `createValue persists and emits events for new value`() {
    val events = mutableListOf<String>()
    val persisted = mutableListOf<TestValue>()

    val transitioner = object : Transitioner<String, TestTransition, TestValue, TestState>() {
      override fun instantiate(id: String, initialState: TestState): Result<TestValue> =
        Result.success(TestValue(id, initialState))

      override fun persist(from: TestState?, value: TestValue, via: TestTransition?): Result<TestValue> {
        persisted.add(value)
        return Result.success(value)
      }

      override fun postHook(from: TestState?, value: TestValue, via: TestTransition?): Result<Unit> {
        events.add(when {
          from == null -> "Created ${value.id} in ${value.state}"
          else -> "Transitioned ${value.id} from $from to ${value.state}"
        })
        return Result.success(Unit)
      }
    }

    val machine = StateMachine(
      transitionMap = mapOf(
        TestState.Initial to mapOf(
          TestState.Next to TestTransition(TestState.Initial, TestState.Next)
        ),
        TestState.Next to emptyMap()
      ),
      transitioner = transitioner
    )

    val result = machine.createValue("test-1", TestState.Initial)
    assertTrue(result.isSuccess)

    val value = result.getOrThrow()
    assertEquals("test-1", value.id)
    assertEquals(TestState.Initial, value.state)

    assertEquals(1, persisted.size)
    assertEquals(value, persisted[0])

    assertEquals(1, events.size)
    assertEquals("Created test-1 in Initial", events[0])
  }

  @Test fun `createValue fails for invalid initial state`() {
    val machine = StateMachine(
      transitionMap = mapOf(
        TestState.Initial to mapOf(
          TestState.Next to TestTransition(TestState.Initial, TestState.Next)
        )
      ),
      transitioner = object : Transitioner<String, TestTransition, TestValue, TestState>() {
        override fun instantiate(id: String, initialState: TestState) =
          Result.success(TestValue(id, initialState))
      }
    )

    val result = machine.createValue("test-1", TestState.Next)
    assertTrue(result.isFailure)
    assertIs<IllegalArgumentException>(result.exceptionOrNull())
  }

  @Test fun `createValue fails if instantiate fails`() {
    val machine = StateMachine(
      transitionMap = mapOf(
        TestState.Initial to mapOf(
          TestState.Next to TestTransition(TestState.Initial, TestState.Next)
        )
      ),
      transitioner = object : Transitioner<String, TestTransition, TestValue, TestState>() {
        override fun instantiate(id: String, initialState: TestState) =
          Result.failure(IllegalStateException("Creation failed"))
      }
    )

    val result = machine.createValue("test-1", TestState.Initial)
    assertTrue(result.isFailure)
    assertIs<IllegalStateException>(result.exceptionOrNull())
  }

  @Test fun `createValue fails if persist fails`() {
    val machine = StateMachine(
      transitionMap = mapOf(
        TestState.Initial to mapOf(
          TestState.Next to TestTransition(TestState.Initial, TestState.Next)
        )
      ),
      transitioner = object : Transitioner<String, TestTransition, TestValue, TestState>() {
        override fun instantiate(id: String, initialState: TestState) =
          Result.success(TestValue(id, initialState))

        override fun persist(from: TestState?, value: TestValue, via: TestTransition?) =
          Result.failure(IllegalStateException("Persist failed"))
      }
    )

    val result = machine.createValue("test-1", TestState.Initial)
    assertTrue(result.isFailure)
    assertIs<IllegalStateException>(result.exceptionOrNull())
  }

  @Test fun `createValue fails if postHook fails`() {
    val machine = StateMachine(
      transitionMap = mapOf(
        TestState.Initial to mapOf(
          TestState.Next to TestTransition(TestState.Initial, TestState.Next)
        )
      ),
      transitioner = object : Transitioner<String, TestTransition, TestValue, TestState>() {
        override fun instantiate(id: String, initialState: TestState) =
          Result.success(TestValue(id, initialState))

        override fun postHook(from: TestState?, value: TestValue, via: TestTransition?) =
          Result.failure(IllegalStateException("PostHook failed"))
      }
    )

    val result = machine.createValue("test-1", TestState.Initial)
    assertTrue(result.isFailure)
    assertIs<IllegalStateException>(result.exceptionOrNull())
  }

  private sealed class TestState : State<String, TestValue, TestState>() {
    object Initial : TestState()
    object Next : TestState()
  }

  private data class TestValue(
    override val id: String,
    override val state: TestState
  ) : Value<String, TestValue, TestState> {
    override fun update(newState: TestState): TestValue = copy(state = newState)
  }

  private class TestTransition(
    from: TestState,
    to: TestState
  ) : Transition<String, TestValue, TestState>(from = from, to = to)
}
