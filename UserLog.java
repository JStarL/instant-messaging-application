import java.sql.Timestamp;

public class UserLog {

    private String username;
    private Timestamp login;
    private Timestamp logout;

    public UserLog() {
        username = null;
        login = null;
        logout = null;
    }

    public UserLog(String username, Timestamp login) {
        this();
        
        this.username = username;
        this.login = login;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Timestamp getLogin() {
        return login;
    }

    public void setLogin(Timestamp login) {
        this.login = login;
    }

    public Timestamp getLogout() {
        return logout;
    }

    public void setLogout(Timestamp logout) {
        this.logout = logout;
    }

    // Methods

    public boolean isLoggedInNow() {
        if (username == null ||
            login == null) {
            
            return false;
        } else if (logout == null) {
            
            return true;
        } else {
            return false;
        }
    }

    public boolean isLoggedInSince(Timestamp now, long since) {
        if (username == null ||
            login == null) {
            
            return false;
        }

        Timestamp timeSince = new Timestamp(now.getTime() - (1000L * since));

        if (timeSince.compareTo(login) <= 0) {
            return true;
        } else if (logout == null) {
            return true;
        } else if (timeSince.compareTo(logout) < 0) {
            return true;
        } else {
            return false;
        }
    }

    

    

}