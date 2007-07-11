package org.hackystat.sensorshell;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import javax.xml.datatype.XMLGregorianCalendar;

import org.hackystat.sensorbase.client.SensorBaseClient;
import org.hackystat.sensorbase.resource.sensordata.Tstamp;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;
import org.hackystat.sensorbase.server.Server;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Provides simple unit tests for the SensorShell.
 * @author Philip Johnson
 */
public class TestSensorShell {
  
  /** The test user. */
  private static String user = "TestShellUser@hackystat.org";
  private static String host;
  private static Server server;
  
  /**
   * Starts the server going for these tests, and makes sure our test user is registered. 
   * @throws Exception If problems occur setting up the server. 
   */
  @BeforeClass public static void setupServer() throws Exception {
    TestSensorShell.server = Server.newInstance();
    TestSensorShell.host = TestSensorShell.server.getHostName();
    SensorBaseClient.registerUser(host, user);
  }
  
  /**
   * Tests that the SensorShell can send data to the Server and this data can be retrieved.
   * @throws Exception If problems occur. 
   */
  @Test public void testSensorShell() throws Exception {
    // First, start up a SensorShell.
    SensorProperties properties = new SensorProperties(host, user, user);
    // Create a SensorShell that is non-interactive, logging as "Test", and no offline storage. 
    SensorShell shell = new SensorShell(properties, false, "Test", false);
    
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
    shell.send();
    shell.ping();
    shell.setAutoSend(1);
    shell.quit();
    
    // Now retrieve it from the server using a SensorBaseClient.
    SensorBaseClient client = new SensorBaseClient(host, user, user);
    // Now, get the data, dude. 
    SensorData data = client.getSensorData(user, tstamp);
    assertEquals("Checking data", tool, data.getTool());
    // Now delete this data and the user for good measure.  
    client.deleteSensorData(user, tstamp);
    client.deleteUser(user);
  }

}
