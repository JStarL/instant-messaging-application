import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;

public class Client implements Runnable {

    private static boolean clientDebugMode;
    private static int serverPort;
    private static String serverName;

    private static int localWritePort;

    /**
     * Maximum number of times a thread will try to send a msg before it is deemed to have been lost
     */
    private static int maxSendNumber;

    private static String username;
    private static boolean isLoggedIn;

    private String threadType;
    private static boolean clientSenderThreadAlive;
    private static boolean clientReaderThreadAlive;

    private Socket conn;

    private static BufferedReader terminalReader;
    private static ArrayList<String> userInputs;

    private PrintWriter clientWriter;
    private BufferedReader clientReader;
    private MessageMapParser mmParser;


    public Client() {
        threadType = null;
        conn = null;

        clientWriter = null;
        clientReader = null;
        mmParser = new MessageMapParser();
    }

    public Client(String threadType) {
        this();
        this.threadType = threadType;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {

        if (args.length == 0) {
            System.out.println("Usage: java Client <server_port>");
            return;
        }

        if (args[0].equals("-d")) {
            if (args.length == 1) {
                System.out.println("Usage: java Client -d <server_port>");
                return;
            }

            clientDebugMode = true;
            serverPort = Integer.parseInt(args[1]);
        } else {
            clientDebugMode = false;
            serverPort = Integer.parseInt(args[0]);
        }

        serverName = "localhost";
        maxSendNumber = 10;
        
        terminalReader = new BufferedReader(new InputStreamReader(System.in));
        userInputs = new ArrayList<String>();


        Client clientSender = new Client("main-write");

        Thread t1 = new Thread(clientSender);

        t1.start();

    }

    
    @Override
    public void run() {
        switch (threadType) {
            case "main-write":
                
            try {
                clientSenderThread();
            } catch (Exception e) {
                printDebug(e.getMessage());
                return;
            }
            break;

            case "main-read":
                
            try {
                clientReaderThread();
            } catch (Exception e) {
                printDebug(e.getMessage());
                return;
            }
            break;
            
            case "user-input":

            try {
                userInputThread();
            } catch (IOException e) {
                printDebug(e.getMessage());
                return;
            }
            
            case "p2p":
                
                break;

            default:
                return;

        }
        
    }
    
    private void userInputThread() throws IOException {
        
        // Read User input, and it to user inputs list

        String userInput = null;
        while ((userInput = terminalReader.readLine()) != null) {
            userInputs.add(userInput);
        }

    }

    private void clientSenderThread() throws IOException, Exception {
        
        if (!initialiseThread()) {throw new Exception("Thread Initialisation Error");}

        printDebug("Client Writer Thread Initialised");

        localWritePort = conn.getLocalPort();

        // Begin Auth Process

        isLoggedIn = false;
        try {
            isLoggedIn = authAction();
        } catch (Exception e) {
            printDebug(e.getMessage());
            return;
        }

        if (!isLoggedIn) {
            return;
        }

        clientSenderThreadAlive = true;

        // Start the Client-Reader Thread

        Client clientRecipient = new Client("main-read");

        Thread t2 = new Thread(clientRecipient);

        t2.start();
        
        clientReaderThreadAlive = true;

        printDebug("Client-Sender Thread - started: Client-Reader");
        
        // Start the User Input Thread
        
        Client userFacingClient = new Client("user-input");
        
        Thread t3 = new Thread(userFacingClient);
        
        t3.start();
        
        printDebug("Client-Sender Thread - started: User Input");

        // Start Checking for inputs from the user

        //printDebug("UserInputs Size: " + userInputs.size());

        while (isLoggedIn) {
            
            if (userInputs.size() == 0) {
                // currently no user input
                // sleep, and then check:
                // 1) user is still logged in
                // 2) there is new input

                //printDebug("Waiting for new user input");
                Thread.sleep(100);
                continue;
            }

            String userInput = userInputs.get(0);
            userInputs.remove(0);

            int firstSpace = userInput.indexOf(' ');

            String commandName;
            if (firstSpace != -1) {
                commandName = userInput.substring(0, firstSpace);
            } else {
                commandName = userInput;
            }
            
            HashMap<String, String> userRequest = new HashMap<String, String>();
            String args[] = null;

            switch (commandName) {
                case "message":

                args = userInput.split(" ", 3);

                if (args.length < 3) {
                    printTerminal("message usage: message <user> <message>");
                    continue;
                }

                if (username.equals(args[1])) {
                    printTerminal("Can't send a message to self");
                    continue;
                }

                userRequest.put("tag", "msg");
                userRequest.put("type", "one");
                userRequest.put("toUser", args[1]);
                userRequest.put("body", args[2]);

                break;
            
                case "broadcast":
                    
                args = userInput.split(" ", 2);

                if (args.length < 2) {
                    printTerminal("broadcast usage: broadcast <message>");
                    continue;
                }

                userRequest.put("tag", "msg");
                userRequest.put("type", "all");
                userRequest.put("body", args[1]);

                break;

                case "whoelse":
                
                userRequest.put("tag", "online");
                userRequest.put("type", "now");
                userRequest.put("body", null);

                break;

                case "whoelsesince":
   
                args = userInput.split(" ", 2);

                if (args.length < 2) {
                    printTerminal("whoelsesince usage: whoelsesince <time>");
                    continue;
                }

                try {
                    int tmp = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    printTerminal("whoelsesince usage: <time> must be a number, in seconds");
                    continue;
                }

                userRequest.put("tag", "online");
                userRequest.put("type", "since");
                userRequest.put("time", args[1]);
                userRequest.put("body", null);

                break;

                case "block":
                    
                args = userInput.split(" ", 2);

                if (args.length < 2) {
                    printTerminal("block usage: block <user>");
                    continue;
                }

                if (username.equals(args[1])) {
                    printTerminal("Can't block self");
                    continue;
                }

                userRequest.put("tag", "block");
                userRequest.put("type", "on");
                userRequest.put("user", args[1]);
                userRequest.put("body", null);

                break;

                case "unblock":
                    
                args = userInput.split(" ", 2);

                if (args.length < 2) {
                    printTerminal("unblock usage: unblock <user>");
                    continue;
                }

                if (username.equals(args[1])) {
                    printTerminal("Can't unblock self");
                    continue;
                }

                userRequest.put("tag", "block");
                userRequest.put("type", "off");
                userRequest.put("user", args[1]);
                userRequest.put("body", null);
                    
                break;

                case "logout":

                userRequest.put("tag", "logout");
                userRequest.put("body", null);                

                break;

                default:
                
                printTerminal("Unsupported Command Type");
                continue;

            }

            // Send Request

            String requestString = null;
            try {
                requestString = mmParser.convertToMsg(userRequest);
            } catch (Exception e) {
                printDebug(e.getMessage());
                continue;
            }
            
            printDebug("Client-Writer: Sending " + requestString);
            
            clientWriter.println(requestString);
            clientWriter.flush();            
            
            // Read Response

            String recvdMsg = null;
            try {
                recvdMsg = clientReader.readLine();
            } catch (SocketTimeoutException e) {
                printTerminal("The command did not receive a response from the server" +
                    " - please try again");
                continue;
            }
            
            printDebug("Client-Writer: Received " + recvdMsg);
            
            HashMap<String, String> recvdMap;
            
            try {
                recvdMap = mmParser.parseMessage(recvdMsg);
            } catch (Exception e) {
                printDebug("Recvd Msg could not be parsed");
                continue;
            }

            String tagType = recvdMap.get("tag");

            switch (tagType) {
                case "msg":

                String returnStatus = recvdMap.get("returnStatus");

                switch (returnStatus) {
                    case "no-such-user":
                    
                    printTerminal("There is no such user with the given username, could not send message");

                    break;
                
                    case "self":
                        
                    printTerminal("Can't send a message to self");

                    break;

                    case "blocked":
                        
                    printTerminal("You have been blocked by " + recvdMap.get("toUser") + " - please try again later");

                    break;

                    case "sent":
                        
                    printTerminal("Your message has been sent to " + recvdMap.get("toUser"));
                    
                    break;

                    case "sent-all":
                        
                    printTerminal("Your message has been sent to all users");
                    
                    break;

                    case "sent-some":
                        
                    printTerminal("Your message has been sent to some users - but blocked by some others");
                    
                    break;

                    default:
                    break;
                }

                break;
            
                case "online":
                
                String onlineUsersString = recvdMap.get("body");

                if (onlineUsersString == null) {
                    break;
                }

                String[] onlineUsersList = onlineUsersString.split(" ");

                for (String eachUser : onlineUsersList) {
                    printTerminal(eachUser);
                }

                break;

                case "block":
                
                String blockUser = recvdMap.get("user");
                String blockType = recvdMap.get("type");
                String blockStatus = recvdMap.get("blockStatus");

                if (blockType.equals("on")) {
                    blockType = "blocked";
                } else if  (blockType.equals("off")) {
                    blockType = "unblocked";
                }

                if (blockStatus.equals("false-self")) {
                    printTerminal("Can't block self");

                } else if (blockStatus.equals("false-already")) {
                    printTerminal(blockUser + " has already been " + blockType);

                } else if (blockStatus.equals("true")) {
                    printTerminal(blockUser + " has been " + blockType);

                }

                break;

                case "logout":
                
                if (recvdMap.containsKey("status") &&
                    recvdMap.get("status").equals("done")) {
                    
                    printTerminal("You have been logged out");
                    isLoggedIn = false;
                    clientSenderThreadAlive = false;

                    return;
                }
                  
                break;


                default:
            }
            

        }
        
        clientSenderThreadAlive = false;
        printDebug("Client Sender Thread Complete");


        if (!isLoggedIn) {
            exitActions();
        }

    }

    private boolean authAction() throws IOException {

        if (conn == null) {
            return false;
        }

        conn.setSoTimeout(0);

        HashMap<String, String> previousMap = new HashMap<String, String>();

        printTerminal("Hello User!");

        // 1 = username, 2 = password
        int round = 1;

        int passwordRound = 1;

        String recvdMsg = null;
        HashMap<String, String> recvdMap;
        String currStatus = null;
        String enteredUsername = null;

        while (round < 3 && passwordRound <= 3) {
            switch (round) {
                case 1:
                printTerminal("Username: ");
                enteredUsername = terminalReader.readLine();

                HashMap<String, String> usernameMap = new HashMap<String, String>();

                usernameMap.put("tag", "auth");
                usernameMap.put("status", "username");
                usernameMap.put("username", enteredUsername);
                usernameMap.put("body", null);

                // convert and send username

                String usernameString = null;
                try {
                    usernameString = mmParser.convertToMsg(usernameMap);
                } catch (Exception e) {
                    printDebug(e.getMessage());
                }
                
                printDebug("Auth - Username: Sending " + usernameString);
                
                clientWriter.println(usernameString);
                clientWriter.flush();

                // read response                

                recvdMsg = null;
                try {
                    recvdMsg = clientReader.readLine();
                } catch (SocketTimeoutException e) {
                    printDebug(e.getMessage());
                    continue;
                }
                
                printDebug("Auth - Username: Received " + recvdMsg);
            
                recvdMap = null;
                
                try {
                    recvdMap = mmParser.parseMessage(recvdMsg);
                } catch (Exception e) {
                    printDebug(e.getMessage());
                    continue;
                }

                // User Response

                currStatus = recvdMap.get("status");

                switch (currStatus) {
                    case "new-user":
                    
                    printTerminal("Hello New User! Registration in progress...");
                    round++;

                    break;
                
                    case "already-online":
                    
                    printTerminal("This user is already logged in - cannot log in twice");
                    return false;

                    case "blocked":

                    printTerminal("This user is currenty blocked - please try again later");
                    return false;

                    case "enter-password":

                    printTerminal("Username Accepted");
                    round++;
                        
                    break;
                    case "username":
                        
                    break;
                    default:
                    continue;
                }


                break;
            
                case 2:
                printTerminal("Password: ");
                String enteredPassword = terminalReader.readLine();

                HashMap<String, String> passwordMap = new HashMap<String, String>();

                passwordMap.put("tag", "auth");
                passwordMap.put("status", "password");
                passwordMap.put("password", enteredPassword);
                passwordMap.put("body", null);

                // convert and send username

                String passwordString = null;
                try {
                    passwordString = mmParser.convertToMsg(passwordMap);
                } catch (Exception e) {
                    printDebug(e.getMessage());
                }
                
                printDebug("Auth - Password: Sending " + passwordString);
                
                clientWriter.println(passwordString);
                clientWriter.flush();

                // read response                

                recvdMsg = null;
                try {
                    recvdMsg = clientReader.readLine();
                } catch (SocketTimeoutException e) {
                    printDebug(e.getMessage());
                    continue;
                }
                
                printDebug("Auth - Password: Received " + recvdMsg);
            
                recvdMap = null;
                
                try {
                    recvdMap = mmParser.parseMessage(recvdMsg);
                } catch (Exception e) {
                    printDebug(e.getMessage());
                    continue;
                }

                // User Response

                currStatus = recvdMap.get("status");

                switch (currStatus) {
                    case "registered":

                    printTerminal("Welcome! You have been registered and logged in");
                    
                    conn.setSoTimeout(100);
                    username = enteredUsername;
                    
                    return true;
                    
                    case "matched":
                    
                    printTerminal("Hello Again! You have been successfully logged in");
                    
                    conn.setSoTimeout(100);
                    username = enteredUsername;

                    return true;
                    
                    case "try-again":
                    
                    printTerminal("This password did not match - please try again");
                    passwordRound++;

                    break;

                    case "blocked":
                        
                    printTerminal("You have been blocked because the password did not match " +
                        passwordRound + " times, please try again later");
                    return false;

                    case "password":
                        
                    break;
                
                    default:
                    break;
                }


                break;

                default:
                break;
            }
            
        }
            
        return false;
    }

    private void clientReaderThread() throws IOException, Exception {
        if (!initialiseThread()) {throw new Exception("Thread Initialisation Error");}
        
        printDebug("Client Reader Thread Initialised");

        while (isLoggedIn) {
            
            String recvdMsg = null;
            try {
                recvdMsg = clientReader.readLine();
            } catch (SocketTimeoutException e) {
                continue;
            }
            
            printDebug("Client-Reader: Received " + recvdMsg);
            
            HashMap<String, String> recvdMap;

            try {
                recvdMap = mmParser.parseMessage(recvdMsg);
            } catch (Exception e) {
                continue;
            }

            String tagType = recvdMap.get("tag");

            switch (tagType) {
                case "msg":
                
                String typeMsg = "";
                if (recvdMap.get("type").equals("all")) {
                    typeMsg = "[broadcast]";
                }
                printTerminal(recvdMap.get("fromUser") + typeMsg +
                    ": " + recvdMap.get("body")
                );

                break;

                case "login":
                
                printTerminal(recvdMap.get("user") + " has just logged in");
                
                break;
                
                case "logout":
                
                String userLoggedOut = recvdMap.get("user");

                if (userLoggedOut.equals(username)) {

                    printTerminal("You have been logged out - due to a period of inactivity");
                    isLoggedIn = false;
                    clientReaderThreadAlive = false;

                } else {
                    printTerminal(userLoggedOut + " has just logged out");
                }

                break;
            
                default:
                break;
            }

            // Send recvd response

            recvdMap.put("status", "recvd");

            String readResponse = null;
            try {
                readResponse = mmParser.convertToMsg(recvdMap);
            } catch (Exception e) {
                printDebug(e.getMessage());
            }
            
            printDebug("Client-Reader Sending " + readResponse);

            clientWriter.println(readResponse);
            clientWriter.flush();


        }

        clientReaderThreadAlive = false;
        printDebug("Client Reader Thread Complete");

        if (!isLoggedIn) {
            exitActions();
        }
        
    }
    
    private boolean initialiseThread() throws IOException, SocketException {
        
        
        
        if (threadType.equals("main-write") || threadType.equals("main-read")) {
            
            conn = new Socket(serverName, serverPort);
            conn.setSoTimeout(100);
            
            clientWriter = new PrintWriter(conn.getOutputStream());
            clientReader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            // prepare initial message map

            HashMap<String, String> initMsg = new HashMap<String, String>();

            initMsg.put("tag", "thread");
            
            if (threadType.equals("main-write")) {
                initMsg.put("client", "write");
            } else {
                initMsg.put("client", "read");
                initMsg.put("mainPort", Integer.toString(localWritePort));
            }

            initMsg.put("body", null);

            // convert to string

            String initString = null;
            try {
                initString = mmParser.convertToMsg(initMsg);
            } catch (Exception e) {
                printDebug(e.getMessage());
                return false;
            }

            HashMap<String, String> previousMap = new HashMap<String, String>();
            for (int i = 0; i < maxSendNumber; i++) {
                
                printDebug("[Round " + i + "]: Sending " + initString);
                
                clientWriter.println(initString);
                clientWriter.flush();


                String recvdMsg = null;
                try {
                    recvdMsg = clientReader.readLine();
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

                previousMap = recvdMap;

                if (recvdMap.containsKey("status") && recvdMap.get("status").equals("recvd")) {
                    // setup complete
                    return true;
                }


            }

            return false;


        } // or if p2p conn...

        return false;
        
    }

    /**
     * Bypassing the blocking user input thread, terminate program if both
     * sender and reader threads are complete
     */
    private static void exitActions() {

        printDebug("Exit Actions: Waiting for both threads to complete");

        while (clientSenderThreadAlive || clientReaderThreadAlive) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                break;
            }
        }

        System.exit(0);

    }

    private static void printTerminal(String msg) {
        System.out.println(msg);
    }

    private static void printDebug(String msg) {
        if (clientDebugMode) {
            System.out.println(msg);
        }
    }

}