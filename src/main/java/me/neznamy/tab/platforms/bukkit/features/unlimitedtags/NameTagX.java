package me.neznamy.tab.platforms.bukkit.features.unlimitedtags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import me.neznamy.tab.api.ArmorStand;
import me.neznamy.tab.api.ArmorStandManager;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.platforms.bukkit.nms.NMSStorage;
import me.neznamy.tab.shared.Property;
import me.neznamy.tab.shared.ProtocolVersion;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.cpu.TabFeature;
import me.neznamy.tab.shared.cpu.UsageType;
import me.neznamy.tab.shared.features.NameTag;
import me.neznamy.tab.shared.features.types.event.RespawnEventListener;
import me.neznamy.tab.shared.features.types.event.SneakEventListener;

/**
 * The core class for unlimited nametag mode
 */
public class NameTagX extends NameTag implements RespawnEventListener, SneakEventListener {

	//config options
	public boolean markerFor18x;
	public boolean disableOnBoats;
	private double spaceBetweenLines;
	public double entityTrackingRange;
	
	//list of defined dynamic lines
	public List<String> dynamicLines = Arrays.asList("belowname", "nametag", "abovename");
	
	//map of defined static lines
	public Map<String, Object> staticLines = new ConcurrentHashMap<String, Object>();

	//entity id counter to pick unique entity IDs
	private int idCounter = 2000000000;

	//player data by entityId, used for better performance
	public Map<Integer, TabPlayer> entityIdMap = new ConcurrentHashMap<Integer, TabPlayer>();
	
	//map of vehicles carrying players
	public Map<Integer, List<Entity>> vehicles = new ConcurrentHashMap<Integer, List<Entity>>();
	
	//bukkit event listener
	private EventListener eventListener;
	
	//list of players currently on boats
	public List<TabPlayer> playersOnBoats = new ArrayList<TabPlayer>();
	
	//list of worlds with unlimited nametag mode disabled
	protected List<String> disabledUnlimitedWorlds;

	/**
	 * Constructs new instance with given parameters and loads config options
	 * @param plugin - plugin instance
	 * @param nms - nms storage
	 * @param tab - tab instance
	 */
	public NameTagX(JavaPlugin plugin, NMSStorage nms, TAB tab) {
		super(tab);
		markerFor18x = tab.getConfiguration().config.getBoolean("unlimited-nametag-prefix-suffix-mode.use-marker-tag-for-1-8-x-clients", false);
		disableOnBoats = tab.getConfiguration().config.getBoolean("unlimited-nametag-prefix-suffix-mode.disable-on-boats", true);
		spaceBetweenLines = tab.getConfiguration().config.getDouble("unlimited-nametag-prefix-suffix-mode.space-between-lines", 0.22);
		entityTrackingRange = tab.getConfiguration().config.getDouble("unlimited-nametag-prefix-suffix-mode.tracking-range", 48);
		disabledUnlimitedWorlds = tab.getConfiguration().config.getStringList("disable-features-in-worlds.unlimited-nametags", Arrays.asList("disabledworld"));
		if (tab.getConfiguration().premiumconfig != null) {
			List<String> realList = tab.getConfiguration().premiumconfig.getStringList("unlimited-nametag-mode-dynamic-lines", Arrays.asList("abovename", "nametag", "belowname", "another"));
			dynamicLines = new ArrayList<String>();
			dynamicLines.addAll(realList);
			Collections.reverse(dynamicLines);
			staticLines = tab.getConfiguration().premiumconfig.getConfigurationSection("unlimited-nametag-mode-static-lines");
		}
		refreshUsedPlaceholders();
		eventListener = new EventListener(this);
		Bukkit.getPluginManager().registerEvents(eventListener, plugin);
		tab.getFeatureManager().registerFeature("nametagx-packet", new PacketListener(this, nms, tab));
	}

	@Override
	public void load() {
		super.load();
		for (TabPlayer all : tab.getPlayers()) {
			entityIdMap.put(((Player) all.getPlayer()).getEntityId(), all);
			loadArmorStands(all);
			if (isDisabledWorld(all.getWorldName()) || isDisabledWorld(disabledUnlimitedWorlds, all.getWorldName())) continue;
			loadPassengers(all);
			for (TabPlayer viewer : tab.getPlayers()) {
				spawnArmorStands(all, viewer, false);
			}
		}
		tab.getCPUManager().startRepeatingMeasuredTask(500, "refreshing nametag visibility", getFeatureType(), UsageType.REFRESHING_NAMETAG_VISIBILITY_AND_COLLISION, new Runnable() {
			public void run() {
				for (TabPlayer p : tab.getPlayers()) {
					if (!p.isLoaded() || isDisabledWorld(p.getWorldName()) || isDisabledWorld(disabledUnlimitedWorlds, p.getWorldName())) continue;
					p.getArmorStandManager().updateVisibility(false);
					if (!disableOnBoats) continue;
					boolean onBoat = ((Player)p.getPlayer()).getVehicle() != null && ((Player)p.getPlayer()).getVehicle().getType() == EntityType.BOAT;
					if (onBoat) {
						if (!playersOnBoats.contains(p)) {
							playersOnBoats.add(p);
							updateTeamData(p);
						}
					} else {
						if (playersOnBoats.contains(p)) {
							playersOnBoats.remove(p);
							updateTeamData(p);
						}
					}
				}
			}
		});
	}

