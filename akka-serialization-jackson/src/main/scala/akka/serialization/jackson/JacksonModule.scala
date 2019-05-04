/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.serialization.jackson

import akka.annotation.InternalApi
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.core.util.VersionUtil
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.Module.SetupContext
import com.fasterxml.jackson.databind.`type`.TypeModifier
import com.fasterxml.jackson.databind.deser.Deserializers
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier
import com.fasterxml.jackson.databind.ser.Serializers

/**
 * INTERNAL API
 */
@InternalApi private[akka] object JacksonModule {

  lazy val version: Version = {
    val groupId = "com.typesafe.akka"
    val artifactId = "akka-serialization-jackson"
    val version = akka.Version.current
    VersionUtil.parseVersion(version, groupId, artifactId)
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[akka] object VersionExtractor {
  def unapply(v: Version) = Some((v.getMajorVersion, v.getMinorVersion))
}

/**
 * INTERNAL API
 */
@InternalApi private[akka] trait JacksonModule extends Module {

  private val initializers = Seq.newBuilder[SetupContext => Unit]

  def version: Version = JacksonModule.version

  def setupModule(context: SetupContext) {
    initializers.result().foreach(_.apply(context))
  }

  protected def +=(init: SetupContext => Unit): this.type = { initializers += init; this }
  protected def +=(ser: Serializers): this.type = this += (_.addSerializers(ser))
  protected def +=(deser: Deserializers): this.type = this += (_.addDeserializers(deser))
  protected def +=(typeMod: TypeModifier): this.type = this += (_.addTypeModifier(typeMod))
  protected def +=(beanSerMod: BeanSerializerModifier): this.type = this += (_.addBeanSerializerModifier(beanSerMod))

}
