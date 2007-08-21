package org.hackystat.sensorshell;


/**
 * An exception that is thrown when SensorProperties can be instantiated.
 * There are several causes for this exception:
 * <ol>
 * <li>Missing sensor properties file</li>
 * <li>Unparseable file</li>
 * <li>Read access is denied on the file</>
 * </ol>
 * @author Aaron A. Kagawa
 */
public class SensorPropertiesException extends Exception {

  /** The default serial version UID. */
  private static final long serialVersionUID = 1L;
  
  /**
   * Thrown when a SensorProperties instance cannot be created.
   * @param message The message indicating the problem. 
   */
  public SensorPropertiesException(String message) {
    super(message);
  }
}
