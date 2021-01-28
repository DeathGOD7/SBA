package pronze.hypixelify.inventories;

import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.events.*;
import org.screamingsandals.bedwars.api.game.ItemSpawnerType;
import org.screamingsandals.bedwars.api.upgrades.Upgrade;
import org.screamingsandals.bedwars.api.upgrades.UpgradeRegistry;
import org.screamingsandals.bedwars.api.upgrades.UpgradeStorage;
import org.screamingsandals.bedwars.commands.DumpCommand;
import org.screamingsandals.bedwars.game.CurrentTeam;
import org.screamingsandals.bedwars.game.Game;
import org.screamingsandals.bedwars.game.GamePlayer;
import org.screamingsandals.bedwars.game.GameStore;
import org.screamingsandals.bedwars.lib.configurate.ConfigurationNode;
import org.screamingsandals.bedwars.lib.debug.Debug;
import org.screamingsandals.bedwars.lib.material.Item;
import org.screamingsandals.bedwars.lib.material.builder.ItemFactory;
import org.screamingsandals.bedwars.lib.player.PlayerMapper;
import org.screamingsandals.bedwars.lib.sgui.SimpleInventoriesCore;
import org.screamingsandals.bedwars.lib.sgui.events.ItemRenderEvent;
import org.screamingsandals.bedwars.lib.sgui.events.OnTradeEvent;
import org.screamingsandals.bedwars.lib.sgui.events.PreClickEvent;
import org.screamingsandals.bedwars.lib.sgui.inventory.GenericItemInfo;
import org.screamingsandals.bedwars.lib.sgui.inventory.Include;
import org.screamingsandals.bedwars.lib.sgui.inventory.InventorySet;
import org.screamingsandals.bedwars.lib.sgui.inventory.Price;
import org.screamingsandals.bedwars.lib.utils.ConfigurateUtils;
import org.screamingsandals.bedwars.special.listener.PermaItemListener;
import org.screamingsandals.bedwars.utils.Debugger;
import org.screamingsandals.bedwars.utils.Sounds;
import pronze.hypixelify.SBAHypixelify;
import pronze.hypixelify.listener.TeamUpgradeListener;
import pronze.hypixelify.utils.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.screamingsandals.bedwars.lib.lang.I.i18nc;
import static org.screamingsandals.bedwars.lib.lang.I18n.i18nonly;

public class CustomShop implements Listener {
    private final Map<String, InventorySet> shopMap = new HashMap<>();

    public CustomShop() {
        SBAHypixelify.getInstance().registerListener(this);

        loadNewShop("default", null, true);
    }

    public static File normalizeShopFile(String name) {
        if (name.split("\\.").length > 1) {
            return new File(Main.getInstance().getDataFolder(), name);
        }

        var fileg = new File(Main.getInstance().getDataFolder(), name + ".groovy");
        if (fileg.exists()) {
            return fileg;
        }
        return new File(Main.getInstance().getDataFolder(), name + ".yml");
    }

    private static String getNameOrCustomNameOfItem(Item item) {
        try {
            if (item.getDisplayName() != null) {
                return item.getDisplayName();
            }
            if (item.getLocalizedName() != null) {
                return item.getLocalizedName();
            }
        } catch (Throwable ignored) {}

        var normalItemName = item.getMaterial().getPlatformName().replace("_", " ").toLowerCase();
        var sArray = normalItemName.split(" ");
        var stringBuilder = new StringBuilder();

        for (var s : sArray) {
            stringBuilder.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(" ");
        }
        return stringBuilder.toString().trim();
    }

    public void show(Player player, GameStore store) {
        try {
            boolean parent = true;
            String fileName = null;
            if (store != null) {
                parent = store.getUseParent();
                fileName = store.getShopFile();
            }
            if (fileName != null) {
                var file = normalizeShopFile(fileName);
                var name = (parent ? "+" : "-") + file.getAbsolutePath();
                if (!shopMap.containsKey(name)) {
                    loadNewShop(name, file, parent);
                }
                PlayerMapper.wrapPlayer(player).openInventory(shopMap.get(name));
            } else {
                PlayerMapper.wrapPlayer(player).openInventory(shopMap.get("default"));
            }
        } catch (Throwable ignored) {
            player.sendMessage(i18nonly("prefix") + " Your shop.yml/shop.groovy is invalid! Check it out or contact us on Discord.");
        }
    }

