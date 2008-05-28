package org.hackystat.sensorshell;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Logger;

import org.hackystat.sensorbase.client.SensorBaseClient;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;
import org.hackystat.sensorshell.command.SensorDataCommand;
import org.hackystat.sensorshell.command.AutoSendCommand;
import org.hackystat.sensorshell.command.PingCommand;
import org.hackystat.sensorshell.command.QuitCommand;
import org.hackystat.utilities.home.HackystatUserHome;
import org.hackystat.utilities.logger.OneLineFormatter;

/**
 * Provides the implementation of a single SensorShell instance. 
 *
 * @author    Philip M. Johnson
 */
public class SingleSensorShell implements Shell {

  /** Indicates if SensorShell is running interactively and thus output should be printed. */
  private boolean isInteractive = false;

  /** The notification shell prompt string. */
  private String prompt = ">> ";

  /** A string indicating the tool name invoking SensorShell, added to the log file name. */
  private String toolName = "interactive";

  /** The delimiter for entry fields. */
  private String delimiter = "#";

  /** The line separator (carriage return character(s)). */
  private String cr = System.getProperty("line.separator");

  /** The input stream used to read input from the command line. */
  private BufferedReader bufferedReader = null;

  /** The sensor properties instance. */
  private SensorShellProperties sensorProperties; 

  /** The logging instance for SensorShells. */
  private Logger logger;

  /** The logging formatter that adds a timestamp but doesn't add a newline. */
  private Formatter oneLineFormatter = new OneLineFormatter(true, false);

  /** The file of commands, if provided on the command line. */
  private File commandFile = null;

  /** A boolean indicating if the command file has been supplied on the command line. */
  private boolean commandFilePresent = false;
  
  /** The ping command. */
  private PingCommand pingCommand;
  
  /** The send command. */
  private SensorDataCommand sensorDataCommand;

  /** The autosend command. */
  @SuppressWarnings("unused")
  private AutoSendCommand autoSendCommand;
  
  /** The quit command. */
  private QuitCommand quitCommand;
  
  /** The SensorBase Client instance used to communicate with the SensorBase. */
  private SensorBaseClient client;
  
  /** The OfflineManager used to recover data. */
  private OfflineManager offlineManager;
  
  /** The startup time for this sensorshell. */
  private Date startTime = new Date();
  
  /**
   * Constructs a new SensorShell instance that can be provided with
   * notification data to be sent eventually to a specific user key and host.
   * The toolName field in the log file name is set to "interactive" if the tool
   * is invoked interactively and "tool" if it is invoked programmatically.
   *
   * @param properties  The sensor properties instance for this run.
   * @param isInteractive     Whether this SensorShell is being interactively invoked or not.
   */
  public SingleSensorShell(SensorShellProperties properties, boolean isInteractive) {
    this(properties, isInteractive, (isInteractive) ? "interactive" : "tool", null);
  }


  /**
   * Constructs a new SensorShell instance that can be provided with
   * notification data to be sent eventually to a specific user key and host.
   *
   * @param properties  The sensor properties instance for this run.
   * @param isInteractive     Whether this SensorShell is being interactively invoked or not.
   * @param tool          Indicates the invoking tool that is added to the log file name.
   */
  public SingleSensorShell(SensorShellProperties properties, boolean isInteractive, String tool) {
    this(properties, isInteractive, tool, null);
  }


  /**
   * Constructs a new SensorShell instance that can be provided with
   * notification data to be sent eventually to a specific user key and host.
   * (For testing purposes only, you may want to disable offline data recovery.)
   *
   * @param properties   The sensor properties instance for this run.
   * @param isInteractive      Whether this SensorShell is being interactively invoked or not.
   * @param toolName           The invoking tool that is added to the log file name.
   * @param commandFile        A file containing shell commands, or null if none provided.
   */
  public SingleSensorShell(SensorShellProperties properties, boolean isInteractive, String toolName,
      File commandFile) {
    this.isInteractive = isInteractive;
    this.toolName = toolName;
    this.sensorProperties = properties;
    this.commandFile = commandFile;
    this.commandFilePresent = ((commandFile != null));
    this.client = new SensorBaseClient(properties.getSensorBaseHost(), 
        properties.getSensorBaseUser(), properties.getSensorBasePassword());
    this.client.setTimeout(properties.getTimeout() * 1000);
    initializeLogger();
    printBanner();
    this.pingCommand = new PingCommand(this, properties);
    this.sensorDataCommand = new SensorDataCommand(this, properties, this.pingCommand, 
        this.client);
    this.autoSendCommand = new AutoSendCommand(this, properties);
    this.quitCommand = new QuitCommand(this, properties, sensorDataCommand, autoSendCommand);

    // Now determine whether to read commands from the input stream or from the command file.
    try {
      this.bufferedReader = (commandFilePresent) ?
        new BufferedReader(new FileReader(this.commandFile)) :
        new BufferedReader(new InputStreamReader(System.in));
    }
    catch (IOException e) {
      this.logger.info(cr);
    }
    
    this.offlineManager = new OfflineManager(this, this.toolName);

    // attempts to recover data if enabled; logs appropriate message in any case. 
    try {
      recoverOfflineData();
    }
    catch (SensorShellException e) {
      this.logger.warning("Error recovering offline data.");
    }
  }
  
