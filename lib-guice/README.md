# KFSM Guice Integration

This module provides [Google Guice](https://github.com/google/guice) integration for KFSM (Kotlin Finite State Machine), enabling automatic discovery and dependency injection of state machine components.

## Features

- Automatic discovery and binding of transitions using annotations
- Automatic discovery and binding of transitioners
- Type-safe state machine injection
- Seamless integration with existing Guice modules

## Installation

Add the dependency to your project:

```kotlin
dependencies {
    implementation("app.cash.kfsm:kfsm-guice:$version")
}
```

## Usage

### 1. Define Your States and Values

```kotlin
enum class OrderState : State<OrderState> {
    PENDING, PROCESSING, COMPLETED, CANCELLED
}

data class Order(
    override val state: OrderState,
    val id: String,
    val items: List<Item>
) : Value<Order, OrderState> {
    override fun update(newState: OrderState): Order = copy(state = newState)
}
```

### 2. Create Transitions

Annotate your transitions with `@TransitionDefinition`:

```kotlin
@TransitionDefinition
class ProcessOrder @Inject constructor(
    private val orderProcessor: OrderProcessor
) : Transition<Order, OrderState>(
    from = States(OrderState.PENDING),
    to = OrderState.PROCESSING
) {
    override fun effect(value: Order): Result<Order> = 
        orderProcessor.process(value)
            .map { it.update(OrderState.PROCESSING) }
}
```

### 3. Create a Transitioner (Optional)

If you need custom transition handling, create a transitioner and annotate it with `@TransitionerDefinition`:

```kotlin
@TransitionerDefinition
class OrderTransitioner @Inject constructor() : 
    Transitioner<Transition<Order, OrderState>, Order, OrderState> {
    override fun transition(
        value: Order,
        transition: Transition<Order, OrderState>
    ): Result<Order> = transition.effect(value)
}
```

### 4. Create a Guice Module

Extend `KfsmModule` to automatically discover and bind your transitions:

```kotlin
class OrderModule : KfsmModule<Order, OrderState>(
    types = typeLiteralsFor(Order::class.java, OrderState::class.java)
)
```

The module will automatically:
- Scan its package for transitions annotated with `@TransitionDefinition`
- Scan for transitioners annotated with `@TransitionerDefinition`
- Bind the state machine and all its dependencies

You can also specify a custom package to scan:

```kotlin
class OrderModule : KfsmModule<Order, OrderState>(
    basePackage = "com.example.orders",
    types = typeLiteralsFor(Order::class.java, OrderState::class.java)
)
```

### 5. Use the State Machine

Inject and use the state machine in your code:

```kotlin
class OrderService @Inject constructor(
    private val stateMachine: StateMachine<Order, OrderState>
) {
    fun processOrder(order: Order) {
        // Get available transitions
        val transitions = stateMachine.getAvailableTransitions(order.state)
        
        // Get a specific transition
        val processTransition = stateMachine.getTransition<ProcessOrder>()
        
        // Execute the transition
        val result = stateMachine.execute(order, processTransition)
    }
}
```

## Testing

The module provides easy testing capabilities. Here's an example using Kotest:

```kotlin
class OrderStateMachineTest : StringSpec({
    val injector = Guice.createInjector(OrderModule())
    val stateMachine = injector.getInstance(
        Key.get(object : TypeLiteral<StateMachine<Order, OrderState>>() {})
    )

    "PENDING order should transition to PROCESSING" {
        val order = Order(OrderState.PENDING, "123", emptyList())
        val result = stateMachine.execute(
            order,
            stateMachine.getTransition<ProcessOrder>()
        ).getOrThrow()
        result.state shouldBe OrderState.PROCESSING
    }
})
```

## Best Practices

1. Keep transitions focused and single-purpose
2. Use dependency injection for transition dependencies
3. Place related transitions in the same package
4. Use meaningful names for transitions that reflect their purpose
5. Handle transition failures appropriately in your transitioner

## License

```
Copyright 2024 Square, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
``` 