package io.github.pronze.sba.visuals;

import io.github.pronze.sba.MessageKeys;
import io.github.pronze.sba.lib.lang.LanguageService;
import io.github.pronze.sba.utils.DateUtils;
import io.github.pronze.sba.utils.Logger;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.events.BedwarsPlayerJoinedEvent;
import org.screamingsandals.bedwars.api.events.BedwarsPlayerLeaveEvent;
import org.screamingsandals.bedwars.api.game.Game;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.game.TeamColor;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.methods.OnPostEnable;
import io.github.pronze.sba.config.SBAConfig;
import io.github.pronze.sba.SBA;
import io.github.pronze.lib.pronzelib.scoreboards.Scoreboard;
import io.github.pronze.lib.pronzelib.scoreboards.ScoreboardManager;

import java.util.*;

@Service
public class LobbyScoreboardManager implements Listener {
    private final Map<UUID, Scoreboard> scoreboardMap = new HashMap<>();

    @OnPostEnable
    public void registerListener() {
        if (!SBAConfig.getInstance().node("lobby-scoreboard", "enabled").getBoolean(true)) {
            return;
        }
        SBA.getInstance().registerListener(this);
    }

    @EventHandler
    public void onPlayerJoin(BedwarsPlayerJoinedEvent e) {
        final var player = e.getPlayer();
        if (e.getGame().getStatus() == GameStatus.WAITING) {
            Bukkit.getScheduler().runTaskLater(SBA.getPluginInstance(), () -> createBoard(player, e.getGame()), 3L);
        }
    }

    private void createBoard(Player player, Game game) {
        Logger.trace("Creating board for player: {}", player.getName());

        final var scoreboardOptional = ScoreboardManager.getInstance()
                .fromCache(player.getUniqueId());
        scoreboardOptional.ifPresent(Scoreboard::destroy);

        var animatedTitle = LanguageService
                .getInstance()
                .get(MessageKeys.ANIMATED_BEDWARS_TITLE)
                .toStringList();

        final var scoreboard = Scoreboard.builder()
                .animate(true)
                .player(player)
                .displayObjective("bwa-lobby")
                .updateInterval(20L)
                .animationInterval(2L)
                .animatedTitle(animatedTitle)
                .updateCallback(board -> {
                    board.setLines(process(player, game, board));
                    return true;
                })
                .build();
        scoreboardMap.put(player.getUniqueId(), scoreboard);
    }

    @EventHandler
    public void onPlayerLeave(BedwarsPlayerLeaveEvent e) {
        remove(e.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        remove(e.getPlayer());
    }

    private void remove(Player player) {
        if (scoreboardMap.containsKey(player.getUniqueId())) {
            final var scoreboard = scoreboardMap.get(player.getUniqueId());
            if (scoreboard != null) {
                scoreboard.destroy();
                scoreboardMap.remove(player.getUniqueId());
            }
        }
    }

    private List<String> process(Player player, Game game, Scoreboard scoreboard) {
        final var lines = new ArrayList<String>();
        String state = LanguageService
                .getInstance()
                .get(MessageKeys.LOBBY_SCOREBOARD_STATE_WAITING)
                .toString();

        int needplayers = game.getMinPlayers() - game.getConnectedPlayers().size();
        needplayers = Math.max(needplayers, 0);
        int s = SBAConfig.game_size.getOrDefault(game.getName(), 4);
        String mode;
        switch (s) {
            case 1:
                mode = LanguageService
                        .getInstance()
                        .get(MessageKeys.LOBBY_SCOREBOARD_SOLO_PREFIX)
                        .toString();
                break;
            case 2:
                mode = LanguageService
                        .getInstance()
                        .get(MessageKeys.LOBBY_SCOREBOARD_DOUBLES_PREFIX)
                        .toString();
                break;
            case 3:
                mode = LanguageService
                        .getInstance()
                        .get(MessageKeys.LOBBY_SCOREBOARD_TRIPLES_PREFIX)
                        .toString();
                break;
            case 4:
                mode = LanguageService
                        .getInstance()
                        .get(MessageKeys.LOBBY_SCOREBOARD_SQUADS_PREFIX)
                        .toString();
                break;
            default:
                mode = s + "v" + s + "v" + s + "v" + s;
        }

        if (game.countConnectedPlayers() >= game.getMinPlayers()
                && game.getStatus() == GameStatus.WAITING) {
            final var time = ((org.screamingsandals.bedwars.game.Game)Main.getInstance().getGameByName(game.getName())).getFormattedTimeLeft();
            if (!time.contains("0-1")) {
                final var units = time.split(":");
                var seconds = Integer.parseInt(units[1]) + 1;
                state = LanguageService
                        .getInstance()
                        .get(MessageKeys.LOBBY_SCOREBOARD_STATE)
                        .replace("%countdown%", String.valueOf(seconds))
                        .toString();
            }
        }

        final var finalState = state;
        final var finalNeedplayers = needplayers;

        var lobbyScoreboardLines = LanguageService
                .getInstance()
                .get(MessageKeys.LOBBY_SCOREBOARD_LINES)
                .toStringList();

        lobbyScoreboardLines.forEach(line -> {
            line = line
                    .replace("%sba_version%", SBA.getInstance().getVersion())
                    .replace("%date%", DateUtils.getFormattedDate())
                    .replace("%state%", finalState)
                    .replace("%game%", game.getName())
                    .replace("%players%", String.valueOf(game.getConnectedPlayers().size()))
                    .replace("%maxplayers%", String.valueOf(game.getMaxPlayers()))
                    .replace("%minplayers%", String.valueOf(game.getMinPlayers()))
                    .replace("%needplayers%", String.valueOf(finalNeedplayers))
                    .replace("%mode%", mode);
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
                line = PlaceholderAPI.setPlaceholders(player, line);
            lines.add(line);
        });

        final var holder = scoreboard.getHolder();
        game.getRunningTeams().forEach(team -> {
            if (!holder.hasTeamEntry(team.getName())) {
                holder.addTeam(team.getName(), TeamColor.fromApiColor(team.getColor()).chatColor);
            }
            final var scoreboardTeam = holder.getTeamOrRegister(team.getName());

            new HashSet<>(scoreboardTeam.getEntries())
                    .stream()
                    .filter(Objects::nonNull)
                    .map(Bukkit::getPlayerExact)
                    .filter(Objects::nonNull)
                    .forEach(teamPlayer -> {
                        if (!team.getConnectedPlayers().contains(teamPlayer)) {
                            scoreboardTeam.removeEntry(teamPlayer.getName());
                        }
                    });

            team.getConnectedPlayers()
                    .stream()
                    .map(Player::getName)
                    .filter(playerName -> !scoreboardTeam.hasEntry(playerName))
                    .forEach(scoreboardTeam::addEntry);
        });
        return lines;
    }
}