	@Override
	public void unload() {
		super.unload();
		HandlerList.unregisterAll(eventListener);
		for (TabPlayer p : tab.getPlayers()) {
			if (p.getArmorStandManager() != null) p.getArmorStandManager().destroy();
		}
		entityIdMap.clear();
	}

	@Override
	public void onJoin(TabPlayer connectedPlayer) {
		super.onJoin(connectedPlayer);
		entityIdMap.put(((Player) connectedPlayer.getPlayer()).getEntityId(), connectedPlayer);
		loadArmorStands(connectedPlayer);
		if (isDisabledWorld(connectedPlayer.getWorldName()) || isDisabledWorld(disabledUnlimitedWorlds, connectedPlayer.getWorldName())) return;
		loadPassengers(connectedPlayer);
		for (TabPlayer viewer : tab.getPlayers()) {
			spawnArmorStands(connectedPlayer, viewer, true);
		}
	}
	
	/**
	 * Returns flat distance between two players ignoring Y value
	 * @param player1 - first player
	 * @param player2 - second player
	 * @return flat distance in blocks
	 */
	private double getDistance(TabPlayer player1, TabPlayer player2) {
		Location loc1 = ((Player) player1.getPlayer()).getLocation();
		Location loc2 = ((Player) player2.getPlayer()).getLocation();
		return Math.sqrt(Math.pow(loc1.getX()-loc2.getX(), 2) + Math.pow(loc1.getZ()-loc2.getZ(), 2));
	}

	/**
	 * Loads all passengers riding this player and adds them to vehicle list
	 * @param p - player to load passengers of
	 */
	private void loadPassengers(TabPlayer p) {
		if (((Entity) p.getPlayer()).getVehicle() == null) return;
		Entity vehicle = ((Entity) p.getPlayer()).getVehicle();
		vehicles.put(vehicle.getEntityId(), getPassengers(vehicle));
	}

	@Override
	public void onQuit(TabPlayer disconnectedPlayer) {
		super.onQuit(disconnectedPlayer);
		for (TabPlayer all : tab.getPlayers()) {
			if (all.getArmorStandManager() != null) all.getArmorStandManager().unregisterPlayer(disconnectedPlayer);
		}
		entityIdMap.remove(((Player) disconnectedPlayer.getPlayer()).getEntityId());
		eventListener.onQuit(disconnectedPlayer);

		tab.getCPUManager().runTaskLater(100, "processing onQuit", getFeatureType(), UsageType.PLAYER_QUIT_EVENT, () -> disconnectedPlayer.getArmorStandManager().destroy());
	}

	@Override
	public void onWorldChange(TabPlayer p, String from, String to) {
		super.onWorldChange(p, from, to);
		p.getArmorStandManager().destroy();
		loadArmorStands(p);
		loadPassengers(p);
		for (TabPlayer viewer : tab.getPlayers()) {
			spawnArmorStands(p, viewer, true);
			viewer.getArmorStandManager().destroy(p);
		}
	}
	
	/**
	 * Spawns armor stands of specified owner to viewer and, mutually if set
	 * @param owner - armor stand owner
	 * @param viewer - player to spawn armor stands for
	 * @param sendMutually - if packets should be sent both ways
	 */
	private void spawnArmorStands(TabPlayer owner, TabPlayer viewer, boolean sendMutually) {
		if (owner == viewer) return; //not displaying own armorstands
		if (((Player) viewer.getPlayer()).getWorld() != ((Player) owner.getPlayer()).getWorld()) return; //different world
		if (getDistance(viewer, owner) <= entityTrackingRange) {
			if (((Player)viewer.getPlayer()).canSee((Player)owner.getPlayer())) owner.getArmorStandManager().spawn(viewer);
			if (sendMutually && viewer.getArmorStandManager() != null && ((Player)owner.getPlayer()).canSee((Player)viewer.getPlayer())) viewer.getArmorStandManager().spawn(owner);
		}
	}

