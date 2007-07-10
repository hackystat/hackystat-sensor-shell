package org.hackystat.sensorshell.command;

import org.hackystat.sensorshell.SensorProperties;
import org.hackystat.sensorshell.SensorShell;

/**
 * A class providing access to information useful for all Command instances. 
 * @author Philip Johnson
 */
public class Command {
  
  /** The sensorshell. */
  protected SensorShell shell;
  /** The SensorProperties. */
  protected SensorProperties properties;
  /** The sensorbase host. */
  protected String host;
  /** The client email. */
  protected String email;
  /** The client password. */
  protected String password;
  /** The line separator. */
  protected String cr = System.getProperty("line.separator");
  
  /**
   * Constructs a Command instance.  Only subclasses call this method. 
   * @param shell The sensorshell. 
   * @param properties The properties. 
   */
  public Command(SensorShell shell, SensorProperties properties) {
    this.shell = shell;
    this.properties = properties;
    this.host = properties.getHackystatHost();
    this.email = properties.getEmail();
    this.password = properties.getPassword();
  }
}
