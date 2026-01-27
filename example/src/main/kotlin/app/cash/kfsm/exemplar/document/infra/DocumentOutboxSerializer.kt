package app.cash.kfsm.exemplar.document.infra

import app.cash.kfsm.exemplar.document.DocumentEffect
import app.cash.kfsm.jooq.JacksonOutboxSerializer

/**
 * Outbox serializer for DocumentEffect using the lib-jooq Jackson serializer.
 */
object DocumentOutboxSerializer {
  
  val instance = JacksonOutboxSerializer.forSealedClassWithStringId(
    sealedClass = DocumentEffect::class
  )
}
