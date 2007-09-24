package org.hackystat.sensorshell;

import java.util.ArrayList;
import java.util.Map;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;

/**
 * MultiSensorShell is a wrapper around SensorShell that is designed to support high performance
 * transmission of sensor data instances from a client to a server. Prior research has determined
 * that when a single SensorShell is used to transmit a large amount of data in a short period of
 * time, it can spend a substantial portion of its time blocked while waiting for an HTTP PUT to
 * complete. MultiSensorShell overcomes this problem by instantiating multiple SensorShell instances
 * internally and then passing sensor data to them in a round-robin fashion. Each SensorShell is
 * passed an autoSendTimeInterval value, which results in a separate thread for each SensorShell
 * instance that will concurrently send any buffered data at regular time intervals. This
 * significantly reduces blocked time for MultiSensorShell, because when any individual SensorShell
 * instance is performing its HTTP PUT call, the MultiSensorShell can be concurrently adding data to
 * one of the other SensorShell instances.
 * <p>
 * MultiSensorShell provides a number of "tuning parameters", including the number of SensorShells
 * to be created, the number of SensorData instances to be sent to a single SensorShell before
 * moving on to the next SensorShell (the "batchSize"), and the autoSendInterval for each
 * SensorShell. There is a MultiSensorShell constructor that provides reasonable default values for
 * these tuning parameters and that should result in good performance under most conditions.
 * <p>
 * The TestMultiSensorShell class provides a main() method that we have used to do some simple
 * performance evaluation, which we report on next. All results were obtained using a MacBook Pro
 * with a 2.33 Ghz Intel Core Duo processor and 3 GB of 667 Mhz DDR2 SDRAM. Both the client and
 * SensorBase server were running on this computer to minimize network latency issues.
 * <p>
 * If you instantiate a MultiSensorShell with the number of SensorShells set to 1, you effectively
 * get the default case. In this situation, we have found that the average time to send a single
 * SensorData instance is approximately 6 milliseconds, almost independent of the settings for
 * batchSize and the autoSendInterval. Increasing the number of SensorShell instances to 5 doubles
 * the throughput, to approximately 3 milliseconds per instance. At this point, some kind of
 * performance plateau is reached, with further tweaking of the tuning parameters seeming to have
 * little effect. We do not know whether this is a "real" limit or an artificial limit based upon
 * some environmental feature (such as the logging mechanism.)
 * <p>
 * With totalData = 500,000, numShells = 10, autoSendInterval = 0.05, and batchSize = 200, we have
 * achieved throughput of 2.8 milliseconds per instance (which is equivalent to 360 instances per
 * second and 1.2M instances per hour.)
 * <p>
 * We have also found that we can store around 350,000 sensor data instances per GB of disk space.
 * <p>
 * Note that a single SensorShell instance is simpler, creates less processing overhead, and has
 * equivalent performance to MultiSensorShell for transmission loads up to a dozen or so sensor data
 * instances per second. We recommend using a single SensorShell instance rather than
 * MultiSensorShell unless optimizing data transmission throughput is an important requirement.
 * 
 * @author Philip Johnson
 */
public class MultiSensorShell {
  /** The internal SensorShells managed by this MultiSensorShell. */
  private ArrayList<SensorShell> shells;
  /** The total number of shells. */
  private int numShells;
  /** The number of SensorData instances to be sent to a single Shell before going to the next. */
  private int batchSize;
  /** A counter that indicates how many instances have been sent to the current SensorShell. */
  private int batchCounter = 0;
  /** A pointer to the current Shell that is receiving SensorData instances. */
  private int currShellIndex = 0;

