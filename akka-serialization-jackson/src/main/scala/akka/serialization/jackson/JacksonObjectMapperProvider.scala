/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.serialization.jackson

import scala.util.Failure
import scala.util.Success

import akka.actor.DynamicAccess
import akka.actor.ExtendedActorSystem
import akka.annotation.InternalApi
import akka.event.Logging
import akka.event.LoggingAdapter
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import com.typesafe.config.Config

/**
 * INTERNAL API
 */
@InternalApi private[akka] object JacksonObjectMapperProvider {

  // FIXME add ActorSystemSetup for programatic initialization of the ObjectMapper

  // FIXME ActorSystem Extension to be able to use same ObjectMapper outside of the serializers?

  /**
   * Creates Jackson `ObjectMapper` with sensible defaults and modules configured
   * in `akka.serialization.jackson.jackson-modules`.
   */
  def create(system: ExtendedActorSystem, jsonFactory: Option[JsonFactory]): ObjectMapper =
    create(
      system.settings.config,
      system.dynamicAccess,
      Some(Logging.getLogger(system, JacksonObjectMapperProvider.getClass)),
      jsonFactory)

  /**
   * Creates Jackson `ObjectMapper` with sensible defaults and modules configured
   * in `akka.serialization.jackson.jackson-modules`.
   */
  def create(
      config: Config,
      dynamicAccess: DynamicAccess,
      log: Option[LoggingAdapter],
      jsonFactory: Option[JsonFactory]): ObjectMapper = {
    import scala.collection.JavaConverters._

    val mapper = new ObjectMapper(jsonFactory.orNull)
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)

    val serializationFeatures = features(config, "akka.serialization.jackson.serialization-features")
    val deserializationFeatures = features(config, "akka.serialization.jackson.deserialization-features")

    serializationFeatures.foreach {
      case (enumName, value) =>
        val feature = SerializationFeature.valueOf(enumName)
        mapper.configure(feature, value)
    }

    deserializationFeatures.foreach {
      case (enumName, value) =>
        val feature = DeserializationFeature.valueOf(enumName)
        mapper.configure(feature, value)
    }

    val configuredModules = config.getStringList("akka.serialization.jackson.jackson-modules").asScala
    val modules =
      if (configuredModules.contains("*"))
        ObjectMapper.findModules(dynamicAccess.classLoader).asScala
      else
        configuredModules.flatMap { fqcn ⇒
          dynamicAccess.createInstanceFor[Module](fqcn, Nil) match {
            case Success(m) ⇒ Some(m)
            case Failure(e) ⇒
              log.foreach(
                _.error(
                  e,
                  s"Could not load configured Jackson module [$fqcn], " +
                  "please verify classpath dependencies or amend the configuration " +
                  "[akka.serialization.jackson-modules]. Continuing without this module."))
              None
          }
        }

    modules.foreach { module ⇒
      if (module.isInstanceOf[ParameterNamesModule])
        // ParameterNamesModule needs a special case for the constructor to ensure that single-parameter
        // constructors are handled the same way as constructors with multiple parameters.
        // See https://github.com/FasterXML/jackson-module-parameter-names#delegating-creator
        mapper.registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
      else mapper.registerModule(module)
      log.foreach(_.debug("Registered Jackson module [{}]", module.getClass.getName))
    }

    mapper
  }

  private def features(config: Config, section: String): Map[String, Boolean] = {
    import scala.collection.JavaConverters._
    val cfg = config.getConfig(section)
    cfg.root
      .keySet()
      .asScala
      .map { key =>
        key -> cfg.getBoolean(key)
      }
      .toMap
  }
}
