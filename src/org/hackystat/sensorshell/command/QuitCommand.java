package org.hackystat.sensorshell.command;

import java.util.logging.Handler;

import org.hackystat.sensorshell.SensorProperties;
import org.hackystat.sensorshell.SensorShell;

/**
 * Implements the Quit command, which sends any buffered data and closes the loggers. 
 * @author Philip Johnson
 */
public class QuitCommand extends Command {
  
  /**
   * Creates the QuitCommand. 
   * @param shell The sensorshell. 
   * @param properties The sensorproperties. 
   */
  public QuitCommand(SensorShell shell, SensorProperties properties) {
    super(shell, properties);
  }
  
  /** Quits the shell. Sends all data and closes the loggers. */
  public void quit() {
    // Log this command if not running interactively.
    if (!this.shell.isInteractive()) {
      this.shell.getLogger().info("#> quit" + cr);
    }
    this.shell.getSensorDataCommand().send();
    // Now close all loggers. 
    if (this.shell.getLogger() != null) {
      // Close all the open handler.
      Handler[] handlers = this.shell.getLogger().getHandlers();
      for (int i = 0; i < handlers.length; i++) {
        handlers[i].close();
      }
    }
    this.shell.println("Quitting.");
  }
}