	/**
	 * Restarts and loads armor stands from config
	 * @param pl - player to load
	 */
	public void loadArmorStands(TabPlayer pl) {
		pl.setArmorStandManager(new ArmorStandManager());
		pl.setProperty("nametag", pl.getProperty("tagprefix").getCurrentRawValue() + pl.getProperty("customtagname").getCurrentRawValue() + pl.getProperty("tagsuffix").getCurrentRawValue());
		double height = -spaceBetweenLines;
		for (String line : dynamicLines) {
			Property p = pl.getProperty(line);
			if (p.getCurrentRawValue().length() == 0) continue;
			pl.getArmorStandManager().addArmorStand(line, new BukkitArmorStand(idCounter++, pl, p, height+=spaceBetweenLines, false));
		}
		for (Entry<String, Object> line : staticLines.entrySet()) {
			Property p = pl.getProperty(line.getKey());
			if (p.getCurrentRawValue().length() == 0) continue;
			pl.getArmorStandManager().addArmorStand(line.getKey(), new BukkitArmorStand(idCounter++, pl, p, Double.parseDouble(line.getValue()+""), true));
		}
		fixArmorStandHeights(pl);
	}

	/**
	 * Fixes heights of all armor stands of specified player due to dynamic lines
	 * @param p - player to fix armor stands heights for
	 */
	public void fixArmorStandHeights(TabPlayer p) {
		p.getArmorStandManager().refresh();
		double currentY = -spaceBetweenLines;
		for (ArmorStand as : p.getArmorStandManager().getArmorStands()) {
			if (as.hasStaticOffset()) continue;
			if (as.getProperty().get().length() != 0) {
				currentY += spaceBetweenLines;
				as.setOffset(currentY);
			}
		}
	}

	@Override
	public void refresh(TabPlayer refreshed, boolean force) {
		super.refresh(refreshed, force);
		if (isDisabledWorld(refreshed.getWorldName()) || isDisabledWorld(disabledUnlimitedWorlds, refreshed.getWorldName())) return;
		if (force) {
			refreshed.getArmorStandManager().destroy();
			loadArmorStands(refreshed);
			loadPassengers(refreshed);
			for (TabPlayer viewer : tab.getPlayers()) {
				if (viewer == refreshed) continue;
				if (viewer.getWorldName().equals(refreshed.getWorldName())) {
					refreshed.getArmorStandManager().spawn(viewer);
				}
			}
		} else {
			boolean fix = false;
			for (ArmorStand as : refreshed.getArmorStandManager().getArmorStands()) {
				if (as.getProperty().update()) {
					as.refresh();
					fix = true;
				}
			}
			if (fix) fixArmorStandHeights(refreshed);
		}
	}

	/**
	 * Updates raw values of properties for specified player
	 * @param p - player to update
	 */
	public void updateProperties(TabPlayer p) {
		super.updateProperties(p);
		p.loadPropertyFromConfig("customtagname", p.getName());
		p.setProperty("nametag", p.getProperty("tagprefix").getCurrentRawValue() + p.getProperty("customtagname").getCurrentRawValue() + p.getProperty("tagsuffix").getCurrentRawValue());
		for (String property : dynamicLines) {
			if (!property.equals("nametag")) p.loadPropertyFromConfig(property);
		}
		for (String property : staticLines.keySet()) {
			if (!property.equals("nametag")) p.loadPropertyFromConfig(property);
		}
	}

	/**
	 * Returns list of all passengers on specified vehicle
	 * @param vehicle - vehicle to check passengers of
	 * @return list of passengers
	 */
	@SuppressWarnings("deprecation")
	public List<Entity> getPassengers(Entity vehicle){
		if (ProtocolVersion.SERVER_VERSION.getMinorVersion() >= 11) {
			return vehicle.getPassengers();
		} else {
			if (vehicle.getPassenger() != null) {
				return Arrays.asList(vehicle.getPassenger());
			} else {
				return new ArrayList<Entity>();
			}
		}
	}

	@Override
	public void refreshUsedPlaceholders() {
		usedPlaceholders = tab.getConfiguration().config.getUsedPlaceholderIdentifiersRecursive("tagprefix", "customtagname", "tagsuffix");
		for (String line : dynamicLines) {
			usedPlaceholders.addAll(tab.getConfiguration().config.getUsedPlaceholderIdentifiersRecursive(line));
		}
		for (String line : staticLines.keySet()) {
			usedPlaceholders.addAll(tab.getConfiguration().config.getUsedPlaceholderIdentifiersRecursive(line));
		}
	}

	@Override
	public TabFeature getFeatureType() {
		return TabFeature.NAMETAGX;
	}

	@Override
	public void onRespawn(TabPlayer respawned) {
		if (isDisabledWorld(respawned.getWorldName())) return;
		respawned.getArmorStandManager().teleport();
	}

	@Override
	public void onSneak(TabPlayer player, boolean isSneaking) {
		if (isDisabledWorld(player.getWorldName())) return;
		player.getArmorStandManager().sneak(isSneaking);
	}

	@Override
	public boolean getTeamVisibility(TabPlayer p, TabPlayer viewer) {
		//only visible if player is on boat & config option is enabled and player is not invisible (1.8 bug)
		return playersOnBoats.contains(p) && !invisiblePlayers.contains(p.getName());
	}
}