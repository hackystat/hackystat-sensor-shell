package org.hackystat.sensorshell;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Logger;

import org.hackystat.sensorbase.client.SensorBaseClient;
import org.hackystat.sensorshell.command.SensorDataCommand;
import org.hackystat.sensorshell.command.AutoSendCommand;
import org.hackystat.sensorshell.command.PingCommand;
import org.hackystat.sensorshell.command.QuitCommand;
import org.hackystat.utilities.logger.OneLineFormatter;

/**
 * Provides "middleware" for accumulating and sending notification (sensor)
 * data to Hackystat. SensorShell has two modes of interaction: command line and
 * programmatic. 
 * <p> 
 * Command line mode is entered by invoking the main() method, and
 * is intended to be used as a kind of subshell to which commands to add and
 * send notification data of various types can be sent. The SensorShell can be invoked
 * without any additional arguments as follows:
 * 
 * <pre>java -jar sensorshell.jar</pre>
 * 
 * Or you can invoke it with one, two, three, or four additional arguments:
 * 
 * <pre>java -jar sensorshell.jar [toolname] [sensor.properties] [no offline] [command file]</pre>
 * <p>
 * Programmatic mode involves creating an instance of SensorShell, retrieving the 
 * appropriate command instance (Ping, Add, etc.) and invoking the appropriate method.
 *
 * @author    Philip M. Johnson
 */
public class SensorShell {

  /** Indicates if SensorShell is running interactively and thus output should be printed. */
  private boolean isInteractive = false;

  /** Indicates if offline data should be stored and/or recovered by this shell. */
  private boolean enableOfflineData = true;

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
  private SensorProperties sensorProperties; 

  /** The logging instance for SensorShells. */
  private Logger logger;

  /** The logging formatter that simply spits out the message string unchanged. */
  private Formatter oneLineFormatter = new OneLineFormatter(false, false);

  /** The file of commands, if provided on the command line. */
  private File commandFile = null;

  /** A boolean indicating if the command file has been supplied on the command line. */
  private boolean commandFilePresent = false;
  
  /** The ping command. */
  private PingCommand pingCommand;
  
  /** The send command. */
  private SensorDataCommand sensorDataCommand;

  /** The autosend command. */
  private AutoSendCommand autoSendCommand;
  
  /** The quit command. */
  private QuitCommand quitCommand;
  
  /** The sensorbaseclient instance used to communicate with the sensorbase. */
  private SensorBaseClient client;
  
  /**
   * Constructs a new SensorShell instance that can be provided with
   * notification data to be sent eventually to a specific user key and host.
   * The toolName field in the log file name is set to "interactive" if the tool
   * is invoked interactively and "tool" if it is invoked programmatically.
   *
   * @param sensorProperties  The sensor properties instance for this run.
   * @param isInteractive     Whether this SensorShell is being interactively invoked or not.
   */
  public SensorShell(SensorProperties sensorProperties, boolean isInteractive) {
    this(sensorProperties, isInteractive, (isInteractive) ? "interactive" : "tool", true, null);
  }


  /**
   * Constructs a new SensorShell instance that can be provided with
   * notification data to be sent eventually to a specific user key and host.
   *
   * @param sensorProperties  The sensor properties instance for this run.
   * @param isInteractive     Whether this SensorShell is being interactively invoked or not.
   * @param toolName          Indicates the invoking tool that is added to the log file name.
   */
  public SensorShell(SensorProperties sensorProperties, boolean isInteractive, String toolName) {
    this(sensorProperties, isInteractive, toolName, true, null);
  }