    public void onGeneratingItem(ItemRenderEvent event) {
        var itemInfo = event.getItem();
        var item = itemInfo.getStack();
        var player = event.getPlayer().as(Player.class);
        var game = Main.getPlayerGameProfile(player).getGame();
        var prices = itemInfo.getOriginal().getPrices();
        if (!prices.isEmpty()) {
            var priceObject = prices.get(0);
            var price = priceObject.getAmount();
            var type = Main.getSpawnerType(priceObject.getCurrency().toLowerCase());
            if (type == null) {
                return;
            }

            var enabled = itemInfo.getFirstPropertyByName("generateLore")
                    .map(property -> property.getPropertyData().getBoolean())
                    .orElseGet(() -> Main.getConfigurator().config.getBoolean("lore.generate-automatically", true));

            if (enabled) {
                var loreText = itemInfo.getFirstPropertyByName("generatedLoreText")
                        .map(property -> property.getPropertyData().childrenList().stream().map(ConfigurationNode::getString))
                        .orElseGet(() -> Main.getConfigurator().config.getStringList("lore.text").stream())
                        .map(s -> s
                                .replaceAll("%price%", Integer.toString(price))
                                .replaceAll("%resource%", type.getItemName())
                                .replaceAll("%amount%", Integer.toString(item.getAmount())))
                        .collect(Collectors.toList());

                item.getLore().addAll(loreText);
            }
        }

        itemInfo.getProperties().forEach(property -> {
            if (property.hasName()) {
                var converted = ConfigurateUtils.raw(property.getPropertyData());
                if (!(converted instanceof Map)) {
                    converted = DumpCommand.nullValuesAllowingMap("value", converted);
                }

                //noinspection unchecked
                var applyEvent = new BedwarsApplyPropertyToDisplayedItem(game,
                        player, item.as(ItemStack.class), property.getPropertyName(), (Map<String, Object>) converted);
                Main.getInstance().getServer().getPluginManager().callEvent(applyEvent);

                event.setStack(ItemFactory.build(applyEvent.getStack()).orElse(item));
            }
        });
    }

    public void onPreAction(PreClickEvent event) {
        if (event.isCancelled()) {
            return;
        }

        var player = event.getPlayer().as(Player.class);
        if (!Main.isPlayerInGame(player)) {
            event.setCancelled(true);
        }

        if (Main.getPlayerGameProfile(player).isSpectator) {
            event.setCancelled(true);
        }
    }

    public void onShopTransaction(OnTradeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (event.getItem().getFirstPropertyByName("upgrade").isPresent()) {
            handleUpgrade(event);
        } else {
            handleBuy(event);
        }
    }

    @EventHandler
    public void onApplyPropertyToBoughtItem(BedwarsApplyPropertyToItem event) {
        if (event.getPropertyName().equalsIgnoreCase("applycolorbyteam")
                || event.getPropertyName().equalsIgnoreCase("transform::applycolorbyteam")) {
            Player player = event.getPlayer();
            CurrentTeam team = (CurrentTeam) event.getGame().getTeamOfPlayer(player);

            if (Main.getConfigurator().config.getBoolean("automatic-coloring-in-shop")) {
                event.setStack(Main.applyColor(team.teamInfo.color, event.getStack()));
            }
        }
    }


 // public List<String> getLore(GenericItemInfo itemInfo, Price priceObject, ItemSpawnerType type) {
 //     var price = priceObject.getAmount();
 //     if (type == null) {
 //         return null;
 //     }

 //     var enabled = itemInfo.getFirstPropertyByName("generateLore")
 //             .map(property -> property.getPropertyData().getBoolean())
 //             .orElseGet(() -> Main.getConfigurator().config.getBoolean("lore.generate-automatically", true));

