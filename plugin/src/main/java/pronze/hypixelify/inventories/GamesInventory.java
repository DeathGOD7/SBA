package pronze.hypixelify.inventories;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.game.Game;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.lib.debug.Debug;
import org.screamingsandals.bedwars.lib.player.PlayerMapper;
import org.screamingsandals.bedwars.lib.sgui.SimpleInventoriesCore;
import org.screamingsandals.bedwars.lib.sgui.events.PostClickEvent;
import org.screamingsandals.bedwars.lib.sgui.inventory.Include;
import org.screamingsandals.bedwars.lib.sgui.inventory.InventorySet;
import org.screamingsandals.bedwars.lib.sgui.inventory.Property;
import pronze.hypixelify.SBAHypixelify;
import pronze.hypixelify.api.MessageKeys;
import pronze.hypixelify.api.events.SBAGamesInventoryOpenEvent;
import pronze.hypixelify.config.SBAConfig;
import pronze.hypixelify.lib.lang.LanguageService;
import pronze.hypixelify.utils.ShopUtil;
import pronze.lib.core.Core;
import pronze.lib.core.annotations.AutoInitialize;
import pronze.lib.core.annotations.OnInit;
import pronze.lib.core.utils.Logger;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;

@AutoInitialize(listener = true, depends = {
        LanguageService.class
})
public class GamesInventory implements Listener {

    public static GamesInventory getInstance() {
        return Core.getObjectFromClass(GamesInventory.class);
    }

    private final static HashMap<Integer, String> labels = new HashMap<>() {
        {
            put(1, "solo");
            put(2, "double");
            put(3, "triple");
            put(4, "squad");
        }
    };
    private final HashMap<Integer, InventorySet> inventoryMap = new HashMap<>();

    @OnInit
    public void loadInventory() {
        try {
            labels.forEach((val, label) -> {
                try {
                    final var siFormat = SimpleInventoriesCore.builder()
                            .categoryOptions(localOptionsBuilder -> {
                                ShopUtil.generateOptions(localOptionsBuilder);
                                localOptionsBuilder.prefix(LanguageService.getInstance().get("games-inventory", "gui", label.toLowerCase() + "-prefix").toString());
                            })
                            .call(categoryBuilder ->{
                                try {
                                    var pathStr = SBAHypixelify.getInstance().getDataFolder().getAbsolutePath() + "/games-inventory/" + label.toLowerCase() + ".yml";
                                    categoryBuilder.include(Include.of(Paths.get(pathStr)));
                            }  catch (Throwable t) {
                                    t.printStackTrace();
                                }
                            })
                            .click(this::onClick)
                            .process()
                            .getInventorySet();

                    inventoryMap.put(val, siFormat);
                    Logger.trace("Successfully loaded games inventory for: {}", label);
                } catch (Throwable t) {
                    Logger.trace("Could not initialize games inventory format for {}", label);
                    t.printStackTrace();
                }
            });
        } catch (Exception ex) {
            Debug.warn("Wrong GamesInventory configuration!", true);
            Debug.warn("Check validity of your YAML/Groovy!", true);
            ex.printStackTrace();
        }
    }

    public void openForPlayer(Player player, int mode) {
        final var event = new SBAGamesInventoryOpenEvent(player, mode);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        final var format = inventoryMap.get(mode);
        if (format != null) {
            PlayerMapper.wrapPlayer(player).openInventory(format);
        }
    }

    public void onClick(PostClickEvent event) {
        final var mode = inventoryMap.keySet()
                .stream()
                .filter(key -> event.getFormat() == inventoryMap.get(key))
                .findFirst()
                .orElse(1);

        final var item = event.getItem();
        final var stack = item.getStack();
        final var player = event.getPlayer().as(Player.class);
        final var properties = item.getProperties();

        if (stack != null) {
            if (item.hasProperties()) {
                var couldNotFindGameMessage = LanguageService
                        .getInstance()
                        .get(MessageKeys.GAMES_INVENTORY_CANNOT_FIND_GAME);
                final var playerWrapper = PlayerMapper.wrapPlayer(player);

                properties.stream()
                        .filter(Property::hasName)
                        .forEach(property -> {
                            switch (property.getPropertyName().toLowerCase()) {
                                case "game":
                                    try {
                                        final var game = (Game) Main
                                                .getInstance()
                                                .getGameManager()
                                                .getGame(property.getPropertyData().node("gameName").getString())
                                                .orElseThrow();
                                        game.joinToGame(player);
                                    } catch (Throwable t) {
                                        couldNotFindGameMessage.send(playerWrapper);
                                    }
                                    player.closeInventory();
                                    break;
                                case "exit":
                                    player.closeInventory();
                                    break;
                                case "randomly_join":
                                    player.closeInventory();
                                    final var games = ShopUtil.getGamesWithSize(mode);
                                    if (games == null || games.isEmpty()) {
                                        couldNotFindGameMessage.send(playerWrapper);
                                        return;
                                    }
                                    games.stream()
                                            .filter(game -> game.getStatus() == GameStatus.WAITING)
                                            .findAny()
                                            .ifPresentOrElse(game -> game.joinToGame(player), () -> couldNotFindGameMessage.send(playerWrapper));
                                    break;
                                case "rejoin":
                                    player.closeInventory();
                                    player.performCommand("bw rejoin");
                                    break;
                                default:
                                    break;
                            }
                        });
            }
        }
    }


}
