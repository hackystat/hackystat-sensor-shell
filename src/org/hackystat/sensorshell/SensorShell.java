package org.hackystat.sensorshell;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Logger;

import org.hackystat.core.kernel.admin.SensorProperties;
import org.hackystat.core.kernel.sdt.SdtManager;
import org.hackystat.core.kernel.sdt.SensorDataType;
import org.hackystat.core.kernel.shell.command.ShellCommand;
import org.hackystat.core.kernel.soap.Notification;
import org.hackystat.core.kernel.util.DateInfo;
import org.hackystat.core.kernel.util.OneLineFormatter;

import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebForm;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

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
 * Programmatic mode is entered by creating an instance of SensorShell and invoking the 
 * doCommand method directly on it. 
 * <p>
 * Each command is implemented by a (singleton) instance of a CommandShell.
 * SensorShell is responsible for instantiating a single instance of each type
 * of CommandShell the first time it is invoked, and caching it for use in
 * processing future commands of its type. Thus, CommandShell instances can
 * preserve the history of their invocations and build state over time. For
 * example, each "add" request can update an internal list of objects for later
 * sending.
 *
 * @author    Philip M. Johnson
 * @version   $Id: SensorShell.java,v 1.1.1.1 2005/10/20 23:56:44 johnson Exp $
 */
public class SensorShell {

  /** Tracks the last timestamp value to make sure that doCommands have a unique timestamp. */
  private long lastTimeStamp = new Date().getTime();
  /** An offset to adjust last timestamp when it repeats. */
  private long lastTimeStampOffset = 0;
  
  /** Indicates if SensorShell is running interactively and thus output should be printed. */
  private boolean isInteractive = false;

  /** Indicates if offline data should be stored and/or recovered by this shell. */
  private boolean enableOfflineData = true;

  /** The notification shell prompt string. */
  private String prompt = ">> ";

  /** The last command's result message. */
  private String resultMessage = "";

  /** A string indicating the tool name invoking SensorShell, added to the log file name. */
  private String toolName = "interactive";

  /** The delimiter for entry fields. */
  private String delimiter = "#";

  /** The line separator (carriage return character(s)). */
  private String cr = System.getProperty("line.separator");

  /** The input stream used to read input from the command line. */
  private BufferedReader bufferedReader = null;

  /** The hackystat host. */
  private String host;

  /** The user's 12 character key. */
  private String key;

  /** The sensor properties instance. */
  private SensorProperties sensorProperties; 

  /** A mapping from class names to their instances. Caches the CommandShell instances. */
  private HashMap<String, ShellCommand> cache = new HashMap<String, ShellCommand>();

  /** The logging instance for SensorShells. */
  private Logger logger;

  /** The logging formatter that simply spits out the message string unchanged. */
  private Formatter oneLineFormatter = new OneLineFormatter(false);

  /** The file of commands, if provided on the command line. */
  private File commandFile = null;

