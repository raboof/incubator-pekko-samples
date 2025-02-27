package sample.cluster.stats;


import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.GroupRouter;
import org.apache.pekko.actor.typed.javadsl.Routers;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.ClusterSingleton;
import org.apache.pekko.cluster.typed.ClusterSingletonSettings;
import org.apache.pekko.cluster.typed.SingletonActor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AppOneMaster {

  private static final ServiceKey<StatsWorker.Process> WORKER_SERVICE_KEY =
      ServiceKey.create(StatsWorker.Process.class, "Worker");

  private static class RootBehavior {
    static Behavior<Void> create() {
      return Behaviors.setup(context -> {
        Cluster cluster = Cluster.get(context.getSystem());

        ClusterSingletonSettings singletonSettings =
            ClusterSingletonSettings.create(context.getSystem())
              .withRole("compute");
        SingletonActor<StatsService.Command> serviceSingleton =
            SingletonActor.of(Behaviors.<StatsService.Command>setup(singletonContext -> {

              // The worker has a per word cache, so send the same word to the same local worker child
              GroupRouter<StatsWorker.Process> workerGroupBehavior =
                      Routers.group(WORKER_SERVICE_KEY).withConsistentHashingRouting(1, process -> process.word);

              ActorRef<StatsWorker.Process> workersRouter =
                  singletonContext.spawn(workerGroupBehavior, "WorkersRouter");
              return StatsService.create(workersRouter);
            }),
            "StatsService")
                .withStopMessage(StatsService.Stop.INSTANCE)
            .withSettings(singletonSettings);
        ActorRef<StatsService.Command> serviceProxy =
            ClusterSingleton.get(context.getSystem()).init(serviceSingleton);

        if (cluster.selfMember().hasRole("compute")) {
          // on every compute node N local workers, which a cluster singleton stats service delegates work to
          final int numberOfWorkers = context.getSystem().settings().config().getInt("stats-service.workers-per-node");
          context.getLog().info("Starting {} workers", numberOfWorkers);
          for (int i = 0; i < 4; i++) {
            ActorRef<StatsWorker.Command> worker = context.spawn(StatsWorker.create(), "StatsWorker" + i);
            context.getSystem().receptionist().tell(Receptionist.register(WORKER_SERVICE_KEY, worker.narrow()));
          }
        }
        if (cluster.selfMember().hasRole("client")) {
          context.spawn(StatsClient.create(serviceProxy.narrow()), "Client");
        }

        return Behaviors.empty();
      });
    }
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      startup("compute", 17356);
      startup("compute", 17357);
      startup("compute", 0);
      startup("client", 0);
    } else {
      if (args.length != 2)
        throw new IllegalArgumentException("Usage: role port");
      startup(args[0], Integer.parseInt(args[1]));
    }
  }

  private static void startup(String role, int port) {

    // Override the configuration of the port
    Map<String, Object> overrides = new HashMap<>();
    overrides.put("org.apache.pekko.remote.artery.canonical.port", port);
    overrides.put("org.apache.pekko.cluster.roles", Collections.singletonList(role));

    Config config = ConfigFactory.parseMap(overrides)
        .withFallback(ConfigFactory.load("stats"));

    ActorSystem<Void> system = ActorSystem.create(RootBehavior.create(), "ClusterSystem", config);

  }
}
