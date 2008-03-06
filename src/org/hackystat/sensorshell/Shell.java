package org.hackystat.sensorshell;

import java.util.Map;

import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;

/**
 * An interface that is implemented by SensorShell and MultiSensorShell. This enables
 * sensors that can restrict themselves to the use of the following public methods to
 * easily toggle between the use of SensorShell or MultiSensorShell depending upon 
 * load requirements.
 * 
 * As a simple example, a sensor might contain code similar to the following:
 * <pre>
 * boolean useMulti = isMultiSensorShellRequested();
 * Shell shell = (useMulti) ? new MultiSensorShell(...) : new SensorShell(...);
 *  :
 * shell.add(...)
 *  :
 * shell.quit()
 * </pre>
 * Thus, the decision to use a SensorShell vs. a MultiSensorShell can be made at run-time.
 *   
 * @author Philip Johnson
 */
public interface Shell {
  
  /**
   * Adds the passed SensorData instance to the Shell.
   * 
   * @param sensorData The SensorData instance to be queued for transmission.
   * @throws SensorShellException If problems occur sending the data.
   */
  public void add(SensorData sensorData) throws SensorShellException;
  
  /**
   * Converts the values in the KeyValMap to a SensorData instance and adds it to the
   * Shell. Owner will default to the hackystat user in the sensor.properties file.
   * Timestamp and Runtime will default to the current time.
   * 
   * @param keyValMap The map of key-value pairs.
   * @throws SensorShellException If the Map cannot be translated into SensorData, 
   * typically because a value
   * was passed for Timestamp or Runtime that could not be parsed into XMLGregorianCalendar.
   * Or if problems occur sending the data.
   */
  public void add(Map<String, String> keyValMap) throws SensorShellException;
  
  /**
   * Returns true if the host can be pinged and the email/password combination is valid.
   * 
   * @return True if the host can be pinged and the user credentials are valid.
   */
  public boolean ping();
  
  /**
   * Immediately invokes send() on this Shell. Note that you will rarely want
   * to invoke this method. Instead, during normal operation you will rely on the
   * autoSendTimeInterval to invoke send() in a separate thread, and then invoke quit() to invoke
   * send() at the conclusion of the run.
   * @return The total number of instances sent by Shell.
   * @throws SensorShellException If problems occur sending the data.  
   */
  public int send() throws SensorShellException;
  
  /**
   * Invokes quit() on this Shell, which invokes a final send() and closes any
   * logging files.
   * @throws SensorShellException If an exception occurred during any autosend. 
   */
  public void quit() throws SensorShellException;
  
  /**
   * Implements the "StateChange" algorithm.  The goal of StateChange is to add a new SensorData
   * instance for sending to the SensorBase only when "something has changed" in the tool (which
   * is typically an interactive editor or IDE). This method is designed to be used in conjunction
   * with a timer-based process in the sensor client.  The timer-based process should wake up 
   * at regular intervals, determine the currently "active" resource (typically a file), and 
   * compute a "checksum" for that resource representing its state. (This checksum is typically
   * the size of the resource in bytes or characters.) Having obtained those two values, the 
   * sensor client can then create a keyValMap as if it were going to do a regular Add command,
   * and invoke this method with it as well as with the resourceCheckSum.  This method keeps
   * track of the last Resource and last ResourceCheckSum provided to it, and if either has
   * changed, it will automatically invoke the Add command, passing the keyValMap to it. 
   * <p>
   * Thus, if an editor is running but the user is out at lunch, repeated invocations of the 
   * StateChange method will not result in any new data being sent to the server.  
   * @param resourceCheckSum An integer representing the state of the Resource. 
   * @param keyValMap A map of key-value pairs representing sensor data fields and properties. 
   * @throws Exception If problems occur during the Add (if the Add actually occurs.)
   */
  public void statechange(long resourceCheckSum, Map<String, String> keyValMap) 
  throws Exception;
  
  /**
   * Returns the SensorShell properties instance used to create this SensorShell.
   * @return The SensorShellProperties instance. 
   */
  public SensorShellProperties getProperties();

}
