package com.kasured.akka_cluster

import java.net.InetAddress

import akka.actor.{ActorSystem, AddressFromURIString}
import akka.cluster.Cluster
import akka.kernel.Bootable
import com.typesafe.config.ConfigFactory

import scala.io.Source
import scala.util.Try

/**
 * @author Evgeny Rusak
 */
class Bootstrap extends Bootable {

  lazy val log = org.slf4j.LoggerFactory.getLogger(this.getClass.getName)

  // Load the configuration
  val config = ConfigFactory.load()

  val appConfig = config.getConfig("application")

  val actorSystemName = appConfig.getString("name")

  // Start the Akka actor system
  val system = ActorSystem(actorSystemName, config)


  // Read cluster seed nodes from the file specified in the configuration
  val seeds = Try(appConfig.getString("cluster.seedsFile")).toOption match {

      case Some(seedsFile) =>
        // Seed file was specified, read it
        log.info(s"seed nodes from file: $seedsFile")
        Source.fromFile(seedsFile).getLines().map { address =>
          AddressFromURIString.parse(s"akka.tcp://$actorSystemName@$address")
        }.toList

      case None =>
        // No seed file specified, use this node as the first seed
        log.info("no seed nodes file found, using default seeds")
        val port = appConfig.getInt("port")
        val localAddress = Try(appConfig.getString("host"))
          .toOption.getOrElse(InetAddress.getLocalHost.getHostAddress)
        List(AddressFromURIString.parse(s"akka.tcp://$actorSystemName@$localAddress:$port"))
  }


  override def startup(): Unit = {
      // Join the cluster with the specified seed nodes and block until termination
      log.info(s"Joining cluster with seed nodes: $seeds")
      Cluster.get(system).joinSeedNodes(seeds.toSeq)
  }

  override def shutdown(): Unit = {
      system.terminate()
  }

}