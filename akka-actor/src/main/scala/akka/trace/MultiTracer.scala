/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.trace

import akka.actor.{ ActorSystem, ActorRef, ExtendedActorSystem }
import akka.util.{ ByteIterator, ByteString }
import scala.annotation.tailrec
import scala.collection.immutable

/**
 * Implementation of Tracer that delegates to multiple tracers.
 * A MultiTracer is only created when there are two or more tracers attached.
 * Trace contexts are stored as a sequence, and aligned with tracers when retrieved.
 * Efficient implementation using an array and manually inlined loops.
 */
class MultiTracer(val tracers: immutable.Seq[Tracer]) extends Tracer {
  implicit private val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

  // DO NOT MODIFY THIS ARRAY
  private val _tracers: Array[Tracer] = tracers.toArray

  private val length: Int = _tracers.length

  final def systemStarted(system: ActorSystem): Unit = {
    var i = 0
    while (i < length) {
      _tracers(i).systemStarted(system)
      i += 1
    }
  }

  final def systemShutdown(system: ActorSystem): Unit = {
    var i = 0
    while (i < length) {
      _tracers(i).systemShutdown(system)
      i += 1
    }
  }

  final def actorTold(actorRef: ActorRef, message: Any, sender: ActorRef): Unit = {
    var i = 0
    while (i < length) {
      _tracers(i).actorTold(actorRef, message, sender)
      i += 1
    }
  }

  final def actorReceived(actorRef: ActorRef, message: Any, sender: ActorRef): Unit = {
    var i = 0
    while (i < length) {
      _tracers(i).actorReceived(actorRef, message, sender)
      i += 1
    }
  }

  final def actorCompleted(actorRef: ActorRef, message: Any, sender: ActorRef): Unit = {
    var i = 0
    while (i < length) {
      _tracers(i).actorCompleted(actorRef, message, sender)
      i += 1
    }
  }

  final def remoteMessageSent(actorRef: ActorRef, message: Any, size: Int, sender: ActorRef): Unit = {
    var i = 0
    while (i < length) {
      _tracers(i).remoteMessageSent(actorRef, message, size, sender)
      i += 1
    }
  }

  final def remoteMessageReceived(actorRef: ActorRef, message: Any, size: Int, sender: ActorRef): Unit = {
    var i = 0
    while (i < length) {
      _tracers(i).remoteMessageReceived(actorRef, message, size, sender)
      i += 1
    }
  }

  final def getContext(): Any = {
    val builder = Vector.newBuilder[Any]
    var i = 0
    while (i < length) {
      builder += _tracers(i).getContext()
      i += 1
    }
    builder.result
  }

  final def setContext(context: Any): Unit = {
    val contexts: Vector[Any] = context match {
      case v: Vector[_] ⇒ v
      case _            ⇒ Vector.empty[Any]
    }
    var i = 0
    while (i < length) {
      val ctx = if (contexts.isDefinedAt(i)) contexts(i) else Tracer.emptyContext
      _tracers(i).setContext(ctx)
      i += 1
    }
  }

  final def clearContext(): Unit = {
    var i = 0
    while (i < length) {
      _tracers(i).clearContext()
      i += 1
    }
  }

  final def identifier: Int = 0

  // serialization by TracedMessageSerializer uses serializeContexts below for identifier mapping
  final def serializeContext(system: ExtendedActorSystem, context: Any): Array[Byte] = Array.empty[Byte]

  final def serializeContexts(system: ExtendedActorSystem, context: Any): Seq[(Int, Array[Byte])] = {
    val builder = Vector.newBuilder[(Int, Array[Byte])]
    val contexts: Vector[Any] = context match {
      case v: Vector[_] ⇒ v
      case _            ⇒ Vector.empty[Any]
    }
    var i = 0
    while (i < length) {
      val ctx = if (contexts.isDefinedAt(i)) contexts(i) else Tracer.emptyContext
      if (ctx != Tracer.emptyContext) {
        val id = _tracers(i).identifier
        val bytes = _tracers(i).serializeContext(system, ctx)
        builder += (id -> bytes)
      }
      i += 1
    }
    builder.result
  }

  // deserialization by TracedMessageSerializer uses deserializeContexts below for identifier mapping
  final def deserializeContext(system: ExtendedActorSystem, context: Array[Byte]): Any = Tracer.emptyContext

  final def deserializeContexts(system: ExtendedActorSystem, contexts: Map[Int, Array[Byte]]): Any = {
    val builder = Vector.newBuilder[Any]
    var i = 0
    while (i < length) {
      val id = _tracers(i).identifier
      val ctx = if (contexts.isDefinedAt(id)) _tracers(i).deserializeContext(system, contexts(id)) else Tracer.emptyContext
      builder += ctx
      i += 1
    }
    builder.result
  }
}
