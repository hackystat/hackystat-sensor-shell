package org.hackystat.sensorshell.command;

import org.hackystat.sensorshell.SensorProperties;
import org.hackystat.sensorshell.SensorShell;

public abstract class Command {
  
  protected SensorShell shell;
  protected SensorProperties properties;
  protected String host;
  protected String email;
  protected String password;
  /** The line separator. */
  protected String cr = System.getProperty("line.separator");
  
  /** Holds the result message after processing the command. */
  protected String resultMessage;
  
  public Command(SensorShell shell, SensorProperties properties) {
    this.shell= shell;
    this.properties = properties;
    this.host = properties.getHackystatHost();
    this.email = properties.getEmail();
    this.password = properties.getPassword();
  }
  
  public abstract String getHelpString();
  
  /**
   * Describes the result of processing the command.
   *
   * @return   The command result.
   */
  public String getResultMessage() {
    return this.resultMessage;
  }
  

}
