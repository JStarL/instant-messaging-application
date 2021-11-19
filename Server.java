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

    private static ArrayList<HashMap<String, String>> onlineHistory;

    private String threadType;
    private Socket conn;

    private PrintWriter serverWriter;
    private BufferedReader serverReader;
    private MessageMapParser mmParser;

    private String username;
    private int userIndex;

    public Server() {
        conn = null;
        threadType = null;

        username = null;
        userIndex = -1;

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

        onlineHistory = new ArrayList<HashMap<String, String>>();

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
                
                break;

            case "":
                
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
            
            boolean goodMsg = false;

            if (recvdMap.get("tag").equals("thread") &&
                recvdMap.containsKey("client")) {
                
                String clientThreadType = recvdMap.get("client");

                if (clientThreadType.equals("write")) {
                    threadType = "server-read";
                    goodMsg = true;
                } else if (clientThreadType.equals("read")) {
                    threadType = "server-write";
                    goodMsg = true;
                } 

            }

            printDebug("The thread type is: " + threadType);
            
            previousMap = mmParser.mapClone(recvdMap);

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
        }



    }

    private boolean userAuthAction() throws IOException {

        HashMap<String, String> previousMap = new HashMap<String, String>();

        String username = null;

        int tryNum = 0;
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
            
            boolean goodMsg = false;
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

            if (recvdMap.containsKey("username")) {
                username = recvdMap.get("username");
            }
            
            if (responseMap.get("status").equals("try-again")) {
                tryNum++;
            }

            previousMap = mmParser.mapClone(recvdMap);
            
            String returnString = null;
            try {
                returnString = mmParser.convertToMsg(responseMap);
            } catch (Exception e) {
                printDebug(e.getMessage());
                continue;
            }
            
            printDebug("[Round " + i + "]: Sending " + returnString);
            
            serverWriter.println(returnString);
            serverWriter.flush();
            
            if (responseMap.get("status").equals("matched") ||
                responseMap.get("status").equals("registered")) {
                return true;
            }
            
            if (responseMap.get("status").equals("blocked")) {
                Thread.sleep(1000L * blockDuration);

                // Set unblocked

                int userPos = indexOfUser(username);

                users.get(userPos).setBlocked(false);

                return false;
            }
        }
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
                    responseMap.replace("status", "new-user");
                    
                } else {
                    if (users.get(userPos).isLoggedIn()) {
                        // already logged in
                        responseMap.replace("status", "already-online");
                        
                    } else if (users.get(userPos).isBlocked()) {
                        // user is Blocked
                        responseMap.replace("status", "blocked");
                        
                    } else {
                        // user can log in
                        responseMap.replace("status", "enter-password");

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

                    responseMap.replace("status", "registered");

                    // append credentials.txt

                    String appendLine = System.getProperty("line.separator") + username + " " + password;
                    Path credFile = Paths.get(credentialsFile);
                    Files.write(credFile, appendLine.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);

                } else {
                    if (tryNum >= maxLoginTries) {
                        responseMap.replace("status", "blocked");

                    } else if (users.get(userPos).isBlocked()) {
                        responseMap.replace("status", "blocked");
                        
                    } else {
                        if (users.get(userPos).getPassword().equals(password)) {
                            responseMap.replace("status", "matched");

                            this.username = username;
                            this.userIndex = userPos;

                            users.get(userIndex).setLoggedIn(true);
                            
                        } else {
                            if (tryNum < (maxLoginTries - 1)) {
                                // more tries left
                                responseMap.replace("status", "try-again");
                            } else{
                                // last mismatch - user now blocked
                                responseMap.replace("status", "blocked");

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
        // user.removeOnlineOnlyJobs()
    }

    private static void printDebug(String msg) {
        if (serverDebugMode) {
            System.out.println(msg);
        }
    }

}