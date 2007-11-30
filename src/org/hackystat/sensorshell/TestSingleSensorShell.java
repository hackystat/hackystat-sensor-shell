package org.hackystat.sensorshell;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import javax.xml.datatype.XMLGregorianCalendar;

import org.hackystat.sensorbase.client.SensorBaseClient;
import org.hackystat.utilities.tstamp.Tstamp;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorDataIndex;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorDataRef;
import org.hackystat.sensorbase.server.Server;
import org.hackystat.sensorbase.server.ServerProperties;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Provides simple unit tests for the SensorShell.
 * @author Philip Johnson
 */
public class TestSingleSensorShell {
  
  /** The test user. */
  private static String user = "TestShellUser@hackystat.org";
  private static String host;
  private static Server server;
  
  /**
   * Starts the server going for these tests, and makes sure our test user is registered. 
   * @throws Exception If problems occur setting up the server. 
   */
  @BeforeClass public static void setupServer() throws Exception {
    ServerProperties properties = new ServerProperties();
    properties.setTestProperties();
    TestSingleSensorShell.server = Server.newInstance(properties);
    TestSingleSensorShell.host = TestSingleSensorShell.server.getHostName();
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
   * Tests that the SensorShell can send data to the Server and this data can be retrieved.
   * @throws Exception If problems occur. 
   */
  @Test public void testSensorShell() throws Exception {
    // First, create a "test" SensorShellProperties with disabled caching and logging.
    SensorShellProperties properties = new SensorShellProperties(host, user, user);
    // Create a SensorShell that is non-interactive, logging as "Test".
    SingleSensorShell shell = new SingleSensorShell(properties, false, "Test");
    
    // Now construct a key-val map representing a SensorData instance. 
    Map<String, String> keyValMap = new HashMap<String, String>();
    XMLGregorianCalendar tstamp = Tstamp.makeTimestamp();
    keyValMap.put("Timestamp", tstamp.toString());
    String tool = "Eclipse";
    keyValMap.put("Tool", tool);
    keyValMap.put("SensorDataType", "DevEvent");
    keyValMap.put("DevEvent-Type", "Compile");
    
    // Now add it to the SensorShell and send it to the server. 
    shell.add(keyValMap);
    int numSent = shell.send();
    assertEquals("Checking numSent", 1, numSent);
    shell.ping();
    shell.quit();
    
    // Now retrieve it from the server using a SensorBaseClient.
    SensorBaseClient client = new SensorBaseClient(host, user, user);
    // Now, get the data, dude. 
    SensorData data = client.getSensorData(user, tstamp);
    assertEquals("Checking data", tool, data.getTool());
  }
  
  /**
   * Tests that statechange works correctly. 
   * @throws Exception If problems occur.
   */
  @Test public void testStateChange () throws Exception {
    // First, create a "test" SensorShellProperties.
    SensorShellProperties properties = new SensorShellProperties(host, user, user);
    // Create a SensorShell that is non-interactive, logging as "Test".
    SingleSensorShell shell = new SingleSensorShell(properties, true, "Test");
    
    // Now construct a key-val map representing a SensorData instance. 
    Map<String, String> keyValMap = new HashMap<String, String>();
    XMLGregorianCalendar tstamp = Tstamp.makeTimestamp();
    keyValMap.put("Timestamp", tstamp.toString());
    String tool = "Eclipse";
    keyValMap.put("Tool", tool);
    keyValMap.put("SensorDataType", "DevEvent");
    keyValMap.put("DevEvent-Type", "Compile");
    keyValMap.put("Resource", "file://foo.java");
    
    // Now test to see that StateChange works correctly.
    shell.statechange(100L, keyValMap);  // should create an add.
    shell.statechange(100L, keyValMap);  // should not create an add.
    shell.statechange(200L, keyValMap); // should create an add.
    keyValMap.put("Resource", "file://bar.java");
    shell.statechange(200L, keyValMap); // should create an add.
    int numSensorData = shell.send();
    assertEquals("Checking numSensorData", 3, numSensorData);
  }

}
