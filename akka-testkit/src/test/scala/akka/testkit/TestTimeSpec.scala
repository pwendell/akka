package akka.testkit

import org.scalatest.matchers.MustMatchers
import org.scalatest.{ BeforeAndAfterEach, WordSpec }
import scala.concurrent.duration._
import com.typesafe.config.Config
import org.scalatest.exceptions.TestFailedException

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class TestTimeSpec extends AkkaSpec(Map("akka.test.timefactor" -> 2.0)) with BeforeAndAfterEach {

  "A TestKit" must {

    "correctly dilate times" taggedAs TimingTest in {
      1.second.dilated.toNanos must be(1000000000L * testKitSettings.TestTimeFactor)

      val probe = TestProbe()
      val now = System.nanoTime
      intercept[AssertionError] { probe.awaitCond(false, Duration("1 second")) }
      val diff = System.nanoTime - now
      val target = (1000000000l * testKitSettings.TestTimeFactor).toLong
      diff must be > (target - 500000000l)
      diff must be < (target + 500000000l)
    }

    "awaitAssert must throw correctly" in {
      awaitAssert("foo" must be("foo"))
      within(300.millis, 2.seconds) {
        intercept[TestFailedException] {
          awaitAssert("foo" must be("bar"), 500.millis, 300.millis)
        }
      }
    }

  }

}
