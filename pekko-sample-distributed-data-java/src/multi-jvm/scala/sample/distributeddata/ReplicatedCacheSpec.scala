package sample.distributeddata

import java.util.Optional
import scala.concurrent.duration._
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.adapter._
import org.apache.pekko.cluster.ddata.Replicator
import org.apache.pekko.cluster.ddata.typed.scaladsl.DistributedData
import org.apache.pekko.cluster.ddata.typed.scaladsl.Replicator.{ GetReplicaCount, ReplicaCount }
import org.apache.pekko.cluster.typed.{ Cluster, Join }
import org.apache.pekko.remote.testconductor.RoleName
import org.apache.pekko.remote.testkit.MultiNodeConfig
import org.apache.pekko.remote.testkit.MultiNodeSpec
import com.typesafe.config.ConfigFactory

object ReplicatedCacheSpec extends MultiNodeConfig {
  val node1 = role("node-1")
  val node2 = role("node-2")
  val node3 = role("node-3")

  commonConfig(ConfigFactory.parseString("""
    pekko.loglevel = INFO
    pekko.actor.provider = "cluster"
    pekko.log-dead-letters-during-shutdown = off
    """))

}

class ReplicatedCacheSpecMultiJvmNode1 extends ReplicatedCacheSpec
class ReplicatedCacheSpecMultiJvmNode2 extends ReplicatedCacheSpec
class ReplicatedCacheSpecMultiJvmNode3 extends ReplicatedCacheSpec

class ReplicatedCacheSpec extends MultiNodeSpec(ReplicatedCacheSpec) with STMultiNodeSpec {
  import ReplicatedCacheSpec._
  import ReplicatedCache._

  override def initialParticipants = roles.size

  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped
  val cluster = Cluster(typedSystem)
  val replicatedCache = system.spawnAnonymous(ReplicatedCache.create())

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      cluster.manager ! Join(node(to).address)
    }
    enterBarrier(from.name + "-joined")
  }

  "Demo of a replicated cache" must {
    "join cluster" in within(20.seconds) {
      join(node1, node1)
      join(node2, node1)
      join(node3, node1)

      awaitAssert {
        val probe = TestProbe[ReplicaCount]()
        DistributedData(typedSystem).replicator ! GetReplicaCount(probe.ref)
        probe.expectMessage(Replicator.ReplicaCount(roles.size))
      }
      enterBarrier("after-1")
    }

    "replicate cached entry" in within(10.seconds) {
      runOn(node1) {
        replicatedCache ! new PutInCache("key1", "A")
      }

      awaitAssert {
        val probe = TestProbe[Cached]()
        replicatedCache.tell(new GetFromCache("key1", probe.ref))
        probe.expectMessage(new Cached("key1", Optional.of("A")))
      }

      enterBarrier("after-2")
    }

    "replicate many cached entries" in within(10.seconds) {
      runOn(node1) {
        for (i <- 100 to 200)
          replicatedCache ! new PutInCache("key" + i, "entry-" + i)
      }

      awaitAssert {
        val probe = TestProbe[Cached]()
        for (i <- 100 to 200) {
          replicatedCache.tell(new GetFromCache("key" + i, probe.ref))
          probe.expectMessage(new Cached("key" + i, Optional.of("entry-" + i)))
        }
      }

      enterBarrier("after-3")
    }

    "replicate evicted entry" in within(15.seconds) {
      runOn(node1) {
        replicatedCache ! new PutInCache("key2", "B")
      }

      awaitAssert {
        val probe = TestProbe[Cached]()
        replicatedCache.tell(new GetFromCache("key2", probe.ref))
        probe.expectMessage(new Cached("key2", Optional.of("B")))
      }
      enterBarrier("key2-replicated")

      runOn(node3) {
        replicatedCache ! new Evict("key2")
      }

      awaitAssert {
        val probe = TestProbe[Cached]()
        replicatedCache.tell(new GetFromCache("key2", probe.ref))
        probe.expectMessage(new Cached("key2", Optional.empty()))
      }

      enterBarrier("after-4")
    }

    "replicate updated cached entry" in within(10.seconds) {
      runOn(node2) {
        replicatedCache ! new PutInCache("key1", "A2")
        replicatedCache ! new PutInCache("key1", "A3")
      }

      awaitAssert {
        val probe = TestProbe[Cached]()
        replicatedCache.tell(new GetFromCache("key1", probe.ref))
        probe.expectMessage(new Cached("key1", Optional.of("A3")))
      }

      enterBarrier("after-5")
    }

  }

}