  /**
   * Returns the offline manager associated with this instance. 
   * @return The offline manager. 
   */
  public synchronized OfflineManager getOfflineManager() {
    return this.offlineManager;
  }


  /**
   * Looks for offline data and recovers any that is found if offline data
   * management is enabled and if the server is currently pingable.
   * @throws SensorShellException If problems occur recovering the data.
   */
  private void recoverOfflineData() throws SensorShellException {
    // Return immediately if server is not available.
    boolean isOfflineRecoveryEnabled = this.sensorProperties.isOfflineRecoveryEnabled();
    // Return immediately if offline recovery is not enabled, but print this to the logger. 
    if (!isOfflineRecoveryEnabled) {
      this.println("Not checking for offline data: Offline recovery disabled.");
      return;
    }

    boolean isPingable = this.pingCommand.isPingable();
    if (isPingable) {
      this.println("Checking for offline data to recover.");
      this.offlineManager.recover();
    }
    else {
      this.println("Not checking for offline data: Server not available.");  
    }
  }


  /**
   * Initializes SensorShell logging. All client input is recorded to a log file
   * in [user.home]/.hackystat/sensorshell/logs.  Note that [user.home] is obtained 
   * from HackystatUserHome.getHome().
   */
  private void initializeLogger() {
    try {
      // First, create the logs directory.
      File logDir = new File(HackystatUserHome.getHome(), ".hackystat/sensorshell/logs/");
      logDir.mkdirs();

      // Now set up logging to a file in that directory.
      this.logger = Logger.getLogger("org.hackystat.sensorshell-" + this.toolName);
      this.logger.setUseParentHandlers(false);
      String fileName = logDir.getAbsolutePath() + "/" + this.toolName + ".%u.log";
      FileHandler handler = new FileHandler(fileName, 500000, 1, true);
      handler.setFormatter(this.oneLineFormatter);
      this.logger.addHandler(handler);
      // Add a couple of newlines to the log file to distinguish new shell sessions.
      logger.info(cr + cr);
      // Now set the logging level based upon the SensorShell Property.
      logger.setLevel(this.sensorProperties.getLoggingLevel());
      logger.getHandlers()[0].setLevel(this.sensorProperties.getLoggingLevel());
    }
    catch (Exception e) {
      System.out.println("Error initializing SensorShell logger:\n" + e);
    }
  }


