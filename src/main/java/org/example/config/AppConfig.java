package org.example.config;

/**
 * Holds runtime configuration for the application.
 * Passed explicitly rather than via public static fields.
 */
public class AppConfig {

    private final String mysqlUser;
    private final String mysqlPassword;
    private final String mysqlUrl;

    public AppConfig(String mysqlUser, String mysqlPassword) {
        this(mysqlUser, mysqlPassword, "jdbc:mysql://localhost:3306");
    }

    public AppConfig(String mysqlUser, String mysqlPassword, String mysqlUrl) {
        this.mysqlUser = mysqlUser;
        this.mysqlPassword = mysqlPassword;
        this.mysqlUrl = mysqlUrl;
    }

    public String getMysqlUser()     { return mysqlUser; }
    public String getMysqlPassword() { return mysqlPassword; }
    public String getMysqlUrl()      { return mysqlUrl; }
}
