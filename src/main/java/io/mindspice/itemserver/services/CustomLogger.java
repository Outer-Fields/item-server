package io.mindspice.itemserver.services;

import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.logging.TLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CustomLogger implements TLogger {
    private static final Logger MINT_LOG = LoggerFactory.getLogger("MINT");
    private static final Logger FAILED_LOG = LoggerFactory.getLogger("FAILED");
    private static final Logger APP_LOG = LoggerFactory.getLogger("APP");


    public void logApp(Class<?> aClass, TLogLevel tLogLevel, String s) {
        String msg = String.format("%s - %s", aClass.getName(), s);
        switch (tLogLevel) {
            case ERROR -> APP_LOG.error(msg);
            case INFO -> APP_LOG.info(msg);
            case WARNING -> APP_LOG.warn(msg);
            case FAILED -> FAILED_LOG.error(msg);
            case DEBUG -> APP_LOG.debug(msg);
        }
    }

    public void logApp(Class<?> aClass, TLogLevel tLogLevel, String s, Exception e) {
        String msg = String.format("%s - %s", aClass.getName(), s);
        switch (tLogLevel) {
            case ERROR -> APP_LOG.error(msg, e);
            case INFO -> APP_LOG.info(msg, e);
            case WARNING -> APP_LOG.warn(msg, e);
            case FAILED -> FAILED_LOG.error(msg, e);
            case DEBUG -> APP_LOG.debug(msg, e);
        }
    }


    @Override
    public void log(Class<?> aClass, TLogLevel tLogLevel, String s) {
        String msg = String.format("%s - %s", aClass.getName(), s);
        switch (tLogLevel) {
            case ERROR -> MINT_LOG.error(msg);
            case INFO -> MINT_LOG.info(msg);
            case WARNING -> MINT_LOG.warn(msg);
            case FAILED -> FAILED_LOG.error(msg);
            case DEBUG -> MINT_LOG.debug(msg);
        }
    }

    @Override
    public void log(Class<?> aClass, TLogLevel tLogLevel, String s, Exception e) {
        String msg = String.format("%s - %s", aClass.getName(), s);
        switch (tLogLevel) {
            case ERROR -> MINT_LOG.error(msg, e);
            case INFO -> MINT_LOG.info(msg, e);
            case WARNING -> MINT_LOG.warn(msg, e);
            case FAILED -> FAILED_LOG.error(msg, e);
            case DEBUG -> MINT_LOG.debug(msg, e);
        }
    }
}
