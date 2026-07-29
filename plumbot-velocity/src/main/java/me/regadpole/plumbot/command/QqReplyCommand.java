package me.regadpole.plumbot.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.bot.QQBot;
import me.regadpole.plumbot.event.qq.QQEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class QqReplyCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player)) {
            invocation.source().sendMessage(
                Component.text("此命令只能由玩家执行", NamedTextColor.RED)
            );
            return;
        }

        Player player = (Player) invocation.source();
        String[] args = invocation.arguments();

        if (args.length < 2) {
            player.sendMessage(
                Component.text("用法：/qqreply <对方昵称> <消息>", NamedTextColor.RED)
            );
            return;
        }

        String messageId = args[0];
        StringBuilder msgBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) msgBuilder.append(" ");
            msgBuilder.append(args[i]);
        }
        String message = msgBuilder.toString();

        QQEvent.ReplyInfo replyInfo = QQEvent.getReplyInfoByMessageId(messageId);
        if (replyInfo == null) {
            player.sendMessage(
                Component.text("找不到该QQ用户，可能消息已过期（5分钟有效）", NamedTextColor.RED)
            );
            return;
        }

        QQBot qqBot = (QQBot) PlumBot.getBot();
        if (qqBot == null) {
            player.sendMessage(
                Component.text("QQ Bot 未启动", NamedTextColor.RED)
            );
            return;
        }

        String qqMessage = "[CQ:at,qq=" + replyInfo.getQqUserId() + "] [MC回复] "
                + player.getUsername() + "：" + message;

        qqBot.sendMsg(true, qqMessage, replyInfo.getGroupId());

        player.sendMessage(
            Component.text("已回复 " + replyInfo.getQqNickname() + "：" + message, NamedTextColor.GREEN)
        );
    }
}
