package com.coxstorageplanner;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.UserSync;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "CoX Storage Planner",
	description = "Plan what to put in and take out of each Chambers of Xeric storage unit, and track the doses you need for Olm",
	tags = {"cox", "chambers", "xeric", "raids", "cm", "challenge mode", "storage", "chest", "overload", "supplies", "party"}
)
public class CoxStoragePlannerPlugin extends Plugin implements CoxStoragePanel.Actions
{
	// keep in sync with build.gradle
	public static final String VERSION = "1.0.0";

	/** Ticks outside before a raid counts as left, so a relog or a reload doesn't wipe the raid's state. */
	private static final int LEAVE_TICKS = 5;
	/** Least ticks between two messages to the party. */
	private static final int SEND_INTERVAL = 5;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private CoxStoragePlannerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChestOverlay chestOverlay;

	@Inject
	private ChestItemOverlay chestItemOverlay;

	@Inject
	private Gson gson;

	@Inject
	private PartyService party;

	@Inject
	private WSClient wsClient;

	private CoxStoragePanel panel;
	private NavigationButton navigationButton;

	private volatile ChestBook chests = new ChestBook();
	/** The chest plans for solo raids, used when the setting keeps them apart. */
	private volatile ChestBook chestsSolo = new ChestBook();
	/** Key of the chest for the room the player is in, null outside a room with one. */
	private volatile String currentChest;
	/** Progress at the chest whose storage is open, for the overlays. */
	private volatile ChestProgress openChest;
	/** Rooms seen this raid: room slot to chest key, to tell the two farming rooms apart. */
	private final Map<String, String> roomKeys = new LinkedHashMap<>();
	private String lastRoomSlot;
	/** Template chunks inside the raid the room table didn't know, logged once each. */
	private final Set<Integer> unknownChunks = new HashSet<>();
	private final Map<Integer, String> itemNames = new HashMap<>();
	/** While on, left-clicking an item in a storage or the inventory adds it to the chest's lists. */
	private volatile boolean marking;
	/** The chest picked in the sidebar, marked from the inventory when no storage is open. */
	private volatile String selectedChest;
	private volatile Needs needs = Needs.defaults();
	private volatile Needs needsSolo = Needs.soloDefaults();
	/** Whether the sidebar shows and edits the solo doses outside a raid. */
	private volatile boolean needsTabSolo;

	/** Guards everything below that the Swing, client and party threads share. */
	private final Object lock = new Object();
	private final Map<Long, MemberSupplies> members = new HashMap<>();
	private Supplies inventory = Supplies.EMPTY;
	private Supplies privateStorage;
	private Supplies sharedStorage;
	/** Item name to quantity in the inventory. */
	private Map<String, Integer> inventoryItems = Collections.emptyMap();
	private Map<String, Integer> wornItems = Collections.emptyMap();
	/** The inventory when the storage was opened, so a "put in, N" line knows how many went in. */
	private Map<String, Integer> openedWith = Collections.emptyMap();

	// client thread only
	/** What's in the private storage, by item id, as last seen or worked out from deposits. */
	private final Map<Integer, Integer> privateItems = new HashMap<>();
	/** Item counts in the inventory at the last inventory change, to see what a deposit moved. */
	private final Map<Integer, Integer> lastInventory = new HashMap<>();
	/** The storage interface that is open (its group id), 0 for none. */
	private int openStorage;
	/** The storage interface that just closed, and the last tick a deposit still counts for it. */
	private int closedStorage;
	private int closedStorageUntil;
	private boolean inRaid;
	private boolean soloRaid;
	private int ticksOutside;
	private int ticksSinceSend = SEND_INTERVAL;
	private CoxStorageMessage lastSent;

	private volatile boolean inventoryDirty;
	private volatile boolean resendStatus;

