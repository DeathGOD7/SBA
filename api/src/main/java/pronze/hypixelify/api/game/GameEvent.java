package pronze.hypixelify.api.game;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import pronze.hypixelify.api.SBAHypixelifyAPI;

import java.util.Arrays;

@RequiredArgsConstructor
@Getter
public enum GameEvent {
    DIAMOND_GEN_UPGRADE_TIER_II("Diamond-II"),
    EMERALD_GEN_UPGRADE_TIER_II("Emerald-II"),
    DIAMOND_GEN_UPGRADE_TIER_III("Diamond-III"),
    EMERALD_GEN_UPGRADE_TIER_III("Emerald-III"),
    DIAMOND_GEN_UPGRADE_TIER_IV("Diamond-IV"),
    EMERALD_GEN_UPGRADE_TIER_V("Emerald-IV"),
    GAME_END("GameEnd");

    private final String key;

    public int getTime() {
        return SBAHypixelifyAPI
                .getInstance()
                .getConfigurator()
                .getInt("upgrades.time." + key, Integer.MAX_VALUE);
    }

    public static GameEvent ofOrdinal(int ordinal) {
        return Arrays.stream(values())
                .filter(val -> val.ordinal() == ordinal)
                .findAny()
                .orElse(GameEvent.GAME_END);
    }

    public GameEvent getNextEvent() {
        return ofOrdinal(ordinal() + 1);
    }
}
