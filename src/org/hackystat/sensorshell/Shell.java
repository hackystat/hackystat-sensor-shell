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
   */
  public void add(SensorData sensorData);
  
  /**
   * Converts the values in the KeyValMap to a SensorData instance and adds it to the
   * Shell. Owner will default to the hackystat user in the sensor.properties file.
   * Timestamp and Runtime will default to the current time.
   * 
   * @param keyValMap The map of key-value pairs.
   * @throws Exception If the Map cannot be translated into SensorData, typically because a value
   * was passed for Timestamp or Runtime that could not be parsed into XMLGregorianCalendar.
   */
  public void add(Map<String, String> keyValMap) throws Exception;
  
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
   */
  public int send();
  
  /**
   * Invokes quit() on this Shell, which invokes a final send() and closes any
   * logging files.
   */
  public void quit();
  
  /**
   * Sets the logging level to be used for this Shell.
   * If the passed string cannot be parsed into a Level, then INFO is set by default.
   * @param level The new Level.
   */
  public void setLoggingLevel(String level);

}
