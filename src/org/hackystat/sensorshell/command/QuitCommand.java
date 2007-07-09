package org.hackystat.sensorshell.command;

import java.util.logging.Handler;

import org.hackystat.sensorshell.SensorProperties;
import org.hackystat.sensorshell.SensorShell;

public class QuitCommand extends Command {
  
  public QuitCommand(SensorShell shell, SensorProperties properties) {
    super(shell, properties);
  }

  @Override
  /** {@inheritDoc} */
  public String getHelpString() {
    return "Provides a help message.";
  }
  
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
