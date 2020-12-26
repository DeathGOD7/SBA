package io.pronze.hypixelify.manager;

import io.pronze.hypixelify.SBAHypixelify;
import io.pronze.hypixelify.api.party.Party;
import org.bukkit.entity.Player;
import io.pronze.hypixelify.api.wrapper.PlayerWrapper;
import io.pronze.hypixelify.utils.MessageUtils;
import io.pronze.hypixelify.utils.ShopUtil;
import org.screamingsandals.bedwars.api.BedwarsAPI;
import org.screamingsandals.bedwars.api.game.Game;
import org.screamingsandals.bedwars.api.game.GameStatus;

import java.util.*;

//TODO: Rewrite entire party system

public class PartyManager implements io.pronze.hypixelify.api.party.PartyManager {

    private final Map<Player, io.pronze.hypixelify.party.Party> parties = new HashMap<>();

    @Override
    public List<Party> getParties() {
        return new ArrayList<>(parties.values());
    }

    @Override
    public void disband(Player leader) {
        final io.pronze.hypixelify.party.Party party = parties.get(leader);

        if (party == null || party.getLeader() == null || !party.getLeader().equals(leader))
            return;

        final List<Player> partyPlayers = party.getPlayers();

        if (partyPlayers != null) {
            partyPlayers.stream()
                    .filter(Objects::nonNull)
                    .forEach(player -> {
                if (player.isOnline())
                    SBAHypixelify.getConfigurator().config.getStringList("party.message.disband")
                            .forEach(player::sendMessage);

                final PlayerWrapper wrapper = SBAHypixelify.getWrapperService().getWrapper(player);
                if (wrapper != null) {
                    wrapper.setIsInParty(false);
                    wrapper.setParty(null);
                }
            });
        }

        SBAHypixelify.getWrapperService().getWrapper(leader).setIsInParty(false);

        parties.get(leader).disband();
        parties.remove(leader);
        SBAHypixelify.getWrapperService().updateAll();
    }

    @Override
    public boolean isInParty(Player player) {
        if(player == null){
            return false;
        }
        return SBAHypixelify.getWrapperService().getWrapper(player).isInParty();
    }


    @Override
    public void addToParty(Player player, Party party) {
        if (party == null) {
            throw new IllegalArgumentException("Party cannot be null");
        }
        final var partyLeader = party.getLeader();
        if (partyLeader == null) {
            return;
        }
        party.addMember(player);
        party.removeInvitedMember(player);
        final var playerWrapper = SBAHypixelify.getWrapperService().getWrapper(player);
        playerWrapper.setParty(partyLeader);
        playerWrapper.setInvited(false);
        playerWrapper.setIsInParty(true);
        playerWrapper.setInvitedParty(null);
        final var partyMembers = party.getAllPlayers();
        if (partyMembers != null) {
            partyMembers
                    .stream()
                    .filter(Objects::nonNull)
                    .forEach(pl -> {
                if (!pl.isOnline()) {
                    return;
                }

                SBAHypixelify.getConfigurator().config.getStringList("party.message.accepted")
                        .stream()
                        .filter(Objects::nonNull)
                        .forEach(message -> pl.sendMessage(ShopUtil.translateColors(message)
                                .replace("{player}", player.getDisplayName())));
            });
        }

    }

    @Override
    public void removeFromParty(Player player, Party party) {
        final var db = SBAHypixelify.getWrapperService().getWrapper(player);
        if (db == null || party == null || party.getLeader() == null)
            return;
        party.removeMember(player);
        final var partyMembers = party.getAllPlayers();
        if (partyMembers != null) {
            partyMembers.stream()
                    .filter(Objects::nonNull)
                    .forEach(member -> {
                if (!member.isOnline()) {
                    return;
                }
                MessageUtils.sendMessage("party.message.offline-quit", member);
            });
        }

        db.setIsInParty(false);
        db.setParty(null);
        SBAHypixelify.getWrapperService().updateAll();

    }

    @Override
    public void kickFromParty(Player player) {
        if (getParty(player) == null || player == null) return;
        final var db = SBAHypixelify.getWrapperService().getWrapper(player);
        if (db == null || db.getPartyLeader() == null) return;
        final var leader = db.getPartyLeader();
        final var party = parties.get(leader);
        if (leader == null || party == null) return;

        party.removeMember(player);
        MessageUtils.sendMessage("party.message.got-kicked", player);
        final var partyMembers = party.getPlayers();

        if (partyMembers != null) {
            final var replacementMap = new HashMap<String, String>();
            replacementMap.put("{player}", player.getDisplayName());
            partyMembers.forEach(member -> MessageUtils.sendMessage("party.message.kicked",
                    player, replacementMap));
        }
        db.setIsInParty(false);
        db.setParty(null);
        SBAHypixelify.getWrapperService().updateAll();
    }

