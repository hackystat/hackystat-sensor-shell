package org.hackystat.sensorshell.usermap;

import java.io.File;

import junit.framework.TestCase;

import org.hackystat.sensorshell.SensorShell;

/**
 * Tests the SensorShellMap class. It ensures that instantiation and use can be done correctly. A
 * dummy UserMap.xml is inputted for instantiation and a dummy user SensorShell is gotten using the
 * SensorShellMap functionality.
 * 
 * @author Julie Ann Sakuda
 */
public class TestSensorShellMap extends TestCase {

  private String tool = "test";
  private String toolAccount = "dummyaccount";

  /**
   * Tests usage of the SensorShellMap class.
   * 
   * @throws Exception if errors occur.
   */
  public void testUserShell() throws Exception {
    SensorShellMap sensorShellMap = new SensorShellMap(this.tool, new File(System
        .getProperty("usermaptestfile")));

    // Test that the tool account exists
    assertTrue("Tool account dummyaccount should exist", sensorShellMap.hasUserShell(toolAccount));
    
    // Test for successful acquisition of user's SensorShell instance
    SensorShell userShell = sensorShellMap.getUserShell(this.toolAccount);
    assertNotNull("Checking successful acquisition of user SensorShell", userShell);
    
    // Test that only one instance per user is created
    assertSame("Should only be creating one sensorshell instance", userShell, sensorShellMap
        .getUserShell(toolAccount));
  }
}