  /** A boolean indicating if the command file has been supplied on the command line. */
  private boolean commandFilePresent = false;


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
    this.host = sensorProperties.getHackystatHost();
    this.key = sensorProperties.getKey();
    this.sensorProperties = sensorProperties;
    this.enableOfflineData = enableOfflineData;
    this.commandFile = commandFile;
    this.commandFilePresent = ((commandFile != null));
    // Create the offline directory in the same directory where sensor.properties was found.
    OfflineManager.getInstance(sensorProperties.getSensorPropertiesDir());
    initializeLogger();
    printBanner(sensorProperties);
    initializeShellCommands(sensorProperties);
    initializeAutoSend(sensorProperties);
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
    if (!isServerPingable()) {
      this.println("Server not available; offline data not recovered.");
      return;
    }
    // Return immediately if the SensorShell was instantiated with offline data disabled.
    if (!this.enableOfflineData) {
      this.println("Offline data management disabled.");
      return;
    }
    // Otherwise do offline data management.
    this.println("Checking for offline data to recover.");
    boolean isDataRecovered = OfflineManager.getInstance().recover(this);
    if (isDataRecovered) {
      this.println("Summary of recovered offline data:");
      this.println(OfflineManager.getInstance().getRecoverInfoString());
    }
    else {
      this.println("No offline data found.");
    }
  }


  /**
   * Returns the SensorProperties instance associated with this SensorShell.
   *
   * @return   The <code>SensorProperties</code> instnace.
   */
  public SensorProperties getSensorProperties() {
    return this.sensorProperties;
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
   * Initializes the cache of CommandShell instances with all currently defined
   * ShellCommands.
   *
   * @param sensorProperties  The SensorProperties instance to be provided to
   *      all ShellCommands.
   */
  private void initializeShellCommands(SensorProperties sensorProperties) {
    // First, initialize the built-in commands.
    String[] commands = {"Ping", "AutoSend"};
    // "Load" is removed from defaults.
    for (int i = 0; i < commands.length; i++) {
      try {
        String commandClassName = "org.hackystat.sensorshell.command." + commands[i]
             + "ShellCommand";
        ShellCommand shellCommand = (ShellCommand) Class.forName(commandClassName).newInstance();
        this.cache.put(commands[i], shellCommand);
        shellCommand.setSensorProperties(sensorProperties);
      }
      catch (Exception e) {
        this.println("Failure to find built-in ShellCommand: " + commands[i]);
      }
    }
    // Now, initialize the current set of Sensor Data Type extensions.
    SdtManager manager = SdtManager.getInstance(this.logger);
    for (SensorDataType sdt : manager.getSensorDataTypes()) {
      try {
        if (sdt.hasShellCommand()) {
          Class shellCommandClass = sdt.getShellCommandClass();
          ShellCommand shellCommand = (ShellCommand) shellCommandClass.newInstance();
          this.cache.put(sdt.getName(), shellCommand);
          shellCommand.setSensorProperties(sensorProperties);
          this.println("Defined shell command: " + sdt.getName());
        }
      }
      catch (Exception e) {
        this.println("Error instantiating SDT command class " + e);
      }
    }
  }


  /**
   * Set the autosend interval appropriately, the time that elapses between automatic sending
   * of collected data to the server.
   *
   * @param sensorProperties  The sensor properties file containing autosend values.
   */
  private void initializeAutoSend(SensorProperties sensorProperties) {
    String interval = sensorProperties.getAutoSendInterval();
    if ("0".equals(interval)) {
      this.println("AutoSend not enabled.");
    }
    else {
      // Enable autosend
      String[] args = {interval};
      // Disable output while doing the doCommand.
      boolean oldIsInteractive = this.isInteractive;
      this.isInteractive = false;
      boolean success = doCommand(new Date(), "AutoSend", Arrays.asList(args));
      //reset to original value.
      this.isInteractive = oldIsInteractive;
      if (success) {
        this.println("AutoSend enabled every " + interval + " minutes.");
      }
      else {
        this.println("AutoSend failed to enable.");
      }
    }
  }


  /** Performs quit actions, such as logging the command and sending data. */
  private void quit() {
    // Log this command if not running interactively.
    if (!this.isInteractive) {
      logger.info("#> quit" + cr);
    }
    this.send();
  }


  /**
   * Does a ping on the hackystat server and returns true if the server was
   * accessible. A ping-able server indicates the data will be sent to it,
   * while a non-pingable server indicates that data will be stored offline.
   *
   * @return   True if the server could be pinged.
   */
  public boolean isServerPingable() {
    return this.isServerPingable(5000);
  }

  /**
   * Does a ping on the hackystat server and returns true if the server was
   * accessible. A ping-able server indicates the data will be sent to it,
   * while a non-pingable server indicates that data will be stored offline.
   * <p/>
   * If the server is not reachable, or does not respond with given time frame, false will
   * be returned.
   *
   * @param milliSecondsToWait Maximum seconds to wait for server response. A 0 value or negative
   *                           value is equivalent to set time out to infinity.
   * @return   True if the server could be pinged.
   */
  public boolean isServerPingable(int milliSecondsToWait) {
    boolean result = false;
    int waitTime = milliSecondsToWait <= 0 ? 0 : milliSecondsToWait;

    PingServerWorkThread workThread = new PingServerWorkThread();
    workThread.start();

    try {
      workThread.join(waitTime);  //block this thread until work thread dies or times out.
    }
    catch (InterruptedException ex) {
      //do nothing
    }

    //if work thread is still alive, then it's time out, result = false by default.
    if (!workThread.isAlive()) {
      result = workThread.serverPingable;
    }

    return result;
  }

  /**
   * Worker thread to ping the server to determine whether it's reachable or not. The original
   * ping command is implemented as a synchronous command, a separate thread is need to
   * implement time out feature.
   *
   * @author Qin ZHANG
   * @version $Id: SensorShell.java,v 1.1.1.1 2005/10/20 23:56:44 johnson Exp $
   */
  private class PingServerWorkThread extends Thread {

    /**
     * This instance will only be be accessed in the parent thread after the termination of this
     * thread, there is no need to synchronize access.
     */
    private boolean serverPingable = false;

    /**
     * Constructs this worker thread.
     */
    public PingServerWorkThread() {
      setDaemon(true); //want VM to termine even if this thread is alive.
    }

    /**
     * Pings the server synchronously.
     */
    public void run() {
      ShellCommand pingCommand = (ShellCommand) cache.get("Ping");
      this.serverPingable = pingCommand.send();
    }
  }


  /**
   * Invokes the send command on all currently instantiated command shell
   * instances. Used by the AutoSend CommandShell command and by the built-in
   * quit command. If the server could be pinged at the beginning of this
   * operation, then the current Offline data is cleared and the send proceeds.
   * If not pingable, then the offline data is saved for a later attempt.
   *
   * @return   True if all of the individual shell send commands complete
   *      successfully.
   */
  public boolean send() {
    boolean success = true;
    // Log this command if not running interactively.
    if (!this.isInteractive) {
      logger.info("#> send" + cr);
    }
    if (isServerPingable()) {
      OfflineManager.getInstance().clear();
      this.println("Sending sensor data (" + DateInfo.makeShortTimestamp() + ")");
      
      for (String commandClassName : this.cache.keySet()) {
        ShellCommand shellCommand = this.cache.get(commandClassName);
        success = (shellCommand.send() && success);
        this.println("  " + getCommandName(commandClassName) + ": " +
            shellCommand.getResultMessage());
      }
    }
    else {
      success = false;
      if (this.enableOfflineData) {
        this.println("Server not available. Storing commands offline.");
        OfflineManager.getInstance().store(this);
      }
      else {
        this.println("Server not available and offline storage disabled. Data lost.");
      }
    }
    this.printPrompt();
    return success;
  }


  /**
   * Returns the command name embedded in the fully qualified command class
   * name. If problems with parsing string, then return the commandClassName
   * unchanged.
   *
   * @param commandClassName  A fully qualified command class name, such as 'bar.BazShellCommand'.
   * @return A command name, such as 'Baz'.
   */
  private String getCommandName(String commandClassName) {
    try {
      int lastDotIndex = commandClassName.lastIndexOf(".");
      int shellCommandIndex = commandClassName.indexOf("ShellCommand");
      return commandClassName.substring(lastDotIndex + 1, shellCommandIndex);
    }
    catch (Exception e) {
      return commandClassName;
    }
  }


  /**
   * Invokes commandName with the supplied argList. Each commandName must have a
   * corresponding ShellCommand implementation. If doCommand is invoked via
   * SensorShell's main() method and thus in an interactive mode, then output is
   * printed. The doCommand method can also be invoked programmatically, in
   * which case no output is printed. The timeStamp argument indicates when this
   * event occurred, which could be now or else sometime in the past (if offline
   * storage was being used).
   * <p>
   * SensorShell.doCommand will be passed a commandName like "Activity", which might have an 
   * argList of [add, Compilation, foo.java].
   *
   * @param timeStamp    A Date instance indicating the time at which this command occurred.
   * @param commandName  A string indicating the command to be invoked.
   * @param argList      A list of strings providing the arguments to this command.
   * @return             True if the command succeeded.
   */
  public boolean doCommand(Date timeStamp, String commandName, List<String> argList) {
    // Log the command if we're not running interactively.
    if (!this.isInteractive) {
      logger.info("#> " + commandName + " " + argList + cr);
    }
    // Find the (cached) class associated with this name; go to top of loop if class not found.
    ShellCommand shellCommand = this.cache.get(commandName);
    if (shellCommand == null) {
      this.println("Command  '" + commandName + "' not found. Try again." + this.cr);
      return false;
    }
    // Found the class. Now guarantee that the passed timestamp differs from the most recent one.
    // We assume that no analyses care about small time differences of a few milliseconds.
    long timeStampLong = timeStamp.getTime();

    if (timeStampLong == this.lastTimeStamp) {
      lastTimeStampOffset++;
      timeStamp = new Date(this.lastTimeStamp + lastTimeStampOffset);
    }
    else { // Reset offset value when timestamp does not repeat.
      lastTimeStampOffset = 0;
    }
    this.lastTimeStamp = timeStampLong;

    // Invoke the shellCommand's doCommand with the time, args, and shell.
    boolean success = shellCommand.doCommand(timeStamp, argList, this);
    this.resultMessage = shellCommand.getResultMessage();
    this.println(shellCommand.getResultMessage());
    return success;
  }
  
  /**
   * Invokes doCommand(Date, String, List(args)). 
   *
   * @param timeStamp    A Date instance indicating the time at which this command occurred.
   * @param commandName  A string indicating the command to be invoked.
   * @param args   A String [] of arguments
   * @return True if the command succeeded.
   */
//  public boolean doCommand(Date timeStamp, String commandName, String [] args) {
//   return doCommand(timeStamp, commandName, Arrays.asList(args));
//  }  
  
  /**
   * Invokes doCommand(Date, String, List(args). 
   *
   * @param timeStamp    A Date instance indicating the time at which this command occurred.
   * @param commandName  A string indicating the command to be invoked.
   * @param args   A variable length list of Strings. Also could be a String[].
   * @return True if the command succeeded.
   */
  public boolean doCommand(Date timeStamp, String commandName, String ... args) {
   return doCommand(timeStamp, commandName, Arrays.asList(args));
  }     


  /**
   * Returns the result message from the last command.
   *
   * @return   The result message.
   */
  public String getResultMessage() {
    return this.resultMessage;
  }


  /**
   * Prints out initial information about the SensorShell.
   *
   * @param sensorProperties  The sensor properties file.
   */
  private void printBanner(SensorProperties sensorProperties) {

    this.println("Hackystat Version: " + getVersion() + " (" + getBuildtime() + ")");
    this.println("SensorShell started at: " + DateInfo.makeTimestamp());
    this.println("Type 'help' for a list of commands.");
    // Ping the host to determine availability.
    String host = sensorProperties.getHackystatHost();
    String key = sensorProperties.getKey();
    String availability = "available and key is valid.";
    try {
      Notification.ping(host, key);
    }
    catch (Exception e) {
      availability = "not available or key not valid.";
    }
    this.println("Host: " + host + " is " + availability);
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
   * notification data. The set of commands that can be processed by the shell
   * are dynamically determined. There are three built-in commands:
   * <ul>
   *   <li> "help" provides a summary of the available commands.
   *   <li> "send" sends all of the accumulated data to the server.
   *   <li> "quit" sends all of the accumulated data to the server and exits.
   *
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
    
    // Perform verification procedures and exit if arg is -verify.
    if ((args.length == 1) && (args[0].equalsIgnoreCase("-verify"))) {
      SensorShell.verifyClientSide();
      return;
    }

    // Set Parameter 1 (toolname) to supplied or default value.
    String toolName = (args.length > 0) ? args[0] : "interactive";

    // Set Parameter 2 (sensor properties file) to supplied or default value. Exit if can't find it.
    SensorProperties sensorProperties = (args.length >= 2) ?
        new SensorProperties("Shell", new File(args[1])) :
        new SensorProperties("Shell");
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
    SensorShell shell = new SensorShell(sensorProperties, interactive, toolName, offlineEnabled,
      commandFile);

    // Start processing commands either interactively or from the command file.
    int count = 0;
    while (true) {
      // Get the next command
      shell.printPrompt();
      String inputString = shell.readLine();
      if (inputString == null) {
        inputString = "";
      }

      // Quit if necessary.
      if (inputString.equalsIgnoreCase("quit")) {
        shell.quit();
        return;
      }

      // Print help strings.
      if (inputString.equalsIgnoreCase("help")) {
        shell.printHelp();
        continue;
      }

      // Send all the data.
      if (inputString.equalsIgnoreCase("send")) {
        shell.send();
        count = 0;
        continue;
      }

      // Otherwise it's an extended command.
      StringTokenizer tokenizer = new StringTokenizer(inputString, shell.delimiter);
      int numTokens = tokenizer.countTokens();
      // Go back to start of loop if the line is empty.
      if (numTokens == 0) {
        continue;
      }

      // Get the command name and any additional arguments.
      String commandName = tokenizer.nextToken();
      ArrayList<String> argList = new ArrayList<String>();
      while (tokenizer.hasMoreElements()) {
        argList.add(tokenizer.nextToken());
      }
      // Invoke the command with a timestamp of right now.
      shell.doCommand(new Date(), commandName, argList);

      // If the commandFile is large we can run into problems reading and sending the whole
      // thing at once. so, we break up the file.      
      if (count >= 500) {
        shell.send();
        count = 0;
        continue;
      }
      count++;
      
    }
  }


  /** Prints the help strings associated with all commands. */
  private void printHelp() {
    this.println("SensorShell Command Summary");
    this.println("help" + cr + "  This message.");
    this.println("quit" + cr + "  Exit this sensor shell and send all accumulated data.");
    this.println("send" + cr + "  Send all accumulated data.");
    
    for (ShellCommand command : this.cache.values()) {
      this.print(command.getHelpString());
    }
  }
  /**
   * Performs verification of client-side settings and its connection to the server.
   */
  private static void verifyClientSide() {
    System.out.println("SensorShell Client-side Verification.");
    System.out.println("Info:    All command line args except -debug ignored.");
    System.out.println("Info:    No logging of this output is performed.");
    System.out.println("Info:    Version: " + getVersion() + " (" + getBuildtime() + ")");

    //  SENSOR.PROPERTIES VERIFICATION.
    System.out.println("\n******** Verifying sensor.properties installation and settings.");
    SensorProperties sensorProperties = new SensorProperties("Debug");
    if (sensorProperties.isFileAvailable()) {
      System.out.println("Success: Found sensor.properties: " + sensorProperties.getAbsolutePath());
    } 
    else {
      System.out.println("Failure: Could not find sensor.properties file. Expected in: " + 
                          sensorProperties.getAbsolutePath());
      System.out.println("Failure: SensorShell client-side verification aborted.");
      return;
    }
    // Found a sensor.properties, so print out information. 
    System.out.println("Info:    Hackystat host is: " + sensorProperties.getHackystatHost());
    String userKey = sensorProperties.getKey();
    System.out.println("Info:    Your user key: '" + userKey + "'");
    
    String contextRoot = System.getProperty("hackystat.context.root", "hackystat");
    System.out.println("Info:    Hackystat context root: " + contextRoot);
    String host = sensorProperties.getHackystatHost() + contextRoot + "/controller";
    System.out.println("Success: sensor.properties file found and host/key values obtained.");

    // HOME PAGE AVAILABILITY VERIFICATION
    System.out.println("\n******** Verifying server home page availability.");
    System.out.println("Info:    Retrieving home page from: " + host);
    WebConversation conversation = null;
    WebResponse response = null;
    try {
      conversation = new WebConversation();
      response = conversation.getResponse(host);
      String homePageTitleString = "Hackystat - Site Home";
      if (response.getText().indexOf("Bringing up hackystat home page") != -1) {
        System.out.println("Info:    Home page not initialized. Retrying for up to 10 seconds...");
        for (int i = 0; i < 10; i++) {
          Thread.sleep(1000);
          System.out.println("Info:    Retrying home page: " + host + " on " + new Date());
          conversation = new WebConversation();
          response = conversation.getResponse(host);
          if (response.getText().indexOf("Bringing up hackystat home page") == -1) {
            // get out of loop
            break;
          }
        }
      }
      if (homePageTitleString.equals(response.getTitle())) {
        System.out.println("Success: Retrieved server home page from: " + host);
      }
      else {
        System.out.println("Failure: Couldn't get home page. Response: " + response.getText());
        System.out.println("Failure: SensorShell client-side verification aborted.");
        return;
      }
    }
    catch (Exception e) {
      System.out.println("Failure: Exception thrown occurred during home page retrieval: " + e);
      e.printStackTrace();
      System.out.println("Failure: SensorShell client-side verification aborted.");
      return;
    }
    
    // USER KEY VERIFICATION
    System.out.println("\n******** Verifying your hackystat user key.");
    try {
      System.out.println("Info:    Attempting to login with key: " + userKey);
      WebForm form = response.getFormWithID("Login");
      WebRequest request = form.getRequest();
      request.setParameter("Key", userKey);
      response = conversation.getResponse(request);
      if ("Hackystat - Analyses".equals(response.getTitle())) {
        System.out.println("Success: Logged in with key: " + userKey);
      }
      else {
        System.out.println("Failure: Unable to login with key: " + userKey);
        System.out.println("         Returned: " + response.getText());
        System.out.println("Failure: SensorShell client-side verification aborted.");
        return;
      }
    }
    catch (Exception e) {
      System.out.println("Failure: Exception thrown during login attempt: " + e);
      e.printStackTrace();
      System.out.println("Failure: SensorShell client-side verification aborted.");
      return;
    }
    
    // SOAP VERIFICATION
    System.out.println("\n******** Verifying Soap service.");
    String soapUrl = sensorProperties.getHackystatHost() + contextRoot + "/servlet/rpcrouter";
    System.out.println("Info:    Checking Soap service from: " + soapUrl);
    try {
      response = conversation.getResponse(soapUrl);
      if ("SOAP RPC Router".equals(response.getTitle())) {
        System.out.println("Success: Contacted Soap service successfully.");
      }
      else {
        System.out.println("Failure: Soap service problem: " + response.getText());
        System.out.println("Failure: SensorShell client-side verification aborted.");
        return;        
      }
    }
    catch (Exception e) {
      System.out.println("Failure: Exception thrown during Soap retrieval: " + e);
      e.printStackTrace();
      System.out.println("Failure: SensorShell client-side verification aborted.");
      return;      
    }
    System.out.println("\nClient-side verification appears to have succeeded.");
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
      logger.info(line + cr);
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
  public void println(String line) {
    logger.info(line + cr);
    if (isInteractive) {
      System.out.println(line);
    }
  }


  /**
   * Prints out the line without newline if in interactive mode, and always logs the line.
   *
   * @param line  The line to be printed.
   */
  private void print(String line) {
    logger.info(line);
    if (isInteractive) {
      System.out.print(line);
    }
  }


// Looks like we can remove this method according to FindBugs.  
//  /** Prints out a newline if in interactive mode. */
//  private void println() {
//    logger.info(this.cr);
//    if (isInteractive) {
//      System.out.println();
//    }
//  }

  /**
   * Return the current version number.
   *
   * @return The version number, or "Unknown" if could not be determined.
   */
  private static String getVersion() {
    String release;
    try {
      Package thisPackage = Class.forName("org.hackystat.sensorshell.SensorShell")
                            .getPackage();
      release = thisPackage.getSpecificationVersion();
    }
    catch (Exception e) {
      release = "Unknown";
    }
    return release;
  }

  /**
   * Closes sensor shell, releases handler of log file. Sensors should close 
   * sensorshell upon finish to avoid multiple log files for the same sensor. 
   *
   */
  public void close() {
    if (this.logger != null) {
      // Close all the open handler.
      Handler[] handlers = this.logger.getHandlers();
      for (int i = 0; i < handlers.length; i++) {
        handlers[i].close();
      }
    }
  }
  
  /**
   * Return the current build time.
   *
   * @return   The buildtime value, or "Unknown" if could not be determined.
   */
  private static String getBuildtime() {
    String build;
    try {
      Package thisPackage = 
        Class.forName("org.hackystat.sensorshell.SensorShell").getPackage();
      build = thisPackage.getImplementationVersion();
    }
    catch (Exception e) {
      build = "Unknown";
    }
    return build;
  }
  
  /**
   * Returns the hackystat host associated with this sensorshell instance.
   * @return The hackystat host.
   */
  public String getHost() {
    return this.host;
  }
  
  /**
   * Returns the hackystat UserKey associated with this sensorshell instance. 
   * @return The userkey.
   */
  public String getUserKey() {
    return this.key;
  }
}

