
package net.joastbg.testing

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise 
import scala.concurrent.Future
import scala.concurrent.duration._

import com.thenewmotion.akka.rabbitmq._

import concurrent.duration._

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
//import akka.actor._

class PublishSubscriber(pushActor: ActorRef)(implicit ec: ExecutionContext) {
  implicit val system = ActorSystem("RMQ")
  val factory = new ConnectionFactory()
  val connection = system.actorOf(ConnectionActor.props(factory), "rabbitmq")
  val exchange = "direct_logs"
    
  def setupPublisher(channel: Channel, self: ActorRef) {
    val queue = channel.queueDeclare().getQueue
    channel.queueBind(queue, exchange, "search")
  }
  connection ! CreateChannel(ChannelActor.props(setupPublisher), Some("publisher"))

  def setupSubscriber(channel: Channel, self: ActorRef) {
    val queue = channel.queueDeclare().getQueue
    channel.queueBind(queue, exchange, "search")
    val consumer = new DefaultConsumer(channel) {
      override def handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: Array[Byte]) {
        println("received: " + fromBytes(body))
        pushActor ! Push("kalle", fromBytes(body))        
      }
    }
    channel.basicConsume(queue, true, consumer)
  }
  connection ! CreateChannel(ChannelActor.props(setupSubscriber), Some("subscriber"))

  Future {
    def loop(n: Long) {
      val publisher = system.actorOf(ChannelActor.props(setupPublisher))

      def publish(channel: Channel) {
        channel.basicPublish(exchange, "", null, toBytes(n))
      }
      publisher ! ChannelMessage(publish, dropIfNoChannel = false)

      Thread.sleep(1000)
      loop(n + 1)
    }
    loop(0)
  }

  def fromBytes(x: Array[Byte]) = new String(x, "UTF-8")
  def toBytes(x: Long) = x.toString.getBytes("UTF-8")
}
