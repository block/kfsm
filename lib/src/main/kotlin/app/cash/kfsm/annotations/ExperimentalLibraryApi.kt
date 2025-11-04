package app.cash.kfsm.annotations

@RequiresOptIn(
  message = "This API is experimental and may change in the future.",
  level = RequiresOptIn.Level.WARNING
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class ExperimentalLibraryApi()
