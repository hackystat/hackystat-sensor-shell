package org.hackystat.sensorshell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.util.Properties;

import org.junit.Test;

/**
 * Provides simple unit tests for SensorShellProperties.
 * @author Aaron A. Kagawa, Philip Johnson
 */
public class TestSensorShellProperties {
  
  /** The test user. */
  private static String user = "TestShellUser@hackystat.org";
  private static String host = "http://TestHost/";
  
  /**
   * Tests SensorProperties class when using the 'test' constructor. 
   * @throws Exception If problems occur. 
   */
  @Test public void testDefaultSensorProperties() throws Exception {
    // Create a 'testing' version, that does not read sensorshell.properties and that disables
    // caching and logging.
    SensorShellProperties properties = SensorShellProperties.getTestInstance(host, user, user);

    assertEquals("Check host", TestSensorShellProperties.host, properties.getSensorBaseHost());
    assertEquals("Check email", TestSensorShellProperties.user, properties.getSensorBaseUser());
    assertEquals("Check pass", TestSensorShellProperties.user, properties.getSensorBasePassword());
    assertEquals("Check auto send time interval", 1.0, properties.getAutoSendTimeInterval(), 0.01);
    assertEquals("Check auto send buffer size", 250, properties.getAutoSendMaxBuffer());
    assertEquals("Check state change interval", 30, properties.getStateChangeInterval());
    assertFalse("Check multishell enabled", properties.isMultiShellEnabled());
    assertEquals("Check multishell numshells", 10, properties.getMultiShellNumShells());
    assertEquals("Check multishell batch size", 499, properties.getMultiShellBatchSize());
    assertFalse("Check offline caching", properties.isOfflineCacheEnabled());
    assertFalse("Check offline recovery", properties.isOfflineRecoveryEnabled());
    assertEquals("Check timeout", 10, properties.getTimeout());
    assertEquals("Check logging level", "OFF", properties.getLoggingLevel().getName());
  }
  
  /**
   * Tests SensorShellProperties class when using the Properties-based constructor.
   * @throws Exception If problems occur. 
   */
  @Test public void testPropertiesConstructor() throws Exception {
    Properties props = new Properties();
    props.put(SensorShellProperties.SENSORSHELL_SENSORBASE_HOST_KEY, host);
    props.put(SensorShellProperties.SENSORSHELL_SENSORBASE_USER_KEY, user);
    props.put(SensorShellProperties.SENSORSHELL_SENSORBASE_PASSWORD_KEY, user);
    props.put(SensorShellProperties.SENSORSHELL_TIMEOUT_KEY, "5");
    // Use 'true' to force the timeout value specified above to override that property value
    // if specified in the local sensorshell.properties file. 
    SensorShellProperties properties = new SensorShellProperties(props, true);

    assertEquals("Check host", TestSensorShellProperties.host, properties.getSensorBaseHost());
    assertEquals("Check email", TestSensorShellProperties.user, properties.getSensorBaseUser());
    assertEquals("Check timeout", 5, properties.getTimeout());
  }
  
  
  
  /**
   * Tests basic functions of an invalid creation of a SensorProperties instance.
   * @throws SensorShellException If problems occur. 
   */
  @Test(expected = SensorShellException.class)
  public void testNullSensorProperties() throws SensorShellException { //NOPMD
    new SensorShellProperties(null);
  }
  
  /**
   * Tests basic functions of an invalid creation of a SensorProperties instance.
   * @throws SensorShellException If problems occur. 
   */
  @Test(expected = SensorShellException.class)
  public void testInvalidSensorProperties() throws SensorShellException { //NOPMD
    new SensorShellProperties(new File("foobarbasbuz.properties"));
  }

  

}
