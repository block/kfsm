package app.cash.kfsm.guice

import app.cash.kfsm.State
import app.cash.kfsm.Transition
import app.cash.kfsm.Transitioner
import app.cash.kfsm.Value
import app.cash.kfsm.guice.annotations.TransitionDefinition
import app.cash.kfsm.guice.annotations.TransitionerDefinition
import com.google.inject.AbstractModule
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Types

/**
 * Base Guice module for KFSM integration that automatically discovers and binds transitions.
 *
 * This module scans the specified package for classes annotated with [TransitionDefinition]
 * and binds them to a set of transitions that can be injected into the [StateMachine].
 * If no package is specified, it will scan the package of the concrete module class.
 *
 * @param ID The type of the ID for the value
 * @param V The type of value being managed by the state machine
 * @param S The type of state, must extend [State]
 * @property basePackage The base package to scan for transitions. If not provided, uses the package of the concrete module class.
 * @property types The type literals for the state machine, transitions, and transitioner
 *
 * Example usage:
 * ```kotlin
 * // With explicit package
 * class MyStateMachineModule : KfsmModule<MyValue, MyState>(
 *   basePackage = "com.example.myapp",
 *   types = typeLiteralsFor(MyValue::class.java, MyState::class.java)
 * )
 *
 * // Using default package (scans the package of MyStateMachineModule)
 * class MyStateMachineModule : KfsmModule<MyValue, MyState>(
 *   types = typeLiteralsFor(MyValue::class.java, MyState::class.java)
 * )
 * ```
 */
abstract class KfsmModule<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(
  private val types: KfsmMachineTypes<ID, V, S>,
  private val basePackage: String? = null,
) : AbstractModule() {

  @Suppress("UNCHECKED_CAST")
  override fun configure() {
    // Use the concrete module's package if basePackage is not provided
    val packageToScan = basePackage ?: this::class.java.`package`.name

    // Create a multibinder for the transition set
    val transitionBinder = Multibinder.newSetBinder(binder(), types.transition)

    // Find and bind all transitions using Java reflection
    findClassesInPackage(packageToScan)
      .filter { it.isAnnotationPresent(TransitionDefinition::class.java) }
      .filter { Transition::class.java.isAssignableFrom(it) }
      .forEach { transitionClass ->
        transitionBinder.addBinding().to(transitionClass as Class<out Transition<ID, V, S>>)
      }

    // Find and bind the transitioner using Java reflection
    findClassesInPackage(packageToScan)
      .filter { it.isAnnotationPresent(TransitionerDefinition::class.java) }
      .filter { Transitioner::class.java.isAssignableFrom(it) }
      .forEach { transitionerClass ->
        bind(types.transitioner)
          .to(transitionerClass as Class<out Transitioner<ID, Transition<ID, V, S>, V, S>>)
      }

    // Bind the state machine
    bind(types.stateMachine)
  }

  /**
   * Finds all classes in the specified package using the class loader.
   * This replaces the org.reflections library functionality.
   */
  private fun findClassesInPackage(packageName: String): List<Class<*>> {
    val classLoader = this::class.java.classLoader
    val packagePath = packageName.replace('.', '/')
    
    try {
      val packageUrl = classLoader.getResource(packagePath)
      if (packageUrl == null) return emptyList()
      
      return when (packageUrl.protocol) {
        "file" -> findClassesInFileSystem(packageUrl.path, packageName, classLoader)
        "jar" -> findClassesInJar(packageUrl.path, packageName, classLoader)
        else -> emptyList()
      }
    } catch (e: Exception) {
      // Log warning or handle gracefully
      return emptyList()
    }
  }

  /**
   * Finds classes in the file system (for development/testing).
   */
  private fun findClassesInFileSystem(packagePath: String, packageName: String, classLoader: ClassLoader): List<Class<*>> {
    val directory = java.io.File(packagePath)
    if (!directory.exists() || !directory.isDirectory) return emptyList()
    
    return directory.walkTopDown()
      .filter { it.isFile && it.extension == "class" }
      .mapNotNull { file ->
        val relativePath = file.relativeTo(directory).path
        val className = packageName + "." + relativePath.removeSuffix(".class").replace('/', '.')
        try {
          Class.forName(className, false, classLoader)
        } catch (e: ClassNotFoundException) {
          null
        }
      }
      .toList()
  }

  /**
   * Finds classes in a JAR file (for production).
   */
  private fun findClassesInJar(jarPath: String, packageName: String, classLoader: ClassLoader): List<Class<*>> {
    val packagePath = packageName.replace('.', '/')
    val jarFile = jarPath.removePrefix("file:").removePrefix("jar:file:").removeSuffix("!/$packagePath")
    
    return try {
      java.util.jar.JarFile(jarFile).use { jar ->
        jar.entries().asSequence()
          .filter { it.name.startsWith(packagePath) && it.name.endsWith(".class") }
          .mapNotNull { entry ->
            val className = entry.name.removeSuffix(".class").replace('/', '.')
            try {
              Class.forName(className, false, classLoader)
            } catch (e: ClassNotFoundException) {
              null
            }
          }
          .toList()
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  companion object {

    data class KfsmMachineTypes<ID, V : Value<ID, V, S>, S : State<ID, V, S>>(
      val stateMachine: TypeLiteral<StateMachine<ID, V, S>>,
      val transition: TypeLiteral<Transition<ID, V, S>>,
      val transitioner: TypeLiteral<Transitioner<ID, Transition<ID, V, S>, V, S>>,
    )

    @Suppress("UNCHECKED_CAST")
    fun <ID, V : Value<ID, V, S>, S : State<ID, V, S>> typeLiteralsFor(
      idType: Class<ID>,
      valueType: Class<V>,
      stateType: Class<S>
    ): KfsmMachineTypes<ID, V, S> {
      val stateMachineType = Types.newParameterizedType(StateMachine::class.java, idType, valueType, stateType)
      val transitionType = Types.newParameterizedType(Transition::class.java, idType, valueType, stateType)
      val transitionerType = Types.newParameterizedType(Transitioner::class.java, idType, transitionType, valueType, stateType)

      return KfsmMachineTypes(
        TypeLiteral.get(stateMachineType) as TypeLiteral<StateMachine<ID, V, S>>,
        TypeLiteral.get(transitionType) as TypeLiteral<Transition<ID, V, S>>,
        TypeLiteral.get(transitionerType) as TypeLiteral<Transitioner<ID, Transition<ID, V, S>, V, S>>,
      )
    }
  }
}


