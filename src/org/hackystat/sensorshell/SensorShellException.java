package org.hackystat.sensorshell;


/**
 * An exception that is thrown when problems occur with instantiating SensorShellProperties or 
 * sending sensor data. 
 * @author Aaron A. Kagawa, Philip Johnson
 */
public class SensorShellException extends Exception {

  /** The default serial version UID. */
  private static final long serialVersionUID = 1L;
  
  /**
   * Thrown when a SensorProperties instance cannot be created.
   * @param message The message indicating the problem. 
   */
  public SensorShellException(String message) {
    super(message);
  }
  
  /**
   * Thrown when a SensorProperties instance cannot be created.
   * @param message The message indicating the problem. 
   * @param e The exception.
   */
  public SensorShellException(String message, Throwable e) {
    super(message, e);
  }
}
