package app.cash.kfsm.exemplar.document.infra

import app.cash.kfsm.exemplar.document.DocumentEffect
import app.cash.kfsm.jooq.MoshiOutboxSerializer

/**
 * Outbox serializer for DocumentEffect using the lib-jooq Moshi serializer.
 */
object DocumentOutboxSerializer {
  
  val instance = MoshiOutboxSerializer.forSealedClassWithStringId(
    sealedClass = DocumentEffect::class
  )
}
