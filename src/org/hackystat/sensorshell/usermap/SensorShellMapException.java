package org.hackystat.sensorshell.usermap;

/**
 * This class implements an exception that is thrown when using a SensorShellMap fails to 
 * instantiate or otherwise does not work correctly.
 * @author Burt Leung
 * @version $Id: SensorShellMapException.java,v 1.1.1.1 2005/10/20 23:56:43 johnson Exp $
 */
public class SensorShellMapException extends Exception {
  
  /** Generated serial UID. */
  private static final long serialVersionUID = -2099926077899340572L;
  
  /**
   * Thrown when exceptions occur during the use of a SensorShellMap instance.
   * @param detailMessage A message describing the problem
   */
  public SensorShellMapException(String detailMessage) {
    super(detailMessage);
  }
  
  /**
   * Thrown when exceptions occur during the use of a SensorShellMap instance.
   * @param detailMessage A message describing the problem.
   * @param error The previous error.
   */
  public SensorShellMapException(String detailMessage, Throwable error) {
    super(detailMessage, error);
  }
  
  /**
   * Convert an exception to a SensorShellMapException.
   * @param exception The exception to be converted
   */
  public SensorShellMapException(Exception exception) {
    super(exception);
  }
}
