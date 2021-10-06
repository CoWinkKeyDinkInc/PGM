package tc.oc.pgm.rounds;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.Collection;
import java.util.logging.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jetbrains.annotations.Nullable;
import tc.oc.pgm.api.map.MapModule;
import tc.oc.pgm.api.map.MapTag;
import tc.oc.pgm.api.map.factory.MapFactory;
import tc.oc.pgm.api.map.factory.MapModuleFactory;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.module.exception.ModuleLoadException;
import tc.oc.pgm.api.party.VictoryCondition;
import tc.oc.pgm.result.VictoryConditions;
import tc.oc.pgm.util.text.TextParser;
import tc.oc.pgm.util.xml.InvalidXMLException;
import tc.oc.pgm.util.xml.XMLUtils;

public class RoundsModule implements MapModule {
  private static final Collection<MapTag> TAGS =
      ImmutableList.of(new MapTag("rounds", "Rounds", false, true));
  // Move this to rounds
  protected final boolean reload;
  protected final Duration timeBetween;
  protected final String type;
  protected final int rounds;
  protected final int maxOvertime;
  protected final Duration time;
  protected final VictoryCondition victoryCondition;
  protected final boolean showTimeLimit;

  public RoundsModule(
      boolean reload,
      Duration timeBetween,
      String type,
      int rounds,
      int maxOvertime,
      Duration time,
      VictoryCondition victoryCondition,
      boolean showTimeLimit) {
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
  public Collection<MapTag> getTags() {
    return TAGS;
  }

  @Nullable
  @Override
  public MatchModule createMatchModule(Match match) throws ModuleLoadException {
    return new RoundsMatchModule(
        match,
        reload,
        timeBetween,
        type,
        rounds,
        maxOvertime,
        time,
        victoryCondition,
        showTimeLimit);
  }

  public static class Factory implements MapModuleFactory<RoundsModule> {
    public RoundsModule parse(MapFactory factory, Logger logger, Document doc)
        throws InvalidXMLException {
      Element roundsEl = doc.getRootElement().getChild("rounds");
      if (roundsEl != null) {
        boolean reload = XMLUtils.parseBoolean(roundsEl.getAttribute("reload"), false);
        Duration timeBetween = TextParser.parseDuration(roundsEl.getTextNormalize());
        if (timeBetween == null) {
          timeBetween = Duration.ofSeconds(7);
        }
        String type = roundsEl.getAttributeValue("type", "best");
        int rounds = XMLUtils.parseNumber(roundsEl.getAttribute("round"), int.class, 5);
        int maxOvertime = XMLUtils.parseNumber(roundsEl.getAttribute("max-overtime"), int.class, 0);

        // timelimit tag
        Element timeEl = roundsEl.getChild("time");
        Duration time = null;
        VictoryCondition victoryCondition = null;
        boolean showTimeLimit = false;
        if (timeEl != null) {
          time = TextParser.parseDuration(timeEl.getTextNormalize());
          victoryCondition = VictoryConditions.parse(factory, timeEl.getAttributeValue("result"));
          showTimeLimit = XMLUtils.parseBoolean(timeEl.getAttribute("show"), true);
        }
        return new RoundsModule(
            reload, timeBetween, type, rounds, maxOvertime, time, victoryCondition, showTimeLimit);
      }
      return null;
    }
  }
}
