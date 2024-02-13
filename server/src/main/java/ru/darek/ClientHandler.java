package ru.darek;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler {
    public static final Logger logger = LogManager.getLogger(ClientHandler.class.getName());
    private Server server;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String username;
    private boolean isAdmin;
    private static final String HELP = String.format("%s\n%s\n%s\n%s\n%s\n%s\n%s"
            , "регистрация /register login password name"
            , "аутентификация /auth login password"
            , "личное сообщение /w name message"
            , "выход из чата /exit"
            , "список активных пользователей /activelist"
            , "история сообщений /history"
            , "доступное меню /help"
    );
    private static final String HELP_ADM = String.format("%s\n%s\n%s"
            , "забанить пользователя /ban name"
            , "разбанить пользователя /unban name"
            , "остановка сервера /shutdown"
    );

    public String getUsername() {
        return username;
    }

    public ClientHandler(Server server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        new Thread(() -> {
            logger.debug("Запущен поток " + Thread.currentThread().getName());
            try {
                logger.debug("Запуск аутентификации.");
                authentication();
                logger.debug("Запуск слушателя.");
                listenUserChatMessages();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                disconnect();
            }
        }).start();
    }

    private void listenUserChatMessages() throws IOException {
        while (true) {
            String message = in.readUTF();
            logger.debug("message : " + message);
            if (message.startsWith("/")) {
                if (message.equals("/exit")) {
                    sendMessage("/exit_confirmed");
                    break;
                }
                if (message.startsWith("/w ")) {
                    String[] elements = message.split(" ", 3);
                    if (elements.length < 3) {
                        sendMessage("Некорректная команда /w");
                    } else server.sendPrivateMessage(this, elements[1], elements[2]);
                }
                if (message.startsWith("/kick ")) {
                    if (!isAdmin) {
                        sendMessage("Вы не обладаете правами админа!");
                    } else {
                        String[] elements = message.split(" ", 2);
                        if (elements.length < 2) {
                            sendMessage("Некорректная команда /kick");
                        } else {
                            server.kick(elements[1], this);
                        }
                    }
                }
                if (message.startsWith("/shutdown")) {
                    if (!isAdmin) {
                        sendMessage("Вы не обладаете правами админа!");
                    } else {
                        server.shutdown();
                    }
                }
                if (message.startsWith("/ban")) {
                    if (!isAdmin) {
                        sendMessage("Вы не обладаете правами админа!");
                    } else {
                        String[] elements = message.split(" ", 2);
                        if (elements.length < 2) {
                            sendMessage("Некорректная команда /ban");
                        } else {
                            server.ban(elements[1], this, true);
                        }
                    }
                }
                if (message.startsWith("/unban")) {
                    if (!isAdmin) {
                        sendMessage("Вы не обладаете правами админа!");
                    } else {
                        String[] elements = message.split(" ", 2);
                        if (elements.length < 2) {
                            sendMessage("Некорректная команда /unban");
                        } else {
                            server.ban(elements[1], this, false);
                        }
                    }
                }
                if (message.startsWith("/activelist")) {
                    server.activelist(this);
                }
                if (message.startsWith("/changenick")) {
                    System.out.println("ClientHandler /changenick");
                    String[] elements = message.split(" ", 2);
                    if (elements.length < 2) {
                        sendMessage("Некорректная команда /changenick");
                    } else {
                        server.changenick(this, elements[1]);
                    }
                }
                if (message.startsWith("/help")) {
                    if (isAdmin) {
                        sendMessage(HELP + "\n" + HELP_ADM);
                    } else {
                        sendMessage(HELP);
                    }
                }
                if (message.startsWith("/history")) {
                    sendMessage(server.getHistory());
                }
            } else {
                server.broadcastMessage(username + ": " + message);
            }
        }
    }

    public void sendMessage(String message) {
        try {
            out.writeUTF(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        logger.debug("disconnect");
        server.unsubscribe(this);
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean tryToAuthenticate(String message) {
        logger.debug("аутентификация: " + message);
        String[] elements = message.split(" "); // /auth login1 pass1
        if (elements.length != 3) {
            sendMessage("СЕРВЕР: некорректная команда аутентификации");
            return false;
        }
        String login = elements[1];
        String password = elements[2];
        String usernameFromUserService = server.getUserService().getUsernameByLoginAndPassword(login, password);
        if (usernameFromUserService == null) {
            sendMessage("СЕРВЕР: пользователя с указанным логин/паролем не существует");
            return false;
        }
        if (server.isUserBusy(usernameFromUserService)) {
            sendMessage("СЕРВЕР: учетная запись уже занята");
            return false;
        }
        if (server.getUserService().isBanByUsername(usernameFromUserService)) {
            sendMessage("СЕРВЕР: Пользователь забанен!");
            return false;
        }
        username = usernameFromUserService;
        isAdmin = server.getUserService().getIsAdminByUsername(username);

        server.subscribe(this);
        sendMessage("/authok " + username);
        sendMessage("СЕРВЕР: " + username + ", добро пожаловать в чат!");
        if (isAdmin) sendMessage("СЕРВЕР: Вы обладаете правами админа!");
        return true;
    }

    private boolean register(String message) {
        logger.debug("регистрация: " + message);
        String[] elements = message.split(" "); // /auth login1 pass1 user1
        if (elements.length != 4) {
            sendMessage("СЕРВЕР: некорректная команда аутентификации");
            return false;
        }
        String login = elements[1];
        String password = elements[2];
        String registrationUsername = elements[3];
        if (server.getUserService().isLoginAlreadyExist(login)) {
            sendMessage("СЕРВЕР: указанный login уже занят");
            return false;
        }
        if (server.getUserService().isUsernameAlreadyExist(registrationUsername)) {
            sendMessage("СЕРВЕР: указанное имя пользователя уже занято");
            return false;
        }
        server.getUserService().createNewUser(login, password, registrationUsername);
        username = registrationUsername;
        sendMessage("/authok " + username);
        sendMessage("СЕРВЕР: " + username + ", вы успешно прошли регистрацию, добро пожаловать в чат!");
        server.subscribe(this);
        return true;
    }

    private void authentication() throws IOException {
        while (true) {
            String message = in.readUTF();
            boolean isSucceed = false;
            if (message.startsWith("/auth ")) {
                isSucceed = tryToAuthenticate(message);
            } else if (message.startsWith("/register ")) {
                isSucceed = register(message);
            } else if (message.startsWith("/help")){
                sendMessage(HELP);
            } else {
                sendMessage("СЕРВЕР: требуется войти в учетную запись или зарегистрироваться");
            }
            if (isSucceed) {
                break;
            }
        }
    }
}