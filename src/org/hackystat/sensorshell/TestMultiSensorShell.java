package org.hackystat.sensorshell;

import static org.junit.Assert.assertEquals;
import java.util.HashMap;
import java.util.Map;
import javax.xml.datatype.XMLGregorianCalendar;
import org.hackystat.sensorbase.client.SensorBaseClient;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorDataIndex;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorDataRef;
import org.hackystat.sensorbase.server.Server;
import org.hackystat.sensorbase.server.ServerProperties;
import org.hackystat.utilities.tstamp.Tstamp;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Provides testing classes and a main method for experimenting with MultiSensorShell performance.
 *
 * @author Philip Johnson
 *
 */
public class TestMultiSensorShell {
  
  /** The test user. */
  private static String user = "MultiShell@hackystat.org";
  private static String host;
  private static Server server;
  
  /**
   * Starts the server going for these tests, and makes sure our test user is registered. 
   * @throws Exception If problems occur setting up the server. 
   */
  @BeforeClass public static void setupServer() throws Exception {
    ServerProperties properties = new ServerProperties();
    properties.setTestProperties();
    TestMultiSensorShell.server = Server.newInstance(properties);
    TestMultiSensorShell.host = TestMultiSensorShell.server.getHostName();
    SensorBaseClient.registerUser(host, user);
  }
  
  /**
   * Gets rid of the sent sensor data and the user. 
   * @throws Exception If problems occur setting up the server. 
   */
  @AfterClass public static void teardownServer() throws Exception {
    // Now delete all data sent by this user.
    SensorBaseClient client = new SensorBaseClient(host, user, user);
    // First, delete all sensor data sent by this user. 
    SensorDataIndex index = client.getSensorDataIndex(user);
    for (SensorDataRef ref : index.getSensorDataRef()) {
      client.deleteSensorData(user, ref.getTimestamp());
    }
    // Now delete the user too.
    client.deleteUser(user);
  }
  
  /**
   * Tests that the MultiSensorShell can send data to the Server and this data can be retrieved.
   * @throws Exception If problems occur. 
   */
  @Test public void testMultiSensorShell() throws Exception {
    // First, start up a SensorShell.
    SensorShellProperties properties = new SensorShellProperties(host, user, user);
    // Create a MultiSensorShell with default performance properties. 
    Shell shell = new MultiSensorShell(properties, "Test");
    
    // Now construct a key-val map representing a SensorData instance. 
    Map<String, String> keyValMap = new HashMap<String, String>();
    XMLGregorianCalendar tstamp = Tstamp.makeTimestamp();
    keyValMap.put("Timestamp", tstamp.toString());
    String tool = "Eclipse";
    keyValMap.put("Tool", tool);
    keyValMap.put("SensorDataType", "DevEvent");
    keyValMap.put("DevEvent-Type", "Compile");
    
    // Now add it to the MultiSensorShell and send it to the server. 
    shell.add(keyValMap);
    shell.send();
    shell.ping();
    shell.quit();
    
    // Now retrieve it from the server using a SensorBaseClient.
    SensorBaseClient client = new SensorBaseClient(host, user, user);
    // Now, get the data, dude. 
    SensorData data = client.getSensorData(user, tstamp);
    assertEquals("Checking data", tool, data.getTool());
  }
  
}
