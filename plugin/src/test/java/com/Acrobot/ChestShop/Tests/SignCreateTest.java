package com.Acrobot.ChestShop.Tests;

import com.Acrobot.Breeze.Utils.StringUtil;
import com.Acrobot.ChestShop.Configuration.Properties;
import com.Acrobot.ChestShop.Database.Account;
import com.Acrobot.ChestShop.Events.AccountAccessEvent;
import com.Acrobot.ChestShop.Events.AccountQueryEvent;
import com.Acrobot.ChestShop.Events.ItemParseEvent;
import com.Acrobot.ChestShop.Events.ItemStringQueryEvent;
import com.Acrobot.ChestShop.Events.PreShopCreationEvent;
import com.Acrobot.ChestShop.Events.ShopCreatedEvent;
import com.Acrobot.ChestShop.Events.ShopDestroyedEvent;
import com.Acrobot.ChestShop.Events.ShopEditedEvent;
import com.Acrobot.ChestShop.Events.SignValidationEvent;
import com.Acrobot.ChestShop.Listeners.Block.SignCreate;
import com.Acrobot.ChestShop.Listeners.Modules.StockCounterModule;
import com.Acrobot.ChestShop.Listeners.Player.PlayerInteract;
import com.Acrobot.ChestShop.Listeners.PreShopCreation.PriceChecker;
import com.Acrobot.ChestShop.Listeners.SignParseListener;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import com.Acrobot.ChestShop.UUIDs.NameManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the sign-change boundary without a running server or database. */
public class SignCreateTest {
    private static final UUID ALICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private Object previousServer;
    private Field serverField;
    private boolean previousDebug;
    private boolean previousAutofill;
    private int previousPrecision;
    private String previousPlayerPattern;
    private String previousAdminName;
    private final List<Event> events = new ArrayList<>();
    private final List<String> queriedOwners = new ArrayList<>();
    private String[] storedLines = {"", "", "", ""};
    private String[] preCreationLines;
    private Consumer<PreShopCreationEvent> creationListener = event -> { };
    private Block block;
    private Block airBlock;
    private Sign sign;
    private Player player;
    private ItemStack heldItem;
    private int updates;
    private int breaks;

