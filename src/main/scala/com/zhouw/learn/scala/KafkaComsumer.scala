package com.zhouw.learn.scala

import java.util.Properties

object KafkaComsumer {

  def main(args: Array[String]): Unit = {
    val properties = new Properties()
    properties.put("bootstrap.servers", "192.168.31.230:9093,192.168.31.230:9094,192.168.31.230:9095")
    properties.put("group.id", "group-2")
    properties.put("enable.auto.commit", "true")
    properties.put("auto.commit.interval.ms", "1000")
    properties.put("auto.offset.reset", "earliest")
    properties.put("session.timeout.ms", "30000")
    properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")

    import java.util

    import org.apache.kafka.clients.consumer.KafkaConsumer

    val kafkaConsumer = new KafkaConsumer[String, String](properties)
    kafkaConsumer.subscribe(util.Arrays.asList("ITOA_TO_DM_REQ"))
    while ( {
      true
    }) {
      val records = kafkaConsumer.poll(100)
      import scala.collection.JavaConversions._
      for (record <- records) {
        println(record.offset())
        println(record.value())
      }
    }
  }

}
