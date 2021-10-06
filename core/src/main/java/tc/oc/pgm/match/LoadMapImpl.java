package tc.oc.pgm.match;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.MapContext;
import tc.oc.pgm.api.map.WorldInfo;
import tc.oc.pgm.api.map.exception.MapMissingException;
import tc.oc.pgm.util.FileUtils;
import tc.oc.pgm.util.chunk.NullChunkGenerator;
import tc.oc.pgm.util.nms.NMSHacks;

public class LoadMapImpl {
  private final MapContext map;
  private File dir;
  private AtomicLong counter;
  private final String worldName;
  private static final World.Environment[] environments = World.Environment.values();
  private static final Difficulty[] difficulties = Difficulty.values();


  public LoadMapImpl(MapContext map, String name, String worldName) {
    this.map = map;
    this.worldName = worldName;
  }

  private InitWorldStage(MapContext map, String worldName) {
    this.map = checkNotNull(map);
    this.worldName = checkNotNull(worldName);
  }

  /** Stage #2: downloads a {@link MapContext} to a local directory. */
  private File getDirectory() {
    if (dir == null) {
      dir =
          new File(
              PGM.get().getServer().getWorldContainer().getAbsoluteFile(),
              "match-" + counter.getAndIncrement());
    }
    return dir;
  }

  private LoadMapImpl createWorld() throws MapMissingException {
    FileUtils.delete(getDirectory()); // Always ensure the directory is empty first

    final File dir = getDirectory();
    if (dir.mkdirs()) {
      map.getSource().downloadTo(dir);
    } else {
      throw new MapMissingException(dir.getPath(), "Unable to mkdirs world directory");
    }
    return new ;
  }


  /** Stage #3: initializes the {@link World} on the main thread. */
  private LoadMapImpl loadWorld() throws IllegalStateException {
    final WorldInfo info = map.getWorld();
    WorldCreator creator = NMSHacks.detectWorld(worldName);
    if (creator == null) {
      creator = new WorldCreator(worldName);
    }
    final World world =
        PGM.get()
            .getServer()
            .createWorld(
                creator
                    .environment(environments[info.getEnvironment()])
                    .generator(info.hasTerrain() ? null : NullChunkGenerator.INSTANCE)
                    .seed(info.hasTerrain() ? info.getSeed() : creator.seed()));
    if (world == null) throw new IllegalStateException("Unable to load a null world");

    world.setPVP(true);
    world.setSpawnFlags(false, false);
    world.setAutoSave(false);
    world.setDifficulty(difficulties[map.getDifficulty()]);

    return new LoadMapImpl(map, dir.getName(), worldName);
  }
}
