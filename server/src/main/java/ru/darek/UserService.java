package ru.darek;

public interface UserService {
    String getUsernameByLoginAndPassword(String login, String password);
    boolean getIsAdminByUsername(String username);
    void createNewUser(String login, String password, String username);
    boolean isLoginAlreadyExist(String login);
    boolean isUsernameAlreadyExist(String username);
    void setBanByUsername(String username,boolean banUnban);
    boolean isBanByUsername(String username);
    void setNewUsername(String currentUserName,String newUsername);

}