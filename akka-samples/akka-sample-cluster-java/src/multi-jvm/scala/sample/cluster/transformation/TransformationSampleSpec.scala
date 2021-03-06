package sample.cluster.transformation

import language.postfixOps
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpecLike
import org.scalatest.matchers.MustMatchers

import akka.actor.Props
import akka.cluster.Cluster
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import sample.cluster.transformation.TransformationMessages._

object TransformationSampleSpecConfig extends MultiNodeConfig {
  // register the named roles (nodes) of the test
  val frontend1 = role("frontend1")
  val frontend2 = role("frontend2")
  val backend1 = role("backend1")
  val backend2 = role("backend2")
  val backend3 = role("backend3")

  // this configuration will be used for all nodes
  // note that no fixed host names and ports are used
  commonConfig(ConfigFactory.parseString("""
    akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
    akka.remote.log-remote-lifecycle-events = off
    # don't use sigar for tests, native lib not in path
    akka.cluster.metrics.collector-class = akka.cluster.JmxMetricsCollector
    """))

  nodeConfig(frontend1, frontend2)(
    ConfigFactory.parseString("akka.cluster.roles =[frontend]"))

  nodeConfig(backend1, backend2, backend3)(
    ConfigFactory.parseString("akka.cluster.roles =[backend]"))

}

// need one concrete test class per node
class TransformationSampleSpecMultiJvmNode1 extends TransformationSampleSpec
class TransformationSampleSpecMultiJvmNode2 extends TransformationSampleSpec
class TransformationSampleSpecMultiJvmNode3 extends TransformationSampleSpec
class TransformationSampleSpecMultiJvmNode4 extends TransformationSampleSpec
class TransformationSampleSpecMultiJvmNode5 extends TransformationSampleSpec

abstract class TransformationSampleSpec extends MultiNodeSpec(TransformationSampleSpecConfig)
  with WordSpecLike with MustMatchers with BeforeAndAfterAll with ImplicitSender {

  import TransformationSampleSpecConfig._

  override def initialParticipants = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  "The japi transformation sample" must {
    "illustrate how to start first frontend" in within(15 seconds) {
      runOn(frontend1) {
        // this will only run on the 'first' node
        Cluster(system) join node(frontend1).address
        val transformationFrontend = system.actorOf(Props[TransformationFrontend], name = "frontend")
        transformationFrontend ! new TransformationJob("hello")
        expectMsgPF() {
          // no backends yet, service unavailble
          case f: JobFailed =>
        }
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      testConductor.enter("frontend1-started")
    }

    "illustrate how a backend automatically registers" in within(15 seconds) {
      runOn(backend1) {
        Cluster(system) join node(frontend1).address
        system.actorOf(Props[TransformationBackend], name = "backend")
      }
      testConductor.enter("backend1-started")

      runOn(frontend1) {
        assertServiceOk()
      }

      testConductor.enter("frontend1-backend1-ok")
    }

    "illustrate how more nodes registers" in within(20 seconds) {
      runOn(frontend2) {
        Cluster(system) join node(frontend1).address
        system.actorOf(Props[TransformationFrontend], name = "frontend")
      }
      testConductor.enter("frontend2-started")
      runOn(backend2, backend3) {
        Cluster(system) join node(backend1).address
        system.actorOf(Props[TransformationBackend], name = "backend")
      }

      testConductor.enter("all-started")

      runOn(frontend1, frontend2) {
        assertServiceOk()
      }

      testConductor.enter("all-ok")

    }

  }

  def assertServiceOk(): Unit = {
    val transformationFrontend = system.actorSelection("akka://" + system.name + "/user/frontend")
    // eventually the service should be ok,
    // backends might not have registered initially
    awaitAssert {
      transformationFrontend ! new TransformationJob("hello")
      expectMsgType[TransformationResult](1.second).getText must be("HELLO")
    }
  }

}