package org.hackystat.sensorshell.usermap;

import java.io.File;
import java.util.Locale;

import org.hackystat.sensorshell.usermap.UserMap.UserMapKey;

import junit.framework.TestCase;

/**
 * A test case class to test the UserMap class. It makes sure that instantiation, and therefore
 * formatting of the UserMap.xml file, is done correctly. It also ensures that it correctly provides
 * its core functionality by testing to see that it gives back a dummy Hackystat value for a given
 * dummy tool account.
 * 
 * @author Julie Ann Sakuda
 */
public class TestUserMap extends TestCase {

  private String tool = "test";
  private String toolAccount = "dummyaccount";
  private String user = "test@hackystat.org";
  private String password = "test@hackystat.org";
  private String sensorbase = "http://localhost/";

  /**
   * Test the UserMap class and the formatting of the UserMap.xml.
   * 
   * @throws Exception if errors occur.
   */
  public void testMap() throws Exception {
    UserMap userMap = null;
    userMap = new UserMap(new File(System.getProperty("usermaptestfile")));
    assertNotNull("Checking that the UserMap is not null", userMap);
    assertEquals("Check user", user, userMap.get(tool, toolAccount, UserMapKey.USER));
    assertEquals("Check password", password, userMap.get(tool, toolAccount, UserMapKey.PASSWORD));
    assertEquals("Check sensorbase", sensorbase, userMap.get(tool, toolAccount,
        UserMapKey.SENSORBASE));
  }
  
  /** 
   * Test that the tool and toolaccount in the user map are case-insensitive on retrieval.
   * 
   *  @throws Exception if an error occurs.
   */
  public void testCaseInsensitiveRetrievals() throws Exception {
    UserMap userMap = null;
    userMap = new UserMap(new File(System.getProperty("usermaptestfile")));
    assertNotNull("Checking that the UserMap is not null", userMap);
    assertNotNull("Checking get 1", userMap.get(tool, toolAccount, UserMapKey.USER));
    assertNotNull("Checking get 2", 
        userMap.get(tool.toUpperCase(Locale.ENGLISH), toolAccount, UserMapKey.USER));
    assertNotNull("Checking get 3", 
        userMap.get(tool, toolAccount.toUpperCase(Locale.ENGLISH), UserMapKey.USER));
  }
}
