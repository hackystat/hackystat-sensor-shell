package org.hackystat.sensorshell.command;

import java.util.logging.Handler;

import org.hackystat.sensorshell.SensorShellException;
import org.hackystat.sensorshell.SensorShellProperties;
import org.hackystat.sensorshell.SingleSensorShell;

/**
 * Implements the Quit command, which sends any buffered data and closes the loggers. 
 * @author Philip Johnson
 */
public class QuitCommand extends Command {
  
  /** Holds the sensor data command for sending data. */
  private SensorDataCommand sensorDataCommand;
  
  /** Holds the autosend instance. */
  private AutoSendCommand autoSendCommand;
  
  /**
   * Creates the QuitCommand. 
   * @param shell The sensorshell. 
   * @param properties The sensorproperties.
   * @param sensorDataCommand The SensorDataCommand. 
   * @param autoSendCommand The AutoSendCommand.
   */
  public QuitCommand(SingleSensorShell shell, SensorShellProperties properties, 
      SensorDataCommand sensorDataCommand, AutoSendCommand autoSendCommand) {
    super(shell, properties);
    this.sensorDataCommand = sensorDataCommand;
    this.autoSendCommand = autoSendCommand;
  }
  
  /** Quits the shell. Sends all data and closes the loggers. */
  public void quit() {
    // Log this command if not running interactively.
    if (!this.shell.isInteractive()) {
      this.shell.getLogger().info("#> quit" + cr);
    }
    try {
      this.sensorDataCommand.send();
    }
    catch (SensorShellException e) {
      this.shell.getLogger().warning("Error sending data. " + e);
    }
    // Now close all loggers. 
    if (this.shell.getLogger() != null) {
      // Close all the open handler.
      Handler[] handlers = this.shell.getLogger().getHandlers();
      for (int i = 0; i < handlers.length; i++) {
        handlers[i].close();
      }
    }
    this.autoSendCommand.quit();
    this.shell.println("Quitting.");
  }
}