	@Provides
	CoxStoragePlannerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CoxStoragePlannerConfig.class);
	}

	@Override
	protected void startUp()
	{
		needs = Needs.parse(config.needs(), Needs.defaults());
		needsSolo = Needs.parse(config.needsSolo(), Needs.soloDefaults());
		needsTabSolo = config.needsTabSolo();
		chests = ChestBook.parse(config.chests(), gson);
		chestsSolo = ChestBook.parse(config.chestsSolo(), gson);
		migrateChestKeys(chests);
		migrateChestKeys(chestsSolo);

		panel = new CoxStoragePanel(this, (label, itemId) -> itemManager.getImage(itemId).addTo(label));
		navigationButton = NavigationButton.builder()
			.tooltip("CoX Storage Planner")
			.icon(icon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		overlayManager.add(chestOverlay);
		overlayManager.add(chestItemOverlay);

		wsClient.registerMessage(CoxStorageMessage.class);

		inventoryDirty = true;
		resendStatus = true;
		refresh();
	}

	@Override
	protected void shutDown()
	{
		wsClient.unregisterMessage(CoxStorageMessage.class);
		overlayManager.remove(chestOverlay);
		overlayManager.remove(chestItemOverlay);
		clientToolbar.removeNavigation(navigationButton);
		panel = null;
		navigationButton = null;

		roomKeys.clear();
		lastRoomSlot = null;
		currentChest = null;
		openChest = null;
		itemNames.clear();
		marking = false;
		synchronized (lock)
		{
			members.clear();
			inventory = Supplies.EMPTY;
			privateStorage = null;
			sharedStorage = null;
			inventoryItems = Collections.emptyMap();
		}
		privateItems.clear();
		lastInventory.clear();
		openStorage = 0;
		inRaid = false;
		lastSent = null;
	}

	private static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// a chest with a vial on top
		g.setColor(new Color(150, 100, 40));
		g.fillRoundRect(2, 10, 20, 12, 4, 4);
		g.setColor(new Color(210, 150, 60));
		g.fillRect(2, 13, 20, 2);
		g.setColor(new Color(240, 200, 80));
		g.fillRect(10, 12, 4, 4);
		g.setColor(new Color(120, 200, 230));
		g.fillRoundRect(9, 2, 6, 8, 3, 3);
		g.dispose();
		return image;
	}

	// ---- game state ----

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		switch (event.getContainerId())
		{
			case InventoryID.INV:
				inventoryChanged(event.getItemContainer().getItems());
				inventoryDirty = true;
				break;
			case InventoryID.RAIDS_PRIVATESTORAGE:
				if (inRaid)
				{
					privateItems.clear();
					for (Item item : event.getItemContainer().getItems())
					{
						if (item.getId() > 0)
						{
							privateItems.merge(item.getId(), item.getQuantity(), Integer::sum);
						}
					}
					publishPrivateStorage();
					updateOpenChest();
				}
				break;
			case InventoryID.RAIDS_SHAREDSTORAGE:
				if (inRaid)
				{
					Supplies stored = count(event.getItemContainer().getItems());
					synchronized (lock)
					{
						sharedStorage = stored;
					}
					inventoryDirty = true;
					updateOpenChest();
				}
				break;
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.RAIDS_STORAGE_PRIVATE || event.getGroupId() == InterfaceID.RAIDS_STORAGE_SHARED)
		{
			openStorage = event.getGroupId();
			synchronized (lock)
			{
				openedWith = inventoryItems;
			}
			String key = currentChest;
			if (key != null && book().get(key) == null && book().getOrCreate(key, chestName(key)) != null)
			{
				saveChests();
			}
			updateOpenChest();
			refresh();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == openStorage)
		{
			// a deposit made as the interface closes still shows up in the inventory this tick or the next
			closedStorage = openStorage;
			closedStorageUntil = client.getTickCount() + 1;
			openStorage = 0;
			openChest = null;
		}
	}

	/** Puts "Mark" on top of an item's menu while marking, so a left click adds it to the chest's list. */
	@Subscribe
	public void onPostMenuSort(PostMenuSort event)
	{
		if (!marking)
		{
			return;
		}
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		for (MenuEntry entry : entries)
		{
			int itemId = entry.getItemId();
			if (itemId <= 0 || entry.getType() == MenuAction.RUNELITE)
			{
				continue;
			}
			int group = entry.getParam1() >> 16;
			boolean deposit;
			String key;
			if (group == InterfaceID.RAIDS_STORAGE_PRIVATE || group == InterfaceID.RAIDS_STORAGE_SHARED)
			{
				deposit = false;
				key = activeChest();
			}
			else if (group == InterfaceID.RAIDS_STORAGE_SIDE)
			{
				deposit = true;
				key = activeChest();
			}
			else if (group == InterfaceID.INVENTORY && openStorage == 0)
			{
				deposit = true;
				key = selectedChest;
			}
			else
			{
				continue;
			}
			if (key == null)
			{
				return;
			}
			String target = entry.getTarget();
			String list = deposit ? "put in" : "take out";
			client.getMenu().createMenuEntry(-1)
				.setOption("Unmark " + list)
				.setTarget(target)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> markItem(key, deposit, itemId, -1));
			client.getMenu().createMenuEntry(-1)
				.setOption("Mark " + list)
				.setTarget(target)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> markItem(key, deposit, itemId, 1));
			return;
		}
	}

	private void markItem(String key, boolean deposit, int itemId, int delta)
	{
		ChestPlan plan = book().getOrCreate(key, chestName(key));
		if (plan != null && ChestPlan.mark(deposit ? plan.getDeposit() : plan.getWithdraw(), itemName(itemId), delta))
		{
			saveChests();
			updateOpenChest();
			refresh();
		}
	}

	/**
	 * The game only sends a storage's contents while its interface is open, so a deposit made as it
	 * closes never arrives. What left the inventory went into the storage, so it's added here.
	 */
	private void inventoryChanged(Item[] items)
	{
		Map<Integer, Integer> now = new HashMap<>();
		for (Item item : items)
		{
			if (item.getId() > 0)
			{
				now.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		int storage = openStorage != 0 ? openStorage : client.getTickCount() <= closedStorageUntil ? closedStorage : 0;
		if (storage != 0 && inRaid && !lastInventory.isEmpty())
		{
			Map<Integer, Integer> moved = new HashMap<>();
			for (Map.Entry<Integer, Integer> e : lastInventory.entrySet())
			{
				int delta = e.getValue() - now.getOrDefault(e.getKey(), 0);
				if (delta != 0)
				{
					moved.put(e.getKey(), delta);
				}
			}
			for (Map.Entry<Integer, Integer> e : now.entrySet())
			{
				if (!lastInventory.containsKey(e.getKey()))
				{
					moved.put(e.getKey(), -e.getValue());
				}
			}
			if (!moved.isEmpty())
			{
				storageChanged(storage, moved);
			}
		}
		lastInventory.clear();
		lastInventory.putAll(now);
	}

	/** @param moved item id to the count that went into the storage, negative for what came out */
	private void storageChanged(int storage, Map<Integer, Integer> moved)
	{
		if (storage == InterfaceID.RAIDS_STORAGE_PRIVATE)
		{
			for (Map.Entry<Integer, Integer> e : moved.entrySet())
			{
				int quantity = privateItems.getOrDefault(e.getKey(), 0) + e.getValue();
				if (quantity > 0)
				{
					privateItems.put(e.getKey(), quantity);
				}
				else
				{
					privateItems.remove(e.getKey());
				}
			}
			publishPrivateStorage();
		}
		else
		{
			Supplies shared;
			synchronized (lock)
			{
				shared = sharedStorage;
			}
			if (shared == null)
			{
				return;
			}
			int[] doses = shared.toArray();
			for (Map.Entry<Integer, Integer> e : moved.entrySet())
			{
				Potion potion = Potion.of(e.getKey());
				if (potion != null)
				{
					doses[potion.ordinal()] += potion.doses(e.getKey()) * e.getValue();
				}
			}
			Supplies changed = Supplies.of(doses);
			synchronized (lock)
			{
				sharedStorage = changed;
			}
			inventoryDirty = true;
		}
	}

	private void publishPrivateStorage()
	{
		Supplies stored = count(privateItems);
		synchronized (lock)
		{
			privateStorage = stored;
		}
		inventoryDirty = true;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		boolean changed = false;

		if (client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1)
		{
			ticksOutside = 0;
			if (!inRaid)
			{
				inRaid = true;
				// what the client still holds is from an earlier raid until the storage is opened
				inventoryDirty = true;
			}
			boolean soloNow = client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE) <= 1;
			if (soloNow != soloRaid)
			{
				soloRaid = soloNow;
				changed = true;
			}
			trackRoom();
		}
		else if (inRaid && ++ticksOutside >= LEAVE_TICKS)
		{
			inRaid = false;
			leftRaid();
			changed = true;
		}

		if (inventoryDirty)
		{
			inventoryDirty = false;
			recompute();
			changed = true;
		}
		if (changed)
		{
			refresh();
		}
		sendToParty();
	}

	/**
	 * Works out which chest the player is at. A room is its template (RAIDS_FARMING, RAIDS_ICE_DEMON...)
	 * and the two rooms of the same template are told apart by which came first this raid, which
	 * is fixed in Challenge Mode. The floors are on different planes (first floor 3, second floor 2),
	 * so the End and Farming rooms are numbered by floor. Room slots are 32x32 tile squares of the
	 * instance; walking within a room that straddles two squares keeps the same key. Ground the table
	 * doesn't know counts as the room before it.
	 */
	private void trackRoom()
	{
		Player player = client.getLocalPlayer();
		WorldView view = client.getTopLevelWorldView();
		if (player == null || view == null || !view.isInstance())
		{
			currentChest = null;
			return;
		}
		LocalPoint local = player.getLocalLocation();
		int[][][] chunks = view.getInstanceTemplateChunks();
		int plane = view.getPlane();
		int chunkX = local.getSceneX() / 8;
		int chunkY = local.getSceneY() / 8;
		if (chunks == null || plane >= chunks.length || chunkX < 0 || chunkX >= chunks[plane].length
			|| chunkY < 0 || chunkY >= chunks[plane][chunkX].length)
		{
			currentChest = null;
			return;
		}
		int chunk = chunks[plane][chunkX][chunkY];
		String room = roomType(chunk);
		if (room == null)
		{
			if (chunk != -1 && inRaid && unknownChunks.add(chunk))
			{
				// so a room the table misses can be reported from the client log
				log.info("unknown raid room: template x={} y={} plane={} at plane {}", (chunk >> 14 & 0x3FF) * 8,
					(chunk >> 3 & 0x7FF) * 8, chunk >> 24 & 3, plane);
			}
			if (!inRaid)
			{
				currentChest = null;
			}
			// inside the raid a stretch the table doesn't know still belongs to the last room, so a
			// storage there (the one after the tightrope) goes on that room's chest
			return;
		}
		WorldPoint world = player.getWorldLocation();
		int slotX = Math.floorDiv(world.getX(), 32);
		int slotY = Math.floorDiv(world.getY(), 32);
		String slot = room + ":" + slotX + ":" + slotY + ":" + world.getPlane();
		String key = roomKeys.get(slot);
		if (key == null)
		{
			// the same room type next door is the same room across a square's edge
			if (lastRoomSlot != null && lastRoomSlot.startsWith(room + ":"))
			{
				String[] parts = lastRoomSlot.split(":");
				if (Math.abs(Integer.parseInt(parts[1]) - slotX) <= 1 && Math.abs(Integer.parseInt(parts[2]) - slotY) <= 1
					&& Integer.parseInt(parts[3]) == world.getPlane())
				{
					key = roomKeys.get(lastRoomSlot);
				}
			}
			if (key == null && FLOOR_ROOMS.contains(room) && floor(world.getPlane()) > 0)
			{
				// one per floor, so the floor is the number whatever order the plugin saw them in
				key = room + "#" + floor(world.getPlane());
			}
			if (key == null)
			{
				int n = 1;
				for (String seen : new HashSet<>(roomKeys.values()))
				{
					if (seen.startsWith(room + "#"))
					{
						n++;
					}
				}
				key = room + "#" + n;
			}
			roomKeys.put(slot, key);
		}
		lastRoomSlot = slot;
		if (!key.equals(currentChest))
		{
			currentChest = key;
			refresh();
		}
	}

	/** Room templates a Challenge Mode raid has more than one of, so the first is "End 1" not "End". */
	private static final Set<String> REPEATED_ROOMS = new HashSet<>(Arrays.asList(
		"RAIDS_END", "RAIDS_FARMING", "RAIDS_SCAVENGERS"));

	/** Rooms every floor has exactly one of, numbered by floor rather than by when they were first seen. */
	private static final Set<String> FLOOR_ROOMS = new HashSet<>(Arrays.asList("RAIDS_END", "RAIDS_FARMING"));

	/** The raid's first floor is on plane 3 and its second on plane 2; 0 for anything else (Olm, the lobby). */
	static int floor(int plane)
	{
		return plane == 3 ? 1 : plane == 2 ? 2 : 0;
	}

	/**
	 * The raid's room templates: template y and plane to room, from the API's InstanceTemplates. Every room is
	 * 96 tiles wide in the template (the API has End at 64, which misses the End room that leads down to
	 * Olm), and the second layouts of the farming and scavenger rooms are the same room.
	 */
	private static final Map<Integer, String> ROOMS = new HashMap<>();

	static
	{
		ROOMS.put(5696, "RAIDS_START");
		ROOMS.put(5152, "RAIDS_END");
		ROOMS.put(5216, "RAIDS_SCAVENGERS");
		ROOMS.put(5248, "RAIDS_SHAMANS");
		ROOMS.put(5280, "RAIDS_VASA");
		ROOMS.put(5312, "RAIDS_VANGUARDS");
		ROOMS.put(5344, "RAIDS_ICE_DEMON");
		ROOMS.put(5376, "RAIDS_THIEVING");
		ROOMS.put(5440, "RAIDS_FARMING");
		ROOMS.put(5216 + (1 << 16), "RAIDS_SCAVENGERS");
		ROOMS.put(5312 + (1 << 16), "RAIDS_MUTTADILES");
		ROOMS.put(5248 + (1 << 16), "RAIDS_MYSTICS");
		ROOMS.put(5280 + (1 << 16), "RAIDS_TEKTON");
		ROOMS.put(5344 + (1 << 16), "RAIDS_TIGHTROPE");
		ROOMS.put(5440 + (1 << 16), "RAIDS_FARMING");
		ROOMS.put(5248 + (2 << 16), "RAIDS_GUARDIANS");
		ROOMS.put(5280 + (2 << 16), "RAIDS_VESPULA");
		ROOMS.put(5344 + (2 << 16), "RAIDS_CRABS");
	}

	/** The room an instance template chunk belongs to, null outside the raid's rooms (and for the lobby). */
	static String roomType(int chunk)
	{
		if (chunk == -1)
		{
			return null;
		}
		int templateY = (chunk >> 3 & 0x7FF) * 8;
		int templateX = (chunk >> 14 & 0x3FF) * 8;
		int templatePlane = chunk >> 24 & 3;
		if (templateX < 3264 || templateX >= 3264 + 96)
		{
			return null;
		}
		return ROOMS.get((templateY & ~31) + (templatePlane << 16));
	}

	/**
	 * Moves chests saved under the old per-layout keys (RAIDS_FARMING2#1) to the room's key, dropping
	 * them if it's taken, and gives chests still carrying an old default name the current one.
	 */
	private void migrateChestKeys(ChestBook chests)
	{
		boolean changed = false;
		for (ChestPlan plan : chests.all())
		{
			if ((plan.getName().equals("End 1") || plan.getName().equals("End 2") || plan.getName().equals("End"))
				&& !plan.getName().equals(chestName(plan.getKey())))
			{
				plan.setName(chestName(plan.getKey()));
				changed = true;
			}
			String[] parts = plan.getKey().split("#", 2);
			if (parts.length == 2 && parts[0].endsWith("2"))
			{
				String key = parts[0].substring(0, parts[0].length() - 1) + "#" + parts[1];
				chests.remove(plan.getKey());
				if (chests.get(key) == null)
				{
					ChestPlan moved = chests.getOrCreate(key, chestName(key));
					moved.getDeposit().addAll(plan.getDeposit());
					moved.getWithdraw().addAll(plan.getWithdraw());
					moved.setOrdered(plan.isOrdered());
				}
				changed = true;
			}
		}
		if (changed)
		{
			saveChests();
		}
	}

	/** The Challenge Mode layout, which is fixed, so the sidebar can list chests in the order you reach them. */
	private static final List<String> RAID_ORDER = Arrays.asList(
		"RAIDS_START#1", "RAIDS_TEKTON#1", "RAIDS_CRABS#1", "RAIDS_ICE_DEMON#1", "RAIDS_FARMING#1", "RAIDS_SHAMANS#1",
		"RAIDS_END#1", "RAIDS_VANGUARDS#1", "RAIDS_THIEVING#1", "RAIDS_VESPULA#1", "RAIDS_FARMING#2",
		"RAIDS_TIGHTROPE#1", "RAIDS_GUARDIANS#1", "RAIDS_VASA#1", "RAIDS_MYSTICS#1", "RAIDS_MUTTADILES#1", "RAIDS_END#2");

	/** Where a chest comes in the raid, for sorting; rooms the layout doesn't place go after the rest, by key. */
	static int raidOrder(String key)
	{
		int index = RAID_ORDER.indexOf(key);
		return index < 0 ? RAID_ORDER.size() : index;
	}

	/** Chests in the order the raid reaches them. */
	static List<ChestPlan> inRaidOrder(List<ChestPlan> plans)
	{
		List<ChestPlan> sorted = new ArrayList<>(plans);
		sorted.sort(Comparator.comparingInt((ChestPlan plan) -> raidOrder(plan.getKey())).thenComparing(ChestPlan::getKey));
		return sorted;
	}

	/** "Farming 2", "Pre-Vanguards", "Ice Demon" from a key like RAIDS_FARMING#2. */
	static String chestName(String key)
	{
		if (key.equals("RAIDS_END#1"))
		{
			// the End rooms are named for what's next, since that's what you're packing for
			return "Pre-Vanguards";
		}
		if (key.equals("RAIDS_END#2"))
		{
			return "Pre-Olm";
		}
		String[] parts = key.substring("RAIDS_".length()).split("#");
		String[] words = parts[0].toLowerCase(Locale.ROOT).split("_");
		StringBuilder name = new StringBuilder();
		for (String word : words)
		{
			name.append(name.length() == 0 ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		if (name.toString().endsWith("2"))
		{
			name.setLength(name.length() - 1);
		}
		String number = parts.length > 1 ? parts[1] : "1";
		boolean several = REPEATED_ROOMS.contains("RAIDS_" + parts[0]) || !number.equals("1");
		return several ? name + " " + number : name.toString();
	}

	private void saveChests()
	{
		configManager.setConfiguration(CoxStoragePlannerConfig.GROUP, CoxStoragePlannerConfig.KEY_CHESTS, chests.encode(gson));
		configManager.setConfiguration(CoxStoragePlannerConfig.GROUP, CoxStoragePlannerConfig.KEY_CHESTS_SOLO, chestsSolo.encode(gson));
	}

	/** The chest plans in use: the solo ones in a solo raid (or on the Solo switch) when kept apart, else the team ones. */
	private ChestBook book()
	{
		return separateChests() && solo() ? chestsSolo : chests;
	}

	private boolean separateChests()
	{
		return config.separateSoloChests();
	}

	/** The chest a storage in front of the player belongs to: the room's, or the sidebar's when the room isn't known. */
	private String activeChest()
	{
		return currentChest != null ? currentChest : selectedChest;
	}

	/** Recomputes the progress the overlays show, on the client thread. */
	private void updateOpenChest()
	{
		ChestPlan plan = openStorage == 0 ? null : book().get(activeChest());
		if (plan == null)
		{
			openChest = null;
			return;
		}
		Map<String, Integer> items;
		Map<String, Integer> worn;
		Map<String, Integer> before;
		synchronized (lock)
		{
			items = inventoryItems;
			worn = wornItems;
			before = openedWith;
		}
		ItemContainer storage = client.getItemContainer(openStorage == InterfaceID.RAIDS_STORAGE_SHARED
			? InventoryID.RAIDS_SHAREDSTORAGE : InventoryID.RAIDS_PRIVATESTORAGE);
		openChest = new ChestProgress(plan, items, worn, before, tally(storage), config.chestPutBack());
	}

	/** Item name to quantity for a container, null for one the client hasn't seen. */
	private Map<String, Integer> tally(ItemContainer container)
	{
		if (container == null)
		{
			return null;
		}
		Map<String, Integer> items = new LinkedHashMap<>();
		for (Item item : container.getItems())
		{
			if (item.getId() > 0)
			{
				items.merge(itemName(item.getId()), item.getQuantity(), Integer::sum);
			}
		}
		return items;
	}

	/** Item name from the cache, filled on the client thread. */
	String itemName(int itemId)
	{
		String name = itemNames.get(itemId);
		if (name == null)
		{
			name = itemManager.getItemComposition(itemId).getName();
			itemNames.put(itemId, name);
		}
		return name;
	}

	ChestProgress getOpenChest()
	{
		return config.chestOverlay() || config.chestGlow() ? openChest : null;
	}

	/** Whether the numbers should be the solo ones right now. */
	private boolean solo()
	{
		return inRaid ? soloRaid : needsTabSolo;
	}

	/** The solo doses for the solo tab, or a solo raid, when they are kept apart. */
	private Needs needsFor(boolean solo)
	{
		return solo && config.separateSoloNeeds() ? needsSolo : needs;
	}

	private void leftRaid()
	{
		soloRaid = false;
		marking = false;
		privateItems.clear();
		roomKeys.clear();
		lastRoomSlot = null;
		currentChest = null;
		openChest = null;
		synchronized (lock)
		{
			privateStorage = null;
			sharedStorage = null;
		}
		inventoryDirty = true;
	}

	private static Supplies count(Item[] items)
	{
		int[] ids = new int[items.length];
		int[] quantities = new int[items.length];
		for (int i = 0; i < items.length; i++)
		{
			ids[i] = items[i].getId();
			quantities[i] = items[i].getQuantity();
		}
		return Supplies.count(ids, quantities);
	}

	private static Supplies count(Map<Integer, Integer> items)
	{
		int[] ids = new int[items.size()];
		int[] quantities = new int[items.size()];
		int i = 0;
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			ids[i] = e.getKey();
			quantities[i++] = e.getValue();
		}
		return Supplies.count(ids, quantities);
	}

	private void recompute()
	{
		ItemContainer carried = client.getItemContainer(InventoryID.INV);
		Supplies carriedSupplies = carried == null ? Supplies.EMPTY : count(carried.getItems());
		Map<String, Integer> items = tally(carried);
		Map<String, Integer> worn = tally(client.getItemContainer(InventoryID.WORN));
		if (items == null)
		{
			items = Collections.emptyMap();
		}
		if (worn == null)
		{
			worn = Collections.emptyMap();
		}
		synchronized (lock)
		{
			inventory = carriedSupplies;
			inventoryItems = items;
			wornItems = worn;
		}
		updateOpenChest();
	}

	private static String name(PartyMember member)
	{
		String name = member.getDisplayName();
		return name == null || name.isEmpty() || "<unknown>".equals(name) ? "Unknown" : name;
	}

	// ---- party ----

	private void sendToParty()
	{
		ticksSinceSend++;
		if (!party.isInParty() || party.getLocalMember() == null)
		{
			lastSent = null;
			return;
		}
		if (ticksSinceSend < SEND_INTERVAL)
		{
			return;
		}
		CoxStorageMessage status;
		synchronized (lock)
		{
			status = new CoxStorageMessage(inventory.toArray(),
				privateStorage == null ? null : privateStorage.toArray(),
				sharedStorage == null ? null : sharedStorage.toArray());
		}
		if (resendStatus || !status.sameContent(lastSent))
		{
			resendStatus = false;
			lastSent = status;
			ticksSinceSend = 0;
			party.send(status);
		}
	}

	@Subscribe
	public void onCoxStorageMessage(CoxStorageMessage message)
	{
		PartyMember local = party.getLocalMember();
		if (local != null && local.getMemberId() == message.getMemberId())
		{
			// the party echoes our own messages; we already know
			return;
		}
		synchronized (lock)
		{
			members.put(message.getMemberId(), MemberSupplies.from(message));
		}
		refresh();
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		resendStatus = true;
		refresh();
	}

	@Subscribe
	public void onUserSync(UserSync event)
	{
		resendStatus = true;
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		synchronized (lock)
		{
			members.remove(event.getMemberId());
		}
		refresh();
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		synchronized (lock)
		{
			members.clear();
		}
		resendStatus = true;
		refresh();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CoxStoragePlannerConfig.GROUP.equals(event.getGroup()))
		{
			if (CoxStoragePlannerConfig.KEY_SEPARATE_SOLO_CHESTS.equals(event.getKey()) && separateChests()
				&& chestsSolo.all().isEmpty() && !chests.all().isEmpty())
			{
				// start the solo set from the team plans rather than from nothing
				chestsSolo = chests.copy(gson);
				saveChests();
			}
			clientThread.invokeLater(this::updateOpenChest);
			refresh();
		}
	}

	// ---- sidebar actions, on the Swing thread ----

	@Override
	public void renameChest(String key, String name)
	{
		ChestPlan plan = book().get(key);
		if (plan != null && !plan.getName().equals(name.trim()))
		{
			plan.setName(name);
			saveChests();
			refresh();
		}
	}

	@Override
	public void setChestOrdered(String key, boolean ordered)
	{
		ChestPlan plan = book().get(key);
		if (plan != null && plan.isOrdered() != ordered)
		{
			plan.setOrdered(ordered);
			saveChests();
			clientThread.invokeLater(this::updateOpenChest);
			refresh();
		}
	}

	@Override
	public void setChestLines(String key, boolean deposit, String text)
	{
		ChestPlan plan = book().get(key);
		if (plan == null)
		{
			return;
		}
		List<String> lines = ChestPlan.lines(text);
		List<String> target = deposit ? plan.getDeposit() : plan.getWithdraw();
		if (!target.equals(lines))
		{
			target.clear();
			target.addAll(lines);
			saveChests();
			clientThread.invokeLater(this::updateOpenChest);
			refresh();
		}
	}

	@Override
	public void copyLoadout(String key)
	{
		clientThread.invokeLater(() ->
		{
			ChestPlan plan = book().get(key);
			if (plan == null)
			{
				return;
			}
			List<String> lines = new ArrayList<>();
			for (int containerId : new int[]{InventoryID.WORN, InventoryID.INV})
			{
				ItemContainer container = client.getItemContainer(containerId);
				if (container == null)
				{
					continue;
				}
				List<String> names = new ArrayList<>();
				List<Integer> quantities = new ArrayList<>();
				for (Item item : container.getItems())
				{
					names.add(item.getId() > 0 ? itemName(item.getId()) : null);
					quantities.add(item.getQuantity());
				}
				lines.addAll(ChestProgress.loadoutLines(names, quantities, containerId == InventoryID.WORN ? "wear " : ""));
			}
			if (lines.isEmpty())
			{
				return;
			}
			plan.getWithdraw().clear();
			plan.getWithdraw().addAll(lines);
			plan.setOrdered(true);
			if (plan.getDeposit().isEmpty())
			{
				// clear out whatever the loadout doesn't have before pulling it
				plan.getDeposit().add(ChestPlan.EVERYTHING_ELSE);
			}
			saveChests();
			updateOpenChest();
			refresh();
		});
	}

	@Override
	public void setMarking(boolean on)
	{
		if (marking != on)
		{
			marking = on;
			refresh();
		}
	}

	@Override
	public void selectChest(String key)
	{
		selectedChest = key;
	}

	@Override
	public void copyChestFromOther(String key)
	{
		ChestBook from = book() == chests ? chestsSolo : chests;
		ChestPlan source = from.get(key);
		ChestPlan plan = book().getOrCreate(key, chestName(key));
		if (source == null || plan == null)
		{
			return;
		}
		plan.setName(source.getName());
		plan.setOrdered(source.isOrdered());
		plan.getDeposit().clear();
		plan.getDeposit().addAll(source.getDeposit());
		plan.getWithdraw().clear();
		plan.getWithdraw().addAll(source.getWithdraw());
		saveChests();
		clientThread.invokeLater(this::updateOpenChest);
		refresh();
	}

	@Override
	public void deleteChest(String key)
	{
		if (book().remove(key))
		{
			saveChests();
			clientThread.invokeLater(this::updateOpenChest);
			refresh();
		}
	}

	@Override
	public void setNeed(Potion potion, int doses)
	{
		Needs plan = needsFor(needsTabSolo);
		if (plan.set(potion, doses))
		{
			configManager.setConfiguration(CoxStoragePlannerConfig.GROUP,
				plan == needsSolo ? CoxStoragePlannerConfig.KEY_NEEDS_SOLO : CoxStoragePlannerConfig.KEY_NEEDS, plan.encode());
			refresh();
		}
	}

	@Override
	public void setNeedsTab(boolean solo)
	{
		if (needsTabSolo != solo)
		{
			needsTabSolo = solo;
			configManager.setConfiguration(CoxStoragePlannerConfig.GROUP, CoxStoragePlannerConfig.KEY_NEEDS_TAB_SOLO, solo);
			refresh();
		}
	}

	// ---- drawing ----

	/** Pushes the current state to the sidebar. Safe from any thread. */
	private void refresh()
	{
		CoxStoragePanel target = panel;
		if (target == null)
		{
			return;
		}
		PanelState state = snapshot();
		SwingUtilities.invokeLater(() -> target.update(state));
	}

	private PanelState snapshot()
	{
		PanelState state = new PanelState();
		state.inParty = party.isInParty();
		state.countShared = config.countShared();
		state.countSplit = config.countSplit();
		state.units = config.needUnits();
		state.chests = book().copy(gson);
		state.currentChest = currentChest;
		state.marking = marking;
		state.putBack = config.chestPutBack();
		state.openChest = openChest;
		state.separateSoloNeeds = config.separateSoloNeeds();
		state.separateSoloChests = separateChests();
		state.trackStamina = config.trackStamina();
		state.solo = solo();
		Needs needs = needsFor(state.solo);
		for (Potion potion : Potion.values())
		{
			state.need.put(potion, state.applies(potion) ? needs.get(potion) : 0);
		}

		PartyMember local = party.getLocalMember();
		List<PartyMember> partyMembers = state.inParty ? party.getMembers() : Collections.emptyList();

		synchronized (lock)
		{
			state.carriedItems = inventoryItems;
			state.wornItems = wornItems;
			state.inventory = inventory;
			state.privateStorage = privateStorage;
			state.sharedStorage = sharedStorage;
			MemberSupplies mine = new MemberSupplies(inventory, privateStorage, sharedStorage);
			for (PartyMember member : partyMembers)
			{
				PanelState.Member view = new PanelState.Member();
				view.name = name(member);
				view.self = local != null && local.getMemberId() == member.getMemberId();
				view.status = view.self ? mine : members.get(member.getMemberId());
				state.team.add(view);
			}
		}
		return state;
	}
}
