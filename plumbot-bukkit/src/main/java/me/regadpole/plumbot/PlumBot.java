package me.regadpole.plumbot;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import me.regadpole.plumbot.bot.Bot;
import me.regadpole.plumbot.bot.KookBot;
import me.regadpole.plumbot.bot.QQBot;
import me.regadpole.plumbot.command.Commands;
import me.regadpole.plumbot.config.Config;
import me.regadpole.plumbot.event.server.QsChatEvent;
import me.regadpole.plumbot.event.server.QsHikariChatEvent;
import me.regadpole.plumbot.event.server.ServerEvent;
import me.regadpole.plumbot.hook.AuthMeHook;
import me.regadpole.plumbot.hook.GriefDefenderHook;
import me.regadpole.plumbot.hook.QuickShopHook;
import me.regadpole.plumbot.hook.ResidenceHook;
import me.regadpole.plumbot.internal.Dependencies;
import me.regadpole.plumbot.internal.Environment;
import me.regadpole.plumbot.internal.FoliaSupport;
import me.regadpole.plumbot.internal.database.Database;
import me.regadpole.plumbot.internal.database.DatabaseManager;
import me.regadpole.plumbot.internal.maven.LibraryLoader;
import me.regadpole.plumbot.metrics.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlumBot extends JavaPlugin implements Listener {

    public static PlumBot INSTANCE;

    private static TaskScheduler scheduler;

    private static Database database;
    private static QQBot qqBot;
    private static KookBot kookBot;
    private static Environment environment;

    @Override
    public void onLoad() {
        INSTANCE = this;

        if (Bukkit.getName().equals("Folia")) FoliaSupport.isFolia = true;

        Config.createConfig();

        LibraryLoader.loadAll(Dependencies.class);
        getLogger().info("PlumBot依赖已加载成功");
    }

    @Override
    public void onEnable() {

        DatabaseManager.start();
        scheduler = UniversalScheduler.getScheduler(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        AuthMeHook.hookAuthme();
        ResidenceHook.hookRes();
        QuickShopHook.hookQuickShop();
        GriefDefenderHook.hookGriefDefender();
        getLogger().info("关联插件连接完毕");
        Bukkit.getPluginManager().registerEvents(new ServerEvent(this), this);
        if (QuickShopHook.hasQs) Bukkit.getPluginManager().registerEvents(new QsChatEvent(),this);
        if (QuickShopHook.hasQsHikari) Bukkit.getPluginManager().registerEvents(new QsHikariChatEvent(),this);
        getLogger().info("服务器事件监听器注册完毕");
        Bukkit.getServer().getPluginCommand("plumbot").setExecutor(new Commands(this));
        getLogger().info("命令注册完毕");

        getScheduler().runTaskAsynchronously(() -> {
            String mode = Config.getBotMode();
            switch (mode) {
                case "go-cqhttp":
                    qqBot = new QQBot();
                    qqBot.start();
                    getLogger().info("已启动go-cqhttp服务");
                    break;
                case "kook":
                    kookBot = new KookBot();
                    kookBot.start();
                    getLogger().info("已启动kook服务");
                    break;
                case "both":
                    qqBot = new QQBot();
                    qqBot.start();
                    getLogger().info("已启动go-cqhttp服务");
                    kookBot = new KookBot();
                    kookBot.start();
                    getLogger().info("已启动kook服务");
                    break;
                default:
                    getLogger().warning("无法启动服务，请检查配置文件，插件已关闭");
                    Bukkit.getPluginManager().disablePlugin(this);
                    break;
            }
        });

        // All you have to do is adding the following two lines in your onEnable method.
        // You can find the plugin ids of your plugins on the page https://bstats.org/what-is-my-plugin-id
        int pluginId = 19427; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);

        environment = new Environment();
        getLogger().info( "PlumBot已启动");

    }

    @Override
    public void onDisable() {

        String mode = Config.getBotMode();
        switch (mode) {
            case "go-cqhttp":
                if (qqBot != null) {
                    qqBot.shutdown();
                    getLogger().info("已关闭go-cqhttp服务");
                }
                break;
            case "kook":
                if (kookBot != null) {
                    kookBot.shutdown();
                    getLogger().info("已关闭kook服务");
                }
                break;
            case "both":
                if (qqBot != null) {
                    qqBot.shutdown();
                    getLogger().info("已关闭go-cqhttp服务");
                }
                if (kookBot != null) {
                    kookBot.shutdown();
                    getLogger().info("已关闭kook服务");
                }
                break;
            default:
                getLogger().warning("无法正常关闭服务，将在服务器关闭后强制关闭");
                Bukkit.getPluginManager().disablePlugin(this);
                break;
        }
        DatabaseManager.close();
        getLogger().info("PlumBot已关闭");
    }

    public static void say(String s) {
        CommandSender sender = Bukkit.getConsoleSender();
        sender.sendMessage(s);
    }

    public static TaskScheduler getScheduler() {
        return scheduler;
    }

    public static QQBot getQQBot() {
        return qqBot;
    }

    public static KookBot getKookBot() {
        return kookBot;
    }

    // 兼容旧调用，仅返回 QQBot（both 模式下 QQ 优先）
    @Deprecated
    public static Bot getBot() {
        return qqBot;
    }

    public static Database getDatabase() {
        return database;
    }
    public void setDatabase(Database database) {
        PlumBot.database = database;
    }
    public Environment getEnvironment() {return environment;}
}
