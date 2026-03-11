package org.example.config;

public class AppConfig {

    private final String mysqlUser;
    private final String mysqlPassword;
    private final String mysqlUrl;
    private final String profileName; // which profile is active, or null

    public AppConfig(String mysqlUser, String mysqlPassword) {
        this(mysqlUser, mysqlPassword, "localhost", "3306", null);
    }

    public AppConfig(String user, String password, String host, String port, String profileName) {
        this.mysqlUser    = user;
        this.mysqlPassword = password;
        this.mysqlUrl     = "jdbc:mysql://" + host + ":" + port;
        this.profileName  = profileName;
    }

    public static AppConfig fromProfile(ProfileStore.Profile p) {
        return new AppConfig(p.username(), p.password(),
                p.host() == null || p.host().isEmpty() ? "localhost" : p.host(),
                p.port() == null || p.port().isEmpty() ? "3306"      : p.port(),
                p.name());
    }

    public String getMysqlUser()     { return mysqlUser;    }
    public String getMysqlPassword() { return mysqlPassword; }
    public String getMysqlUrl()      { return mysqlUrl;     }
    public String getProfileName()   { return profileName;  }
}