package tc.oc.pgm.loot;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.module.exception.ModuleLoadException;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.countdowns.CountdownContext;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.filters.query.BlockQuery;

@ListenerScope(MatchScope.RUNNING)
public class LootMatchModule implements MatchModule, Listener {

  // Map of containers to a flag indicating whether they contained objective wool when the match
  // started.
  // For this to work, containers have to be checked for wool before their contents can be changed.
  // To ensure this,
  // containers are registered in this map the first time they are opened or accessed by a hopper or
  // dispenser.
  private final Map<Inventory, Boolean> containers = new HashMap<>();

  // Containers which have cached items at match start
  // have an entry in this map representing the exact
  // layout of the items in the inventory. This is used to refill the container.
  private final Map<Inventory, Map<Integer, ItemStack>> cachedContainers = new HashMap<>();

  private final Match match;
  private final List<LootableDefinition> definitions;
  private final List<LootCountdown> lootCountdowns;
  private final CountdownContext countdownContext;

  public LootMatchModule(Match match, List<LootableDefinition> definitions) {
    this.match = match;
    this.definitions = definitions;
    this.lootCountdowns = new ArrayList<>(this.definitions.size());
    this.countdownContext = new CountdownContext(match, match.getLogger());
  }

  @Override
  public void load() throws ModuleLoadException {
    // FilterMatchModule fmm = match.needModule(FilterMatchModule.class);
    for (LootableDefinition definition : this.definitions) {
      LootCountdown countdown = new LootCountdown(match, this, definition);
      this.lootCountdowns.add(countdown);
      // looking at the code it seems like if a chest is opened that's in the defined region and
      // passes the filter the inventory of it will be cached then when that chest is set to refill
      // it will populate it using what items were in there from the start rather than rolling a
      // loot table
      /*
      if (definition.getRefillTrigger() != null) {
        fmm.onRise(
            MatchPlayer.class,
            definition.getRefillTrigger(),
            listener -> {
              // TODO figure this out
            });
       */
    }
  }

  @Override
  public void disable() {
    for (LootCountdown countdown : this.getAllCountdowns()) {
      this.countdownContext.cancel(countdown);
    }
  }

  private void registerContainer(Inventory inv, Block block) {
    // When a chest (or other block inventory) is accessed, check if it's a cache chest
    Boolean isCacheChest = this.containers.get(inv);
    if (isCacheChest == null) {
      // If we haven't seen this chest yet, check it for cache
      isCacheChest = this.isCacheChest(block);
      this.containers.put(inv, isCacheChest);
      if (isCacheChest) {
        // If it is a cached chest, take a snapshot of the items
        Map<Integer, ItemStack> contents = new HashMap<>();
        this.cachedContainers.put(inv, contents);
        for (int slot = 0; slot < inv.getSize(); ++slot) {
          ItemStack stack = inv.getItem(slot);
          if (stack != null) {
            contents.put(slot, stack.clone());
          }
        }
      }
    }
  }