  /**
   * Prints out initial information about the SensorShell.
   */
  private void printBanner() {
    this.println("Hackystat SensorShell Version: " + getVersion());
    this.println("SensorShell started at: " + this.startTime);
    this.println(sensorProperties.toString());
    this.println("Type 'help' for a list of commands.");
    // Ping the host to determine availability.
    String host = sensorProperties.getSensorBaseHost();
    String email = sensorProperties.getSensorBaseUser();
    String password = sensorProperties.getSensorBasePassword();
    String availability = SensorBaseClient.isHost(host) ? "available." : "not available";
    this.println("Host: " + sensorProperties.getSensorBaseHost() + " is " + availability);
    String authorized = SensorBaseClient.isRegistered(host, email, password) ? 
        " authorized " : " not authorized ";
    this.println("User " + email + " is" + authorized + "to login at this host.");
    this.println("Maximum Java heap size (bytes): " + Runtime.getRuntime().maxMemory());
  }
  
  
  /**
   * Process a single input string representing a command.
   * @param inputString A command as a String.
   * @throws SensorShellException If problems occur sending the data. 
   */
  void processInputString(String inputString) throws SensorShellException {
    // Ignore empty commands. 
    if ((inputString == null) || ("".equals(inputString))) {
      return;
    }
    // Log the command if we're not running interactively.
    if (!this.isInteractive) {
      logger.info("#> " + inputString);
    }
    // Process quit command.
    if ("quit".equals(inputString)) {
      this.quitCommand.quit();
      return;
    }

    // Process help command.
    if ("help".equals(inputString)) {
      this.printHelp();
      return;
    }

    // Process send command.
    if ("send".equals(inputString)) {
      this.sensorDataCommand.send();
      return;
    }
    
    // Process ping command.
    if ("ping".equals(inputString)) {
      boolean isPingable = this.pingCommand.isPingable();
      this.println("Ping of host " + this.sensorProperties.getSensorBaseHost() + " for user " +
          this.sensorProperties.getSensorBaseUser() + 
          (isPingable ? " succeeded." : " did not succeed"));
      return;
    }

    // Process commands with arguments. 
    StringTokenizer tokenizer = new StringTokenizer(inputString, this.delimiter);
    int numTokens = tokenizer.countTokens();
    // All remaining commands must have arguments. 
    if (numTokens == 0) {
      this.println("Error: unknown command or command requires arguments.");
      return;
    }

    // Get the command name and any arguments. 
    String commandName = tokenizer.nextToken();
    ArrayList<String> argList = new ArrayList<String>();
    while (tokenizer.hasMoreElements()) {
      argList.add(tokenizer.nextToken());
    }
    
    if ("add".equals(commandName)) {
      // For an Add command, the argument list should be a set of key-value pairs.
      // So, build the Map of key-value pairs.
      Map<String, String> keyValMap = new HashMap<String, String>();
      for (String arg : argList) {
        int delim = arg.indexOf('=');
        if (delim == -1) {
          this.println("Error: can't parse argument string for add command.");
        }
        keyValMap.put(arg.substring(0, delim), arg.substring(delim + 1));
      }
      try {
        this.sensorDataCommand.add(keyValMap);
      }
      catch (Exception e) {
        this.println("Error: Can't parse the Timestamp or Runtime arguments.");
      }
      return;
    }
    
    if ("statechange".equals(commandName)) {
      String resourceCheckSumString = argList.get(0);
      int resourceCheckSum = 0;
      try {
        resourceCheckSum = Integer.parseInt(resourceCheckSumString);
      }
      catch (Exception e) {
        this.println("Error: Can't parse the checksum into an integer.");
        return;
      }
      argList.remove(0);
      // Now do almost the same as for an add command. 
      // Build the Map of key-value pairs.
      Map<String, String> keyValMap = new HashMap<String, String>();
      for (String arg : argList) {
        int delim = arg.indexOf('=');
        if (delim == -1) {
          this.println("Error: can't parse argument string for statechange command.");
        }
        keyValMap.put(arg.substring(0, delim), arg.substring(delim + 1));
      }
      try {
        this.sensorDataCommand.statechange(resourceCheckSum, keyValMap);
      }
      catch (Exception e) {
        this.println("Error: Can't parse the Timestamp or Runtime arguments.");
      }
      return;
    }

    // Otherwise we don't understand.
    this.println("Invalid command entered and ignored. Type 'help' for help.");
  }


  
  /** Prints the help strings associated with all commands. */
  private void printHelp() {
    String helpString = 
      "SensorShell Command Summary " + cr
      + "  add#<key>=<value>[#<key>=<value>]..." + cr
      + "    Adds a new sensor data instance for subsequent sending." + cr 
      + "    Provide fields and properties as key=value pairs separated by '#'." + cr
      + "    Owner, Timestamp, and Runtime fields will default to the current user and time." + cr
      + "    Example: add#Tool=Eclipse#SensorDataType=DevEvent#DevEventType=Compile" + cr 
      + "  send" + cr
      + "    Sends any added sensor data to the server. " + cr
      + "    Server is pinged, and if it does not respond, then data is stored offline." + cr
      + "    Example: send" + cr
      + "  ping" + cr
      + "    Pings the server and checks email/password credentials." + cr
      + "    Example: ping" + cr
      + "  statechange#<ResourceCheckSum>#<key>=<value>[#<key>=<value>]..." + cr
      + "    Generates an 'add' command when the 'state' has changed." + cr
      + "    ResourceCheckSum is an integer that represents the current state of the Resource." + cr
      + "    This command compares ResourceCheckSum and the Resource field value to the values" + cr
      + "    saved from the last call to statechange.  If either have changed, indicating that" + cr
      + "    the state has changed, then the key-value pairs are passed to the Add command." + cr
      + "    This command facilitates the implementation of timer-based sensor processes that" + cr
      + "    wake up periodically and emit statechange commands, with the knowledge that if " + cr
      + "    the user has not been active, these statechange commands will not result in" + cr
      + "    actual sensor data being sent to the server." + cr
      + "  quit" + cr
      + "    Sends any remaining data and exits the sensorshell." + cr
      + "    Example: quit" + cr;
    this.print(helpString);
  }