    @BeforeEach
    public void setUp() throws Exception {
        previousDebug = Properties.DEBUG;
        previousAutofill = Properties.ALLOW_AUTO_ITEM_FILL;
        previousPrecision = Properties.PRICE_PRECISION;
        previousPlayerPattern = Properties.VALID_PLAYERNAME_REGEXP;
        previousAdminName = Properties.ADMIN_SHOP_NAME;
        Properties.DEBUG = false;
        Properties.ALLOW_AUTO_ITEM_FILL = true;
        Properties.PRICE_PRECISION = 2;
        Properties.VALID_PLAYERNAME_REGEXP = "[A-Za-z0-9_]+";
        Properties.ADMIN_SHOP_NAME = "Admin Shop";

        PluginManager manager = proxy(PluginManager.class, (instance, method, args) -> {
            if (method.getName().equals("callEvent")) {
                dispatch((Event) args[0]);
                return null;
            }
            return defaultValue(method.getReturnType());
        });
        Server server = proxy(Server.class, (instance, method, args) -> {
            if (method.getName().equals("getPluginManager")) {
                return manager;
            }
            if (method.getName().equals("isPrimaryThread")) {
                return true;
            }
            return defaultValue(method.getReturnType());
        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, server);

        World world = proxy(World.class, (instance, method, args) -> {
            if (method.getName().equals("isChunkLoaded")) {
                return true;
            }
            if (method.getName().equals("getBlockAt")) {
                return airBlock;
            }
            if (method.getName().equals("getName")) {
                return "test";
            }
            return defaultValue(method.getReturnType());
        });
        BlockData signData = proxy(org.bukkit.block.data.type.Sign.class,
                (instance, method, args) -> defaultValue(method.getReturnType()));
        airBlock = proxy(Block.class, (instance, method, args) -> {
            if (method.getName().equals("getWorld")) {
                return world;
            }
            if (method.getName().equals("getType")) {
                return Material.AIR;
            }
            return defaultValue(method.getReturnType());
        });
        block = proxy(Block.class, (instance, method, args) -> {
            switch (method.getName()) {
                case "getWorld": return world;
                case "getType": return Material.getMaterial("OAK_SIGN") != null
                        ? Material.getMaterial("OAK_SIGN") : Material.getMaterial("SIGN");
                case "getBlockData": return signData;
                case "getState": return sign;
                case "getLocation": return new Location(world, 0, 64, 0);
                case "breakNaturally": breaks++; return true;
                default: return defaultValue(method.getReturnType());
            }
        });
        sign = proxy(Sign.class, (instance, method, args) -> {
            switch (method.getName()) {
                case "getLines": return storedLines.clone();
                case "getLine": return storedLines[(Integer) args[0]];
                case "setLine": storedLines[(Integer) args[0]] = (String) args[1]; return null;
                case "getBlock": return block;
                case "getBlockData": return signData;
                case "getLocation": return new Location(world, 0, 64, 0);
                case "update": updates++; return true;
                default: return defaultValue(method.getReturnType());
            }
        });
        PlayerInventory playerInventory = proxy(PlayerInventory.class, (instance, method, args) -> {
            if (method.getName().equals("getItemInMainHand")) {
                return heldItem;
            }
            return defaultValue(method.getReturnType());
        });
        player = proxy(Player.class, (instance, method, args) -> {
            switch (method.getName()) {
                case "getName": return "Alice";
                case "getUniqueId": return ALICE_ID;
                case "getInventory": return playerInventory;
                default: return defaultValue(method.getReturnType());
            }
        });
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (serverField != null) {
            serverField.set(null, previousServer);
        }
        Properties.DEBUG = previousDebug;
        Properties.ALLOW_AUTO_ITEM_FILL = previousAutofill;
        Properties.PRICE_PRECISION = previousPrecision;
        Properties.VALID_PLAYERNAME_REGEXP = previousPlayerPattern;
        Properties.ADMIN_SHOP_NAME = previousAdminName;
    }

    @Test
    public void plainCreationKeepsExistingNormalization() {
        SignChangeEvent event = change("Alice", "64", "B 2.5000 : S 1.00", "Stone");

        assertFalse(event.isCancelled());
        assertArrayEquals(new String[]{"Alice", "64", "B 2.5 : S 1", "Stone"}, event.getLines());
        assertEquals(1, countEvents(ShopCreatedEvent.class));
        assertEquals(0, breaks);
    }

    @Test
    public void colorsAreRenderedAfterPlainValidationAndPriceNormalization() {
        SignChangeEvent event = change("&aAlice", "&e64", "&aB 2.5000 : &cS 1.00", "&bStone");

        assertFalse(event.isCancelled());
        assertArrayEquals(new String[]{"Alice", "64", "B 2.5000 : S 1.00", "Stone"}, preCreationLines);
        assertArrayEquals(new String[]{"Alice", "64", "B 2.5 : S 1", "Stone"}, StringUtil.stripColourCodes(event.getLines()));
        assertEquals("\u00a7aAlice", event.getLine(0));
        assertEquals("\u00a7e64", event.getLine(1));
        assertTrue(event.getLine(2).startsWith("\u00a7a"));
        assertTrue(event.getLine(2).contains("\u00a7c"));
        assertEquals("\u00a7bStone", event.getLine(3));
        ShopCreatedEvent created = (ShopCreatedEvent) events.stream()
                .filter(ShopCreatedEvent.class::isInstance).findFirst().get();
        assertArrayEquals(event.getLines(), created.getSignLines());
        assertEquals("Alice", ChestShopSign.getOwner(event.getLines()));
        assertEquals(64, ChestShopSign.getQuantity(event.getLines()));
        assertEquals("B 2.5 : S 1", ChestShopSign.getPrice(event.getLines()));
        assertEquals("Stone", ChestShopSign.getItem(event.getLines()));
    }

    @Test
    public void colorOnlyOwnerIsFilledWithCreatorsCanonicalName() {
        SignChangeEvent event = change("&a", "&e64", "&bB 2", "&cStone");

        assertFalse(event.isCancelled());
        assertEquals("", preCreationLines[ChestShopSign.NAME_LINE]);
        assertEquals("\u00a7aAlice", event.getLine(ChestShopSign.NAME_LINE));
        assertEquals("Alice", ChestShopSign.getOwner(event.getLines()));
        assertEquals(1, countEvents(ShopCreatedEvent.class));
        assertEquals(0, breaks);
    }

    @Test
    public void coloredOwnerCannotBypassCreationOwnershipGuard() {
        SignChangeEvent event = change("&cBob", "&e64", "&aB 2", "&bStone");

        assertTrue(event.isCancelled());
        assertEquals(0, countEvents(PreShopCreationEvent.class));
        assertEquals(0, countEvents(ShopCreatedEvent.class));
        assertTrue(queriedOwners.contains("Bob"));
        assertEquals(1, updates);
        assertEquals(0, breaks);
    }

    @Test
    public void coloredAdminOwnerStillRequiresAdminPermission() {
        SignChangeEvent event = change("&cAdmin Shop", "64", "B 2", "Stone");

        assertTrue(event.isCancelled());
        assertEquals(0, countEvents(PreShopCreationEvent.class));
        assertEquals(0, countEvents(ShopCreatedEvent.class));
    }

    @Test
    public void coloredExistingShopCannotBeEditedWithoutAccess() {
        storedLines = new String[]{"\u00a7cBob", "\u00a7e64", "\u00a7aB 2", "\u00a7bStone"};
        String[] previous = storedLines.clone();

        SignChangeEvent event = change("&aAlice", "64", "B 3", "Stone");

        assertTrue(event.isCancelled());
        assertArrayEquals(previous, storedLines);
        assertEquals(0, countEvents(PreShopCreationEvent.class));
        assertEquals(0, countEvents(ShopDestroyedEvent.class));
        assertEquals(1, updates);
    }

    @Test
    public void unrelatedSignTextIsNotTranslated() {
        String[] lines = {"&aWelcome", "to our", "&bshop", "&rEnjoy!"};

        SignChangeEvent event = change(lines);

        assertFalse(event.isCancelled());
        assertArrayEquals(lines, event.getLines());
        assertEquals(0, countEvents(PreShopCreationEvent.class));
        assertEquals(0, countEvents(ShopCreatedEvent.class));
        assertEquals(0, breaks);
    }

    @Test
    public void cancelledCreationDoesNotRestoreRemovedOwnerOrPriceDigits() {
        creationListener = event -> {
            event.setSignLine(ChestShopSign.NAME_LINE, "");
            event.setSignLine(ChestShopSign.PRICE_LINE, "B 1");
            event.setOutcome(PreShopCreationEvent.CreationOutcome.OTHER);
        };

        SignChangeEvent event = change("&aAlice", "&e64", "&bB 100", "&cStone");

        assertFalse(event.isCancelled());
        assertArrayEquals(new String[]{"", "64", "B 1", "Stone"}, StringUtil.stripColourCodes(event.getLines()));
        assertEquals(0, countEvents(ShopCreatedEvent.class));
        assertEquals(0, breaks);
    }

    @Test
    public void unchangedColoredEditDoesNotRecreateShop() {
        storedLines = new String[]{"\u00a7aAlice", "\u00a7e64", "\u00a7bB 2", "\u00a7cStone"};

        SignChangeEvent event = change(storedLines.clone());

        assertFalse(event.isCancelled());
        assertArrayEquals(storedLines, event.getLines());
        assertEquals(0, countEvents(PreShopCreationEvent.class));
        assertEquals(0, countEvents(ShopCreatedEvent.class));
        assertEquals(0, countEvents(ShopDestroyedEvent.class));
        assertEquals(0, countEvents(ShopEditedEvent.class));
    }

    @Test
    public void equivalentAmpersandEditKeepsRenderedColorsWithoutRecreatingShop() {
        storedLines = new String[]{"\u00a7aAlice", "\u00a7e64", "\u00a7bB 2", "\u00a7cStone"};

        SignChangeEvent event = change("&aAlice", "&e64", "&bB 2", "&cStone");

        assertFalse(event.isCancelled());
        assertArrayEquals(storedLines, event.getLines());
        assertEquals(0, countEvents(PreShopCreationEvent.class));
        assertEquals(0, countEvents(ShopCreatedEvent.class));
        assertEquals(0, countEvents(ShopDestroyedEvent.class));
        assertEquals(0, countEvents(ShopEditedEvent.class));
    }

    @Test
    public void authorizedColoredEditPublishesCanonicalDisplayAndOriginalLines() {
        storedLines = new String[]{"\u00a7aAlice", "\u00a7e64", "\u00a7bB 2", "\u00a7cStone"};
        String[] previous = storedLines.clone();

        SignChangeEvent event = change("&aAlice", "&e64", "&bB 3.00", "&cStone");

        assertFalse(event.isCancelled());
        assertArrayEquals(new String[]{"Alice", "64", "B 3", "Stone"}, StringUtil.stripColourCodes(event.getLines()));
        assertEquals("\u00a7bB 3", event.getLine(ChestShopSign.PRICE_LINE));
        assertEquals(1, countEvents(PreShopCreationEvent.class));
        assertEquals(1, countEvents(ShopDestroyedEvent.class));
        assertEquals(1, countEvents(ShopEditedEvent.class));
        assertEquals(1, countEvents(ShopCreatedEvent.class));
        ShopEditedEvent edited = (ShopEditedEvent) events.stream()
                .filter(ShopEditedEvent.class::isInstance).findFirst().get();
        assertArrayEquals(previous, edited.getOldLines());
        assertArrayEquals(event.getLines(), edited.getNewLines());
        assertTrue(edited.modifiedByOwner());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\u00a7e"})
    public void removingStockCounterKeepsQuantityAndFormatting(String color) {
        storedLines = new String[]{"Alice", color + "Q 64 : C 123", "B 2", "Stone"};

        StockCounterModule.removeCounterFromQuantityLine(sign);

        assertEquals(color + "64", storedLines[ChestShopSign.QUANTITY_LINE]);
        assertEquals(64, ChestShopSign.getQuantity(sign));
        assertEquals(1, updates);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\u00a7e"})
    public void updatingStockCounterKeepsQuantityAndFormatting(String color) {
        storedLines = new String[]{"Alice", color + "Q 64 : C 123", "B 2", "Stone"};
        heldItem = new ItemStack(Material.STONE, 23);
        HashMap<Integer, ItemStack> contents = new HashMap<>();
        contents.put(0, heldItem);
        Inventory inventory = proxy(Inventory.class, (instance, method, args) -> {
            switch (method.getName()) {
                case "contains": return true;
                case "getType": return InventoryType.CHEST;
                case "all": return contents;
                default: return defaultValue(method.getReturnType());
            }
        });

        StockCounterModule.updateCounterOnQuantityLine(sign, inventory);

        assertEquals(color + "Q 64 : C 23", storedLines[ChestShopSign.QUANTITY_LINE]);
        assertEquals(64, ChestShopSign.getQuantity(sign));
        assertEquals(1, updates);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\u00a7b"})
    public void heldItemAutofillKeepsColorButRemovesBoldQuestionMarkMarker(String color) {
        storedLines = new String[]{"Alice", "64", "B 2", color + "\u00a7l?"};
        heldItem = new ItemStack(Material.STONE);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, heldItem, block, BlockFace.UP);

        PlayerInteract.onInteract(event);

        assertTrue(event.isCancelled());
        assertEquals(color + "Stone", storedLines[ChestShopSign.ITEM_LINE]);
        assertEquals("Stone", ChestShopSign.getItem(sign));
        assertEquals(1, countEvents(SignChangeEvent.class));
        assertEquals(1, countEvents(ShopCreatedEvent.class));
        assertEquals(1, updates);
    }

    private SignChangeEvent change(String... lines) {
        SignChangeEvent event = new SignChangeEvent(block, player, lines.clone());
        SignCreate.onSignChange(event);
        return event;
    }

    private long countEvents(Class<? extends Event> type) {
        return events.stream().filter(type::isInstance).count();
    }

    private void dispatch(Event event) {
        events.add(event);
        if (event instanceof SignChangeEvent) {
            SignCreate.onSignChange((SignChangeEvent) event);
        } else if (event instanceof ItemStringQueryEvent) {
            ((ItemStringQueryEvent) event).setItemString("Stone");
        } else if (event instanceof ItemParseEvent) {
            ItemParseEvent parse = (ItemParseEvent) event;
            if (parse.getItemString().equals("Stone")) {
                parse.setItem(heldItem);
            }
        } else if (event instanceof SignValidationEvent) {
            SignValidationEvent validation = (SignValidationEvent) event;
            assertArrayEquals(StringUtil.stripColourCodes(validation.getLines()), validation.getLines());
            new SignParseListener().onSignValidation(validation);
        } else if (event instanceof AccountQueryEvent) {
            AccountQueryEvent query = (AccountQueryEvent) event;
            queriedOwners.add(query.getName());
            if (query.getName().equals("Alice")) {
                query.setAccount(new Account("Alice", "Alice", ALICE_ID));
            } else if (query.getName().equals("Bob")) {
                query.setAccount(new Account("Bob", "Bob", BOB_ID));
            }
        } else if (event instanceof AccountAccessEvent) {
            NameManager.onAccountAccessCheck((AccountAccessEvent) event);
        } else if (event instanceof PreShopCreationEvent) {
            PreShopCreationEvent creation = (PreShopCreationEvent) event;
            preCreationLines = creation.getSignLines().clone();
            assertArrayEquals(StringUtil.stripColourCodes(preCreationLines), preCreationLines);
            creation.setOwnerAccount(new Account("Alice", "Alice", ALICE_ID));
            creation.setSignLine(ChestShopSign.NAME_LINE, "Alice");
            PriceChecker.onPreShopCreation(creation);
            creationListener.accept(creation);
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "equals": return instance == args[0];
                    case "hashCode": return System.identityHashCode(instance);
                    case "toString": return "Test " + type.getSimpleName();
                    default: throw new AssertionError(method);
                }
            }
            return handler.invoke(instance, method, args);
        }));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
