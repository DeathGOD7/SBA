package io.github.pronze.sba.commands.party;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandMethod;
import io.github.pronze.sba.lang.LangKeys;
import io.github.pronze.sba.party.PartyManager;
import io.github.pronze.sba.wrapper.PlayerSetting;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.screamingsandals.lib.lang.Message;
import org.screamingsandals.lib.player.PlayerMapper;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.methods.OnPostEnable;
import io.github.pronze.sba.SBA;
import io.github.pronze.sba.events.SBAPlayerPartyKickEvent;
import io.github.pronze.sba.wrapper.SBAPlayerWrapper;
import io.github.pronze.sba.commands.CommandManager;
import io.github.pronze.sba.lib.lang.SBALanguageService;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PartyKickCommand {

    @OnPostEnable
    public void onPostEnable() {
        CommandManager.getInstance().getManager().getParserRegistry().registerSuggestionProvider("kick", (ctx, s) -> {
            final var player = PlayerMapper
                    .wrapPlayer((Player)ctx.getSender())
                    .as(SBAPlayerWrapper.class);
            final var optionalParty = SBA
                    .getInstance()
                    .getPartyManager()
                    .getPartyOf(player);

            if (optionalParty.isEmpty() || !player.getSettings().isToggled(PlayerSetting.IN_PARTY) || !player.equals(optionalParty.get().getPartyLeader())) {
                return List.of();
            }
            return optionalParty.get()
                    .getMembers()
                    .stream()
                    .map(SBAPlayerWrapper::getName)
                    .filter(name -> !player.getName().equalsIgnoreCase(name))
                    .collect(Collectors.toList());
        });
        CommandManager.getInstance().getAnnotationParser().parse(this);
    }

    @CommandMethod("party kick <player>")
    private void commandKick(
            final @NotNull Player playerArg,
            final @NotNull @Argument(value = "player", suggestions = "kick") Player toKick
    ) {
        final var player = PlayerMapper
                .wrapPlayer(playerArg)
                .as(SBAPlayerWrapper.class);

        final var args = PlayerMapper
                .wrapPlayer(toKick)
                .as(SBAPlayerWrapper.class);

        PartyManager
                .getInstance()
                .getPartyOf(player)
                .ifPresentOrElse(party -> {
                            if (!party.getPartyLeader().equals(player)) {
                                Message.of(LangKeys.PARTY_MESSAGE_ACCESS_DENIED).send(player);
                                return;
                            }

                            if (!party.getMembers().contains(args)) {
                                Message.of(LangKeys.PARTY_MESSAGE_PLAYER_NOT_FOUND).send(player);
                                return;
                            }

                            final var kickEvent = new SBAPlayerPartyKickEvent(player, party);
                            SBA.getPluginInstance()
                                    .getServer()
                                    .getPluginManager()
                                    .callEvent(kickEvent);

                            if (kickEvent.isCancelled()) return;

                            party.removePlayer(args);
                            Message.of(LangKeys.PARTY_MESSAGE_KICKED)
                                    .placeholder("player", args.getName())
                                    .send(party.getMembers());

                            Message.of(LangKeys.PARTY_MESSAGE_KICKED_RECEIVED).send(args);

                            if (party.getMembers().size() == 1) {
                                PartyManager
                                        .getInstance()
                                        .disband(party.getUUID());
                            }
                        },() -> Message.of(LangKeys.PARTY_MESSAGE_ERROR).send(player)
                );
    }
}
