package me.regadpole.plumbot.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.event.qq.QQEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;

public class QqReplyCommand implements SimpleCommand {

    private final PlumBot plugin;

    public QqReplyCommand(PlumBot plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("此命令仅限玩家使用"));
            return;
        }

        if (args.length < 2) {
            source.sendMessage(Component.text("用法: /qqreply <昵称> <消息>"));
            return;
        }

        String targetNickname = args[0];
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        QQEvent.ReplyInfo replyInfo = QQEvent.getReplyInfo(targetNickname);
        if (replyInfo == null) {
            source.sendMessage(Component.text("未找到可回复的 QQ 用户，可能已超时（5分钟）").color(NamedTextColor.RED));
            return;
        }

        String fullMessage = "[MC回复] " + player.getUsername() + "：" + message;
        PlumBot.getBot().sendMsg(true, fullMessage, replyInfo.groupId());
        source.sendMessage(Component.text("已回复 " + targetNickname, NamedTextColor.GREEN));
    }
}