  /**
   * Constructs a new SensorShell instance that can be provided with
   * notification data to be sent eventually to a specific user key and host.
   * (For testing purposes only, you may want to disable offline data recovery.)
   *
   * @param sensorProperties   The sensor properties instance for this run.
   * @param isInteractive      Whether this SensorShell is being interactively invoked or not.
   * @param toolName           The invoking tool that is added to the log file name.
   * @param enableOfflineData  A boolean indicating whether to recover and/or store offline data.
   * @param commandFile        A file containing shell commands, or null if none provided.
   */
  public SensorShell(SensorProperties sensorProperties, boolean isInteractive, String toolName,
      boolean enableOfflineData, File commandFile) {
    this.isInteractive = isInteractive;
    this.toolName = toolName;
    this.sensorProperties = sensorProperties;
    this.enableOfflineData = enableOfflineData;
    this.commandFile = commandFile;
    this.commandFilePresent = ((commandFile != null));
    this.client = new SensorBaseClient(sensorProperties.getHackystatHost(), 
        sensorProperties.getEmail(), sensorProperties.getPassword());
    // Create the offline directory in the same directory where sensor.properties was found.
    //OfflineManager.getInstance(sensorProperties.getSensorPropertiesDir());
    initializeLogger();
    this.pingCommand = new PingCommand(this, sensorProperties);
    this.sensorDataCommand = new SensorDataCommand(this, sensorProperties, this.pingCommand, 
        this.client);
    this.autoSendCommand = new AutoSendCommand(this, sensorProperties);
    this.quitCommand = new QuitCommand(this, sensorProperties, this.sensorDataCommand);
    printBanner(sensorProperties);
    this.autoSendCommand.initialize();
    recoverOfflineData();

    // Now determine whether to read commands from the input stream or from the command file.
    try {
      this.bufferedReader = (commandFilePresent) ?
        new BufferedReader(new FileReader(this.commandFile)) :
        new BufferedReader(new InputStreamReader(System.in));
    }
    catch (IOException e) {
      this.logger.info(cr);
    }
  }


  /**
   * Constructs a new SensorShell instance that can be provided with
   * notification data to be sent eventually to a specific user key and host.
   * (For testing purposes only, you may want to disable offline data recovery.)
   *
   * @param sensorProperties   The sensor properties instance for this run.
   * @param isInteractive      Whether this SensorShell is being interactively invoked or not.
   * @param toolName           The invoking tool that is added to the log file name.
   * @param enableOfflineData  A boolean indicating whether to recover and/or store offline data.
   */
  public SensorShell(SensorProperties sensorProperties, boolean isInteractive, String toolName,
      boolean enableOfflineData) {
    this(sensorProperties, isInteractive, toolName, enableOfflineData, null);
  }


  /**
   * Looks for offline data and recovers any that is found if offline data
   * management is enabled and if the server is currently pingable.
   */
  private void recoverOfflineData() {
    // Return immediately if server is not available.
    if (!this.pingCommand.isPingable()) {
      this.println("Server not available; offline data not recovered.");
      return;
    }
    // Return immediately if the SensorShell was instantiated with offline data disabled.
    if (!this.enableOfflineData) {
      this.println("Offline data management disabled.");
      return;
    }
    // Otherwise do offline data management.
//    this.println("Checking for offline data to recover.");
//    boolean isDataRecovered = OfflineManager.getInstance().recover(this);
//    if (isDataRecovered) {
//      this.println("Summary of recovered offline data:");
//      this.println(OfflineManager.getInstance().getRecoverInfoString());
//    }
//    else {
//      this.println("No offline data found.");
//    }
  }


  /**
   * Initializes SensorShell logging. All client input is recorded to a log file
   * in .hackystat/logs/.
   */
  private void initializeLogger() {
    try {
      // First, create the logs directory, using the same location as sensor.properties if possible.
      File logDir = (sensorProperties.getSensorPropertiesDir() == null) ?
          new File(System.getProperty("user.home") + "/.hackystat/logs") :
          new File(sensorProperties.getSensorPropertiesDir(), "/logs");
      logDir.mkdirs();

      // Now set up logging to a file in that directory.
      this.logger = Logger.getLogger("hackystat.client.cli.SensorShell");
      this.logger.setUseParentHandlers(false);
      String fileName = logDir.getAbsolutePath() + "/" + this.toolName + ".%u.log";
      FileHandler handler = new FileHandler(fileName, 500000, 1, true);
      handler.setFormatter(this.oneLineFormatter);
      this.logger.addHandler(handler);
      // Add a couple of newlines to the log file to distinguish new shell sessions.
      logger.info(cr + cr);
    }
    catch (Exception e) {
      System.out.println("Error initializing SensorShell logger:\n" + e);
    }
  }