    @Override
    public io.pronze.hypixelify.party.Party getParty(Player player) {
        if (!isInParty(player)) return null;
        if(player == null){
            return null;
        }
        final var database = SBAHypixelify.getWrapperService().getWrapper(player);
        if (database == null) return null;
        final var partyLeader = database.getPartyLeader();
        if (partyLeader != null && isInParty(partyLeader)) {
            return parties.get(partyLeader);
        }
        return null;
    }


    @Override
    public void warpPlayersToLeader(Player leader) {
        final var bedwarsAPI = BedwarsAPI.getInstance();
        final var party = getParty(leader);
        if (party == null) {
            return;
        }
        final var partyMembers = party.getPlayers();
        if (partyMembers == null) {
            return;
        }
        if (bedwarsAPI.isPlayerPlayingAnyGame(leader)) {
            final var game = bedwarsAPI.getGameOfPlayer(leader);
            if (game.getStatus() != GameStatus.WAITING) {
                leader.sendMessage("Cannot do this now!");
                return;
            }
            ShopUtil.sendMessage(leader, SBAHypixelify.getConfigurator().getStringList("party.message.warping"));
            partyMembers.forEach(pl -> {
                if (game.getConnectedPlayers().size() >= game.getMaxPlayers()) {
                    pl.sendMessage("§cYou could not be warped to game");
                    return;
                }

                final Game playerGame = bedwarsAPI.getGameOfPlayer(pl);
                if (playerGame != null) {
                    if (playerGame.equals(game)) {
                        return;
                    }

                    playerGame.leaveFromGame(pl);
                }
                ShopUtil.sendMessage(pl, SBAHypixelify.getConfigurator().getStringList("party.message.warp"));
                game.joinToGame(pl);
            });

        } else {
            ShopUtil.sendMessage(leader, SBAHypixelify.getConfigurator().getStringList("party.message.warping"));
            partyMembers.forEach(member -> {
                member.teleport(leader.getLocation());
                ShopUtil.sendMessage(member, SBAHypixelify.getConfigurator().getStringList("party.message.warp"));
            });

        }
    }

    @Override
    public void removeFromInvitedParty(Player player) {
        final var database = SBAHypixelify.getWrapperService().getWrapper(player);
        if (database == null || !database.isInvited()) return;
        var invitedParty = database.getInvitedParty();
        if (invitedParty == null) return;
        invitedParty.removeInvitedMember(player);
        database.setInvitedParty(null);
        database.setInvited(false);
    }

    @Override
    public io.pronze.hypixelify.party.Party createParty(Player player) {
        if (parties.containsKey(player)) return null;
        final var party = new io.pronze.hypixelify.party.Party(player);
        parties.put(player, party);
        return party;
    }

    @Override
    public void removeParty(Player leader) {
        parties.remove(leader);
    }

    @Override
    public void removeParty(Party party) {
        if(party == null){
            return;
        }
        parties.remove(party.getLeader());
    }

    @Override
    public void databaseDeletionFromParty(Player player, Player partyLeader) {
        final var party = getParty(partyLeader);
        final var database = SBAHypixelify.getWrapperService().getWrapper(player);

        if (database == null || party == null) {
            return;
        }

        final var partyMembers = party.getAllPlayers();
        final var replacementMap = new HashMap<String, String>();

        replacementMap.put("{player}", player.getDisplayName());

        if (!partyLeader.getUniqueId().equals(player.getUniqueId())) {
            if (partyMembers != null) {
                partyMembers.forEach(member -> {
                    if (!player.equals(member)) {
                        MessageUtils.sendMessage("party.message.offline-left", member, replacementMap);
                    }
                });
            }

            party.removeMember(player);
        } else {
            partyMembers.forEach(member -> {
                if (member.isOnline()) {
                    ShopUtil.sendMessage(member, SBAHypixelify.getConfigurator().getStringList("party.message.disband-inactivity"));
                }

                final var plDatabase = SBAHypixelify.getWrapperService().getWrapper(member);
                if (plDatabase != null) {
                    plDatabase.setIsInParty(false);
                    plDatabase.setParty(null);
                }

            });

            party.disband();
            removeParty(partyLeader);
        }

        database.setIsInParty(false);
        database.setParty(null);
    }

}
