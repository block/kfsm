package app.cash.kfsm.guice.annotations

import com.google.inject.BindingAnnotation

@BindingAnnotation
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class TransitionKey