  /**
   * Prints out initial information about the SensorShell.
   *
   * @param sensorProperties  The sensor properties file.
   */
  private void printBanner(SensorProperties sensorProperties) {
    this.println("Hackystat SensorShell Version: " + getVersion());
    this.println("SensorShell started at: " + new Date());
    this.println("Using Sensor Properties in: " + sensorProperties.getAbsolutePath());
    this.println("Type 'help' for a list of commands.");
    // Ping the host to determine availability.
    String host = sensorProperties.getHackystatHost();
    String email = sensorProperties.getEmail();
    String password = sensorProperties.getPassword();
    String availability = SensorBaseClient.isHost(host) ? "available." : "not available";
    this.println("Host: " + sensorProperties.getHackystatHost() + " is " + availability);
    String authorized = SensorBaseClient.isRegistered(host, email, password) ? 
        " authorized " : " not authorized ";
    this.println("User " + email + " is" + authorized + "to login at this host.");
  }
  
  
  /**
   * Process a single input string representing a command.
   * @param inputString A command as a String.
   */
  private void processInputString(String inputString) {
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
      this.println("Ping of host " + this.sensorProperties.getHackystatHost() + " for user " +
          this.sensorProperties.getEmail() + 
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
    
    if ("autosend".equals(commandName)) {
      String interval = (argList.isEmpty()) ? "" : argList.get(0);
      this.autoSendCommand.initialize(interval);
    }
  }


  /**
   * The command line shell interface.
   * <ul>
   *   <li> If invoked with no arguments, then the default sensor.properties
   *   file is used and the toolName field in the sensor log file name is
   *   "interactive".
   *   <li> If invoked with one argument, that argument is used as the toolName
   *   value.
   *   <li> If invoked with two arguments, then the first argument is used as
   *   the toolName value and the second is used as the sensor.properties file
   *   path.
   *   <li> If invoked with three arguments, then the first argument is the
   *   toolname, the second is the sensor.properties file path, and the third
   *   argument (regardless of value) disables offline data management.
   *   <li> If invoked with four arguments, then the first argument is the
   *   toolname, the second is the sensor.properties file path, the third
   *   argument (regardless of value) disables offline data management, and
   *   the fourth argument specifies a file of commands (the last of which should be 'quit').
   * </ul>
   * Unless four arguments are provided,
   * the shell then provides a ">>" prompt and supports interactive entry of
   * sensor data. The following commands are supported:
   * <ul>
   *   <li> "help" provides a summary of the available commands.
   *   <li> "send" sends all of the accumulated data to the server.
   *   <li> "autosend" sets up a timer-based process that invokes send intermittently.
   *   <li> "quit" sends all of the accumulated data to the server and exits.
   *   <li> "ping" checks to see if the host/user/password is valid. 
   *   <li> "add" adds a single Sensor Data instance to the buffered list to send.
   * </ul>
   *
   * @param args  The command line parameters. See above for details.
   */
  public static void main(String args[]) {
    // Print help line and exit if arg is -help.
    if ((args.length == 1) && (args[0].equalsIgnoreCase("-help"))) {
      System.out.println("java -jar sensorshell.jar [toolname] [sensor.properties] [no offline] "
           + "[command filename]");
      return;
    }
    
    // Set Parameter 1 (toolname) to supplied or default value.
    String toolName = (args.length > 0) ? args[0] : "interactive";

    // Set Parameter 2 (sensor properties file) to supplied or default value. Exit if can't find it.
    SensorProperties sensorProperties = (args.length >= 2) ?
        new SensorProperties(new File(args[1])) : new SensorProperties();
    if (!sensorProperties.isFileAvailable()) {
      System.out.println("Could not find sensor.properties file. ");
      System.out.println("Expected in: " + sensorProperties.getAbsolutePath());
      System.out.println("Exiting...");
      return;
    }

    // Set Parameter 3 (offline). True if we don't supply a value, false if any value supplied.
    boolean offlineEnabled = ((args.length < 3));

    // Set Parameter 4 (command file). Null if not supplied. Exit if supplied and bogus.
    File commandFile = null;
    if (args.length == 4) {
      commandFile = new File(args[3]);
      if (!(commandFile.exists() && commandFile.isFile())) {
        System.out.println("Could not find the command file. Exiting...");
        return;
      }
    }

    // Set interactive parameter. From command line, always interactive unless using command file.
    boolean interactive = ((commandFile == null));

    // Now create the shell instance, supplying it with all the appropriate arguments.
    SensorShell shell = 
      new SensorShell(sensorProperties, interactive, toolName, offlineEnabled, commandFile);

    // Start processing commands either interactively or from the command file.
    while (true) {
      // Get the next command
      shell.printPrompt();
      String inputString = shell.readLine();
      shell.processInputString(inputString);
      if (inputString.equalsIgnoreCase("quit")) {
        return;
      }
    }
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
      + "  autosend#<integer>" + cr
      + "    Sets the interval in minutes between automatic sending of added sensor data." + cr
      + "    Provide 0 as the integer to disable autosend." + cr
      + "    Example: autosend#15" + cr
      + "  quit" + cr
      + "    Sends any remaining data and exits the sensorshell." + cr
      + "    Example: quit" + cr;
    this.print(helpString);
  }

 
  /** Print out a prompt if in interactive mode. */
  private void printPrompt() {
    this.print(this.prompt);
  }


  /**
   * Returns a string with the next line of input from the user. If input
   * errors, returns the string "quit".
   *
   * @return   A string with user input.
   */
  private String readLine() {
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
  public final void println(String line) {
    logger.info(line + cr);
    if (isInteractive) {
      System.out.print(line + cr);
    }
  }


  /**
   * Prints out the line without newline if in interactive mode.
   *
   * @param line  The line to be printed.
   */
  private void print(String line) {
    logger.info(line);
    if (isInteractive) {
      System.out.print(line);
    }
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
  public boolean isInteractive() {
    return this.isInteractive;
  }
  
  /**
   * Returns the Logger associated with this sensorshell.
   * @return The Logger. 
   */
  public Logger getLogger() {
    return this.logger;
  }
  
  
  /**
   * Returns true if offline data saving is enabled. 
   * @return True if offline data is enabled. 
   */
  public boolean enableOfflineData() {
    return this.enableOfflineData;
  }
  
  /**
   * Converts the values in the KeyValMap to a SensorData instance and adds it to the shell.
   * This is the easiest way to add sensor data.
   * The Owner will default to the hackystat user in the sensor.properties file.
   * The Timestamp and Runtime will default to the current time.  
   * @param keyValMap The map of key-value pairs. 
   * @throws Exception If the Map cannot be translated into SensorData, typically because a 
   * value was passed for Timestamp or Runtime that could not be parsed into XMLGregorianCalendar. 
   */
  public void add(Map<String, String> keyValMap) throws Exception {
    this.sensorDataCommand.add(keyValMap);
  }
  
  /**
   * Sends any accumulated SensorData instances to the Server. 
   */
  public void send() {
    this.sensorDataCommand.send();
  }
  
  /** Shuts down this SensorShell, sending any unsent data and closing log files.  */
  public void quit() {
    this.quitCommand.quit();
  }
}