  /** Print out a prompt if in interactive mode. */
  void printPrompt() {
    this.print(this.prompt);
  }

  /**
   * Returns a string with the next line of input from the user. If input
   * errors, returns the string "quit".
   *
   * @return   A string with user input.
   */
  String readLine() {
    try {
      String line = this.bufferedReader.readLine();
      logger.info(line);
      return (line == null) ? "" : line;
    }
    catch (IOException e) {
      //logger.info(cr);
      return "quit";
    }
  }

  /**
   * Prints out the line plus newline if in interactive mode, and always logs the line.
   * Provided to clients to support logging of error messages. 
   * 
   * @param line  The line to be printed.
   */
  public final synchronized void println(String line) {
    logger.info(line + cr);
    if (isInteractive) {
      SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.US);
      System.out.print(dateFormat.format(new Date()) + " " + line + cr);
    }
  }


  /**
   * Prints out the line without newline if in interactive mode.
   *
   * @param line  The line to be printed.
   */
  public final synchronized void print(String line) {
    logger.info(line);
    if (isInteractive) {
      SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.US);
      System.out.print(dateFormat.format(new Date()) + " " + line);
    }
  }
  
  /**
   * Returns a Date instance indicating when this SensorShell was started. 
   * @return The Date when this instance started up. 
   */
  public synchronized Date getStartTime() {
    return new Date(this.startTime.getTime());
  }
  
  /**
   * Returns true if this shell has stored any data offline. 
   * @return True if any data has been stored offline. 
   */
  public synchronized boolean hasOfflineData() {
    return this.offlineManager.hasOfflineData();
  }
  
  /**
   * Returns the total number of instances sent by this shell's SensorDataCommand. 
   * @return The total number of instances sent. 
   */
  public synchronized long getTotalSent() {
    return this.sensorDataCommand.getTotalSent();
  }

  /**
   * Return the current version number.
   *
   * @return The version number, or "Unknown" if could not be determined.
   */
  private String getVersion() {
    String release;
    try {
      Package thisPackage = Class.forName("org.hackystat.sensorshell.SensorShell").getPackage();
      release = thisPackage.getImplementationVersion();
    }
    catch (Exception e) {
      release = "Unknown";
    }
    return release;
  }
  
  /**
   * Returns true if this sensorshell is being run interactively from the command line.
   * @return True if sensorshell is interactive. 
   */
  public synchronized boolean isInteractive() {
    return this.isInteractive;
  }
  
  /**
   * Returns the Logger associated with this sensorshell.
   * @return The Logger. 
   */
  public synchronized Logger getLogger() {
    return this.logger;
  }
  
  
  /** {@inheritDoc} */
  public synchronized void add(Map<String, String> keyValMap) throws SensorShellException {
    this.sensorDataCommand.add(keyValMap); 
  }
  
  /** {@inheritDoc} */
  public synchronized void add(SensorData sensorData) throws SensorShellException {
    this.sensorDataCommand.add(sensorData);
  }
  
  /** {@inheritDoc} */
  public synchronized int send() throws SensorShellException {
    return this.sensorDataCommand.send();
  }
  
  /** {@inheritDoc} */
  public synchronized void quit() throws SensorShellException {
    this.quitCommand.quit();
  }
  
  /** {@inheritDoc} */
  public synchronized boolean ping() {
    return this.pingCommand.isPingable();
  }
  
  /** {@inheritDoc} */
  public synchronized void statechange(long resourceCheckSum, Map<String, String> keyValMap) 
  throws Exception {
    this.sensorDataCommand.statechange(resourceCheckSum, keyValMap);
  }
  
  /** {@inheritDoc} */
  public synchronized SensorShellProperties getProperties() {
    return this.sensorProperties;
  }
}

