package tc.oc.pgm.rounds;

import java.time.Duration;
import java.util.Collection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.match.event.MatchEvent;
import tc.oc.pgm.api.match.event.MatchStartEvent;
import tc.oc.pgm.api.party.VictoryCondition;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.events.PlayerPartyChangeEvent;
import tc.oc.pgm.match.MatchFactoryImpl;
import tc.oc.pgm.timelimit.TimeLimit;
import tc.oc.pgm.timelimit.TimeLimitMatchModule;

@ListenerScope(MatchScope.RUNNING)
public class RoundsMatchModule implements MatchModule, Listener {
  // this does the things it needs to do
  private final Match match;
  private final boolean reload;
  private final Duration timeBetween;
  private final String type;
  private final int rounds;
  private final int maxOvertime;
  private final Duration time;
  private final VictoryCondition victoryCondition;
  private final boolean showTimeLimit;
  private Collection<MatchPlayer> players;

  public RoundsMatchModule(
      Match match,
      boolean reload,
      Duration timeBetween,
      String type,
      int rounds,
      int maxOvertime,
      Duration time,
      VictoryCondition victoryCondition,
      boolean showTimeLimit) {
    this.match = match;
    this.reload = reload;
    this.timeBetween = timeBetween;
    this.type = type;
    this.rounds = rounds;
    this.maxOvertime = maxOvertime;
    this.time = time;
    this.victoryCondition = victoryCondition;
    this.showTimeLimit = showTimeLimit;
  }

  @Override
  public void load() {}

  // Players that join are added to a list to help reset round
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void addPlayer(final PlayerPartyChangeEvent event) {
    players.add(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void roundStart(final MatchStartEvent event) {
    if (reload) {
      // reload world
      // HOLY FUCK CAN THIS PIECE OF SHIT SHOW HOW THE FUCK TO DO ANYTHING
      // HOLY SHIT WE DID IT

      PGM.get().getMapLibrary().loadExistingMap(match.getId()).thenApply(MatchFactoryImpl.DownloadMapStage::new);
    }
    TimeLimitMatchModule timeLimitMatchModule = match.getModule(TimeLimitMatchModule.class);
    if (timeLimitMatchModule != null) {
      timeLimitMatchModule.setTimeLimit(
          new TimeLimit(null, time, null, null, null, victoryCondition, showTimeLimit));
    }
    // allow players to join a blitz match in pre round
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void roundFinish(final MatchEvent event) {
    // find where the winner thing is, bring it here before it tries to end the match
    // title screen saying round score and so on
  }
}
