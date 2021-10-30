package io.github.pronze.sba.party;

import io.github.pronze.sba.wrapper.PlayerSetting;
import org.jetbrains.annotations.NotNull;
import org.screamingsandals.lib.player.PlayerMapper;
import org.screamingsandals.lib.plugin.ServiceManager;
import org.screamingsandals.lib.utils.annotations.Service;
import io.github.pronze.sba.SBA;
import io.github.pronze.sba.events.SBAPlayerPartyCreatedEvent;
import io.github.pronze.sba.wrapper.SBAPlayerWrapper;
import io.github.pronze.sba.utils.Logger;
import io.github.pronze.sba.utils.SBAUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service(dependsOn = {
        Logger.class
})
public class PartyManagerImpl implements io.github.pronze.sba.manager.PartyManager {

    public static PartyManagerImpl getInstance() {
        return ServiceManager.get(PartyManagerImpl.class);
    }

    private final Map<UUID, PartyImpl> partyMap = new HashMap<>();

    public PartyManagerImpl() {}

    @Override
    public Optional<PartyImpl> createParty(@NotNull SBAPlayerWrapper leader) {
        final var party = new PartyImpl(leader);
        final var partyCreateEvent = new SBAPlayerPartyCreatedEvent(leader, party);
        SBA.getPluginInstance()
                .getServer()
                .getPluginManager()
                .callEvent(partyCreateEvent);
        if (partyCreateEvent.isCancelled()) return Optional.empty();
        partyMap.put(party.getUUID(), party);
        return Optional.of(party);
    }

    @Override
    public Optional<PartyImpl> get(@NotNull SBAPlayerWrapper leader) {
        return partyMap
                .values()
                .stream()
                .filter(p -> p.getPartyLeader().equals(leader))
                .findAny();
    }

    @Override
    public Optional<PartyImpl> get(@NotNull UUID partyUUID) {
        if (!partyMap.containsKey(partyUUID)) {
            return Optional.empty();
        }
        return Optional.of(partyMap.get(partyUUID));
    }

    @Override
    public Optional<PartyImpl> getOrCreate(@NotNull SBAPlayerWrapper player) {
        return getPartyOf(player).or(() -> createParty(player));
    }

    @Override
    public Optional<PartyImpl> getPartyOf(@NotNull SBAPlayerWrapper player) {
        return partyMap.values()
                .stream()
                .filter(party -> party.getMembers().contains(player))
                .findAny();
    }

    @Override
    public Optional<PartyImpl> getInvitedPartyOf(@NotNull SBAPlayerWrapper player) {
        return partyMap.values()
                .stream()
                .filter(party -> party.isInvited(player))
                .findAny();
    }

    @Override
    public void disband(@NotNull UUID partyUUID) {
        if (partyMap.containsKey(partyUUID)) {
            final var party = partyMap.get(partyUUID);
            disband(party);
        }
    }

    @Override
    public void disband(@NotNull SBAPlayerWrapper leader) {
        getPartyOf(leader).ifPresent(this::disband);
    }

    private void disband(@NotNull PartyImpl party) {
        Logger.trace("Disbandoning party: {}", party.debugInfo());
        final var disbandMessage = SBA
                .getInstance()
                .getConfigurator()
                .getStringList("party.message.disband");

        party.getMembers().forEach(member -> {
            member.getSettings().disable(PlayerSetting.IN_PARTY);
            party.removePlayer(member);
            final var wrapperImpl = PlayerMapper
                    .wrapPlayer(member.getInstance())
                    .as(SBAPlayerWrapper.class);
            if (!wrapperImpl.isOnline()) {
                return;
            }
            disbandMessage
                    .forEach(wrapperImpl::sendMessage);
        });

        party.getInvitedPlayers().forEach(invitedPlayer -> {
            invitedPlayer.getSettings().disable(PlayerSetting.IN_PARTY);
            party.removeInvitedPlayer(invitedPlayer);
            final var wrapperImpl = PlayerMapper
                    .wrapPlayer(invitedPlayer.getInstance())
                    .as(SBAPlayerWrapper.class);
            if (!wrapperImpl.isOnline()) {
                return;
            }
            SBA.getInstance()
                    .getConfigurator()
                    .getStringList("party.message.invite-expired")
                    .forEach(wrapperImpl::sendMessage);
        });

        party.getInviteData().forEach(data -> {
            final var inviteTask = data.getInviteTask();
            SBAUtil.cancelTask(inviteTask);
        });

        partyMap.remove(party.getUUID());
    }
}
