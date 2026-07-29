package me.regadpole.plumbot.event.qq;

import com.velocitypowered.api.proxy.Player;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.bot.QQBot;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.internal.DbConfig;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.tool.StringTool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import sdk.event.message.GroupMessage;
import sdk.event.message.PrivateMessage;
import sdk.event.notice.GroupDecreaseNotice;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QQEvent {

    // MiniMessage 实例，用于解析 <gold>、<green> 等标签
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

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

        List<String> playerNames = new ArrayList<>();

        for (Player player
                : plugin.getServer().getAllPlayers()) {
            playerNames.add(player.getUsername());
        }

        PlumBot.getBot().sendMsg(
                false,
                "当前在线：("
                        + plugin.getServer()
                        .getAllPlayers()
                        .size()
                        + "人)"
                        + playerNames,
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
                processCQCodes(filteredMessage);

        broadcastToMinecraft(
                formatQqMessage(
                        groupName,
                        filteredName,
                        filteredMessage
                )
        );
    }

    private boolean shouldIgnoreMessage(
            String message
    ) {
        Pattern xmlPattern =
                Pattern.compile("<?xm.*");

        if (xmlPattern.matcher(message).find()) {
            return true;
        }

        Pattern appPattern =
                Pattern.compile("\"ap.*");

        return appPattern.matcher(message).find();
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

            List<String> playerNames =
                    new ArrayList<>();

            for (Player player
                    : plugin.getServer()
                    .getAllPlayers()) {
                playerNames.add(
                        player.getUsername()
                );
            }

            PlumBot.getBot().sendMsg(
                    true,
                    "当前在线：("
                            + plugin.getServer()
                            .getAllPlayers()
                            .size()
                            + "人)"
                            + playerNames,
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

        // 只转义被替换进去的用户输入，format 自身的 <gold> 等标签保留
        String safeGroup = miniMessage.escapeTags(group);
        String safePlayer = miniMessage.escapeTags(player);
        String safeMessage = miniMessage.escapeTags(message);

        return format
                .replace("{group}", safeGroup)
                .replace("{player}", safePlayer)
                .replace("{message}", safeMessage);
    }

    // Bug 修复 1/2：把各类 CQ 码替换成占位文本，而非整段删除
    private String processCQCodes(String msg) {
        // 图片
        msg = msg.replaceAll("\\[CQ:image,[^\\]]*\\]", "[图片]");
        // 表情
        msg = msg.replaceAll("\\[CQ:face,[^\\]]*\\]", "[表情]");
        // 回复
        msg = msg.replaceAll("\\[CQ:reply,[^\\]]*\\]", "[回复]");
        // @某人，提取 QQ 号
        msg = msg.replaceAll("\\[CQ:at,qq=(\\d+)[^\\]]*\\]", "@$1");
        // @全体成员
        msg = msg.replaceAll("\\[CQ:at,qq=all[^\\]]*\\]", "@全体");
        // 戳一戳
        msg = msg.replaceAll("\\[CQ:poke,[^\\]]*\\]", "[戳一戳]");
        // 语音
        msg = msg.replaceAll("\\[CQ:record,[^\\]]*\\]", "[语音]");
        // 视频
        msg = msg.replaceAll("\\[CQ:video,[^\\]]*\\]", "[视频]");
        // 文件
        msg = msg.replaceAll("\\[CQ:file,[^\\]]*\\]", "[文件]");
        // 转发
        msg = msg.replaceAll("\\[CQ:forward,[^\\]]*\\]", "[合并转发]");
        // 红包
        msg = msg.replaceAll("\\[CQ:redbag,[^\\]]*\\]", "[红包]");
        // 礼物
        msg = msg.replaceAll("\\[CQ:gift,[^\\]]*\\]", "[礼物]");
        // JSON 卡片
        msg = msg.replaceAll("\\[CQ:json,[^\\]]*\\]", "[卡片]");
        // XML 消息
        msg = msg.replaceAll("\\[CQ:xml,[^\\]]*\\]", "[XML]");
        // 其他未识别的 CQ 码，直接删除
        msg = msg.replaceAll("\\[CQ:[^\\]]*\\]", "");
        return msg;
    }

    // MiniMessage 接入：用 miniMessage.deserialize 解析 <gold> 等标签
    private void broadcastToMinecraft(
            String message
    ) {
        Component component =
                miniMessage.deserialize(message);

        plugin.getServer()
                .getAllPlayers()
                .forEach(
                        player ->
                                player.sendMessage(
                                        component
                                )
                );
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
