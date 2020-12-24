package tc.oc.pgm.modes;

import static net.kyori.adventure.text.Component.text;

import java.time.Duration;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.material.MaterialData;

public class Mode {

  private final @Nullable String id;
  private final MaterialData material;
  private final Duration after;
  private final @Nullable String name;
  private final Component componentName;
  private final Duration showBefore;

  public Mode(
      final String id, final MaterialData material, final Duration after, Duration showBefore) {
    this(id, material, after, null, showBefore);
  }

  public Mode(
      final String id,
      final MaterialData material,
      final Duration after,
      final @Nullable String name,
      Duration showBefore) {
    this.id = id;
    this.material = material;
    this.after = after;
    this.name = name;
    this.componentName =
        text(name != null ? name : getPreformattedMaterialName(), NamedTextColor.RED);
    this.showBefore = showBefore;
  }

  public String getId() {
    return this.id;
  }

  public MaterialData getMaterialData() {
    return this.material;
  }

  public Component getComponentName() {
    return componentName;
  }

  public String getPreformattedMaterialName() {
    return ModeUtils.formatMaterial(this.material);
  }

  public Duration getAfter() {
    return this.after;
  }

  public Duration getShowBefore() {
    return this.showBefore;
  }

  public @Nullable String getName() {
    return this.name;
  }
}
