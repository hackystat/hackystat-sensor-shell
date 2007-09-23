package org.hackystat.sensorshell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;

import org.junit.Test;

/**
 * Provides simple unit tests for the SensorProperties.
 * @author Aaron A. Kagawa
 */
public class TestSensorProperties {
  
  /** The test user. */
  private static String user = "TestShellUser@hackystat.org";
  private static String host = "TestHost";
  
  /**
   * Tests basic functions of the SensorProperties class.
   * @throws Exception If problems occur. 
   */
  @Test public void testSensorProperties() throws Exception {
    SensorProperties properties = new SensorProperties(host, user, user);
    // notice the difference between these two results.
    assertEquals("Checking hackystat host", TestSensorProperties.host + "/", 
        properties.getHackystatHost());
    // how is anyone supposed to know to use HACKYSTAT_SENSORBASE_HOST? 
    assertEquals("Checking hackystat host", TestSensorProperties.host, 
        properties.getProperty("HACKYSTAT_SENSORBASE_HOST"));
    
    assertEquals("Checking email", TestSensorProperties.user, 
        properties.getEmail());
    assertEquals("Checking password", TestSensorProperties.user, 
        properties.getPassword());
    assertEquals("Checking auto send time interval", "10", properties.getAutoSendTimeInterval());
    assertEquals("Checking auto send buffer size", "250", properties.getAutoSendBufferSize());
    assertEquals("Checking state change interval", 30, properties.getStateChangeInterval());
    
    //note that there are a few untestable pieces of code. for example, it is impossible to 
    //test the default values for Hackystat host, email, and password. It is also impossible to 
    //test setting of the AutoSendInterval and StateChangeInterval properties.
  }
  
  /**
   * Tests basic functions of an invalid creation of a SensorProperties instance.
   * @throws Exception If problems occur. 
   */
  @Test public void testInvalidSensorProperties() throws Exception {
    try {
      new SensorProperties(null);
      fail("Should throw an exception");
    }
    catch (SensorPropertiesException e) {
      assertEquals("Checking the exception message", "Invalid sensor properties file", 
          e.getMessage());
    }
    try {
      new SensorProperties(new File("shoulnd't exist file.wrong extension"));
      fail("Should throw an exception");
    }
    catch (SensorPropertiesException e) {
      assertNotNull("Checking the exception message", e.getMessage());
    }
    
  }

  

}
