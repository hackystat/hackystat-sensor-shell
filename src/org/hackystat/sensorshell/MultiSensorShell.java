package org.hackystat.sensorshell;

import java.util.ArrayList;
import java.util.Date;
import org.hackystat.sensorbase.resource.sensordata.jaxb.Properties;
import org.hackystat.sensorbase.resource.sensordata.jaxb.Property;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;
import org.hackystat.utilities.tstamp.Tstamp;

/**
 * MultiSensorShell is an experimental class designed to explore performance optimization of 
 * sensor data transmission.  MultiSensorShell is designed to explore the premise that one way to
 * improve the throughput of sensor data transmission is to create multiple SensorShells that 
 * are all sending data to a single SensorBase simultaneously.
 * <p> 
 * When instantiating a MultiSensorShell, you specify the number of SensorShells to be created, the
 * number of SensorData instances to be sent to a single SensorShell before moving on to the next
 * SensorShell (the "batchSize"), and the autoSendInterval for each SensorShell. Since the 
 * AutoSend facility in SensorShell creates a separate thread, this enables a simple form of
 * concurrency: it is possible for one SensorShell to be sending its data while the MultiSensorShell
 * is adding data to another SensorShell. 
 * <p>
 * The main() method provides a simple experimental interface.  To use it, bring up a SensorBase.
 * Then invoke the main() method, editing the initial local variables as you wish. The system
 * will run, printing out the elapsed time in milliseconds required for each add() of an 
 * individual SensorData instance.  At the end of the run, it will print out the total elapsed
 * time and the average time to send each SensorData instance to the server.  Note that I have
 * seen a variance of around 0.5 milliseconds in the average time per instance send. So, for
 * example, when repeatedly running under the same settings, I have obtained values 
 * varying between 3.1 to about 3.7 milliseconds per instance.  This is with totalData = 5000.
 * It appears that the variance decreases if you increase totalData to about 50,000. At that 
 * point, I get relatively consistent throughput of around 2.8 milliseconds per instance. 
 * <p>
 * If you instantiate a MultiSensorShell with the number of SensorShells set to 1, you effectively
 * get the default case.  In this situation, I have found that the average time to send a 
 * single SensorData instance is approximately 6 milliseconds, almost independent of the 
 * settings for batchSize and the autoSendInterval.   By increasing the number of SensorShell
 * instances to 5, I can double the throughput, to approximately 3 milliseconds per instance. At
 * this point, some kind of performance plateau is reached, and I have not yet been able to 
 * improve the performance further via manipulation of these variables.  I do not know whether
 * this is a "real" limit or an artificial limit imposed by my running both client and server
 * on the same system (a MacBook Pro). 
 * <p>
 * My best throughput has been achieved with totalData = 50,000; numShells = 10; autoSendInterval
 * = 0.05; and batchSize = 200. Under these conditions, I get 2.8 milliseconds per instance, or 
 * around 360 instances per second throughput.  I've done a run with totalData = 500,000 without
 * problems and with a sustained throughput of 2.8 milliseconds/instance.   
 * <p>
 * I'm also finding I can store around 350,000 sensor data instances per GB of disk space. 
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
   * Creates a new MultiSensorShell for concurrent transmission of SensorData instances to a 
   * SensorBase. 
   * @param properties A SensorProperties instance. 
   * @param autoSendTimeInterval The time in minutes between autoSends for each shell.
   * @param numShells The total number of SensorShell instances to create. 
   * @param batchSize The number of SensorData instances to send in a row to a single SensorShell.
   * @throws Exception If problems occur. 
   */
  public MultiSensorShell(SensorProperties properties, double autoSendTimeInterval, int numShells, 
      int batchSize) 
  throws Exception {
    this.shells = new ArrayList<SensorShell>();
    this.numShells = numShells;
    this.batchSize = batchSize;
    for (int i = 0; i < numShells; i++) {
      SensorShell shell = new SensorShell(properties, false);
      shell.setAutoSendTimeInterval(autoSendTimeInterval);
      shell.setAutoSendBufferSize(1000);
      this.shells.add(shell);
    }
  }
  
  /**
   * Adds the passed SensorData instance to one of the internal SensorShells. 
   * @param sensorData The SensorData instance to be queued for transmission. 
   */
  public void add(SensorData sensorData) {
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
    // Now that batchCounter and currShellIndex have the appropriate values, do the add.
    this.shells.get(currShellIndex).add(sensorData);
  }
  
  /**
   * Invokes send() on all of the internal SensorShells. Call this at the end of a run to 
   * ensure that all of the buffered data has been sent. 
   */
  public void send() {
    for (int i = 0; i < numShells; i++) {
      this.shells.get(i).send();
    }
  }
  
  /**
   * Creates a sample SensorData instance given a user. 
   * @param user The user.
   * @return The new SensorData instance. 
   */
  private static SensorData makeSensorData(String user) {
    String sdt = "TestSdt";
    SensorData data = new SensorData();
    String tool = "Subversion";
    data.setTool(tool);
    data.setOwner(user);
    data.setSensorDataType(sdt);
    data.setTimestamp(Tstamp.makeTimestamp());
    data.setResource("file://foo/bar/baz.txt");
    data.setRuntime(Tstamp.makeTimestamp());
    Property property = new Property();
    property.setKey("SampleField");
    property.setValue("The test value for Sample Field");
    Properties properties = new Properties();
    properties.getProperty().add(property);
    data.setProperties(properties);
    return data;
  }
  
  /**
   * A simple main class that illustrates how to invoke the MultiSensorShell to explore 
   * performance improvements related to throughput of SensorData instances.  The idea is 
   * bring up a local SensorBase, edit the local variables of this method, then run this 
   * main() to see how fast you can transmit the SensorData to the SensorBase. 
   * @param args Ignored.
   * @throws Exception If things go wrong. 
   */
  public static void main(String[] args) throws Exception {
    long totalData = 50000;
    int numShells = 10;
    double autoSendTimeInterval = 0.05;
    int batchSize = 200;
    String host = "http://localhost:9876/sensorbase";
    String user = "TestUser@hackystat.org";
    SensorProperties properties = new SensorProperties(host, user, user);
    MultiSensorShell multiShell = 
      new MultiSensorShell(properties, autoSendTimeInterval, numShells, batchSize);
    Date startTime = new Date();
    for (int i = 0; i < totalData; i++) {
      long time1 = new Date().getTime();
      SensorData data = makeSensorData(user);
      multiShell.add(data);
      long time2 = new Date().getTime();
      System.out.println("Elapsed millis for add: " + (time2 - time1));
    }
    multiShell.send();
    long totalTime = (new Date().getTime() - startTime.getTime());
    double timePerData = (double)totalTime / (double)totalData;
    System.out.println("Total time: " + totalTime + " time/data: " + timePerData);

  }
}

