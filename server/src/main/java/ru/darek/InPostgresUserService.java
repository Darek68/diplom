package ru.darek;

import java.sql.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mindrot.jbcrypt.BCrypt;

public class InPostgresUserService implements UserService {
    public static final Logger logger = LogManager.getLogger(InPostgresUserService.class.getName());
    private static final String DATABASE_URL = "jdbc:postgresql://localhost:5432/postgres";
    //  private  static final String SELECT_USERS_SQL = "SELECT u.login, u.username FROM homework24.users u WHERE u.login = ? AND u.password = ?;";
    private static final String SELECT_USERS_SQL = "SELECT u.login,u.hashpass, u.username FROM homework24.users u WHERE u.login = ?;";
    private static final String SELECT_IS_ADMIN_SQL = """
               SELECT u.login FROM homework24.users u, homework24.usertorole ur,homework24.roles r 
               WHERE u.username = ? AND u.login = ur.user_id AND r.id = ur.role_id AND r.name = 'ADMIN';
            """;
    private static final String SELECT_USER_BY_LOGIN_SQL = "SELECT * FROM homework24.users u WHERE u.login = ?;";
    private static final String SELECT_USER_BY_USERNAME_SQL = "SELECT * FROM homework24.users u WHERE u.username = ?;";
    private static final String INSERT_USER_SQL = """   
                BEGIN TRANSACTION;        
                INSERT INTO homework24.users (login,hashpass,username) VALUES (?,?,?);
                INSERT INTO homework24.usertorole (user_id,role_id) VALUES (?,2);
                COMMIT TRANSACTION;
            """;
    private static final String UPDATE_BAN_BY_USERNAME_SQL = "UPDATE homework24.users u SET ban = ? WHERE u.username = ?;";
    private static final String UPDATE_USERNAME_BY_USERNAME_SQL = "UPDATE homework24.users u SET username = ? WHERE u.username = ?;";

    @Override
    public String getUsernameByLoginAndPassword(String login, String password) {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL, "postgres", "352800")) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_USERS_SQL)) {
                System.out.println("login: " + login + " password: " + password);
                preparedStatement.setString(1, login);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        logger.debug(resultSet.getString(1) + " " + resultSet.getString(2) + " " + resultSet.getString(3));
                        if (BCrypt.checkpw(password, resultSet.getString(2))) {
                            String username = resultSet.getString(3);
                            return username;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Поиск пользователя в БД вызвал исключение: " + e.getMessage());
            e.printStackTrace();
        }
        logger.debug("Не нашли пользователя " + login);
        return null;
    }

    @Override
    public boolean getIsAdminByUsername(String username) {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL, "postgres", "352800")) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_IS_ADMIN_SQL)) {
                preparedStatement.setString(1, username);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        String role = resultSet.getString(1);
                        logger.info("InPostgresUserService: пользователь " + username + " имеет роль ADMIN");
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Запрос наличия админских прав вызвал исключение: " + e.getMessage());
            e.printStackTrace();
        }
        logger.info("Y пользователя " + username + " нет роли ADMIN");
        return false;
    }

    @Override
    public void createNewUser(String login, String password, String username) {
        String salt = BCrypt.gensalt();
        String hashedPassword = BCrypt.hashpw(password, salt);
        try (Connection connection = DriverManager.getConnection(DATABASE_URL, "postgres", "352800")) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(INSERT_USER_SQL)) {
                preparedStatement.setString(1, login);
                preparedStatement.setString(2, hashedPassword);
                preparedStatement.setString(3, username);
                preparedStatement.setString(4, login);
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error("Запрос на создание пользователя вызвал исключение: " + e.getMessage());
            e.printStackTrace();
        }
        logger.info("Пользователь " + username + "(" + login + ") успешно создан c ролю manager");
    }

    @Override
    public boolean isLoginAlreadyExist(String login) {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL, "postgres", "352800")) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_USER_BY_LOGIN_SQL)) {
                preparedStatement.setString(1, login);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        String username = resultSet.getString(3);
                        logger.debug("логин " + login + " уже занят пользователем: " + username);
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Запрос свободности логина вызвал исключение: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean isUsernameAlreadyExist(String username) {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL, "postgres", "352800")) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_USER_BY_USERNAME_SQL)) {
                preparedStatement.setString(1, username);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        String login = resultSet.getString(1);
                        logger.debug("Пользователь " + username + " уже занят логином: " + login);
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка запроса проверки username " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void setBanByUsername(String username, boolean banUnban) {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL, "postgres", "352800")) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_BAN_BY_USERNAME_SQL)) {
                preparedStatement.setBoolean(1, banUnban);
                preparedStatement.setString(2, username);
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error("Ошибка запроса бана " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean isBanByUsername(String username) {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL, "postgres", "352800")) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_USER_BY_USERNAME_SQL)) {
                preparedStatement.setString(1, username);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        return resultSet.getBoolean(4);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Запрос наличия бана вызвал исключение: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void setNewUsername(String currentUserName, String newUsername) {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL, "postgres", "352800")) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_USERNAME_BY_USERNAME_SQL)) {
                preparedStatement.setString(1, newUsername);
                preparedStatement.setString(2, currentUserName);
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error("Запрос смены имени вызвал исключение: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
