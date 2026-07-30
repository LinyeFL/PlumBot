package me.regadpole.plumbot.event.server;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.bot.Bot;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.internal.DbConfig;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.tool.StringTool;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerEvent {

    private final Set<UUID> connectedPlayers = ConcurrentHashMap.newKeySet();

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Pattern pattern;
        Matcher matcher;

        if (!Config.config.Forwarding.enable) {
            return;
        }

        String name = StringTool.filterColor(event.getPlayer().getUsername());
        String message = StringTool.filterColor(event.getMessage());
        String server = event.getPlayer().getCurrentServer()
                .map(connection -> connection.getServer().getServerInfo().getName())
                .orElse("unknown");

        if (Config.config.Forwarding.mode == 1) {
            pattern = Pattern.compile(Config.config.Forwarding.prefix + ".*");
            matcher = pattern.matcher(message);
            if (!matcher.find()) {
                return;
            }

            String forwardedMessage = matcher.group()
                    .replaceAll(Config.config.Forwarding.prefix, "");
            sendToAllPlatforms(formatChat(server, name, forwardedMessage));
            return;
        }

        sendToAllPlatforms(formatChat(server, name, message));
    }

    @Subscribe
    public void onPreConnect(ServerPreConnectEvent event) {
        String name = StringTool.filterColor(event.getPlayer().getUsername());

        if (Config.config.WhiteList.enable) {
            PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {
                long qq;
                qq = (DatabaseManager.getBindId(name, DbConfig.type.toLowerCase(), PlumBot.getDatabase()));
                if (qq == 0L) {
                    PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {
                        event.getPlayer().disconnect(Component.text(Config.config.WhiteList.kickMsg));
                    }).delay(2L, TimeUnit.SECONDS).schedule();
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    sendToAllPlatforms("玩家" + name + "因为未在白名单中被踢出");
                    return;
                }
                for (long groupID : Config.bot.Groups) {
                    Bot qqBot = PlumBot.getQQBot();
                    if (qqBot != null && !qqBot.checkUserInGroup(qq, groupID)) {
                        PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {
                            event.getPlayer().disconnect(Component.text(Config.config.WhiteList.kickMsg));
                        }).delay(2L, TimeUnit.SECONDS).schedule();
                        event.setResult(ServerPreConnectEvent.ServerResult.denied());
                        sendToAllPlatforms("玩家" + name + "因为未在白名单中被踢出");
                        DatabaseManager.removeBind(String.valueOf(qq), DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                        return;
                    }
                }
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(event.getOriginalServer()));
            }).schedule();
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        String name = StringTool.filterColor(event.getPlayer().getUsername());
        String toServer = event.getServer().getServerInfo().getName();

        boolean firstConnection = connectedPlayers.add(event.getPlayer().getUniqueId());

        if (firstConnection) {
            if (Config.messages.Notifications.joinQuitEnabled) {
                sendNotificationToAllPlatforms(format(
                        Config.messages.Notifications.join,
                        "{player}", name,
                        "{server}", getServerDisplayName(toServer)
                ));
            }
            return;
        }

        if (!Config.messages.Notifications.serverSwitchEnabled
                || event.getPreviousServer().isEmpty()) {
            return;
        }

        String fromServer = event.getPreviousServer()
                .get()
                .getServerInfo()
                .getName();

        if (fromServer.equals(toServer)) {
            return;
        }

        sendNotificationToAllPlatforms(format(
                Config.messages.Notifications.switchServer,
                "{player}", name,
                "{from_server}", getServerDisplayName(fromServer),
                "{to_server}", getServerDisplayName(toServer)
        ));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (!connectedPlayers.remove(event.getPlayer().getUniqueId())) {
            return;
        }

        if (!Config.messages.Notifications.joinQuitEnabled) {
            return;
        }

        String name = StringTool.filterColor(event.getPlayer().getUsername());
        sendNotificationToAllPlatforms(format(
                Config.messages.Notifications.quit,
                "{player}", name
        ));
    }

    private String formatChat(String server, String player, String message) {
        return format(
                Config.messages.Chat.format,
                "{server}", getServerDisplayName(server),
                "{player}", player,
                "{message}", message
        );
    }

    private String getServerDisplayName(String serverName) {
        if (Config.messages.Servers == null) {
            return serverName;
        }
        return Config.messages.Servers.getOrDefault(serverName, serverName);
    }

    private String format(String template, String... replacements) {
        String result = template == null ? "" : template;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace(replacements[i], replacements[i + 1]);
        }
        return result;
    }

    // === 双平台扇出 ===

    private void sendToAllPlatforms(String message) {
        // QQ
        Bot qqBot = PlumBot.getQQBot();
        if (qqBot != null) {
            List<Long> qqGroups = Config.bot.Groups;
            for (long groupID : qqGroups) {
                if (!isForwardEnabled(groupID)) continue;
                qqBot.sendMsg(true, message, groupID);
            }
        }
        // KOOK
        Bot kookBot = PlumBot.getKookBot();
        if (kookBot != null) {
            List<Long> kookGroups = Config.bot.KookGroups;
            if (kookGroups != null) {
                for (long channelID : kookGroups) {
                    kookBot.sendMsg(true, message, channelID);
                }
            }
        }
    }

    private void sendNotificationToAllPlatforms(String message) {
        // QQ（受群通知开关控制）
        Bot qqBot = PlumBot.getQQBot();
        if (qqBot != null) {
            List<Long> qqGroups = Config.bot.Groups;
            for (long groupID : qqGroups) {
                if (!isNotifyEnabled(groupID)) continue;
                qqBot.sendMsg(true, message, groupID);
            }
        }
        // KOOK（直接发，暂无频道级通知开关）
        Bot kookBot = PlumBot.getKookBot();
        if (kookBot != null) {
            List<Long> kookGroups = Config.bot.KookGroups;
            if (kookGroups != null) {
                for (long channelID : kookGroups) {
                    kookBot.sendMsg(true, message, channelID);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isNotifyEnabled(long groupId) {
        try {
            Map<String, Object> msgObj = PlumBot.INSTANCE.vconf.getMessagesObj();
            if (msgObj != null) {
                Map<String, Object> qqMap = (Map<String, Object>) msgObj.get("QQ");
                if (qqMap != null) {
                    Object switchesObj = qqMap.get("group-switches");
                    if (switchesObj instanceof Map) {
                        Map<String, Object> switches = (Map<String, Object>) switchesObj;
                        Object groupSwitchObj = switches.get(String.valueOf(groupId));
                        if (groupSwitchObj instanceof Map) {
                            Map<String, Object> groupSwitch = (Map<String, Object>) groupSwitchObj;
                            Object notify = groupSwitch.get("notify");
                            if (notify != null) {
                                return Boolean.parseBoolean(String.valueOf(notify));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 静默回退
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean isForwardEnabled(long groupId) {
        try {
            Map<String, Object> msgObj = PlumBot.INSTANCE.vconf.getMessagesObj();
            if (msgObj != null) {
                Map<String, Object> qqMap = (Map<String, Object>) msgObj.get("QQ");
                if (qqMap != null) {
                    Object switchesObj = qqMap.get("group-switches");
                    if (switchesObj instanceof Map) {
                        Map<String, Object> switches = (Map<String, Object>) switchesObj;
                        Object groupSwitchObj = switches.get(String.valueOf(groupId));
                        if (groupSwitchObj instanceof Map) {
                            Map<String, Object> groupSwitch = (Map<String, Object>) groupSwitchObj;
                            Object forward = groupSwitch.get("forward");
                            if (forward != null) {
                                return Boolean.parseBoolean(String.valueOf(forward));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 静默回退
        }
        return true;
    }
}