 //     if (enabled) {
 //         var loreText = itemInfo.getFirstPropertyByName("generatedLoreText")
 //                 .map(property -> property.getPropertyData().childrenList().stream().map(ConfigurationNode::getString))
 //                 .orElseGet(() -> Main.getConfigurator().config.getStringList("lore.text").stream())
 //                 .map(s -> s
 //                         .replaceAll("%price%", Integer.toString(price))
 //                         .replaceAll("%resource%", type.getItemName())
 //                         .replaceAll("%amount%", Integer.toString(item.getAmount())))
 //                 .collect(Collectors.toList());
 //         return loreText;
 //     }
 //     return null;
 // }


    @SneakyThrows
    private void loadDefault(InventorySet inventorySet) {
        inventorySet.getMainSubInventory().dropContents();
        inventorySet.getMainSubInventory().getWaitingQueue()
                .add(Include.of((new File(SBAHypixelify.getConfigurator()
                        .dataFolder.toString() +"/shops", "shop.yml"))));
        inventorySet.getMainSubInventory().process();
    }

    private void loadNewShop(String name, File file, boolean useParent) {
        final var shopInventory = SimpleInventoriesCore.builder()
                .genericShop(true)
                .genericShopPriceTypeRequired(true)
                .animationsEnabled(true)
                .categoryOptions(localOptionsBuilder ->
                        localOptionsBuilder
                                .backItem(Main.getConfigurator().readDefinedItem("shopback", "BARRIER"), itemBuilder ->
                                        itemBuilder.name(i18nonly("shop_back"))
                                )
                                .pageBackItem(Main.getConfigurator().readDefinedItem("pageback", "ARROW"), itemBuilder ->
                                        itemBuilder.name(i18nonly("page_back"))
                                )
                                .pageForwardItem(Main.getConfigurator().readDefinedItem("pageforward", "BARRIER"), itemBuilder ->
                                        itemBuilder.name(i18nonly("page_forward"))
                                )
                                .cosmeticItem(Main.getConfigurator().readDefinedItem("shopcosmetic", "AIR"))
                                .rows(Main.getConfigurator().config.getInt("shop.rows", 4))
                                .renderActualRows(Main.getConfigurator().config.getInt("shop.render-actual-rows", 6))
                                .renderOffset(Main.getConfigurator().config.getInt("shop.render-offset", 9))
                                .renderHeaderStart(Main.getConfigurator().config.getInt("shop.render-header-start", 0))
                                .renderFooterStart(Main.getConfigurator().config.getInt("shop.render-footer-start", 45))
                                .itemsOnRow(Main.getConfigurator().config.getInt("shop.items-on-row", 9))
                                .showPageNumber(Main.getConfigurator().config.getBoolean("shop.show-page-numbers", true))
                                .inventoryType(Main.getConfigurator().config.getString("shop.inventory-type", "CHEST"))
                                .prefix(i18nonly("item_shop_name", "[BW] Shop"))
                )

                // old shop format compatibility
                .variableToProperty("upgrade", "upgrade")
                .variableToProperty("generate-lore", "generateLore")
                .variableToProperty("generated-lore-text", "generatedLoreText")
                .variableToProperty("currency-changer", "currencyChanger")

                .render(this::onGeneratingItem)
                .preClick(this::onPreAction)
                .buy(this::onShopTransaction)
                .define("team", (key, player, playerItemInfo, arguments) -> {
                    GamePlayer gPlayer = Main.getPlayerGameProfile(player.as(Player.class));
                    CurrentTeam team = gPlayer.getGame().getPlayerTeam(gPlayer);
                    if (arguments.length > 0) {
                        String fa = arguments[0];
                        switch (fa) {
                            case "color":
                                return team.teamInfo.color.name();
                            case "chatcolor":
                                return team.teamInfo.color.chatColor.toString();
                            case "maxplayers":
                                return Integer.toString(team.teamInfo.maxPlayers);
                            case "players":
                                return Integer.toString(team.players.size());
                            case "hasBed":
                                return Boolean.toString(team.isBed);
                        }
                    }
                    return team.getName();
                })
                .define("spawner", (key, player, playerItemInfo, arguments) -> {
                    GamePlayer gPlayer = Main.getPlayerGameProfile(player.as(Player.class));
                    Game game = gPlayer.getGame();
                    if (arguments.length > 2) {
                        String upgradeBy = arguments[0];
                        String upgrade = arguments[1];
                        UpgradeStorage upgradeStorage = UpgradeRegistry.getUpgrade("spawner");
                        if (upgradeStorage == null) {
                            return null;
                        }
                        List<Upgrade> upgrades = null;
                        switch (upgradeBy) {
                            case "name":
                                upgrades = upgradeStorage.findItemSpawnerUpgrades(game, upgrade);
                                break;
                            case "team":
                                upgrades = upgradeStorage.findItemSpawnerUpgrades(game, game.getPlayerTeam(gPlayer));
                                break;
                        }

                        if (upgrades != null && !upgrades.isEmpty()) {
                            String what = "level";
                            if (arguments.length > 3) {
                                what = arguments[2];
                            }
                            double heighest = Double.MIN_VALUE;
                            switch (what) {
                                case "level":
                                    for (Upgrade upgrad : upgrades) {
                                        if (upgrad.getLevel() > heighest) {
                                            heighest = upgrad.getLevel();
                                        }
                                    }
                                    return String.valueOf(heighest);
                                case "initial":
                                    for (Upgrade upgrad : upgrades) {
                                        if (upgrad.getInitialLevel() > heighest) {
                                            heighest = upgrad.getInitialLevel();
                                        }
                                    }
                                    return String.valueOf(heighest);
                            }
                        }
                    }
                    return "";
                })
                .call(categoryBuilder -> {
                    if (useParent) {
                        var shopFileName = "shop.yml";
                        categoryBuilder.include(Include.of(new
                                File(SBAHypixelify.getConfigurator().dataFolder.toString() +"/shops", shopFileName)));
                    }

                    if (file != null) {
                        categoryBuilder.include(Include.of(new
                                File(SBAHypixelify.getConfigurator().dataFolder.toString() +"/shops", name)));
                    }

                })
                .getInventorySet();

        try {
            shopInventory.getMainSubInventory().process();
        } catch (Exception ex) {
            Debug.warn("Wrong shop.yml/shop.groovy configuration!", true);
            Debug.warn("Check validity of your YAML/Groovy!", true);
            ex.printStackTrace();
            loadDefault(shopInventory);
        }

        shopMap.put(name, shopInventory);
    }