  /**
   * Creates a new MultiSensorShell for multi-threaded transmission of SensorData instances to a
   * SensorBase.
   * 
   * @param properties A SensorProperties instance.
   * @param toolName The name of the tool, used to name the log file.
   * @param enableOfflineData Whether or not to save/restore offline data.
   * @param autoSendTimeInterval The time in minutes between autoSends for each shell.
   * @param autoSendBatchSize The maximum buffer size for each shell.
   * @param numShells The total number of SensorShell instances to create. Must be greater than 0.
   * @param batchSize The number of SensorData instances to send in a row to a single SensorShell.
   * Must be greater than 0.
   * @throws Exception If numShells or batchSize values are illegal, or other problems occur.
   */
  public MultiSensorShell(SensorProperties properties, String toolName, boolean enableOfflineData,
      double autoSendTimeInterval, int autoSendBatchSize, int numShells, int batchSize)
      throws Exception {
    if (numShells < 1) {
      throw new Exception("Number of shells must be greater than 0.");
    }
    if (batchSize < 1) {
      throw new Exception("Batch size must be greater than 0.");
    }
    this.shells = new ArrayList<SensorShell>();
    this.numShells = numShells;
    this.batchSize = batchSize;
    for (int i = 0; i < numShells; i++) {
      // MultiSensorShells must always be non-interactive.
      boolean isInteractive = false;
      SensorShell shell = new SensorShell(properties, isInteractive, toolName, enableOfflineData);
      shell.setAutoSendTimeInterval(autoSendTimeInterval);
      shell.setAutoSendBufferSize(autoSendBatchSize);
      this.shells.add(shell);
    }
  }

  /**
   * Constructs a new MultiSensorShell instance with reasonable default values for enableOfflineData
   * (true), autoSendTimeInterval (6 seconds), autoSendBatchSize (1000), numShells (5), and
   * batchSize (200).
   * 
   * @param sensorProperties The sensor properties instance for this run.
   * @param toolName Indicates the invoking tool that is added to the log file name.
   * @throws Exception If problems occur.
   */
  public MultiSensorShell(SensorProperties sensorProperties, String toolName) throws Exception {
    this(sensorProperties, toolName, true, 0.1, 1000, 5, 200);
  }

  /**
   * Adds the passed SensorData instance to one of the internal SensorShells.
   * 
   * @param sensorData The SensorData instance to be queued for transmission.
   */
  public void add(SensorData sensorData) {
    this.shells.get(getCurrShellIndex()).add(sensorData);
  }

  /**
   * Converts the values in the KeyValMap to a SensorData instance and adds it to one of the
   * internal SensorShells. Owner will default to the hackystat user in the sensor.properties file.
   * Timestamp and Runtime will default to the current time.
   * 
   * @param keyValMap The map of key-value pairs.
   * @throws Exception If the Map cannot be translated into SensorData, typically because a value
   * was passed for Timestamp or Runtime that could not be parsed into XMLGregorianCalendar.
   */
  public void add(Map<String, String> keyValMap) throws Exception {
    this.shells.get(getCurrShellIndex()).add(keyValMap);
  }

  /**
   * Returns an index to the current SensorShell index to be used for data transmission. Internally
   * updates the batchCounter.
   * 
   * @return The index to the current SensorShell instance.
   */
  private int getCurrShellIndex() {
    // First, update the batchCounter and change the currShellIndex if necessary.
    // batchCounter goes from 1 to batchSize.
    // currShellIndex goes from 0 to numShells -1
    batchCounter++;
    if (this.batchCounter > batchSize) {
      batchCounter = 0;
      this.currShellIndex++;
      if (this.currShellIndex >= this.numShells) {
        this.currShellIndex = 0;
      }
    }
    return currShellIndex;
  }

  /**
   * Returns true if the host can be pinged and the email/password combination is valid.
   * 
   * @return True if the host can be pinged and the user credentials are valid.
   */
  public boolean ping() {
    return this.shells.get(0).ping();
  }

  /**
   * Immediately invokes send() on all of the internal SensorShells. Note that you will rarely want
   * to invoke this method. Instead, during normal operation you will rely on the
   * autoSendTimeInterval to invoke send() in a separate thread, and then invoke quit() to invoke
   * send() at the conclusion of the run.
   * @return The total number of instances sent by all internal SensorShells. 
   */
  public int send() {
    int totalSent = 0;
    for (int i = 0; i < numShells; i++) {
      totalSent += this.shells.get(i).send();
    }
    return totalSent;
  }

  /**
   * Invokes quit() on all of the internal SensorShells, which invokes a final send() and closes any
   * logging files.
   */
  public void quit() {
    for (int i = 0; i < numShells; i++) {
      this.shells.get(i).quit();
    }
  }
}