  private boolean isCacheChest(Block block) {
    for (LootableDefinition definition : definitions) {
      if (definition.getCache() != null) {
        if (definition.getCache().getRegion().query(new BlockQuery(block)).isAllowed()) {
          return true;
        }
      }
    }
    return false;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onContainerPlace(BlockPlaceEvent event) {
    // Blacklist any placed container blocks
    if (event.getBlock().getState() instanceof InventoryHolder) {
      this.containers.put(((InventoryHolder) event.getBlock().getState()).getInventory(), false);
    }
  }

  /*
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onItemTransfer(InventoryMoveItemEvent event) {
    // When a hopper or dispenser transfers an item, register both blocks involved
    //TODO find out how to get the blocks from this event, casts doesn't work
    this.registerContainer(event.getSource(), (Block) event.getSource().getHolder());
    this.registerContainer(event.getDestination(), (Block) event.getDestination().getHolder());
  }
   */

  // function for adding items from <cache>, places items in the exact same pattern
  private void refillContainers() {
    // for each cachedContainer, which contains both a map of inventory and map of slotEntries
    for (Map.Entry<Inventory, Map<Integer, ItemStack>> container :
        this.cachedContainers.entrySet()) {
      Inventory inv = container.getKey();
      // for each slotEntry (which contains an index number and item) in the container
      for (Map.Entry<Integer, ItemStack> slotEntry : container.getValue().entrySet()) {
        int slot = slotEntry.getKey();
        // from cached containers
        ItemStack cachedItem = slotEntry.getValue();
        // item (or empty space) retrieved from inventory
        ItemStack retrievedItem = inv.getItem(slotEntry.getKey());

        if (retrievedItem == null) {
          retrievedItem = cachedItem.clone();
          inv.setItem(slot, retrievedItem);
          break;
        } else if (retrievedItem.isSimilar(cachedItem)
            && retrievedItem.getAmount() < cachedItem.getAmount()) {
          retrievedItem.setAmount(retrievedItem.getAmount() + 1);
          inv.setItem(slot, retrievedItem);
          break;
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onInventoryOpen(InventoryOpenEvent event) {
    Block clickedMaterial = event.getPlayer().getTargetBlock((Set<Material>) null, 5);
    if (clickedMaterial.getState() instanceof InventoryHolder) {
      this.registerContainer(event.getInventory(), clickedMaterial);
      MatchPlayer matchPlayer = match.getPlayer(event.getPlayer());
      Inventory containerInventory = event.getInventory();
      for (LootableDefinition definition : definitions) {
        for (LootCountdown countdown : getAllCountdowns()) {
          if (countdown.getLootableDefinition().equals(definition)) {
            BlockQuery query = new BlockQuery(event, clickedMaterial);
            // filter defined in <fill>
            if (definition.filter.query(query).isAllowed()) {
              if (definition.refillClear) {
                containerInventory.clear();
              }
              // add items that will always be in loot
              for (Loot loot : definition.lootableItems) {
                containerInventory.addItem(loot.getStack());
              }
              // add maybe items
              for (Maybe maybe : definition.maybeLootables) {
                addMaybeLootables(maybe, containerInventory, matchPlayer);
              }
              // add any items
              for (Any any : definition.anyLootables) {
                addAnyLootables(any, containerInventory, matchPlayer);
              }
            }
          }
        }
        // start refill interval
        // TODO fix countdowns or implement them as a separate function somewhere else
        for (LootCountdown countdown : lootCountdowns) {
          if (countdown.getLootableDefinition().equals(definition)) {
            this.countdownContext.start(countdown, definition.refillInterval);
          }
        }
      }
    }
  }

  public void addAnyLootables(Any any, Inventory containerInventory, MatchPlayer matchPlayer) {
    Random rand = match.getRandom();
    if (!any.getAnyItems().isEmpty()) {
      List<Loot> anyItems = new ArrayList<>(any.getAnyItems());
      List<Any> anyChildren = new ArrayList<>(any.getAnyChildren());
      List<Maybe> maybeChildren = new ArrayList<>(any.getMaybeChildren());
      // TODO make count range work
      for (int i = 0; i < any.getCount(); ) {
        int accumulated = anyItems.size() + anyChildren.size() + maybeChildren.size();
        int randomNumber = rand.nextInt(accumulated);
        if (randomNumber <= anyItems.size()) {
          Loot chosenItem = anyItems.get(randomNumber);
          containerInventory.addItem(chosenItem.getStack());
          if (any.isUnique()) {
            anyItems.remove(chosenItem);
          }
        } else if (randomNumber <= anyItems.size() + anyChildren.size()) {
          Any chosenAny = anyChildren.get(randomNumber - anyItems.size());
          addAnyLootables(chosenAny, containerInventory, matchPlayer);
          if (any.isUnique()) {
            anyChildren.remove(chosenAny);
          }
        } else {
          Maybe chosenMaybe =
              maybeChildren.get(randomNumber - (anyItems.size() - anyChildren.size()));
          addMaybeLootables(chosenMaybe, containerInventory, matchPlayer);
          if (any.isUnique()) {
            maybeChildren.remove(chosenMaybe);
          }
        }
        i++;
      }
    }
    if (!any.getOptions().isEmpty()) {
      List<Option> options = new ArrayList<>(any.getOptions());
      // TODO implement weight probability
      double accumulatedWeight = 0;
      for (Option option : options) {
        accumulatedWeight += option.getWeight();
      }
      for (int i = 0; i < any.getCount(); ) {
        double random = rand.nextDouble() * accumulatedWeight;
        if (options.get(i).getWeight() >= random) {
          Option chosenOption = options.get(i);
          if (chosenOption.getFilter().query(matchPlayer).isAllowed()) {
            if (!chosenOption.getLootables().isEmpty()) {
              for (Loot loot : chosenOption.getLootables()) {
                containerInventory.addItem(loot.getStack());
              }
            } else if (!chosenOption.getAnyChildren().isEmpty()) {
              for (Any anyChild : chosenOption.getAnyChildren()) {
                addAnyLootables(anyChild, containerInventory, matchPlayer);
              }
            } else {
              for (Maybe maybeChild : chosenOption.getMaybeChildren()) {
                addMaybeLootables(maybeChild, containerInventory, matchPlayer);
              }
            }
            if (any.isUnique()) {
              options.remove(chosenOption);
              // remove weight too?
              accumulatedWeight -= chosenOption.getWeight();
            }
            i++;
            // do we still count the option if it is ineligible by the filter?
          }
        }
      }
    }
  }

  public void addMaybeLootables(
      Maybe maybe, Inventory containerInventory, MatchPlayer matchPlayer) {
    if (maybe.getFilter().query(matchPlayer).isAllowed()) {
      for (Loot loot : maybe.getMaybeItems()) {
        containerInventory.addItem(loot.getStack());
      }
      for (Any any : maybe.getAnyChildren()) {
        addAnyLootables(any, containerInventory, matchPlayer);
      }
      for (Maybe maybeChild : maybe.getMaybeChildren()) {
        addMaybeLootables(maybeChild, containerInventory, matchPlayer);
      }
    }
  }

  public CountdownContext getCountdown() {
    return this.countdownContext;
  }

  public List<LootCountdown> getAllCountdowns() {
    return new ImmutableList.Builder<LootCountdown>()
        .addAll(this.countdownContext.getAll(LootCountdown.class))
        .build();
  }
}
