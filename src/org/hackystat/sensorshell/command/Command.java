package org.hackystat.sensorshell.command;

import org.hackystat.sensorshell.SensorShellProperties;
import org.hackystat.sensorshell.SingleSensorShell;

/**
 * A class providing access to information useful for all Command instances. 
 * @author Philip Johnson
 */
public class Command {
  
  /** The sensorshell. */
  protected SingleSensorShell shell;
  /** The SensorProperties. */
  protected SensorShellProperties properties;
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
  public Command(SingleSensorShell shell, SensorShellProperties properties) {
    this.shell = shell;
    this.properties = properties;
    this.host = properties.getSensorBaseHost();
    this.email = properties.getSensorBaseUser();
    this.password = properties.getSensorBasePassword();
  }
}
