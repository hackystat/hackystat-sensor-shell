package org.hackystat.sensorshell;

import java.io.File;
import java.util.Map;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;


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
 * Or you can invoke it with one, two, or three additional arguments:
 * 
 * <pre>java -jar sensorshell.jar [tool] [sensorshell.properties] [command file]</pre>
 * <p>
 * Programmatic mode involves creating an instance of SensorShell, retrieving the 
 * appropriate command instance (Ping, Add, etc.) and invoking the appropriate method.
 *
 * @author    Philip M. Johnson
 */
public class SensorShell implements Shell {

  /** The underlying SingleSensorShell or MultiSensorShell. */
  private Shell shell = null;
  
  /**
   * Constructs a new SensorShell instance that can be provided with
   * notification data to be sent eventually to a specific user key and host.
   * The toolName field in the log file name is set to "interactive" if the tool
   * is invoked interactively and "tool" if it is invoked programmatically.
   *
   * @param properties  The sensor properties instance for this run.
   * @param isInteractive     Whether this SensorShell is being interactively invoked or not.
   */
  public SensorShell(SensorShellProperties properties, boolean isInteractive) {
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
  public SensorShell(SensorShellProperties properties, boolean isInteractive, String tool) {
    this(properties, isInteractive, tool, null);
  }


  /**
   * Constructs a new SensorShell instance that can be provided with
   * notification data to be sent eventually to a specific user key and host.
   *
   * @param properties   The sensor properties instance for this run.
   * @param isInteractive Whether this SensorShell is being interactively invoked or not.
   * @param toolName  The invoking tool that is added to the log file name.
   * @param commandFile A file containing shell commands, or null if none provided.
   */
  public SensorShell(SensorShellProperties properties, boolean isInteractive, String toolName,
      File commandFile) {
    if (properties.isMultiShellEnabled()) {
      // Note that isInteractive, commandFile are ignored if MultiSensorShell is specified.
      shell = new MultiSensorShell(properties, toolName);
    }
    else {
      shell = new SingleSensorShell(properties, isInteractive, toolName, commandFile);
    }
  }
  
  /** {@inheritDoc} */
  public void add(Map<String, String> keyValMap) throws SensorShellException {
    this.shell.add(keyValMap);
  }
  
  /** {@inheritDoc} */
  public void add(SensorData sensorData) throws SensorShellException {
    this.shell.add(sensorData);
  }
  
  /** {@inheritDoc} */
  public int send() throws SensorShellException {
    return this.shell.send();
  }
  
  /** {@inheritDoc} */
  public void quit() {
    this.shell.quit();
  }
  
  /** {@inheritDoc} */
  public boolean ping() {
    return this.shell.ping();
  }
  
  /** {@inheritDoc} */
  public SensorShellProperties getProperties() {
    return this.shell.getProperties();
  }
  
  /** {@inheritDoc} */
  public void statechange(long resourceCheckSum, Map<String, String> keyValMap) throws Exception {
    this.shell.statechange(resourceCheckSum, keyValMap);
  }
  
  /**
   * The command line shell interface.
   * <ul>
   *   <li> If invoked with no arguments, then the default sensor.properties
   *   file is used and the toolName field in the sensor log file name is
   *   "interactive".
   *   <li> If invoked with one argument, that argument is used as the toolName
   *   value.
   *   <li> If invoked with two arguments, then 
   *   the second is used as the sensorshell.properties file
   *   path.
   *   <li> If invoked with three arguments, then the 
   *   the third argument specifies a file of commands (the last of which should be 'quit').
   * </ul>
   * Unless three arguments are provided,
   * the shell then provides a ">>" prompt and supports interactive entry of
   * sensor data. The following commands are supported:
   * <ul>
   *   <li> "help" provides a summary of the available commands.
   *   <li> "send" sends all of the accumulated data to the server.
   *   <li> "quit" sends all of the accumulated data to the server and exits.
   *   <li> "ping" checks to see if the host/user/password is valid. 
   *   <li> "add" adds a single Sensor Data instance to the buffered list to send.
   * </ul>
   *
   * @param args  The command line parameters. See above for details.
   * @throws SensorShellException If problems occur sending data. 
   */
  public static void main(String args[]) throws SensorShellException {
    // Print help line and exit if arg is -help.
    if ((args.length == 1) && (args[0].equalsIgnoreCase("-help"))) {
      System.out.println("java -jar sensorshell.jar [tool] [sensorshell.properties] [commandfile]");
      return;
    }
    
    // Set Parameter 1 (toolname) to supplied or default value.
    String toolName = (args.length > 0) ? args[0] : "interactive";

    // Set Parameter 2 (sensorshell.properties file) to supplied or default value. Exit if error.
    SensorShellProperties sensorProps = null;
    try {
      sensorProps = (args.length >= 2) ?
          new SensorShellProperties(new File(args[1])) : new SensorShellProperties();
    }
    catch (SensorShellException e) {
      System.out.println(e.getMessage());
      System.out.println("Exiting...");
      return;
    }

    // Set Parameter 3 (command file). Null if not supplied. Exit if supplied and bogus.
    File commandFile = null;
    if (args.length == 3) {
      commandFile = new File(args[2]);
      if (!(commandFile.exists() && commandFile.isFile())) {
        System.out.println("Could not find the command file. Exiting...");
        return;
      }
    }

    // Set interactive parameter. From command line, always interactive unless using command file.
    boolean interactive = ((commandFile == null));

    // Now create the (single) shell instance, supplying it with all the appropriate arguments.
    SingleSensorShell shell = 
      new SingleSensorShell(sensorProps, interactive, toolName, commandFile);

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

}

