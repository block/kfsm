package app.cash.kfsm.v2.exemplar.document.infra

import app.cash.kfsm.v2.exemplar.document.DocumentEffect
import app.cash.kfsm.v2.jooq.MoshiOutboxSerializer

/**
 * Outbox serializer for DocumentEffect using the lib-jooq Moshi serializer.
 */
object DocumentOutboxSerializer {
  
  val instance = MoshiOutboxSerializer.forSealedClassWithStringId(
    sealedClass = DocumentEffect::class
  )
}
