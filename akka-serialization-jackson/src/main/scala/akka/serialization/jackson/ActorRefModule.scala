/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.serialization.jackson

import akka.actor.ActorRef
import akka.annotation.InternalApi
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonTokenId
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.deser.Deserializers
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer
import com.fasterxml.jackson.databind.ser.Serializers
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer

/**
 * INTERNAL API: Adds support for serializing and deserializing [[ActorRef]].
 */
@InternalApi private[akka] trait ActorRefModule extends ActorRefSerializerModule with ActorRefDeserializerModule

/**
 * INTERNAL API
 */
@InternalApi private[akka] object ActorRefSerializer {
  val instance: ActorRefSerializer = new ActorRefSerializer
}

/**
 * INTERNAL API
 */
@InternalApi private[akka] class ActorRefSerializer
    extends StdScalarSerializer[ActorRef](classOf[ActorRef])
    with ActorSystemAccess {
  override def serialize(value: ActorRef, jgen: JsonGenerator, provider: SerializerProvider) {
    val serializedActorRef = value.path.toSerializationFormatWithAddress(currentSystem().provider.getDefaultAddress)
    jgen.writeString(serializedActorRef)
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[akka] object ActorRefSerializerResolver extends Serializers.Base {

  private val ActorRefClass = classOf[ActorRef]

  override def findSerializer(
      config: SerializationConfig,
      javaType: JavaType,
      beanDesc: BeanDescription): JsonSerializer[_] = {
    val cls = javaType.getRawClass
    if (ActorRefClass.isAssignableFrom(cls))
      ActorRefSerializer.instance
    else
      super.findSerializer(config, javaType, beanDesc)
  }

}

/**
 * INTERNAL API: Adds serialization support for ActorRef.
 */
@InternalApi private[akka] trait ActorRefSerializerModule extends JacksonModule {
  this += { ctx =>
    ctx.addSerializers(ActorRefSerializerResolver)
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[akka] object ActorRefDeserializer {
  val instance: ActorRefDeserializer = new ActorRefDeserializer
}

/**
 * INTERNAL API
 */
@InternalApi private[akka] class ActorRefDeserializer
    extends StdScalarDeserializer[ActorRef](classOf[ActorRef])
    with ActorSystemAccess {

  def deserialize(jp: JsonParser, ctxt: DeserializationContext): ActorRef = {
    if (jp.currentTokenId() == JsonTokenId.ID_STRING) {
      val serializedActorRef = jp.getText()
      currentSystem().provider.resolveActorRef(serializedActorRef)
    } else
      ctxt.handleUnexpectedToken(handledType(), jp).asInstanceOf[ActorRef]
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[akka] object ActorRefDeserializerResolver extends Deserializers.Base {

  private val ActorRefClass = classOf[ActorRef]

  override def findBeanDeserializer(
      javaType: JavaType,
      config: DeserializationConfig,
      beanDesc: BeanDescription): JsonDeserializer[_] = {
    val cls = javaType.getRawClass
    if (ActorRefClass.isAssignableFrom(cls))
      ActorRefDeserializer.instance
    else
      super.findBeanDeserializer(javaType, config, beanDesc)
  }

}

/**
 * INTERNAL API: Adds deserialization support for ActorRef.
 */
@InternalApi private[akka] trait ActorRefDeserializerModule extends JacksonModule {
  this += ActorRefDeserializerResolver
}
