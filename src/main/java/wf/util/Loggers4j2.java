package wf.util;

import java.util.function.Function;
import org.apache.logging.log4j.core.async.AsyncLogger;
import reactor.util.Logger;

/**
 * 預設的 reactor.util.{@link Logger} 缺少 {@link  #useLog4j2Loggers()} 所以補上<br/>
 * 但是Log4j2本身的設定就支援 {@link  AsyncLogger} 也許這個Class是多此一舉
 *
 * @author Kent Yeh
 */
public class Loggers4j2 {

    private static Function<String, Logger> LOGGER_FACTORY;
    private static boolean includeLocation = "dev".endsWith(System.getProperty("spring.profiles.active"));

    static {
        resetLoggerFactory();
    }

    public static Function<Class, Logger> includeLocation(boolean includeLocation) {
        Loggers4j2.includeLocation = includeLocation;
        return name -> Loggers4j2.getLogger(name);
    }

    public static void resetLoggerFactory() {
        try {
            useLog4j2Loggers();
        } catch (Throwable t) {
            reactor.util.Loggers.resetLoggerFactory();
        }
    }

    public static void useLog4j2Loggers() {
        String name = Loggers4j2.class.getName();
        LOGGER_FACTORY = new Log4j2LoggerFactory();
        LOGGER_FACTORY.apply(name).debug("Using Apache Log4j2 logging framework");
    }

    public static Logger getLogger(String name) {
        return LOGGER_FACTORY.apply(name);
    }

    public static Logger getLogger(Class<?> cls) {
        return LOGGER_FACTORY.apply(cls.getName());
    }

    private static class Log4j2LoggerFactory implements Function<String, Logger> {

        @Override
        public Logger apply(String name) {
            return new Log4j2Logger(org.apache.logging.log4j.LogManager.getLogger(name));
        }
    }

    private static class Log4j2Logger implements Logger {

        private final org.apache.logging.log4j.Logger logger;

        public Log4j2Logger(org.apache.logging.log4j.Logger logger) {
            this.logger = logger;
        }

        @Override
        public String getName() {
            return logger.getName();
        }

        @Override
        public boolean isTraceEnabled() {
            return logger.isTraceEnabled();
        }

        private String findNoLog(StackTraceElement[] stes) {
            for (int i = 1; i < stes.length; i++) {
                if (!stes[i].getClassName().toLowerCase().contains("log")) {
                    return "\033[m" + stes[i].getClassName() + "." + stes[i].getMethodName() + "(" + stes[i].getLineNumber() + ")  ~ ";
                }
            }
            return "";
        }

        @Override
        public void trace(String string) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[90m" : "";
            logger.trace(prefix + string);
        }

        @Override
        public void trace(String string, Object... os) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[90m" : "";
            logger.trace(prefix + string, os);
        }

        @Override
        public void trace(String string, Throwable thrwbl) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[90m" : "";
            logger.trace(prefix + string, thrwbl);
        }

        @Override
        public boolean isDebugEnabled() {
            return logger.isDebugEnabled();
        }

        @Override
        public void debug(String string) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[36m" : "";
            logger.debug(prefix + string);
        }

        @Override
        public void debug(String string, Object... os) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[36m" : "";
            logger.debug(prefix + string, os);
        }

        @Override
        public void debug(String string, Throwable thrwbl) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[36m" : "";
            logger.debug(prefix + string, thrwbl);
        }

        @Override
        public boolean isInfoEnabled() {
            return logger.isInfoEnabled();
        }

        @Override
        public void info(String string) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[32m" : "";
            logger.info(prefix + string);
        }

        @Override
        public void info(String string, Object... os) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[32m" : "";
            logger.info(prefix + string, os);
        }

        @Override
        public void info(String string, Throwable thrwbl) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[32m" : "";
            logger.info(prefix + string, thrwbl);
        }

        @Override
        public boolean isWarnEnabled() {
            return logger.isWarnEnabled();
        }

        @Override
        public void warn(String string) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[33m" : "";
            logger.warn(prefix + string);
        }

        @Override
        public void warn(String string, Object... os) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[33m" : "";
            logger.warn(prefix + string, os);
        }

        @Override
        public void warn(String string, Throwable thrwbl) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[33m" : "";
            logger.warn(prefix + string, thrwbl);
        }

        @Override
        public boolean isErrorEnabled() {
            return logger.isErrorEnabled();
        }

        @Override
        public void error(String string) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[91m" : "";
            logger.error(prefix + string);
        }

        @Override
        public void error(String string, Object... os) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[91m" : "";
            logger.error(prefix + string, os);
        }

        @Override
        public void error(String string, Throwable thrwbl) {
            String prefix = includeLocation ? findNoLog(Thread.currentThread().getStackTrace()) + "\033[91m" : "";
            logger.error(prefix + string, thrwbl);
        }
    }
}
