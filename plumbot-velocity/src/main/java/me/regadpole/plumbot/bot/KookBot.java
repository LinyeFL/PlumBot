package me.regadpole.plumbot.bot;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.event.kook.KookEvent;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.internal.kook.KookClient;
import snw.jkook.JKook;
import snw.jkook.config.ConfigurationSection;
import snw.jkook.config.file.YamlConfiguration;
import snw.jkook.entity.User;
import snw.jkook.entity.channel.Channel;
import snw.jkook.entity.channel.TextChannel;
import snw.jkook.event.channel.ChannelMessageEvent;
import snw.jkook.event.pm.PrivateMessageReceivedEvent;
import snw.jkook.message.component.BaseComponent;
import snw.jkook.message.component.card.CardBuilder;
import snw.jkook.message.component.card.MultipleCardComponent;
import snw.jkook.message.component.card.Size;
import snw.jkook.message.component.card.Theme;
import snw.jkook.message.component.card.element.ImageElement;
import snw.jkook.message.component.card.module.ContainerModule;
import snw.jkook.util.PageIterator;
import snw.kookbc.impl.CoreImpl;
import snw.kookbc.impl.KBCClient;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static me.regadpole.plumbot.PlumBot.INSTANCE;

public class KookBot implements Bot {

    private final PlumBot plugin;
    private KBCClient kookClient;
    private boolean kookEnabled = false;
    private boolean debug;

    public KookBot(PlumBot plugin) {
        this.plugin = plugin;
        this.debug = Config.bot.Bot.kook.Debug;
    }

    @Override
    public void start() {
        CoreImpl kookcore;
        File kookFolder = new File(INSTANCE.getDataFolder(), "kook");
        ConfigurationSection config;
        File kookPlugins;
        KBCClient kook;

        kookcore = new CoreImpl();
        JKook.setCore(kookcore);

        config = YamlConfiguration.loadConfiguration(new File(kookFolder, "kbc.yml"));
        kookPlugins = new File(kookFolder, "plugins");

        kook = new KookClient(
                kookcore,
                config,
                kookPlugins,
                Config.bot.Bot.kook.Token,
                "websocket"
        );

        try {
            kook.start();
            plugin.getLogger().info("KookClient start() 调用完成");
            
            // 检查连接状态
            if (kook.getCore().getHttpAPI() != null) {
                plugin.getLogger().info("KookClient HTTP API 可用");
            } else {
                plugin.getLogger().warn("KookClient HTTP API 不可用");
            }
        } catch (Exception e) {
            plugin.getLogger().error("KookClient 启动异常", e);
        }
        this.kookClient = kook;
        this.kookEnabled = true;

        if (debug) {
            plugin.getLogger().info("[KookBot] Kook 客户端已启动，Token 长度: " + Config.bot.Bot.kook.Token.length());
            plugin.getLogger().info("[KookBot] 已注册事件监听器");
        }

        kook.getCore()
                .getEventManager()
                .registerHandlers(
                        kook.getInternalPlugin(),
                        new KookEvent(this, plugin)
                );

        if (Config.messages.Notifications.pluginStatusEnabled) {
            List<String> groups = Config.bot.KookGroups;
            if (groups != null) {
                for (String groupIDStr : groups) {
                    sendMsg(true, "PlumBot已启动", Long.parseLong(groupIDStr));
                }
            }
            if (debug) {
                plugin.getLogger().info("[KookBot] 已发送启动通知到 " + (groups != null ? groups.size() : 0) + " 个 KOOK 频道");
            }
        }

        if (debug) {
            plugin.getLogger().info("[KookBot] KookBot 启动完成");
        }
    }

    @Override
    public void shutdown() {
        if (Config.messages.Notifications.pluginStatusEnabled) {
            List<String> groups = Config.bot.KookGroups;
            if (groups != null) {
                for (String groupIDStr : groups) {
                    long groupID = Long.parseLong(groupIDStr);
                    sendChannelMessage("PlumBot已关闭", getChannel(groupID));
                }
            }
        }
        kookClient.shutdown();
        kookEnabled = false;
        if (debug) {
            plugin.getLogger().info("[KookBot] KookBot 已关闭");
        }
    }

    @Override
    public void sendMsg(boolean isGroup, String message, long id) {
        if (id == 0L) return;
        if ("".equals(message)) return;

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            if (isGroup) {
                sendChannelMessage(message, getChannel(id));
            } else {
                sendPrivateMessage(message, getUser(id));
            }
        }).schedule();
    }

    public void sendMsg(boolean isGroup, BaseComponent message, long id) {
        if (id == 0L) return;
        if (message.toString().isEmpty()) return;

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            if (isGroup) {
                sendChannelMessage(message, getChannel(id));
            } else {
                sendPrivateMessage(message, getUser(id));
            }
        }).schedule();
    }

    public void sendChannelReply(ChannelMessageEvent event, String message) {
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            event.getMessage().reply(message);
        }).schedule();
    }

    public void sendPrivateReply(PrivateMessageReceivedEvent event, String message) {
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            event.getMessage().reply(message);
        }).schedule();
    }

    public void sendPrivateFileReply(PrivateMessageReceivedEvent event, String path) {
        List<ImageElement> list = new ArrayList<>();
        list.add(new ImageElement(createFile(path), "", false));
        MultipleCardComponent card = new CardBuilder()
                .setTheme(Theme.PRIMARY)
                .setSize(Size.LG)
                .addModule(new ContainerModule(list))
                .build();
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            event.getMessage().reply(card);
        }).schedule();
    }

    public void sendChannelFileReply(ChannelMessageEvent event, String path) {
        List<ImageElement> list = new ArrayList<>();
        list.add(new ImageElement(createFile(path), "", false));
        MultipleCardComponent card = new CardBuilder()
                .setTheme(Theme.PRIMARY)
                .setSize(Size.LG)
                .addModule(new ContainerModule(list))
                .build();
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            event.getMessage().reply(card);
        }).schedule();
    }

    private void sendChannelMessage(String message, TextChannel channel) {
        channel.sendComponent(message);
        if (debug) {
            plugin.getLogger().info("[KookBot] → 频道 " + channel.getName() + ": " + message.substring(0, Math.min(80, message.length())));
        }
    }

    private void sendChannelMessage(BaseComponent message, TextChannel channel) {
        channel.sendComponent(message);
    }

    private void sendPrivateMessage(String message, User user) {
        user.sendPrivateMessage(message);
    }

    private void sendPrivateMessage(BaseComponent message, User user) {
        user.sendPrivateMessage(message);
    }

    private String createFile(String path) {
        return "Kook不支持图片";
    }

    @Override
    public String getGroupName(long groupId) {
        Channel channel = getChannel(groupId);
        return channel.getName();
    }

    public TextChannel getChannel(long groupId) {
        return (TextChannel) kookClient.getCore().getHttpAPI().getChannel(String.valueOf(groupId));
    }

    public User getUser(long id) {
        return kookClient.getCore().getHttpAPI().getUser(String.valueOf(id));
    }

    @Override
    public boolean checkUserInGroup(long userId, long groupId) {
        PageIterator<Set<User>> iterator = getChannel(groupId).getGuild().getUsers();
        while (iterator.hasNext()) {
            for (User user : iterator.next()) {
                if (user.getId().equalsIgnoreCase(String.valueOf(userId))) {
                    return true;
                }
            }
        }
        return false;
    }

    public KBCClient getKookClient() {
        return kookClient;
    }

    public boolean isKookEnabled() {
        return kookEnabled;
    }
}
