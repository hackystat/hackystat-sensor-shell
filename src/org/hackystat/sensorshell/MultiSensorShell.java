package org.hackystat.sensorshell;

import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;

/**
 * MultiSensorShell is a wrapper around SingleSensorShell that is designed for high performance
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
 * The sensorshell.properties file provides a number of tuning parameters for MultiSensorShell
 * processing. We currently recommend the following settings for best performance:
 * <ul>
 * <li>sensorshell.multishell.enabled = true
 * <li>sensorshell.multishell.numshells = 10
 * <li>sensorshell.multishell.batchsize = 250
 * <li>sensorshell.multishell.autosend.timeinterval = 0.05
 * <li>sensorshell.offline.cache.enabled = false
 * <li>sensorshell.offline.recovery.enabled = false
 * <li>sensorshell.autosend.maxbuffer = 30000
 * <li>sensorshell.logging.level = OFF
 * <li>sensorshell.timeout = 30
 * </ul>
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
 * some environmental feature.
 * <p>
 * With the sensorshell.properties settings listed above, we have 
 * achieved throughput of 2.8 milliseconds per instance (which is equivalent to 360 instances per
 * second and 1.2M instances per hour.)
 * <p>
 * We have also found that we can store around 350,000 sensor data instances per GB of disk space.
 * <p>
 * Note that we are effectively disabling autosend.batchsize by setting it
 * to a high value (30,000). This is because 
 * reaching the batchSize limit forces a blocking send() of the data, which is precisely what we
 * want to avoid in MultiSensorShell.  Instead, we try to tune the autosend.timeinterval so that as
 * many of our send() invocations as possible occur asynchronously in a separate thread. 
 * <p>
 * Note that a single SensorShell instance is simpler, creates less processing overhead, and has
 * equivalent performance to MultiSensorShell for transmission loads up to a dozen or so sensor data
 * instances per second. We recommend using a single SensorShell instance rather than
 * MultiSensorShell unless optimizing data transmission throughput is an important requirement.
 * 
 * @author Philip Johnson
 */
public class MultiSensorShell implements Shell {
  /** The internal SensorShells managed by this MultiSensorShell. */
  private ArrayList<SingleSensorShell> shells;
  /** The total number of shells. */
  private int numShells;
  /** The number of SensorData instances to be sent to a single Shell before going to the next. */
  private int batchSize;
  /** A counter that indicates how many instances have been sent to the current SensorShell. */
  private int batchCounter = 0;
  /** A pointer to the current Shell that is receiving SensorData instances. */
  private int currShellIndex = 0;
  /** Used when batchSize == 0 */
  private Random generator = new Random(0L);

  /**
   * Creates a new MultiSensorShell for multi-threaded transmission of SensorData instances to a
   * SensorBase.
   * 
   * @param properties A SensorProperties instance.
   * @param toolName The name of the tool, used to name the log file.
   */
  public MultiSensorShell(SensorShellProperties properties, String toolName) {
    this.shells = new ArrayList<SingleSensorShell>(properties.getMultiShellNumShells());
    this.numShells = properties.getMultiShellNumShells();
    this.batchSize = properties.getMultiShellBatchSize();
    properties.switchToMultiShellMode();
    for (int i = 0; i < numShells; i++) { 
      // MultiSensorShells must always be non-interactive.
      boolean isInteractive = false;
      SingleSensorShell shell = new SingleSensorShell(properties, isInteractive, toolName);
      this.shells.add(shell);
    }
  }

  /** {@inheritDoc} */
  public void add(SensorData sensorData) throws SensorShellException {
    this.shells.get(getCurrShellIndex()).add(sensorData);
  }

  /** {@inheritDoc} */
  public void add(Map<String, String> keyValMap) throws SensorShellException {
    this.shells.get(getCurrShellIndex()).add(keyValMap);
  }

  /**
   * Returns an index to the current SensorShell index to be used for data transmission. Internally
   * updates the batchCounter.
   * If batchSize is 0, then an index is returned at random. In our initial trials, this was found
   * to be a suboptimal strategy; it is better to set the batchSize to something like 200.
   * 
   * @return The index to the current SensorShell instance.
   */
  private int getCurrShellIndex() {
    // If batchSize is 0, then we return a shell index chosen randomly.
    if (batchSize == 0) {
      return generator.nextInt(numShells);
    }
    // Now, update the batchCounter and change the currShellIndex if necessary.
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

  /** {@inheritDoc} */
  public boolean ping() {
    return this.shells.get(0).ping();
  }

  /** {@inheritDoc} */
  public int send() throws SensorShellException {
    int totalSent = 0;
    for (int i = 0; i < numShells; i++) {
      totalSent += this.shells.get(i).send();
    }
    return totalSent;
  }

  /** {@inheritDoc} */
  public void quit() {
    for (int i = 0; i < numShells; i++) {
      this.shells.get(i).quit();
    }
  }
  
  /** {@inheritDoc} */
  public void statechange(long resourceCheckSum, Map<String, String> keyValMap) throws Exception {
    // The same SingleSensorShell always has to process statechange events.  
    this.shells.get(0).statechange(resourceCheckSum, keyValMap);
  }
  
  /** {@inheritDoc} */
  public SensorShellProperties getProperties() {
    return this.shells.get(0).getProperties();
  }
}
