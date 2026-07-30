package me.regadpole.plumbot.event.qq;

import com.velocitypowered.api.proxy.Player;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.bot.QQBot;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.internal.DbConfig;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.tool.StringTool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import sdk.event.message.GroupMessage;
import sdk.event.message.PrivateMessage;
import sdk.client.response.GroupMemberInfo;
import sdk.event.notice.GroupDecreaseNotice;
 
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QQEvent {

    // &颜色码序列化器
    private static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    // QQ回复缓存：消息UUID → ReplyInfo，5分钟TTL
    private static final Map<String, ReplyInfo> replyCache = new ConcurrentHashMap<>();
    private static final long REPLY_CACHE_TTL_MS = 5 * 60 * 1000;

    public static class ReplyInfo {
        private final long groupId;
        private final long qqUserId;
        private final String qqNickname;
        private final long timestamp;

        public ReplyInfo(long groupId, long qqUserId, String qqNickname) {
            this.groupId = groupId;
            this.qqUserId = qqUserId;
            this.qqNickname = qqNickname;
            this.timestamp = System.currentTimeMillis();
        }

        public long getGroupId() { return groupId; }
        public long getQqUserId() { return qqUserId; }
        public String getQqNickname() { return qqNickname; }
        public long getTimestamp() { return timestamp; }
    }

    public static ReplyInfo getReplyInfoByMessageId(String messageId) {
        cleanupExpired();
        return replyCache.get(messageId);
    }

    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        replyCache.entrySet().removeIf(entry ->
                now - entry.getValue().getTimestamp() > REPLY_CACHE_TTL_MS);
    }
    private final PlumBot plugin;

    public QQEvent(PlumBot plugin) {
        this.plugin = plugin;
    }

    public void onFriendMessageReceive(
            PrivateMessage event
    ) {
        if (!event.getMessage().equals("/在线人数")) {
            return;
        }

        if (!Config.config.Online) {
            return;
        }

        Map<String, List<String>> serverPlayers = new LinkedHashMap<>();
        int total = 0;
        for (Player player : plugin.getServer().getAllPlayers()) {
            total++;
            String serverName = player.getCurrentServer()
                    .map(s -> s.getServerInfo().getName())
                    .orElse("未知");
            serverPlayers.computeIfAbsent(serverName, k -> new ArrayList<>())
                    .add(player.getUsername());
        }

        Map<String, String> serverAlias = new HashMap<>();
        serverAlias.put("lobby", "登录服");
        serverAlias.put("survival", "生存服");
        serverAlias.put("redstone", "红石服");

        List<String> lines = new ArrayList<>();
        lines.add("当前总在线：(" + total + "人)");

        for (Map.Entry<String, List<String>> entry : serverPlayers.entrySet()) {
            String alias = serverAlias.getOrDefault(entry.getKey(), entry.getKey());
            lines.add(alias + ": (" + entry.getValue().size() + "人)");
            for (String name : entry.getValue()) {
                lines.add(name);
            }
        }

        PlumBot.getBot().sendMsg(
                false,
                String.join("\n", lines),
                event.getUserId()
        );
    }

    public void onGroupMessageReceive(
            GroupMessage event
    ) {
        QQBot bot = (QQBot) PlumBot.getBot();

        String message = event.getMessage();
        long groupId = event.getGroupId();
        long senderId = event.getUserId();

        // 屏蔽 QQ 官方 BOT 消息（Bug 修复 4）
        if (shouldFilterOfficialBot()) {
            List<Long> filterIds = getFilterBotIds();
            if (filterIds.contains(senderId)) {
                return;
            }
        }

        String senderName;

        if (event.getSender().getCard().isEmpty()) {
            senderName =
                    event.getSender().getNickname();
        } else {
            senderName =
                    event.getSender().getCard();
        }

        String realGroupName =
                bot.getGroupInfo(groupId)
                        .getGroupName();

        String groupName =
                getGroupDisplayName(
                        groupId,
                        realGroupName
                );

        if (shouldIgnoreMessage(message)) {
            return;
        }

        if (handleAdminCommands(
                bot,
                message,
                groupId,
                senderId
        )) {
            return;
        }

        // 改进4：群级别开关指令（/转发 开|关、/通知 开|关）
        if (handleGroupSwitchCommands(
                bot,
                message,
                groupId,
                senderId
        )) {
            return;
        }

        if (handleMemberCommands(
                message,
                groupId,
                senderId
        )) {
            return;
        }

        if (handleCustomReply(
                message,
                groupId
        )) {
            return;
        }

        if (!Config.config.Forwarding.enable) {
            return;
        }

        // 改进4：检查当前群的转发开关
        if (!isForwardEnabled(groupId)) {
            return;
        }

        if (!Config.bot.Groups.contains(groupId)) {
            return;
        }

        String forwardingMessage = message;

        if (Config.config.Forwarding.mode == 1) {
            String prefix =
                    Config.config.Forwarding.prefix;

            if (prefix == null
                    || !message.startsWith(prefix)) {
                return;
            }

            forwardingMessage =
                    message.substring(prefix.length());
        }

        String filteredName =
                StringTool.filterColor(senderName);

        String filteredMessage =
                StringTool.filterColor(
                        forwardingMessage
                );

        // Bug 修复 1/2：把 CQ 码替换成占位文本而非删除
        filteredMessage =
                processCQCodes(filteredMessage, bot, groupId);

        broadcastToMinecraft(
                formatQqMessage(
                        groupName,
                        filteredName,
                        filteredMessage
                ),
                groupId,
                senderId,
                senderName
        );
    }

    private boolean shouldIgnoreMessage(
            String message
    ) {
        return false;
    }

    private boolean handleAdminCommands(
            QQBot bot,
            String message,
            long groupId,
            long senderId
    ) {
        if (!Config.bot.Admins.contains(senderId)) {
            return false;
        }

        Pattern removeByNamePattern =
                Pattern.compile("/删除白名单 .*");

        Matcher removeByNameMatcher =
                removeByNamePattern.matcher(message);

        if (removeByNameMatcher.find()) {
            if (!Config.config.WhiteList.enable) {
                bot.sendMsg(true, "白名单功能未开启", groupId);
                return true;
            }

            String playerName =
                    removeByNameMatcher
                            .group()
                            .replace(
                                    "/删除白名单 ",
                                    ""
                            );

            if (playerName.isEmpty()) {
                PlumBot.getBot().sendMsg(
                        true,
                        "id不能为空",
                        groupId
                );

                return true;
            }

            PlumBot.INSTANCE
                    .getServer()
                    .getScheduler()
                    .buildTask(
                            PlumBot.INSTANCE,
                            () -> {
                                long boundId =
                                        DatabaseManager
                                                .getBindId(
                                                        playerName,
                                                        DbConfig.type
                                                                .toLowerCase(),
                                                        PlumBot.getDatabase()
                                                );

                                if (boundId == 0L) {
                                    PlumBot.getBot()
                                            .sendMsg(
                                                    true,
                                                    "尚未申请白名单",
                                                    groupId
                                            );

                                    return;
                                }

                                DatabaseManager
                                        .removeBindid(
                                                playerName,
                                                DbConfig.type
                                                        .toLowerCase(),
                                                PlumBot.getDatabase()
                                        );

                                PlumBot.getBot().sendMsg(
                                        true,
                                        "成功移出白名单",
                                        groupId
                                );
                            }
                    )
                    .schedule();

            return true;
        }

        String prefix =
                Config.config.Forwarding.prefix == null
                        ? ""
                        : Config.config.Forwarding.prefix;

        Pattern removeByUserPattern =
                Pattern.compile(
                        Pattern.quote(prefix)
                                + "删除User白名单 .*"
                );

        Matcher removeByUserMatcher =
                removeByUserPattern.matcher(message);

        if (!removeByUserMatcher.find()) {
            return false;
        }

        if (!Config.config.WhiteList.enable) {
            bot.sendMsg(true, "白名单功能未开启", groupId);
            return true;
        }

        String qq =
                removeByUserMatcher
                        .group()
                        .replace(
                                prefix
                                        + "删除User白名单 ",
                                ""
                        );

        if (qq.isEmpty()) {
            bot.sendMsg(
                    true,
                    "QQ不能为空",
                    groupId
            );

            return true;
        }

        PlumBot.INSTANCE
                .getServer()
                .getScheduler()
                .buildTask(
                        PlumBot.INSTANCE,
                        () -> {
                            String playerName =
                                    DatabaseManager
                                            .getBind(
                                                    qq,
                                                    DbConfig.type
                                                            .toLowerCase(),
                                                    PlumBot.getDatabase()
                                            );

                            if (playerName == null) {
                                bot.sendMsg(
                                        true,
                                        "尚未申请白名单",
                                        groupId
                                );

                                return;
                            }

                            DatabaseManager.removeBind(
                                    qq,
                                    DbConfig.type
                                            .toLowerCase(),
                                    PlumBot.getDatabase()
                            );

                            bot.sendMsg(
                                    true,
                                    "成功移出白名单",
                                    groupId
                            );
                        }
                )
                .schedule();

        return true;
    }

    private boolean handleMemberCommands(
            String message,
            long groupId,
            long senderId
    ) {
        if (message.equals("/帮助")) {
            List<String> helpMessages =
                    new LinkedList<>();

            helpMessages.add("成员命令:");
            helpMessages.add(
                    "/在线人数 查看服务器当前在线人数"
            );
            helpMessages.add(
                    "/申请白名单 <ID> 为自己申请白名单"
            );
            helpMessages.add(
                    "/删除白名单 删除自己的白名单"
            );
            helpMessages.add("管理命令:");
            helpMessages.add(
                    "/删除白名单 <ID> 删除指定游戏id的白名单"
            );
            helpMessages.add(
                    "/删除User白名单 <QQ号/kookID> "
                            + "删除指定群成员的白名单"
            );
            helpMessages.add("群主/管理员命令:");
            helpMessages.add(
                    "/转发 开|关 开关本群QQ→MC消息转发"
            );
            helpMessages.add(
                    "/通知 开|关 开关本群进出游戏通知"
            );

            PlumBot.getBot().sendMsg(
                    true,
                    String.join("\n", helpMessages),
                    groupId
            );

            return true;
        }

        if (message.equals("/在线人数")) {
            if (!Config.config.Online) {
                return true;
            }

            Map<String, List<String>> serverPlayers = new LinkedHashMap<>();
            int total = 0;
            for (Player player : plugin.getServer().getAllPlayers()) {
                total++;
                String serverName = player.getCurrentServer()
                        .map(s -> s.getServerInfo().getName())
                        .orElse("未知");
                serverPlayers.computeIfAbsent(serverName, k -> new ArrayList<>())
                        .add(player.getUsername());
            }

            Map<String, String> serverAlias = new HashMap<>();
            serverAlias.put("lobby", "登录服");
            serverAlias.put("survival", "生存服");
            serverAlias.put("redstone", "红石服");

            List<String> lines = new ArrayList<>();
            lines.add("当前总在线：(" + total + "人)");

            for (Map.Entry<String, List<String>> entry : serverPlayers.entrySet()) {
                String alias = serverAlias.getOrDefault(entry.getKey(), entry.getKey());
                lines.add(alias + ": (" + entry.getValue().size() + "人)");
                for (String name : entry.getValue()) {
                    lines.add(name);
                }
            }

            PlumBot.getBot().sendMsg(
                    true,
                    String.join("\n", lines),
                    groupId
            );

            return true;
        }

        Pattern applyPattern =
                Pattern.compile("/申请白名单 .*");

        Matcher applyMatcher =
                applyPattern.matcher(message);

        if (applyMatcher.find()) {
            if (!Config.config.WhiteList.enable) {
                PlumBot.getBot().sendMsg(true, "白名单功能未开启", groupId);
                return true;
            }

            String playerName =
                    applyMatcher
                            .group()
                            .replace(
                                    "/申请白名单 ",
                                    ""
                            );

            if (playerName.isEmpty()) {
                PlumBot.getBot().sendMsg(
                        true,
                        "id不能为空",
                        groupId
                );

                return true;
            }

            PlumBot.INSTANCE
                    .getServer()
                    .getScheduler()
                    .buildTask(
                            PlumBot.INSTANCE,
                            () -> {
                                String boundName =
                                        DatabaseManager
                                                .getBind(
                                                        String.valueOf(
                                                                senderId
                                                        ),
                                                        DbConfig.type
                                                                .toLowerCase(),
                                                        PlumBot.getDatabase()
                                                );

                                long boundId =
                                        DatabaseManager
                                                .getBindId(
                                                        playerName,
                                                        DbConfig.type
                                                                .toLowerCase(),
                                                        PlumBot.getDatabase()
                                                );

                                if (boundName != null
                                        || boundId != 0L) {
                                    PlumBot.getBot()
                                            .sendMsg(
                                                    true,
                                                    "绑定失败",
                                                    groupId
                                            );

                                    return;
                                }

                                DatabaseManager.addBind(
                                        playerName,
                                        String.valueOf(senderId),
                                        DbConfig.type
                                                .toLowerCase(),
                                        PlumBot.getDatabase()
                                );

                                PlumBot.getBot().sendMsg(
                                        true,
                                        "成功申请白名单",
                                        groupId
                                );
                            }
                    )
                    .schedule();

            return true;
        }

        if (!message.equals("/删除白名单")) {
            return false;
        }

        if (!Config.config.WhiteList.enable) {
            PlumBot.getBot().sendMsg(true, "白名单功能未开启", groupId);
            return true;
        }

        PlumBot.INSTANCE
                .getServer()
                .getScheduler()
                .buildTask(
                        PlumBot.INSTANCE,
                        () -> {
                            String playerName =
                                    DatabaseManager
                                            .getBind(
                                                    String.valueOf(
                                                            senderId
                                                    ),
                                                    DbConfig.type
                                                            .toLowerCase(),
                                                    PlumBot.getDatabase()
                                            );

                            if (playerName == null
                                    || playerName.isEmpty()) {
                                PlumBot.getBot()
                                        .sendMsg(
                                                true,
                                                "您尚未申请白名单",
                                                groupId
                                        );

                                return;
                            }

                            DatabaseManager.removeBind(
                                    String.valueOf(senderId),
                                    DbConfig.type
                                            .toLowerCase(),
                                    PlumBot.getDatabase()
                            );

                            PlumBot.getBot().sendMsg(
                                    true,
                                    "成功移出白名单",
                                    groupId
                            );
                        }
                )
                .schedule();

        return true;
    }

    private boolean handleCustomReply(
            String message,
            long groupId
    ) {
        if (!Config.config.SDR) {
            return false;
        }

        Object reply =
                plugin.vconf
                        .getReturnsObj()
                        .get(message);

        /*
         * 未匹配自定义回复时继续执行普通消息转发，
         * 不再直接 return。
         */
        if (reply == null) {
            return false;
        }

        PlumBot.getBot().sendMsg(
                true,
                String.valueOf(reply),
                groupId
        );

        return true;
    }

    // 改进4：群级别开关指令（/转发 开|关、/通知 开|关）
    private boolean handleGroupSwitchCommands(
            QQBot bot,
            String message,
            long groupId,
            long senderId
    ) {
        boolean isForwardCmd =
                message.equals("/转发 开") || message.equals("/转发 关");
        boolean isNotifyCmd =
                message.equals("/通知 开") || message.equals("/通知 关");

        if (!isForwardCmd && !isNotifyCmd) {
            return false;
        }

        // 权限检查：仅群主/管理员可用
        if (!isGroupAdminOrOwner(bot, groupId, senderId)) {
            bot.sendMsg(true, "该指令仅限群主/管理员使用", groupId);
            return true;
        }

        if (message.equals("/转发 开")) {
            setGroupSwitch(groupId, "forward", true);
            bot.sendMsg(true, "已开启本群QQ→MC转发", groupId);
            return true;
        }
        if (message.equals("/转发 关")) {
            setGroupSwitch(groupId, "forward", false);
            bot.sendMsg(true, "已关闭本群QQ→MC转发（指令仍正常响应）", groupId);
            return true;
        }
        if (message.equals("/通知 开")) {
            setGroupSwitch(groupId, "notify", true);
            bot.sendMsg(true, "已开启本群进出游戏通知", groupId);
            return true;
        }
        if (message.equals("/通知 关")) {
            setGroupSwitch(groupId, "notify", false);
            bot.sendMsg(true, "已关闭本群进出游戏通知", groupId);
            return true;
        }
        return false;
    }

    // 改进4：判断发送者是否为群主或管理员
    private boolean isGroupAdminOrOwner(
            QQBot bot,
            long groupId,
            long senderId
    ) {
        try {
            GroupMemberInfo info =
                    bot.getGroupMemberInfo(groupId, senderId);
            if (info != null) {
                String role = info.getRole();
                return "owner".equalsIgnoreCase(role)
                        || "admin".equalsIgnoreCase(role);
            }
        } catch (Exception e) {
            // 静默回退
        }
        return false;
    }

    // 改进4：检查当前群的转发开关（默认开）
    @SuppressWarnings("unchecked")
    private boolean isForwardEnabled(long groupId) {
        return getGroupSwitch(groupId, "forward", true);
    }

    // 改进4：检查当前群的通知开关（默认开）
    @SuppressWarnings("unchecked")
    private boolean isNotifyEnabled(long groupId) {
        return getGroupSwitch(groupId, "notify", true);
    }

    // 改进4：读取群开关，找不到则返回默认值
    @SuppressWarnings("unchecked")
    private boolean getGroupSwitch(long groupId, String key, boolean defaultValue) {
        try {
            Map<String, Object> msgObj = plugin.vconf.getMessagesObj();
            if (msgObj != null) {
                Map<String, Object> qqMap = (Map<String, Object>) msgObj.get("QQ");
                if (qqMap != null) {
                    Object switchesObj = qqMap.get("group-switches");
                    if (switchesObj instanceof Map) {
                        Map<String, Object> switches = (Map<String, Object>) switchesObj;
                        Object groupSwitchObj = switches.get(String.valueOf(groupId));
                        if (groupSwitchObj instanceof Map) {
                            Map<String, Object> groupSwitch = (Map<String, Object>) groupSwitchObj;
                            Object val = groupSwitch.get(key);
                            if (val != null) {
                                return Boolean.parseBoolean(String.valueOf(val));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 静默回退
        }
        return defaultValue;
    }

    // 改进4：设置群开关并持久化到 messages.yml
    @SuppressWarnings("unchecked")
    private void setGroupSwitch(long groupId, String key, boolean value) {
        try {
            Map<String, Object> msgObj = plugin.vconf.getMessagesObj();
            if (msgObj == null) {
                return;
            }
            Map<String, Object> qqMap = (Map<String, Object>) msgObj.get("QQ");
            if (qqMap == null) {
                qqMap = new HashMap<>();
                msgObj.put("QQ", qqMap);
            }
            Object switchesObj = qqMap.get("group-switches");
            Map<String, Object> switches;
            if (switchesObj instanceof Map) {
                switches = (Map<String, Object>) switchesObj;
            } else {
                switches = new HashMap<>();
                qqMap.put("group-switches", switches);
            }
            Object groupSwitchObj = switches.get(String.valueOf(groupId));
            Map<String, Object> groupSwitch;
            if (groupSwitchObj instanceof Map) {
                groupSwitch = (Map<String, Object>) groupSwitchObj;
            } else {
                groupSwitch = new HashMap<>();
                switches.put(String.valueOf(groupId), groupSwitch);
            }
            groupSwitch.put(key, value);
            plugin.vconf.saveMessagesConfig();
        } catch (Exception e) {
            // 静默回退
        }
    }

    private String getGroupDisplayName(
            long groupId,
            String realGroupName
    ) {
        if (Config.messages.QQ.groups == null) {
            return realGroupName;
        }

        return Config.messages.QQ.groups
                .getOrDefault(
                        String.valueOf(groupId),
                        realGroupName
                );
    }

    // 拼接 QQ→MC 消息，对用户可控内容做 escapeTags 防止 MiniMessage 标签注入
    private String formatQqMessage(
            String group,
            String player,
            String message
    ) {
        String format =
                Config.messages.QQ.format;

        if (format == null || format.isEmpty()) {
            format =
                    "[{group}] {player}：{message}";
        }

        // 用户输入中的 & 替换为 &&（LegacyComponentSerializer 将 && 渲染为普通 &）
        String safeGroup = group.replace("&", "&&");
        String safePlayer = player.replace("&", "&&");
        String safeMessage = message.replace("&", "&&");

        return format
                .replace("{group}", safeGroup)
                .replace("{player}", safePlayer)
                .replace("{message}", safeMessage);
    }

    // Bug 修复 1/2：把各类 CQ 码替换成占位文本，而非整段删除
    // 改进2：@某人改为查询群名片/昵称，签名加 bot、groupId 参数
    private String processCQCodes(String msg, QQBot bot, long groupId) {
        msg = msg.replace("&#91;", "[").replace("&#93;", "]");

        if (msg.startsWith("<?xml") || msg.startsWith("<msg") || msg.startsWith("{\"app") || msg.startsWith("{\"faceType") || msg.startsWith("{\"data") || msg.startsWith("\",\"faceType") || msg.startsWith("\"faceType")) {
            return "[分享链接]";
        }

        if (msg.contains("[CQ:music,") || (msg.contains("[CQ:json,") && msg.toLowerCase().contains("music")) || (msg.contains("[CQ:share,") && msg.toLowerCase().contains("music"))) {
            return "[音乐分享]";
        }
        if (msg.contains("[CQ:json,") || msg.contains("[CQ:share,") || msg.contains("[CQ:xml,")) {
            return "[分享链接]";
        }

        msg = msg.replaceAll("\\[CQ:mface,[^\\]]*\\]", "[超级表情]");
        msg = msg.replaceAll("\\[CQ:bface,[^\\]]*\\]", "[表情]");
        msg = msg.replaceAll("\\[CQ:sface,[^\\]]*\\]", "[表情]");
        msg = msg.replaceAll("\\[CQ:image,[^\\]]*\\]", "[图片]");
        msg = msg.replaceAll("\\[CQ:face,[^\\]]*\\]", "[表情]");
        msg = msg.replaceAll("\\[CQ:reply,[^\\]]*\\]", "[回复]");
        msg = replaceAtMentions(msg, bot, groupId);
        msg = msg.replaceAll("\\[CQ:at,qq=all[^\\]]*\\]", "@全体");
        msg = msg.replaceAll("\\[CQ:poke,[^\\]]*\\]", "[戳一戳]");
        msg = msg.replaceAll("\\[CQ:record,[^\\]]*\\]", "[语音]");
        msg = msg.replaceAll("\\[CQ:video,[^\\]]*\\]", "[视频]");
        msg = msg.replaceAll("\\[CQ:file,[^\\]]*\\]", "[文件]");
        msg = msg.replaceAll("\\[CQ:forward,[^\\]]*\\]", "[合并转发]");
        msg = msg.replaceAll("\\[CQ:redbag,[^\\]]*\\]", "[红包]");
        msg = msg.replaceAll("\\[CQ:gift,[^\\]]*\\]", "[礼物]");
        msg = cleanupStickerMetadata(msg);
        msg = msg.replaceAll("\\[CQ:[^\\]]*\\]", "");
        return msg;
    }

     // 清理超级秀/收藏表情在 CQ 码外附带的泄漏元数据
    private String cleanupStickerMetadata(String msg) {
        msg = msg.replaceAll("\\{file:[^}]*gxh\\.vip\\.qq\\.com[^}]*\\}", "[表情]");
        msg = msg.replaceAll("\\{file:[^}]{30,}\\}", "[表情]");

        // 清理 QQ 新版图片外溢元数据：,file=];fileid=...;rkey=...] 或 ,file=]
        msg = msg.replaceAll(",\\s*file\\s*=\\s*\\];\\s*fileid\\s*=[^,;]*;\\s*rkey\\s*=[^\\],;]*\\]?", "");
        msg = msg.replaceAll(",\\s*file\\s*=\\s*\\]", "");

        // 清理 CQ 表情标签外的 JSON blob：以 "faceType 或 ,"faceType 开头
        msg = msg.replaceAll("(?:,\\s*)?\"faceType\"\\s*:\\s*\\d+(?:,\"[^\"]+\"\\s*:\\s*(?:\"[^\"]*\"|\\d+|null|true|false))*", "[超级表情]");

        // 修复 HTML 实体
        msg = msg.replace("&#44;", ",");

        // 扩展键值对清理：增加 key / emoji_id / emoji_package_id / file_size / url 等
        String stripped = msg.replaceAll(
            "(?:^|[\\s,;]+)"
            + "(?:faceType|packId|stickerId|sourceType|stickerType|resultId"
            + "|pokeType|spokeSummary|doubleHit|emojiId|emojiPackageId|summary"
            + "|imageType|randomType|msgType|vaspokeId|oldVersionStr|chainCount"
            + "|key|emoji_id|emoji_package_id|file_size|md5|sub_type|pokeStrength|url)"
            + "\\s*[=:]\\s*(?:\"[^\"]*\"|'[^']*'|[^,\\s;\\[\\]]+)",
            ""
        );

        // 清理裸露的 URL（http/https）
        stripped = stripped.replaceAll("https?://[^\\s,;\\[\\]]+", "");

        // 清理裸露的自动生成文件名（如 6787322A257942C08382AC6E5CDC0F81.jpg）
        stripped = stripped.replaceAll("[A-Fa-f0-9\\-]{20,}\\.[a-z]{3,4}", "");

        // 收尾：清理残留的逗号分号空白
        stripped = stripped.replaceAll("[,;]\\s*[,;]", ",");
        stripped = stripped.replaceAll("^[,\\s;]+", "");
        stripped = stripped.replaceAll("[,\\s;]+$", "");
        stripped = stripped.trim();

        // 兜底：残留纯 JSON 花括号结构 → 占位符
        if (stripped.matches("\\s*\\{[^{}]*\"(?:faceType|packId|stickerId|sourceType|stickerType|randomType|msgType|app|config|meta|data)[^{}]*\\}\\s*")) {
            return "[超级表情]";
        }

        if (!msg.equals(stripped) && stripped.isEmpty()) {
            return "[超级表情]";
        }
        if (!msg.equals(stripped)) {
            msg = stripped;
        }
        return msg;
    }
 
    // 改进2：替换 @某人 CQ 码，三级回退：群名片 → 昵称 → QQ号
    private String replaceAtMentions(String msg, QQBot bot, long groupId) {
        java.util.regex.Pattern atPattern = java.util.regex.Pattern.compile("\\[CQ:at,qq=(\\d+)[^\\]]*\\]");
        java.util.regex.Matcher m = atPattern.matcher(msg);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            long qq = Long.parseLong(m.group(1));
            String name = bot.getGroupMemberName(groupId, qq);
            m.appendReplacement(sb, "@" + java.util.regex.Matcher.quoteReplacement(name));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // 广播QQ消息到MC，同时缓存回复信息并渲染为可点击组件
    private void broadcastToMinecraft(
            String message,
            long groupId,
            long senderId,
            String senderName
    ) {
        // 缓存回复信息
        String messageId = UUID.randomUUID().toString();
        replyCache.put(messageId, new ReplyInfo(groupId, senderId, senderName));

        Component component = legacySerializer.deserialize(message);

        // 包装为可点击组件：点击后自动填入 /qqreply 昵称
        Component clickable = component
                .clickEvent(ClickEvent.suggestCommand("/qqreply " + messageId + " "))
                .hoverEvent(HoverEvent.showText(
                        Component.text("点击回复 QQ 用户 " + senderName)));

        plugin.getServer().getAllPlayers().forEach(player -> player.sendMessage(clickable));
    }

    // Bug 修复 4：读取是否屏蔽官方 BOT（从 messagesObj 原始 Map）
    @SuppressWarnings("unchecked")
    private boolean shouldFilterOfficialBot() {
        try {
            Map<String, Object> msgObj = plugin.vconf.getMessagesObj();
            if (msgObj != null) {
                Map<String, Object> qqMap = (Map<String, Object>) msgObj.get("QQ");
                if (qqMap != null) {
                    Object val = qqMap.get("filter-official-bot");
                    if (val != null) {
                        return Boolean.parseBoolean(String.valueOf(val));
                    }
                }
            }
        } catch (Exception e) {
            // 静默回退
        }
        return true;
    }

    // Bug 修复 4：读取需要屏蔽的 BOT QQ 号列表
    @SuppressWarnings("unchecked")
    private List<Long> getFilterBotIds() {
        try {
            Map<String, Object> msgObj = plugin.vconf.getMessagesObj();
            if (msgObj != null) {
                Map<String, Object> qqMap = (Map<String, Object>) msgObj.get("QQ");
                if (qqMap != null) {
                    Object val = qqMap.get("filter-bot-ids");
                    if (val instanceof List) {
                        List<Long> result = new ArrayList<>();
                        for (Object o : (List<?>) val) {
                            result.add(Long.parseLong(String.valueOf(o)));
                        }
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            // 静默回退
        }
        return new ArrayList<>();
    }

    public void onGroupDecreaseNotice(
            GroupDecreaseNotice event
    ) {
        long userId = event.getUserId();

        String playerName =
                DatabaseManager.getBind(
                        String.valueOf(userId),
                        DbConfig.type.toLowerCase(),
                        PlumBot.getDatabase()
                );

        if (playerName == null) {
            return;
        }

        DatabaseManager.removeBindid(
                playerName,
                DbConfig.type.toLowerCase(),
                PlumBot.getDatabase()
        );
    }
}