    private void handleBuy(OnTradeEvent event) {
        var player = event.getPlayer().as(Player.class);
        var game = Main.getPlayerGameProfile(player).getGame();
        var clickType = event.getClickType();
        var itemInfo = event.getItem();

        // TODO: multi-price feature
        var price = event.getPrices().get(0);
        ItemSpawnerType type = Main.getSpawnerType(price.getCurrency().toLowerCase());

        var newItem = event.getStack();

        var amount = newItem.getAmount();
        var priceAmount = price.getAmount();
        int inInventory = 0;

        var currencyChanger = itemInfo.getFirstPropertyByName("currencyChanger");
        if (currencyChanger.isPresent()) {
            var changeItemToName = currencyChanger.get().getPropertyData().getString();
            ItemSpawnerType changeItemType;
            if (changeItemToName == null) {
                return;
            }

            changeItemType = Main.getSpawnerType(changeItemToName.toLowerCase());
            if (changeItemType == null) {
                return;
            }

            newItem = ItemFactory.build(changeItemType.getStack()).orElse(newItem);
        }

        var originalMaxStackSize = newItem.getMaterial().as(Material.class).getMaxStackSize();
        if (clickType.isShiftClick() && originalMaxStackSize > 1) {
            double priceOfOne = (double) priceAmount / amount;
            double maxStackSize;
            int finalStackSize;

            for (ItemStack itemStack : player.getInventory().getStorageContents()) {
                if (itemStack != null && itemStack.isSimilar(type.getStack())) {
                    inInventory = inInventory + itemStack.getAmount();
                }
            }
            if (Main.getConfigurator().config.getBoolean("sell-max-64-per-click-in-shop")) {
                maxStackSize = Math.min(inInventory / priceOfOne, originalMaxStackSize);
            } else {
                maxStackSize = inInventory / priceOfOne;
            }

            finalStackSize = (int) maxStackSize;
            if (finalStackSize > amount) {
                priceAmount = (int) (priceOfOne * finalStackSize);
                newItem.setAmount(finalStackSize);
                amount = finalStackSize;
            }
        }

        var materialItem = ItemFactory.build(type.getStack(priceAmount)).orElseThrow();
        if (event.hasPlayerInInventory(materialItem)) {
            Map<String, Object> permaItemPropertyData = new HashMap<>();
            for (var property : itemInfo.getProperties()) {
                var converted = ConfigurateUtils.raw(property.getPropertyData());
                if (!(converted instanceof Map)) {
                    converted = DumpCommand.nullValuesAllowingMap("value", converted);
                }
                //noinspection unchecked
                var propertyData = (Map<String, Object>) converted;
                if (property.hasName()) {
                    var applyEvent = new BedwarsApplyPropertyToBoughtItem(game, player,
                            newItem.as(ItemStack.class), property.getPropertyName(), propertyData);
                    Main.getInstance().getServer().getPluginManager().callEvent(applyEvent);

                    newItem = ItemFactory.build(applyEvent.getStack()).orElse(newItem);
                }
                // Checks if the player is buying a permanent item. Setting name to empty string to prevent other listeners from erroring out.
                else if (propertyData.get(PermaItemListener.getPermItemPropKey()) != null) {
                    permaItemPropertyData = propertyData;
                }
            }

            if (!permaItemPropertyData.isEmpty()) {
                BedwarsApplyPropertyToBoughtItem applyEvent = new BedwarsApplyPropertyToBoughtItem(game, player,
                        newItem.as(ItemStack.class), "", permaItemPropertyData);
                Main.getInstance().getServer().getPluginManager().callEvent(applyEvent);
            }

            event.sellStack(materialItem);
            List<Item> notFit = event.buyStack(newItem);
            if (!notFit.isEmpty()) {
                notFit.forEach(stack -> player.getLocation().getWorld().dropItem(player.getLocation(), stack.as(ItemStack.class)));
            }

            if (!Main.getConfigurator().config.getBoolean("removePurchaseMessages", false)) {
                player.sendMessage(i18nc("buy_succes", game.getCustomPrefix()).replace("%item%", amount + "x " + getNameOrCustomNameOfItem(newItem))
                        .replace("%material%", priceAmount + " " + type.getItemName()));
            }
            Sounds.playSound(player, player.getLocation(),
                    Main.getConfigurator().config.getString("sounds.on_item_buy"), Sounds.ENTITY_ITEM_PICKUP, 1, 1);
        } else {
            if (!Main.getConfigurator().config.getBoolean("removePurchaseMessages", false)) {
                player.sendMessage(i18nc("buy_failed", game.getCustomPrefix()).replace("%item%", amount + "x " + getNameOrCustomNameOfItem(newItem))
                        .replace("%material%", priceAmount + " " + type.getItemName()));
            }
        }
    }

