package ru.darek;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

public class ServerApplication {
    public static final Logger logger = LogManager.getLogger(ServerApplication.class.getName());
    public static void main(String[] args) {
        Server server = new Server(8189);
        logger.info("Сервер запущен с параметрами args: " + Arrays.toString(args));
        logger.info("-Dport=" + System.getProperties().getOrDefault("port", "null"));
        server.start();
        logger.info("Сервер закрывается.");
    }
}