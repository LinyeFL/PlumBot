package me.regadpole.plumbot.config;

import me.regadpole.plumbot.PlumBot;
import me.regadpole.plumbot.internal.Config;
import me.regadpole.plumbot.internal.DbConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VelocityConfig {
    private static VelocityConfig Instance;

    private final PlumBot plugin;
    private Map<String, Object> returnsObj;

    public VelocityConfig(PlumBot plugin) {
        Instance = this;
        this.plugin = plugin;
        Config.PluginDir = plugin.getDataFolder();
    }

    private static List<Long> toLongList(Object obj) {
        List<Long> result = new ArrayList<>();

        if (obj instanceof List<?>) {
            for (Object value : (List<?>) obj) {
                if (value instanceof Number) {
                    result.add(((Number) value).longValue());
                } else if (value != null) {
                    try {
                        result.add(
                                Long.parseLong(
                                        String.valueOf(value).trim()
                                )
                        );
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return result;
    }

    public void loadConfig() throws IOException {
        File botFile = new File(
                plugin.getDataFolder(),
                "bot.yml"
        );

        File configFile = new File(
                plugin.getDataFolder(),
                "config.yml"
        );

        File returnsFile = new File(
                plugin.getDataFolder(),
                "returns.yml"
        );

        File messagesFile = new File(
                plugin.getDataFolder(),
                "messages.yml"
        );

        File kook = new File(
                plugin.getDataFolder(),
                "kook"
        );

        File kookConf = new File(
                kook,
                "kbc.yml"
        );

        File kookPlu = new File(
                kook,
                "plugins"
        );

        if (!Config.PluginDir.exists()
                && !Config.PluginDir.mkdirs()) {
            throw new RuntimeException(
                    "Failed to create data folder!"
            );
        }

        File[] allFile = {
                botFile,
                configFile,
                returnsFile,
                messagesFile
        };

        for (File file : allFile) {
            if (!file.exists()) {
                try (InputStream inputStream =
                             plugin.getClass()
                                     .getResourceAsStream(
                                             "/" + file.getName()
                                     )) {

                    if (inputStream == null) {
                        throw new IOException(
                                "Missing bundled resource: "
                                        + file.getName()
                        );
                    }

                    Files.copy(
                            inputStream,
                            file.toPath()
                    );
                }
            }
        }

        if (!kook.exists()) {
            kook.mkdirs();
        }

        if (!kookPlu.exists()) {
            kookPlu.mkdirs();
        }

        if (!kookConf.exists()) {
            String resourcePath =
                    "/"
                            + kookConf.getParentFile().getName()
                            + "/"
                            + kookConf.getName();

            try (InputStream inputStream =
                         plugin.getClass()
                                 .getResourceAsStream(resourcePath)) {

                if (inputStream == null) {
                    throw new IOException(
                            "Missing bundled resource: "
                                    + resourcePath
                    );
                }

                Files.copy(
                        inputStream,
                        kookConf.toPath()
                );
            }
        }

        try (
                InputStream botIs =
                        new FileInputStream(botFile);

                InputStream configIs =
                        new FileInputStream(configFile);

                InputStream returnsIs =
                        new FileInputStream(returnsFile);

                InputStream messagesIs =
                        new FileInputStream(messagesFile)
        ) {
            Yaml yaml = new Yaml();

            Map<String, Object> botObj =
                    loadMap(yaml, botIs);

            Map<String, Object> configObj =
                    loadMap(yaml, configIs);

            Map<String, Object> loadedReturns =
                    loadMap(yaml, returnsIs);

            Map<String, Object> messagesObj =
                    loadMap(yaml, messagesIs);

            this.returnsObj = loadedReturns;

            loadBotConfig(botObj);
            loadMainConfig(configObj);
            loadMessagesConfig(messagesObj);
            loadDatabaseConfig(configObj);

            Config.returns.Ver = getString(
                    loadedReturns,
                    "Ver",
                    "1.0"
            );
        }

        if (!"1.3.0".equals(Config.bot.Ver)) {
            replaceWithBundledResource(botFile);
            Instance.loadConfig();
            return;
        }

        if (!"1.2.2".equals(Config.config.Ver)) {
            replaceWithBundledResource(configFile);
            Instance.loadConfig();
            return;
        }

        if (!"1.2".equals(Config.returns.Ver)) {
            replaceWithBundledResource(returnsFile);
            Instance.loadConfig();
        }
    }

    private void loadBotConfig(
            Map<String, Object> botObj
    ) {
        Config.bot.Ver = getString(
                botObj,
                "Ver",
                "1.0"
        );

        Map<String, Object> botMap =
                getMap(botObj, "Bot");

        Config.bot.Bot.Mode = getString(
                botMap,
                "Mode",
                "go-cqhttp"
        ).toLowerCase();

        Map<String, Object> cqMap =
                getMap(botMap, "go-cqhttp");

        Config.bot.Bot.gocqhttp.HTTP = getString(
                cqMap,
                "Http",
                "http://127.0.0.1:5700"
        );

        Config.bot.Bot.gocqhttp.Token = getString(
                cqMap,
                "Token",
                ""
        );

        Config.bot.Bot.gocqhttp.IsAccessToken =
                getBoolean(
                        cqMap,
                        "IsAccessToken",
                        false
                );

        Config.bot.Bot.gocqhttp.ListenPort =
                getInteger(
                        cqMap,
                        "ListenPort",
                        5701
                );

        Map<String, Object> kookMap =
                getMap(botMap, "Kook");

        Config.bot.Bot.kook.Token = getString(
                kookMap,
                "Token",
                ""
        );

        Config.bot.Groups =
                toLongList(botObj.get("Groups"));

        Config.bot.Admins =
                toLongList(botObj.get("Admins"));
    }

    private void loadMainConfig(
            Map<String, Object> configObj
    ) {
        Config.config.Ver = getString(
                configObj,
                "Ver",
                "1.0"
        );

        Map<String, Object> forwardingMap =
                getMap(configObj, "Forwarding");

        Config.config.Forwarding.enable =
                getBoolean(
                        forwardingMap,
                        "enable",
                        true
                );

        Config.config.Forwarding.mode =
                getInteger(
                        forwardingMap,
                        "mode",
                        0
                );

        Config.config.Forwarding.prefix =
                getString(
                        forwardingMap,
                        "prefix",
                        "#"
                );

        Map<String, Object> whiteListMap =
                getMap(configObj, "WhiteList");

        Config.config.WhiteList.enable =
                getBoolean(
                        whiteListMap,
                        "enable",
                        false
                );

        Config.config.WhiteList.kickMsg =
                getString(
                        whiteListMap,
                        "kickMsg",
                        "请加入 QQ 群申请白名单"
                );

        Config.config.JoinAndLeave =
                getBoolean(
                        configObj,
                        "JoinAndLeave",
                        false
                );

        Config.config.Online =
                getBoolean(
                        configObj,
                        "Online",
                        false
                );

        Config.config.SDR =
                getBoolean(
                        configObj,
                        "SDR",
                        false
                );

        Config.config.Maven =
                getString(
                        configObj,
                        "Maven",
                        "https://repo1.maven.org/maven2"
                );
    }

    private void loadMessagesConfig(
            Map<String, Object> messagesObj
    ) {
        Config.messages.Servers =
                new HashMap<>();

        Object serversValue =
                messagesObj.get("Servers");

        if (serversValue instanceof Map<?, ?>) {
            Map<?, ?> serversMap =
                    (Map<?, ?>) serversValue;

            for (Map.Entry<?, ?> entry
                    : serversMap.entrySet()) {

                if (entry.getKey() != null
                        && entry.getValue() != null) {

                    Config.messages.Servers.put(
                            String.valueOf(
                                    entry.getKey()
                            ),
                            String.valueOf(
                                    entry.getValue()
                            )
                    );
                }
            }
        }

        Map<String, Object> chatMap =
                getMap(messagesObj, "Chat");

        Config.messages.Chat.format =
                getString(
                        chatMap,
                        "format",
                        "[{server}] {player}：{message}"
                );

        Map<String, Object> qqMap =
                getMap(messagesObj, "QQ");

        Config.messages.QQ.groups =
                new HashMap<>();

        Object qqGroupsValue =
                qqMap.get("groups");

        if (qqGroupsValue instanceof Map<?, ?>) {
            Map<?, ?> qqGroupsMap =
                    (Map<?, ?>) qqGroupsValue;

            for (Map.Entry<?, ?> entry
                    : qqGroupsMap.entrySet()) {

                if (entry.getKey() != null
                        && entry.getValue() != null) {

                    Config.messages.QQ.groups.put(
                            String.valueOf(
                                    entry.getKey()
                            ),
                            String.valueOf(
                                    entry.getValue()
                            )
                    );
                }
            }
        }

        Config.messages.QQ.format =
                getString(
                        qqMap,
                        "format",
                        "[{group}] {player}：{message}"
                );

        Map<String, Object> notificationsMap =
                getMap(
                        messagesObj,
                        "Notifications"
                );

        Config.messages.Notifications
                .joinQuitEnabled =
                getBoolean(
                        notificationsMap,
                        "join-quit-enabled",
                        true
                );

        Config.messages.Notifications
                .serverSwitchEnabled =
                getBoolean(
                        notificationsMap,
                        "server-switch-enabled",
                        true
                );

        Config.messages.Notifications
                .pluginStatusEnabled =
                getBoolean(
                        notificationsMap,
                        "plugin-status-enabled",
                        false
                );

        Config.messages.Notifications.join =
                getString(
                        notificationsMap,
                        "join",
                        "玩家 {player} 进入服务器"
                );

        Config.messages.Notifications.quit =
                getString(
                        notificationsMap,
                        "quit",
                        "玩家 {player} 离开服务器"
                );

        Config.messages.Notifications.switchServer =
                getString(
                        notificationsMap,
                        "switch",
                        "玩家 {player} 从 {from_server} 前往 {to_server}"
                );
    }

    private void loadDatabaseConfig(
            Map<String, Object> configObj
    ) {
        Map<String, Object> databaseMap =
                getMap(configObj, "database");

        DbConfig.type = getString(
                databaseMap,
                "type",
                "sqlite"
        );

        Map<String, Object> settingsMap =
                getMap(databaseMap, "settings");

        Map<String, Object> sqliteMap =
                getMap(settingsMap, "sqlite");

        DbConfig.settings.sqlite.path =
                getString(
                        sqliteMap,
                        "path",
                        "%plugin_folder%/database.db"
                ).replace(
                        "%plugin_folder%",
                        PlumBot.INSTANCE
                                .getDataFolder()
                                .toPath()
                                .toString()
                );

        Map<String, Object> mysqlMap =
                getMap(settingsMap, "mysql");

        DbConfig.settings.mysql.host =
                getString(
                        mysqlMap,
                        "host",
                        "localhost"
                );

        DbConfig.settings.mysql.port =
                getString(
                        mysqlMap,
                        "port",
                        "3306"
                );

        DbConfig.settings.mysql.database =
                getString(
                        mysqlMap,
                        "database",
                        "plumbot"
                );

        DbConfig.settings.mysql.user =
                getString(
                        mysqlMap,
                        "user",
                        "plumbot"
                );

        DbConfig.settings.mysql.password =
                getString(
                        mysqlMap,
                        "password",
                        "plumbot"
                );

        DbConfig.settings.mysql.parameters =
                getString(
                        mysqlMap,
                        "parameters",
                        "?useSSL=false"
                );

        Map<String, Object> poolMap =
                getMap(settingsMap, "pool");

        DbConfig.settings.pool.connectionTimeout =
                getLong(
                        poolMap,
                        "connectionTimeout",
                        30000L
                );

        DbConfig.settings.pool.idleTimeout =
                getLong(
                        poolMap,
                        "idleTimeout",
                        600000L
                );

        DbConfig.settings.pool.maxLifetime =
                getLong(
                        poolMap,
                        "maxLifetime",
                        1800000L
                );

        DbConfig.settings.pool.maximumPoolSize =
                getInteger(
                        poolMap,
                        "maximumPoolSize",
                        15
                );

        DbConfig.settings.pool.keepaliveTime =
                getLong(
                        poolMap,
                        "keepaliveTime",
                        0L
                );

        DbConfig.settings.pool.minimumIdle =
                getInteger(
                        poolMap,
                        "minimumIdle",
                        5
                );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadMap(
            Yaml yaml,
            InputStream inputStream
    ) {
        Object loaded = yaml.load(inputStream);

        if (loaded instanceof Map<?, ?>) {
            return (Map<String, Object>) loaded;
        }

        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(
            Map<String, Object> parent,
            String key
    ) {
        Object value = parent.get(key);

        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }

        return new HashMap<>();
    }

    private static String getString(
            Map<String, Object> parent,
            String key,
            String defaultValue
    ) {
        Object value = parent.get(key);

        return value == null
                ? defaultValue
                : String.valueOf(value);
    }

    private static boolean getBoolean(
            Map<String, Object> parent,
            String key,
            boolean defaultValue
    ) {
        Object value = parent.get(key);

        return value == null
                ? defaultValue
                : Boolean.parseBoolean(
                        String.valueOf(value)
                );
    }

    private static int getInteger(
            Map<String, Object> parent,
            String key,
            int defaultValue
    ) {
        Object value = parent.get(key);

        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(
                    String.valueOf(value)
            );
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static long getLong(
            Map<String, Object> parent,
            String key,
            long defaultValue
    ) {
        Object value = parent.get(key);

        if (value == null) {
            return defaultValue;
        }

        try {
            return Long.parseLong(
                    String.valueOf(value)
            );
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private void replaceWithBundledResource(
            File targetFile
    ) throws IOException {
        try (InputStream inputStream =
                     plugin.getClass()
                             .getResourceAsStream(
                                     "/" + targetFile.getName()
                             )) {

            if (inputStream == null) {
                throw new IOException(
                        "Missing bundled resource: "
                                + targetFile.getName()
                );
            }

            Files.copy(
                    inputStream,
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    public Map<String, Object> getReturnsObj() {
        return returnsObj;
    }

    public static void reloadConfig()
            throws IOException {
        Instance.loadConfig();
    }
}
