package me.regadpole.plumbot.event.kook;

import com.velocitypowered.api.proxy.Player;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.bot.KookBot;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.internal.DbConfig;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.tool.StringTool;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import snw.jkook.event.EventHandler;
import snw.jkook.event.Listener;
import snw.jkook.event.channel.ChannelMessageEvent;
import snw.jkook.event.pm.PrivateMessageReceivedEvent;
import snw.jkook.event.user.UserLeaveGuildEvent;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KookEvent implements Listener {

    public static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder().build();

    private final PlumBot plugin;
    private final KookBot kBot;
    private final String Prefix = Config.config.Forwarding.prefix;

    public KookEvent(KookBot kookBot, PlumBot plugin) {
        this.kBot = kookBot;
        this.plugin = plugin;
    }

    @EventHandler
    public void onChannelMessageReceive(ChannelMessageEvent e) {

        // 频道白名单：只处理配置的 KOOK 频道
        List<String> kookGroups = Config.bot.KookGroups;
        if (kookGroups == null || kookGroups.isEmpty()) return;

        boolean inConfiguredChannel = false;
        for (String groupId : kookGroups) {
            if (e.getChannel().getId().equalsIgnoreCase(groupId)) {
                inConfiguredChannel = true;
                break;
            }
        }
        if (!inConfiguredChannel) return;

        Pattern pattern;
        Matcher matcher;

        ArrayList<String> groups = new ArrayList<>(kookGroups);

        ArrayList<String> admins = new ArrayList<>();
        for (long adminId : Config.bot.Admins) {
            admins.add(kBot.getUser(adminId).getId());
        }

        String msg = e.getMessage().getComponent().toString();
        String senderID = e.getMessage().getSender().getId();
        String senderName = e.getMessage().getSender().getNickName(e.getChannel().getGuild());
        String groupName = e.getChannel().getGuild().getName() + "/" + e.getChannel().getName();

        // XML 消息过滤
        pattern = Pattern.compile("<?xm.*");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            String sendmsg = "§6" + "[" + groupName + "]" + "§f" + ":" + "不支持的消息类型，请在群聊中查看";
            plugin.getServer().getAllServers().forEach(server -> {
                server.sendMessage(SERIALIZER.deserialize(sendmsg));
            });
            return;
        }

        // 特殊消息过滤
        pattern = Pattern.compile("\"ap.*");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            String sendmsg = "§6" + "[" + groupName + "]" + "§f" + ":" + "不支持的消息类型，请在群聊中查看";
            plugin.getServer().getAllServers().forEach(server -> {
                server.sendMessage(SERIALIZER.deserialize(sendmsg));
            });
            return;
        }

        // 管理员命令：删除白名单
        if (admins.contains(senderID)) {
            pattern = Pattern.compile(Prefix + "删除白名单 .*");
            matcher = pattern.matcher(msg);
            if (matcher.find()) {
                if (!Config.config.WhiteList.enable) {
                    kBot.sendChannelReply(e, "白名单功能未开启");
                    return;
                }
                String name = matcher.group().replace(Prefix + "删除白名单 ", "");
                if (name.isEmpty()) {
                    kBot.sendChannelReply(e, "id不能为空");
                    return;
                }
                plugin.getServer().getScheduler().buildTask(plugin, () -> {
                    long nameForId = DatabaseManager.getBindId(name, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                    if (nameForId == 0L) {
                        kBot.sendChannelReply(e, "尚未申请白名单");
                        return;
                    }
                    DatabaseManager.removeBindid(name, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                    kBot.sendChannelReply(e, "成功移出白名单");
                }).schedule();
                return;
            }

            pattern = Pattern.compile(Prefix + "删除User白名单 .*");
            matcher = pattern.matcher(msg);
            if (matcher.find()) {
                if (!Config.config.WhiteList.enable) {
                    kBot.sendChannelReply(e, "白名单功能未开启");
                    return;
                }
                String kookId = matcher.group().replace(Prefix + "删除User白名单 ", "");
                if (kookId.isEmpty()) {
                    kBot.sendChannelReply(e, "KOOK ID不能为空");
                    return;
                }
                PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {
                    String idForName = DatabaseManager.getBind(kookId, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                    if (idForName == null) {
                        kBot.sendChannelReply(e, "尚未申请白名单");
                        return;
                    }
                    DatabaseManager.removeBind(kookId, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                    kBot.sendChannelReply(e, "成功移出白名单");
                }).schedule();
                return;
            }
        }

        // 帮助
        if (msg.equals(Prefix + "帮助")) {
            List<String> messages = new LinkedList<>();
            StringBuilder stringBuilder = new StringBuilder();
            messages.add("成员命令:");
            messages.add("/在线人数 查看服务器当前在线人数");
            messages.add("/申请白名单 <ID> 为自己申请白名单");
            messages.add("/删除白名单 删除自己的白名单");
            messages.add("管理命令:");
            messages.add("/删除白名单 <ID> 删除指定游戏id的白名单");
            messages.add("/删除User白名单 <KOOK ID> 删除指定群成员的白名单");
            for (String message : messages) {
                if (messages.get(messages.size() - 1).equalsIgnoreCase(message)) {
                    stringBuilder.append(message.replaceAll("§\\S", ""));
                } else {
                    stringBuilder.append(message.replaceAll("§\\S", "")).append("\n");
                }
            }
            kBot.sendChannelReply(e, stringBuilder.toString());
            return;
        }

        // 在线人数
        if (msg.equals(Prefix + "在线人数")) {
            if (!Config.config.Online) {
                return;
            }
            List<String> pname = new ArrayList<>();
            for (Player player : plugin.getServer().getAllPlayers()) {
                pname.add(player.getUsername());
            }
            kBot.sendChannelReply(e, "当前在线：" + "(" + plugin.getServer().getAllPlayers().size() + "人)" + pname);
            return;
        }

        // 申请白名单
        pattern = Pattern.compile(Prefix + "申请白名单 .*");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            if (!Config.config.WhiteList.enable) {
                kBot.sendChannelReply(e, "白名单功能未开启");
                return;
            }
            String PlayerName = matcher.group().replace(Prefix + "申请白名单 ", "");
            if (PlayerName.isEmpty()) {
                kBot.sendChannelReply(e, "id不能为空");
                return;
            }
            plugin.getServer().getScheduler().buildTask(plugin, () -> {
                if ((DatabaseManager.getBind(senderID, DbConfig.type.toLowerCase(), PlumBot.getDatabase()) != null)
                        || (DatabaseManager.getBindId(PlayerName, DbConfig.type.toLowerCase(), PlumBot.getDatabase()) != 0L)) {
                    kBot.sendChannelReply(e, "绑定失败");
                    return;
                }
                DatabaseManager.addBind(PlayerName, senderID, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                kBot.sendChannelReply(e, "成功申请白名单");
            }).schedule();
            return;
        }

        // 删除自己的白名单
        pattern = Pattern.compile("/删除白名单");
        matcher = pattern.matcher(msg);
        if (matcher.find()) {
            if (!Config.config.WhiteList.enable) {
                kBot.sendChannelReply(e, "白名单功能未开启");
                return;
            }
            PlumBot.INSTANCE.getServer().getScheduler().buildTask(PlumBot.INSTANCE, () -> {
                String idForName = DatabaseManager.getBind(String.valueOf(senderID), DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                if (idForName == null || idForName.isEmpty()) {
                    kBot.sendChannelReply(e, "您尚未申请白名单");
                    return;
                }
                DatabaseManager.removeBind(String.valueOf(senderID), DbConfig.type.toLowerCase(), PlumBot.getDatabase());
                kBot.sendChannelReply(e, "成功移出白名单");
            }).schedule();
            return;
        }

        // 自动回复
        if (Config.config.SDR) {
            if (plugin.vconf.getReturnsObj().get(msg) == null) {
                // fall through to forwarding
            } else {
                String back = String.valueOf(plugin.vconf.getReturnsObj().get(msg));
                if (back != null) {
                    kBot.sendChannelReply(e, back);
                    return;
                }
            }
        }

        // 消息转发到 MC
        if (!Config.config.Forwarding.enable) {
            return;
        }

        if (Config.config.Forwarding.mode == 1 && groups.contains(e.getChannel().getId())) {
            pattern = Pattern.compile(Config.config.Forwarding.prefix + ".*");
            matcher = pattern.matcher(msg);
            if (!matcher.find()) {
                return;
            }
            String fmsg = msg.replace(Config.config.Forwarding.prefix, "");
            String name = StringTool.filterColor(senderName);
            String smsg = StringTool.filterColor(fmsg);
            String sendmsg = "§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg;
            plugin.getServer().getAllServers().forEach(server -> {
                server.sendMessage(SERIALIZER.deserialize(sendmsg));
            });
            return;
        }

        if (groups.contains(e.getChannel().getId())) {
            String name = StringTool.filterColor(senderName);
            String smsg = StringTool.filterColor(msg);
            String sendmsg = "§6" + "[" + groupName + "]" + "§a" + name + "§f" + ":" + smsg;
            plugin.getServer().getAllServers().forEach(server -> {
                server.sendMessage(SERIALIZER.deserialize(sendmsg));
            });
        }
    }

    @EventHandler
    public void onPrivateMessageReceive(PrivateMessageReceivedEvent e) {

        // 私聊只响应管理员
        boolean isAdmin = false;
        for (long adminId : Config.bot.Admins) {
            if (e.getUser().getId().equalsIgnoreCase(kBot.getUser(adminId).getId())) {
                isAdmin = true;
                break;
            }
        }
        if (!isAdmin) return;

        if (e.getMessage().toString().equals(Prefix + "在线人数")) {
            if (!Config.config.Online) {
                return;
            }
            List<String> pname = new ArrayList<>();
            for (Player player : plugin.getServer().getAllPlayers()) {
                pname.add(player.getUsername());
            }
            kBot.sendPrivateReply(e, "当前在线：" + "(" + plugin.getServer().getAllPlayers().size() + "人)" + pname);
        }
    }

    @EventHandler
    public void onGroupDecreaseNotice(UserLeaveGuildEvent e) {
        String userId = e.getUser().getId();
        String player = DatabaseManager.getBind(userId, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
        if (player == null) {
            return;
        }
        DatabaseManager.removeBindid(player, DbConfig.type.toLowerCase(), PlumBot.getDatabase());
    }
}