    private void handleUpgrade(OnTradeEvent event) {
        var player = event.getPlayer().as(Player.class);
        var game = Main.getPlayerGameProfile(player).getGame();
        var itemInfo = event.getItem();

        // TODO: multi-price feature
        var price = event.getPrices().get(0);
        ItemSpawnerType type = Main.getSpawnerType(price.getCurrency().toLowerCase());

        var priceAmount = price.getAmount();

        var upgrade = itemInfo.getFirstPropertyByName("upgrade").orElseThrow();
        var itemName = upgrade.getPropertyData().node("shop-name").getString("UPGRADE");
        var entities = upgrade.getPropertyData().node("entities").childrenList();

        boolean sendToAll = false;
        boolean isUpgrade = true;
        var materialItem = ItemFactory.build(type.getStack(priceAmount)).orElseThrow();

        if (event.hasPlayerInInventory(materialItem)) {
            event.sellStack(materialItem);
            for (var entity : entities) {
                var configuredType = entity.node("type").getString();
                if (configuredType == null) {
                    return;
                }

                var upgradeStorage = UpgradeRegistry.getUpgrade(configuredType);
                if (upgradeStorage != null) {

                    // TODO: Learn SimpleGuiFormat upgrades pre-parsing and automatic renaming old
                    // variables
                    var team = game.getTeamOfPlayer(player);
                    double addLevels = entity.node("add-levels").getDouble(entity.node("levels").getDouble(0));
                    /* You shouldn't use it in entities */
                    itemName = entity.node("shop-name").getString(itemName);
                    sendToAll = entity.node("notify-team").getBoolean();

                    List<Upgrade> upgrades = new ArrayList<>();

                    var spawnerNameNode = entity.node("spawner-name");
                    var spawnerTypeNode = entity.node("spawner-type");
                    var teamUpgradeNode = entity.node("team-upgrade");
                    var customNameNode = entity.node("customName");

                    if (!spawnerNameNode.empty()) {
                        String customName = spawnerNameNode.getString();
                        upgrades = upgradeStorage.findItemSpawnerUpgrades(game, customName);
                    } else if (!spawnerTypeNode.empty()) {
                        String mapSpawnerType = spawnerTypeNode.getString();
                        ItemSpawnerType spawnerType = Main.getSpawnerType(mapSpawnerType);

                        upgrades = upgradeStorage.findItemSpawnerUpgrades(game, team, spawnerType);
                    } else if (!teamUpgradeNode.empty()) {
                        boolean upgradeAllSpawnersInTeam = teamUpgradeNode.getBoolean();

                        if (upgradeAllSpawnersInTeam) {
                            upgrades = upgradeStorage.findItemSpawnerUpgrades(game, team);
                        }

                    } else if (!customNameNode.empty()) { // Old configuration
                        String customName = customNameNode.getString();
                        upgrades = upgradeStorage.findItemSpawnerUpgrades(game, customName);
                    } else {
                        isUpgrade = false;
                        Debugger.warn("[BedWars]> Upgrade configuration is invalid.");
                    }

                    if (isUpgrade) {
                        BedwarsUpgradeBoughtEvent bedwarsUpgradeBoughtEvent = new BedwarsUpgradeBoughtEvent(game,
                                upgradeStorage, upgrades, player, addLevels);
                        Bukkit.getPluginManager().callEvent(bedwarsUpgradeBoughtEvent);

                        if (bedwarsUpgradeBoughtEvent.isCancelled()) {
                            continue;
                        }

                        if (upgrades.isEmpty()) {
                            continue;
                        }

                        for (var anUpgrade : upgrades) {
                            BedwarsUpgradeImprovedEvent improvedEvent = new BedwarsUpgradeImprovedEvent(game,
                                    upgradeStorage, anUpgrade, anUpgrade.getLevel(), anUpgrade.getLevel() + addLevels);
                            Bukkit.getPluginManager().callEvent(improvedEvent);
                        }
                    }
                }

                if (sendToAll) {
                    for (Player player1 : game.getTeamOfPlayer(player).getConnectedPlayers()) {
                        if (!Main.getConfigurator().config.getBoolean("removePurchaseMessages", false)) {
                            player1.sendMessage(i18nc("buy_succes", game.getCustomPrefix()).replace("%item%", itemName).replace("%material%",
                                    priceAmount + " " + type.getItemName()));
                        }
                        Sounds.playSound(player1, player1.getLocation(),
                                Main.getConfigurator().config.getString("sounds.on_upgrade_buy"),
                                Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                    }
                } else {
                    if (!Main.getConfigurator().config.getBoolean("removePurchaseMessages", false)) {
                        player.sendMessage(i18nc("buy_succes", game.getCustomPrefix()).replace("%item%", itemName).replace("%material%",
                                priceAmount + " " + type.getItemName()));
                    }
                    Sounds.playSound(player, player.getLocation(),
                            Main.getConfigurator().config.getString("sounds.on_upgrade_buy"),
                            Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                }
            }
        } else {
            if (!Main.getConfigurator().config.getBoolean("removePurchaseMessages", false)) {
                player.sendMessage(i18nc("buy_failed", game.getCustomPrefix()).replace("%item%", "UPGRADE").replace("%material%",
                        priceAmount + " " + type.getItemName()));
            }
        }
    }
}