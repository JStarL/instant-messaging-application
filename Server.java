import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;

public class Server implements Runnable {

    private static boolean serverDebugMode;

    private static int serverPort;
    private static int blockDuration;
    private static int timeout;
    /**
     * Maximum number of times a thread will try to send a msg before it is deemed to have been lost
     */
    private static int maxSendNumber;

    private static int maxLoginTries;

    private static String credentialsFile;
    private static ArrayList<User> users;

    private static ArrayList<UserLog> onlineHistory;

    private String threadType;
    private Socket conn;

    private PrintWriter serverWriter;
    private BufferedReader serverReader;
    private MessageMapParser mmParser;

    private String username;
    private int userIndex;
    private User clientUser;


    public Server() {
        conn = null;
        threadType = null;

        username = null;
        userIndex = -1;
        clientUser = null;

        serverReader = null;
        serverWriter = null;
        mmParser = new MessageMapParser();
    }

    public Server(Socket s) {
        this();
        
        conn = s;
    }

    private static int indexOfUser(String username) {
        if (username.equals("")) return -1;

        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUsername().equals(username)) {
                return i;
            }
        }

        return -1;
    }

    private static User getUser(String username) {
        if (username.equals("")) return null;

        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUsername().equals(username)) {
                return users.get(i);
            }
        }

        return null;
    }

    private static User getUserByPort(int port) {

        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getMainPort() == port) {
                return users.get(i);
            }
        }

        return null;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {

        if (args.length < 3) {
            System.out.println("Usage: java Server <server_port> <block_duration> <timeout>");
            return;
        }

        if (args[0].equals("-d")) {
            if (args.length < 4) {
                System.out.println("Usage: java Server -d <server_port> <block_duration> <timeout>");
                return;
            }

            serverPort = Integer.parseInt(args[1]);
            blockDuration = Integer.parseInt(args[2]);
            timeout = Integer.parseInt(args[3]);

            serverDebugMode = true;

        } else {
            serverPort = Integer.parseInt(args[0]);
            blockDuration = Integer.parseInt(args[1]);
            timeout = Integer.parseInt(args[2]);

            serverDebugMode = false;
        }

        maxSendNumber = 10;
        maxLoginTries = 3;

        credentialsFile = "credentials.txt";
        users = new ArrayList<User>();

        loadCredentialsFile();

        onlineHistory = new ArrayList<UserLog>();

        ServerSocket mainSocket = new ServerSocket(serverPort);
        printDebug("Server listening on port " + serverPort);

        while (true) {
            Server clientResponder = new Server(mainSocket.accept());
            Thread thread = new Thread(clientResponder);
            thread.start();
        }

    }

    private static void loadCredentialsFile() throws IOException {
        Path credFile = Paths.get(credentialsFile);
        List<String> credentials = Files.readAllLines(credFile);

        Iterator<String> credIterator = credentials.iterator();

        printDebug("Loading Credentials File...");

        while (credIterator.hasNext()) {
            String userCredentials = credIterator.next();
            String[] userAndPword = userCredentials.split(" ");

            printDebug("User: " + userAndPword[0]);
            printDebug("Password: " + userAndPword[1]);

            User user = new User(userAndPword[0], userAndPword[1]);

            users.add(user);
        }

    }

    @Override
    public void run() {
        if (conn != null) {
            try {
                if (!initialiseClientThread()) {throw new Exception("Server-Client thread not initialised");}
            } catch (Exception e) {
                printDebug(e.getMessage());
                return;
            }
        }

        switch (threadType) {
            case "server-read":
                
                serverReaderThread();

                break;
        
            case "server-write":
                
                serverWriterThread();

                break;

            default:
                break;
        }

    }

    private boolean initialiseClientThread() throws IOException, SocketException {
        conn.setSoTimeout(100);

        serverWriter = new PrintWriter(conn.getOutputStream());
        serverReader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

        HashMap<String, String> previousMap = new HashMap<String, String>();

        for (int i = 0; i < maxSendNumber; i++) {
            String recvdMsg = null;
            try {
                recvdMsg = serverReader.readLine();
            } catch (SocketTimeoutException e) {
                continue;
            }
            
            printDebug("[Round " + i + "]: Received " + recvdMsg);
            
            HashMap<String, String> recvdMap;
            
            try {
                recvdMap = mmParser.parseMessage(recvdMsg);
            } catch (Exception e) {
                continue;
            }
            
            if (recvdMap.equals(previousMap)) {
                continue;
            }
            
            previousMap = mmParser.mapClone(recvdMap);
            
            boolean goodMsg = false;

            if (recvdMap.get("tag").equals("thread") &&
                recvdMap.containsKey("client")) {
                
                String clientThreadType = recvdMap.get("client");

                if (clientThreadType.equals("write")) {
                    threadType = "server-read";
                    goodMsg = true;
                } else if (clientThreadType.equals("read")) {
                    threadType = "server-write";
                    
                    if (recvdMap.containsKey("mainPort")) {
                        int clientWritePort = Integer.parseInt(recvdMap.get("mainPort"));

                        clientUser = getUserByPort(clientWritePort);

                        if (clientUser != null) {
                            this.username = clientUser.getUsername();
                            this.userIndex = indexOfUser(this.username);
                            recvdMap.put("user", this.username);
                            goodMsg = true;
                        } else {
                            recvdMap.put("invalid", "port");
                        }
                    }
                    
                } 
                
            }

            printDebug("The thread type is: " + threadType);
            

            recvdMap.remove("client");

            if (goodMsg) {
                recvdMap.put("status", "recvd");
            } else {
                recvdMap.put("status", "try-again");
            }

            String returnString = null;
            try {
                returnString = mmParser.convertToMsg(recvdMap);
            } catch (Exception e) {
                printDebug(e.getMessage());
                continue;
            }

            printDebug("[Round " + i + "]: Sending " + returnString);

            serverWriter.println(returnString);
            serverWriter.flush();

            if (goodMsg) {
                return true;
            }

        }

        return false;
    }    

    private void serverReaderThread() {

        boolean userAuthed;
        try {
            userAuthed = userAuthAction();
        } catch (Exception e) {
            printDebug(e.getMessage());
            return;
        }

        if (!userAuthed) {
            return;
        } else {
            /*
            1) Add user to online history
            2) Send log in notification to all other users (except blocked)
            */

            UserLog userInfo = new UserLog(this.username, new Timestamp(System.currentTimeMillis()));
            onlineHistory.add(userInfo);

            loginNotifyAction();
        }

        HashMap<String, String> previousMap = new HashMap<String, String>();

        String serverReaderDebugPrompt = "Server-Reader [User = " + this.username + "] : ";
        while (clientUser.isLoggedIn()) {
            
            // 1) Receive Message

            String recvdMsg = null;
            try {
                recvdMsg = serverReader.readLine();
            } catch (SocketTimeoutException e) {
                // check this user is still logged in
                continue;
            } catch (Exception e) {
                printDebug(e.getMessage());
                return;
            }
            
            printDebug(serverReaderDebugPrompt + "Received " + recvdMsg);
            
            HashMap<String, String> recvdMap;
            
            try {
                recvdMap = mmParser.parseMessage(recvdMsg);
            } catch (Exception e) {
                printDebug(e.getMessage());
                continue;
            }
            
            if (recvdMap.equals(previousMap)) {
                continue;
            }
            
            previousMap = mmParser.mapClone(recvdMap);

            // 2) Create Response

            //boolean goodMsg = false;
            HashMap<String, String> responseMap;
                
            try {
                responseMap = serverReaderAction(recvdMap);                    
            } catch (Exception e) {
                printDebug(e.getMessage());
                continue;
            }

            // 3) Save state

            // 4) Send Response

            String returnString = null;
            try {
                returnString = mmParser.convertToMsg(responseMap);
            } catch (Exception e) {
                printDebug(e.getMessage());
                continue;
            }
            
            printDebug(serverReaderDebugPrompt + "Sending " + returnString);
            
            serverWriter.println(returnString);
            serverWriter.flush();

            // 5) Take end action, eg return...

            // if valid msg, increase the timeout

            if (!responseMap.containsKey("missing") &&
            !responseMap.containsKey("invalid")) {
                clientUser.setTimeoutTime(System.currentTimeMillis() + (1000L * timeout));
            }

        }

    }

    private HashMap<String, String> serverReaderAction(HashMap<String, String> recvdMap) throws Exception {

        HashMap<String, String> responseMap = mmParser.mapClone(recvdMap);

        String tagType = recvdMap.get("tag");

        switch (tagType) {
            case "msg":
                messageAction(recvdMap, responseMap);
            break;
        
            case "online":
                onlineHistoryAction(recvdMap, responseMap);
            break;

            case "block":
                blockAction(recvdMap, responseMap);
            break;

            case "logout":
                logoutAction(responseMap);
                
            break;


            default:
            
            throw new Exception("Received Unsupported Tag Type");

        }

        return responseMap;

    }

    private void loginNotifyAction() {

        if (clientUser.isLoggedIn()) {
            
            HashMap<String, String> loginJob = new HashMap<String, String>();
        
            loginJob.put("tag", "login");
            loginJob.put("body", null);
            loginJob.put("user", this.username);

            for (User eachUser : users) {
                if (eachUser.getUsername().equals(this.username)) {
                    continue;
                }

                if (clientUser.isUserBlocked(eachUser.getUsername())) {
                    continue;
                }

                HashMap<String, String> eachUserJob = mmParser.mapClone(loginJob);

                eachUser.appendJob(eachUserJob);
            }
        }
    }

    private void logoutAction(HashMap<String, String> responseMap) {

        if (clientUser.isLoggedIn()) {
            
            // 1) Log Out

            clientUser.setLoggedIn(false);
            responseMap.put("status", "done");

            clientUser.setMainPort(-1);
            
            // 2) Presence Notification - Log Out

            responseMap.put("user", this.username);

            for (User eachUser : users) {
                
                if (eachUser.getUsername().equals(this.username)) {
                    continue;
                }

                if (clientUser.isUserBlocked(eachUser.getUsername())) {
                    continue;
                }

                HashMap<String, String> eachUserJob = mmParser.mapClone(responseMap);

                eachUser.appendJob(eachUserJob);

            }

            // responseMap.remove("user", this.username);

            // 3) update Online History

            for (int i = onlineHistory.size() - 1; i >=0; i--) {
                
                UserLog currUserLog = onlineHistory.get(i);
                
                if (this.username.equals(currUserLog.getUsername()) &&
                currUserLog.getLogout() == null) {
                    currUserLog.setLogout(new Timestamp(System.currentTimeMillis()));
                    break;
                }
            }

        } else {
            responseMap.put("status", "already-out");

        }
        
    }

    private void messageAction(HashMap<String, String> recvdMap, HashMap<String, String> responseMap) {

        if (!recvdMap.containsKey("type")) {
            responseMap.put("missing", "type");
            return;
        }

        String msgType = recvdMap.get("type");
        if (msgType.equals("one")) {
            
            if (!recvdMap.containsKey("toUser")) {
                responseMap.put("missing", "toUser");
                return;
            }

            String recipientUsername = recvdMap.get("toUser");

            User recipientUser = getUser(recipientUsername);

            if (recipientUser == null) {
                responseMap.put("returnStatus", "no-such-user");
                
            } else if (recipientUsername.equals(this.username)) {
                responseMap.put("returnStatus", "self");
                
            } else {
                
                if (recipientUser.isUserBlocked(this.username)) {
                    responseMap.put("returnStatus", "blocked");
                    
                } else {
                    responseMap.put("returnStatus", "sent");
                    
                    recvdMap.put("fromUser", this.username);

                    recipientUser.appendJob(recvdMap);

                }
            }

        } else if (msgType.equals("all")) {
            
            recvdMap.put("fromUser", this.username);

            boolean sentToAll = true;
            for (User eachUser : users) {
                
                if (eachUser.getUsername().equals(this.username)) {
                    continue;
                }

                if (eachUser.isUserBlocked(this.username)) {
                    sentToAll = false;
                } else {
                    
                    HashMap<String, String> eachUserMap = mmParser.mapClone(recvdMap);

                    eachUser.appendJob(eachUserMap);

                }
            }

            if (sentToAll) {
                responseMap.put("returnStatus", "sent-all");
            } else {
                responseMap.put("returnStatus", "sent-some");
            }

        } else {
            responseMap.put("invalid", "type");

        }


    }

    private void onlineHistoryAction(HashMap<String, String> recvdMap, HashMap<String, String> responseMap) {
        if (!recvdMap.containsKey("type")) {
            responseMap.put("missing", "type");
            return;
        }

        String msgType = recvdMap.get("type");
        if (msgType.equals("now")) {
            ArrayList<String> usersOnlineNow = new ArrayList<String>();

            for (UserLog userInfo : onlineHistory) {
                
                if (userInfo.getUsername().equals(this.username)) {
                    continue;
                }

                if (userInfo.isLoggedInNow()) {
                    
                    // add only if not blocked

                    User currUser = getUser(userInfo.getUsername());
                    
                    if (currUser.isUserBlocked(this.username)) {
                        continue;
                    }

                    usersOnlineNow.add(userInfo.getUsername());
                }
            }

            String onlineUserString = null;
            if (usersOnlineNow.size() > 0) {
                onlineUserString = String.join(" ", usersOnlineNow);
            }

            responseMap.put("body", onlineUserString);

        } else if (msgType.equals("since")) {
            
            if (!recvdMap.containsKey("time")) {
                responseMap.put("missing", "time");
                return;
            }
            
            int secondsSince = 0;
            try {
                secondsSince = Integer.parseInt(recvdMap.get("time"));
                
            } catch (NumberFormatException e) {
                responseMap.put("invalid", "time");
                return;
            }

            ArrayList<String> usersOnlineSince = new ArrayList<String>();

            Timestamp nowTimestamp = new Timestamp(System.currentTimeMillis());

            for (UserLog userInfo : onlineHistory) {
                
                if (userInfo.getUsername().equals(this.username)) {
                    continue;
                }
                
                if (userInfo.isLoggedInSince(nowTimestamp, secondsSince)) {
                    
                    // add only if not blocked

                    User currUser = getUser(userInfo.getUsername());
                    
                    if (currUser.isUserBlocked(this.username)) {
                        continue;
                    }
                    
                    usersOnlineSince.add(userInfo.getUsername());
                }
            }

            String onlineUserString = null;
            if (usersOnlineSince.size() > 0) {
                onlineUserString = String.join(" ", usersOnlineSince);
            }

            responseMap.put("body", onlineUserString);

        } else {
            responseMap.put("invalid", "type");
            
        }
    }

    private void blockAction(HashMap<String, String> recvdMap, HashMap<String, String> responseMap) {
        if (!recvdMap.containsKey("type")) {
            responseMap.put("missing", "type");
            return;
        } else if (!recvdMap.containsKey("user")) {
            responseMap.put("missing", "user");
            return;
        }

        String msgType = recvdMap.get("type");
        String targetUser = recvdMap.get("user");

        if (targetUser.equals(this.username)) {
            responseMap.put("blockStatus", "false-self");
            return;
        }

        if (msgType.equals("on")) {
            
            boolean blockSuccess = clientUser.addBlockedUser(targetUser);

            if (blockSuccess) {
                responseMap.put("blockStatus", "true");                
            } else {
                responseMap.put("blockStatus", "false-already");
            }


        } else if (msgType.equals("off")) {
            
            boolean unblockSuccess = clientUser.removeBlockedUser(targetUser);

            if (unblockSuccess) {
                responseMap.put("blockStatus", "true");                
            } else {
                responseMap.put("blockStatus", "false-already");
            }

        } else {
            responseMap.put("invalid", "type");

        }

    }

    private boolean userAuthAction() throws IOException, InterruptedException {

        HashMap<String, String> previousMap = new HashMap<String, String>();

        String username = null;

        conn.setSoTimeout(0);

        int tryNum = 0;
        for (int i = 0; i < maxSendNumber; i++) {
            
            // 1) Receive Message

            String recvdMsg = null;
            try {
                recvdMsg = serverReader.readLine();
            } catch (SocketTimeoutException e) {
                continue;
            }
            
            printDebug("Auth: Received " + recvdMsg);
            
            HashMap<String, String> recvdMap;
            
            try {
                recvdMap = mmParser.parseMessage(recvdMsg);
            } catch (Exception e) {
                continue;
            }
            
            if (recvdMap.equals(previousMap)) {
                continue;
            }
            
            // 2) Create Response

            //boolean goodMsg = false;
            HashMap<String, String> responseMap;

            if (!recvdMap.get("tag").equals("auth") ||
                !recvdMap.containsKey("status")) {
                continue;
            }
                
            try {
                responseMap = serverAuthAction(recvdMap, username, tryNum);                    
            } catch (Exception e) {
                printDebug(e.getMessage());
                continue;
            }

            // 3) Save state in this method

            if (recvdMap.containsKey("username")) {
                username = recvdMap.get("username");
            }
            
            if (responseMap.get("status").equals("try-again")) {
                tryNum++;
            }

            previousMap = mmParser.mapClone(recvdMap);
            
            // 4) Send Response

            String returnString = null;
            try {
                returnString = mmParser.convertToMsg(responseMap);
            } catch (Exception e) {
                printDebug(e.getMessage());
                continue;
            }
            
            printDebug("Auth: Sending " + returnString);
            
            serverWriter.println(returnString);
            serverWriter.flush();
            
            // 5) Take end action, eg return...

            if (responseMap.get("status").equals("matched") ||
                responseMap.get("status").equals("registered")) {
                
                conn.setSoTimeout(100);
                return true;
            }
            
            if (responseMap.get("status").equals("already-online")) {
                return false;
            }

            if (responseMap.get("status").equals("blocked")) {

                User clientUser = getUser(username);

                if (!clientUser.isBlocked()) {

                    clientUser.setBlocked(true);

                    Thread.sleep(1000L * blockDuration);
    
                    // Set unblocked
                    
                    clientUser.setBlocked(false);

                }

                return false;
            }
        }

        return false;
    }

    private HashMap<String, String> serverAuthAction(HashMap<String, String> map, String username, int tryNum) throws Exception {
        String authStatus = map.get("status");

        HashMap<String, String> responseMap = mmParser.mapClone(map);
        int userPos = -1;
        switch (authStatus) {
            case "username":
                
                if (!map.containsKey("username")) {
                    throw new Exception("<auth> does not contain 'username'");
                }

                String uname = map.get("username");

                userPos = indexOfUser(uname);

                responseMap.remove("username");

                if (userPos == -1) {
                    // new user
                    responseMap.put("status", "new-user");
                    
                } else {
                    if (users.get(userPos).isLoggedIn()) {
                        // already logged in
                        responseMap.put("status", "already-online");
                        
                    } else if (users.get(userPos).isBlocked()) {
                        // user is Blocked
                        responseMap.put("status", "blocked");
                        
                    } else {
                        // user can log in
                        responseMap.put("status", "enter-password");

                    }
                }


                break;
        
            case "password":
                
                if (!map.containsKey("password")) {
                    throw new Exception("<auth> does not contain 'password'");
                }

                userPos = indexOfUser(username);

                String password = map.get("password");

                responseMap.remove("password");

                if (userPos == -1) {
                    // New User's Password -> Create New User
                    User newUser = new User(username, password);

                    users.add(newUser);

                    this.username = username;
                    this.userIndex = users.size() - 1;
                    this.clientUser = newUser;

                    clientUser.setLoggedIn(true);
                    clientUser.setTimeoutTime(System.currentTimeMillis() + (1000L * timeout));
                    clientUser.setMainPort(conn.getPort());

                    responseMap.replace("status", "registered");

                    // append credentials.txt

                    String appendLine = System.getProperty("line.separator") + username + " " + password;
                    Path credFile = Paths.get(credentialsFile);
                    Files.write(credFile, appendLine.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);

                } else {
                    User tmpUser = getUser(username);
                    
                    if (tryNum >= maxLoginTries) {
                        responseMap.put("status", "blocked");

                    } else if (tmpUser.isBlocked()) {
                        responseMap.put("status", "blocked");
                        
                    } else {
                        if (tmpUser.getPassword().equals(password)) {
                            responseMap.put("status", "matched");

                            this.username = username;
                            this.userIndex = userPos;
                            this.clientUser = tmpUser;

                            clientUser.setLoggedIn(true);
                            clientUser.setBlocked(false);

                            clientUser.setTimeoutTime(System.currentTimeMillis() + (1000L * timeout));
                            clientUser.setMainPort(conn.getPort());
                            
                        } else {
                            if (tryNum < (maxLoginTries - 1)) {
                                // more tries left
                                responseMap.put("status", "try-again");
                            } else{
                                // last mismatch - user now blocked
                                /* 
                                    NOTE userAuth implements the actual blocking,
                                    and the unblocking after block_duration
                                    by looking for the "status" -> "blocked" entry
                                */
                                responseMap.put("status", "blocked");

                            }

                        }
                    }
                }


                break;
            
            default:
                break;
        }

        return responseMap;
    }

    private void serverWriterThread() {
        
        if (clientUser == null) {
            return;
        }
        
        clientUser.removeOnlineOnlyJobs();

        while (clientUser.isLoggedIn()) {
            
            // Timeout Action

            if (clientUser.isPastTimeoutTime()) {

                Timestamp tNow = new Timestamp(System.currentTimeMillis());
                printDebug("User: " + this.username + "has timed out at " + tNow.toString());

                HashMap<String, String> logoutJob = new HashMap<String, String>();

                logoutJob.put("tag", "logout");
                logoutJob.put("body", null);

                logoutAction(logoutJob);

                // Send Logout Tag

                String logoutString = null;
                try {
                    logoutString = mmParser.convertToMsg(logoutJob);
                } catch (Exception e) {
                    printDebug(e.getMessage());
                }
                
                printDebug("Logout: Sending " + logoutString);
                
                serverWriter.println(logoutString);
                serverWriter.flush();
                
            } else {

                // Send this user's job from the start of the queue

                HashMap<String, String> nextJob = clientUser.popJob();

                if (nextJob == null) {
                    
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                        printDebug("Server-Writer Interrupted");
                    }
                    
                    continue;
                }

                    
                HashMap<String, String> previousMap = new HashMap<String, String>();
            
                String serverWriterDebugPrompt = "Server-Wrtier [User = " + this.username + "] : ";

                for (int i = 0; i < maxSendNumber; i++) {
                    
                    
                    String jobString = null;
                    try {
                        jobString = mmParser.convertToMsg(nextJob);
                    } catch (Exception e) {
                        printDebug(e.getMessage());
                    }
                    
                    printDebug(serverWriterDebugPrompt + "Sending " + jobString);
                    
                    serverWriter.println(jobString);
                    serverWriter.flush();


                    String recvdMsg = null;
                    try {
                        recvdMsg = serverReader.readLine();
                    } catch (SocketTimeoutException e) {
                        continue;
                    } catch (IOException e) {
                        printDebug(e.getMessage());
                        break;
                    }
                    
                    printDebug(serverWriterDebugPrompt + "Received " + recvdMsg);
                    
                    HashMap<String, String> recvdMap;

                    try {
                        recvdMap = mmParser.parseMessage(recvdMsg);
                    } catch (Exception e) {
                        continue;
                    }

                    if (recvdMap.equals(previousMap)) {
                        continue;
                    }
                    
                    previousMap = recvdMap;

                    if (recvdMap.containsKey("status") && recvdMap.get("status").equals("recvd")) {
                        // setup complete
                        break;
                    }


                }

                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    printDebug("Server-Writer Interrupted");
                }

            }
        }


    }

    private static void printDebug(String msg) {
        if (serverDebugMode) {
            System.out.println(msg);
        }
    }

}