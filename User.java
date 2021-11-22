import java.util.ArrayList;
import java.util.HashMap;

public class User {

    private String username;
    private String password;

    
    private boolean loggedIn;
    private boolean blocked;
    
    private ArrayList<String> blockedUsers;

    /**
     * Types of jobs:
     * Messages -> single user
     * Broadcasts
     * Presence Notifications -> logged in / out
     */
    private ArrayList<HashMap<String, String>> jobs;

    private long timeoutTime;
    private int mainPort;
    
    public User() {
    
        username = "";
        password = "";

        loggedIn = false;
        blocked = false;

        blockedUsers = new ArrayList<String>();
        jobs = new ArrayList<HashMap<String, String>>();

        timeoutTime = System.currentTimeMillis();
        mainPort = -1;
    }    

    public User(String username, String password) {
        this();
        
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }
    
    public long getTimeoutTime() {
        return timeoutTime;
    }

    public void setTimeoutTime(long timeoutTime) {
        this.timeoutTime = timeoutTime;
    }

    public boolean isPastTimeoutTime() {
        if (System.currentTimeMillis() >= timeoutTime) {
            return true;
        } else {
            return false;
        }
    }

    public int getMainPort() {
        return mainPort;
    }

    public void setMainPort(int mainPort) {
        this.mainPort = mainPort;
    }

    public boolean addBlockedUser(String blockedUser) {
        if (blockedUser == null || blockedUser == "") {
            return false;
        }

        for (int i = 0; i < blockedUsers.size() ; i++) {
            if (blockedUsers.get(i).equals(blockedUser)) {
                // already blocked
                return false;
            }
        }

        blockedUsers.add(blockedUser);
        return true;
    }

    public boolean removeBlockedUser(String blockedUser) {
        if (blockedUser == null || blockedUser == "") {
            return false;
        }

        for (int i = 0; i < blockedUsers.size() ; i++) {
            if (blockedUsers.get(i).equals(blockedUser)) {
                // remove blocked user
                blockedUsers.remove(i);
                return true;
            }
        }

        return false;
    }

    public boolean isUserBlocked(String targetUser) {
        if (targetUser == null || targetUser == "") {
            return false;
        }

        for (int i = 0; i < blockedUsers.size() ; i++) {
            if (blockedUsers.get(i).equals(targetUser)) {
                // targetUser is blocked
                return true;
            }
        }

        // targetUser not blocked
        return false;
    }

    public boolean appendJob(HashMap<String, String> job) {
        if (job != null) {
            jobs.add(job);
            return true;
        } else {
            return false;
        }
    }

    public HashMap<String, String> popJob() {
        if (jobs.size() > 0) {
            HashMap<String, String> nextJob = jobs.get(0);
            jobs.remove(0);
            return nextJob;
        } else {
            return null;
        }
    }

    public int removeOnlineOnlyJobs() {
        /**
         * Removes:
         *  Broadcasts
         *  Presence Notifications -> Logged in, Logged Out
         * Keeps:
         *  Messages to a single user
         */

        int currSize = jobs.size();

        int i = 0;
        while (i < currSize) {
            HashMap<String, String> currMap = jobs.get(i);

            String tagType = currMap.get("tag");

            // Presence Notification
            if (tagType.equals("login") || tagType.equals("logout")) {
                jobs.remove(i);
                currSize--;
                continue;
            } else if (tagType.equals("msg")) {

                // Broadcast
                if (currMap.get("type").equals("all")) {
                    jobs.remove(i);
                    currSize--;
                    continue;
                }
            }

            i++;
        }

        return i;
    }

}