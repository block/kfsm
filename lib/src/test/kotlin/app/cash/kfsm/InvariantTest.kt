package app.cash.kfsm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

data class Order(
    override val state: OrderState,
    override val id: String,
    val items: List<Item>,
    val total: BigDecimal,
    val shippingAddress: String? = null
) : Value<String, Order, OrderState> {
    override fun update(newState: OrderState): Order = copy(state = newState)
}

data class Item(val name: String, val price: BigDecimal)

sealed class OrderState(
    transitionsFn: () -> Set<OrderState>,
    invariants: List<Invariant<String, Order, OrderState>> = emptyList()
) : State<String, Order, OrderState>(transitionsFn, invariants) {
    object Draft : OrderState(
        transitionsFn = { setOf(Submitted) },
        invariants = listOf(
            invariant("Order must have at least one item") { it.items.isNotEmpty() },
            invariant("Order total must be positive") { it.total > BigDecimal.ZERO }
        )
    )
    
    object Submitted : OrderState(
        transitionsFn = { setOf(Processing) },
        invariants = listOf(
            invariant("Order must have a shipping address") { it.shippingAddress != null }
        )
    )
    
    object Processing : OrderState(
        transitionsFn = { setOf(Shipped) }
    )
    
    object Shipped : OrderState(
        transitionsFn = { setOf(Delivered) }
    )
    
    object Delivered : OrderState(
        transitionsFn = { emptySet() }
    )
}

class InvariantTest : StringSpec({
    class OrderTransition : Transition<String, Order, OrderState>(OrderState.Draft, OrderState.Submitted) {
        override fun effect(value: Order): Result<Order> = Result.success(value)
    }

    class TestTransitioner : Transitioner<String, OrderTransition, Order, OrderState>()

    val transitioner = TestTransitioner()
    val transition = OrderTransition()

    "Draft state should validate order with items and positive total" {
        val order = Order(
            state = OrderState.Draft,
            id = "order-1",
            items = listOf(Item("Widget", BigDecimal("10.00"))),
            total = BigDecimal("10.00")
        )
        OrderState.Draft.validate(order).shouldBeSuccess()
    }

    "Draft state should fail validation for empty order" {
        val order = Order(
            state = OrderState.Draft,
            id = "order-1",
            items = emptyList(),
            total = BigDecimal("10.00")
        )
        OrderState.Draft.validate(order).shouldBeFailure<PreconditionNotMet>()
    }

    "Draft state should fail validation for negative total" {
        val order = Order(
            state = OrderState.Draft,
            id = "order-1",
            items = listOf(Item("Widget", BigDecimal("10.00"))),
            total = BigDecimal("-10.00")
        )
        OrderState.Draft.validate(order).shouldBeFailure<PreconditionNotMet>()
    }

    "Submitted state should validate order with shipping address" {
        val order = Order(
            state = OrderState.Submitted,
            id = "order-1",
            items = listOf(Item("Widget", BigDecimal("10.00"))),
            total = BigDecimal("10.00"),
            shippingAddress = "123 Main St"
        )
        OrderState.Submitted.validate(order).shouldBeSuccess()
    }

    "Submitted state should fail validation without shipping address" {
        val order = Order(
            state = OrderState.Submitted,
            id = "order-1",
            items = listOf(Item("Widget", BigDecimal("10.00"))),
            total = BigDecimal("10.00")
        )
        OrderState.Submitted.validate(order).shouldBeFailure<PreconditionNotMet>()
    }

    "Transitioner should validate invariants during transition" {
        val draftOrder = Order(
            state = OrderState.Draft,
            id = "order-1",
            items = listOf(Item("Widget", BigDecimal("10.00"))),
            total = BigDecimal("10.00")
        )
        
        val result = transitioner.transition(draftOrder, transition)
        result.shouldBeFailure<PreconditionNotMet>()
    }

    "Transitioner should succeed when all invariants are met" {
        val draftOrder = Order(
            state = OrderState.Draft,
            id = "order-1",
            items = listOf(Item("Widget", BigDecimal("10.00"))),
            total = BigDecimal("10.00"),
            shippingAddress = "123 Main St"
        )
        
        val result = transitioner.transition(draftOrder, transition)
        result.shouldBeSuccess()
        result.getOrThrow().state shouldBe OrderState.Submitted
    }

    "InvalidInvariant should contain the correct error message" {
        val order = Order(
            state = OrderState.Draft,
            id = "order-1",
            items = emptyList(),
            total = BigDecimal("10.00")
        )

        val result = OrderState.Draft.validate(order)
        result.shouldBeFailure<PreconditionNotMet>()
        result.exceptionOrNull()?.message shouldBe "Order must have at least one item"
    }

    "Multiple invariant failures should show the first failure message" {
        val order = Order(
            state = OrderState.Draft,
            id = "order-1",
            items = emptyList(),
            total = BigDecimal("-10.00")
        )

        val result = OrderState.Draft.validate(order)
        result.shouldBeFailure<PreconditionNotMet>()
        result.exceptionOrNull()?.message shouldBe "Order must have at least one item"
    }

    "Transitioner should preserve invariant error messages" {
        val draftOrder = Order(
            state = OrderState.Draft,
            id = "order-1",
            items = emptyList(),
            total = BigDecimal("10.00")
        )
        
        val result = transitioner.transition(draftOrder, transition)
        result.shouldBeFailure<PreconditionNotMet>()
        result.exceptionOrNull()?.message shouldBe "Order must have at least one item"
    }
}) 
