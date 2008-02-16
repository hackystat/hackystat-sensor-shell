package org.hackystat.sensorshell;

import java.io.File;
import java.util.Date;

import org.hackystat.sensorbase.client.SensorBaseClient;
import org.hackystat.sensorbase.resource.sensordata.jaxb.Properties;
import org.hackystat.sensorbase.resource.sensordata.jaxb.Property;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;
import org.hackystat.utilities.tstamp.Tstamp;

/**
 * A command line tool that facilitates performance evaluation and tuning of sensor data
 * transmission. To use this tool, invoke:
 * 
 * <pre>
 * java -jar shellperfeval.jar  [numInstancesToSend] 
 * </pre>
 * 
 * With a sensorshell.properties file located in the same directory as this jar file.
 * <p>
 * This results in the following:
 * <ul>
 * <li> The sensorshell.properties file is read in and used to initialize a SensorShell, except that
 * the user and password fields are not used. Instead, a test user called
 * "ShellPerfEval@hackystat.org" is created and used instead.
 * <li> Offline caching and recovery are also disabled. 
 * These properties are printed out.
 * <li> The number provided on the command line is read and used to generate and send that many
 * Sensor Data instances to the server. The number of milliseconds required to send each request is
 * printed out, 50 per line.
 * <li> Once all of the instances have been sent to the server, summary statistics are printed out,
 * and all of the data associated with this test user is deleted.
 * </ul>
 * 
 * @author Philip Johnson
 * 
 */
public class ShellPerfEval {

  /**
   * Provide a sensorshell.properties file in the same directory as the location of the jar file
   * invoking this program. Invoke it with one argument, the number of instances to send.
   * 
   * @param args One argument, the number of instances to send.
   * @throws Exception If things go wrong.
   */
  public static void main(String[] args) throws Exception {
    // Get the number of instances, or exit if failure.
    int numInstances = 0;
    try {
      numInstances = Integer.parseInt(args[0]);
    }
    catch (Exception e) {
      System.out.println("java -jar shellperfeval [numInstancesToSend]");
      return;
    }
    // Get the sensorshell.properties file, or exit if failure.
    File propsFile = new File(System.getProperty("user.dir"), "sensorshell.properties");
    if (!propsFile.exists()) {
      System.out.println("sensorshell.properties file must be in this directory.");
      return;
    }

    String user = "ShellPerfEval@hackystat.org";

    // Create our SensorShellProperties with the overridden user and password.
    SensorShellProperties origProps = new SensorShellProperties(propsFile);
    java.util.Properties props = new java.util.Properties();
    props.setProperty(SensorShellProperties.SENSORSHELL_SENSORBASE_USER_KEY, user);
    props.setProperty(SensorShellProperties.SENSORSHELL_SENSORBASE_PASSWORD_KEY, user);
    props.setProperty(SensorShellProperties.SENSORSHELL_OFFLINE_CACHE_ENABLED_KEY, "false");
    props.setProperty(SensorShellProperties.SENSORSHELL_OFFLINE_RECOVERY_ENABLED_KEY, "false");
    SensorShellProperties shellProps = new SensorShellProperties(origProps, props);
    System.out.println(shellProps.toString());

    // Instantiate the sensorshell.
    SensorShell shell = new SensorShell(shellProps, false, "ShellPerfEval");

    // Make sure the user remembers to get the SensorBase running. :-)
    if (!shell.ping()) {
      System.out.println("Error: Could not contact: " + shell.getProperties().getSensorBaseHost());
    }

    // Register our user.
    SensorBaseClient.registerUser(shellProps.getSensorBaseHost(), user);

    // Now do the run.
    Date startTime = new Date();
    System.out.println("Number of milliseconds for each 'add' command, 50 per line:");
    for (long i = 0; i < numInstances; i++) {
      long time1 = new Date().getTime();
      SensorData data = makeSensorData(user, time1 + (i * 10));
      shell.add(data);
      long time2 = new Date().getTime();
      if ((i > 0) && ((i % 50) == 0)) {
        System.out.println();
      }
      if ((i > 0) && ((i % 1000) == 0)) {
        System.out.println("Finished: " + i + " out of " + numInstances);
      }
      System.out.print((time2 - time1) + " ");
    }

    // Make sure the user remembers to get the SensorBase running. :-)
    if (!shell.ping()) {
      System.out.println("Error: Could not contact: " + shell.getProperties().getSensorBaseHost());
      // return;
    }
    System.out.println();
    // Make sure all data has been sent.
    shell.quit();
    long totalTime = (new Date().getTime() - startTime.getTime());
    System.out.println("Total time: " + totalTime + " milliseconds.");
    double timePerData = (double) totalTime / (double) numInstances;
    System.out.println("Milliseconds/sensordata instance: " + timePerData);
    SensorBaseClient client = new SensorBaseClient(shellProps.getSensorBaseHost(), user, user);
    System.out.print("Deleting data from sensorbase...");
    client.deleteSensorData(user);
    System.out.println(" done.");
  }

  /**
   * Creates a sample SensorData instance given a user.
   * 
   * @param user The user.
   * @param utc The timestamp to be used for this instance.
   * @return The new SensorData instance.
   */
  private static SensorData makeSensorData(String user, long utc) {
    String sdt = "TestSdt";
    SensorData data = new SensorData();
    String tool = "Subversion";
    data.setTool(tool);
    data.setOwner(user);
    data.setSensorDataType(sdt);
    data.setTimestamp(Tstamp.makeTimestamp(utc));
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

}
