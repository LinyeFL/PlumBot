package me.regadpole.plumbot.internal;

import java.io.File;
import java.util.List;
import java.util.Map;

public class Config {
    public static File PluginDir;

    public static class bot {
        public static String Ver;
        public static class Bot {
            public static String Mode;
            public static class gocqhttp {
                public static String HTTP;
                public static String Token;
                public static boolean IsAccessToken;
                public static int ListenPort;
            }
            public static class kook {
                public static String Token;
            }
        }
        public static List<Long> Groups;
        public static List<Long> Admins;
    }

    public static class config {
        public static String Ver;
        public static class Forwarding {
            public static boolean enable;
            public static int mode;
            public static String prefix;
        }
        public static class WhiteList {
            public static boolean enable;
            public static String kickMsg;
        }
        public static boolean JoinAndLeave;
        public static boolean Online;
        public static boolean SDR;
        public static String Maven;
    }

    public static class messages {
        public static Map<String, String> Servers;
        public static class Chat {
            public static String format;
        }
        public static class Notifications {
            public static boolean joinQuitEnabled;
            public static boolean serverSwitchEnabled;
            public static String join;
            public static String quit;
            public static String switchServer;
        }
    }

    public static class returns {
        public static String Ver;
    }
}
