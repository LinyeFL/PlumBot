package me.regadpole.plumbot.event.server;
 
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import me.regadpole.plumbot.PlumBot;
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
    /**
     * 只有成功连接过至少一个后端服务器的玩家才会进入此集合。
     *
     * ServerConnectedEvent 在首次进入和跨服时都会触发，因此不能把该事件
     * 本身直接当作"加入代理网络"。DisconnectEvent 则代表彻底离开代理。
     */
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
            sendToGroups(formatChat(server, name, forwardedMessage));
            return;
        }
 
        sendToGroups(formatChat(server, name, message));
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
                    List<Long> groups = Config.bot.Groups;
                    for (long groupID : groups) {
                        PlumBot.getBot().sendMsg(true, "玩家" + name + "因为未在白名单中被踢出", groupID);
                    }
                    return;
                }
                for (long groupID : Config.bot.Groups) {
                    if (!PlumBot.getBot().checkUserInGroup(qq, groupID)) {
                        PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {
                            event.getPlayer().disconnect(Component.text(Config.config.WhiteList.kickMsg));
                        }).delay(2L, TimeUnit.SECONDS).schedule();
                        event.setResult(ServerPreConnectEvent.ServerResult.denied());
                        List<Long> groups = Config.bot.Groups;
                        for (long group : groups) {
                            PlumBot.getBot().sendMsg(true, "玩家" + name + "因为未在白名单中被踢出", group);
                        }
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
 
        /*
         * add 返回 true 表示这是该玩家首次成功连接后端服，即首次进入代理网络。
         * 后续 ServerConnectedEvent 均属于重连当前服或跨子服，不再重复发送加入通知。
         */
        boolean firstConnection = connectedPlayers.add(event.getPlayer().getUniqueId());
 
        if (firstConnection) {
            if (Config.messages.Notifications.joinQuitEnabled) {
                sendNotificationToGroups(format(
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
 
        // 某些重连场景可能产生同服连接事件，不应被报告为跨服。
        if (fromServer.equals(toServer)) {
            return;
        }
 
        sendNotificationToGroups(format(
                Config.messages.Notifications.switchServer,
                "{player}", name,
                "{from_server}", getServerDisplayName(fromServer),
                "{to_server}", getServerDisplayName(toServer)
        ));
    }
 
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        /*
         * 未成功进入过任何后端服的连接（登录失败、白名单拒绝等）不会发送离开通知。
         * remove 返回 true 时才表示玩家此前确实在代理网络内。
         */
        if (!connectedPlayers.remove(event.getPlayer().getUniqueId())) {
            return;
        }
 
        if (!Config.messages.Notifications.joinQuitEnabled) {
            return;
        }
 
        String name = StringTool.filterColor(event.getPlayer().getUsername());
        sendNotificationToGroups(format(
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
 
    private void sendToGroups(String message) {
        List<Long> groups = Config.bot.Groups;
        for (long groupID : groups) {
            if (!isForwardEnabled(groupID)) {
                continue;
            }
            PlumBot.getBot().sendMsg(true, message, groupID);
        }
    }
 
    // 改进4：发送通知到各群，受群级别通知开关控制
    private void sendNotificationToGroups(String message) {
        List<Long> groups = Config.bot.Groups;
        for (long groupID : groups) {
            if (!isNotifyEnabled(groupID)) {
                continue;
            }
            PlumBot.getBot().sendMsg(true, message, groupID);
        }
    }
 
    // 改进4：检查群通知开关（默认开）
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

    // 检查群转发开关（默认开）
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
