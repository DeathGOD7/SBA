package pronze.hypixelify.api.manager;

import org.bukkit.entity.Player;

public interface ScoreboardManager {
    /**
     *
     * @param player
     */
    void createBoard(Player player);

    /**
     *
     * @param player
     */
    void removeBoard(Player player);

    /**
     *
     */
    void destroy();
}
