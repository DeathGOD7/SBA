package io.github.pronze.sba.utils;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.java.JavaPlugin;
import org.screamingsandals.lib.utils.annotations.Service;
import io.github.pronze.sba.config.SBAConfig;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class Logger {
    private static Logger instance;
    private Level level;
    private java.util.logging.Logger logger;
    private boolean testMode;

    public static void mockMode() {
       //instance = new Logger();
       //instance.testMode = true;
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    public Logger(JavaPlugin plugin) {
        if (instance != null) {
            throw new UnsupportedOperationException("Logger has already been initialized!");
        }
        instance = this;
        instance.level = Level.ALL;
        instance.logger = plugin.getLogger();
    }

    protected static void mockDebug(String message, Object... params) {
        System.out.println(getMessage(message, params));
    }

    public static void trace(@NonNull String message, Object... params) {
        if (instance.testMode) {
            mockDebug(message, params);
            return;
        }
        if (instance.level.getLevel() >= Level.TRACE.getLevel()) {
            instance.logger.info(getMessage(message, params));
        }
    }

    public static void warn(@NonNull String message, Object... params) {
        if (instance.testMode) {
            mockDebug(message, params);
            return;
        }
        if (instance.level.getLevel() >= Level.WARNING.getLevel()) {
            instance.logger.warning(getMessage(message, params));
        }
    }

    public static void error(@NonNull String message, Object... params) {
        if (instance.testMode) {
            mockDebug(message, params);
            return;
        }
        if (instance.level.getLevel() >= Level.ERROR.getLevel()) {
            instance.logger.warning(getMessage(message, params));
        }
    }

    private static String getMessage(String message, Object... params) {
        for (var param : params) {
            if (param == null) {
                param = "NULL";
            }
            if (!(param instanceof String)) {
                param = param.toString();
            }
            message = message.replaceFirst(Pattern.quote("{}"), Matcher.quoteReplacement((String) param));
        }
        return message;
    }

    public static void setMode(Level level) {
        instance.level = level;
    }

    public enum Level {
        DISABLED(0),
        TRACE(1),
        WARNING(2),
        ERROR(3),
        ALL(4);

        @Getter
        private final int level;

        Level(int level) {
            this.level = level;
        }
    }
}
