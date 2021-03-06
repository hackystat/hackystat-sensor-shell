package org.hackystat.sensorshell.usermap;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.hackystat.sensorshell.SensorShell;
import org.hackystat.sensorshell.SensorShellProperties;
import org.hackystat.sensorshell.usermap.UserMap.UserMapKey;

/**
 * Allows for lazy instantiation of SensorShells for user tool accounts. This class abstracts the
 * use of the UserMap class so that sensors for applicable tool sensors won't have to manage it.
 * <p>
 * The <code>SensorShellMap</code> is meant to only derive SensorShells for a specific tool.
 * Queries for SensorShells are done by giving an account name to the <code>getUserShell</code>
 * method. This account name must be specific to the tool of the <code>SensorShellMap</code>
 * instance.
 * <p>
 * Note that the "tool" name is used in a case-insensitive fashion.
 * 
 * @author Burt Leung, Julie Ann Sakuda (v8 port)
 */
public class SensorShellMap {

  /** The tool that this SensorShellMap will return SensorShells for. */
  private String tool;

  /** Mapping of Tool specific user accounts to their Hackystat information. */
  private UserMap userMap;

  /** Map of Tool specific tool accounts to their SensorShells. */
  private Map<String, SensorShell> toolAccountsToShells = new HashMap<String, SensorShell>();

  /**
   * Instantiate this class which initializes the UserMap used to get SensorShells for users and
   * also sets the Hackystat host for the SensorShells.
   * 
   * @param tool The specific tool that this SensorShellMap will provide SensorShells for.
   * @throws SensorShellMapException Indicates that the usermaps.xml file is not found.
   */
  public SensorShellMap(String tool) throws SensorShellMapException {
    // Ensure valid argument
    if (tool == null || tool.length() == 0) {
      throw new SensorShellMapException("Error: tool is null or empty string.");
    }
    this.tool = tool.trim();
    this.userMap = new UserMap();
  }

  /**
   * A package private constructor meant to be used only by Junit test cases.
   * 
   * @param tool The specific tool that this SensorShellMap will provide SensorShells for.
   * @param userMapFile The UserMap.xml file.
   * @throws SensorShellMapException Indicates that the UserMap.xml file is not found.
   */
  public SensorShellMap(String tool, File userMapFile)
      throws SensorShellMapException {
    this.tool = tool;
    this.userMap = new UserMap(userMapFile);
  }

  /**
   * Returns true if toolAccount is known so that a corresponding SensorShell can be retrieved.
   * 
   * @param toolAccount The toolAccount for the tool in this instance, such as "johnson".
   * @return True if toolAccount is known, otherwise false.
   */
  public boolean hasUserShell(String toolAccount) {
    if (toolAccount == null || toolAccount.length() == 0) {
      return false;
    }
    else {
      return this.userMap.hasUser(this.tool, toolAccount.trim());
    }
  }
  
  /**
   * Returns the set of tool account names associated with this tool in the usermaps file.
   * @param tool The tool of interest.
   * @return The known tool account names for this tool.
   */
  public Set<String> getToolAccounts(String tool) {
    return this.userMap.getToolAccounts(tool);
  }
  
  /**
   * Returns the UserMap file name associated with this SensorShellMap.
   * This file may or may not exist.
   * @return The usermap file name associated with this SensorShellMap. 
   */
  public String getUserMapFile() {
    return this.userMap.getUserMapFile();
  }
  
  /**
   * Validates the hackystat user, password, and sensorbase associated with tool. 
   * Throws an exception if the sensorbase cannot be contacted and/or if the user/password
   * combination is not valid for the associated sensorbase. 
   * @param tool The tool of interest. 
   * @throws SensorShellMapException Thrown if user map info cannot be validated. 
   */
  public void validateHackystatInfo(String tool) throws SensorShellMapException {
    this.userMap.validateHackystatUsers(tool);
  }

  /**
   * Gets the SensorShell instance for a Hackystat user with an account for the given tool. Assumes
   * that toolAccount is known and will map to a userKey; use hasUserShell() to check whether this
   * is true before calling this method. Instantiates the SensorShell instance if it is not yet
   * available.
   * 
   * @param toolAccount The name of the account for the given tool. This is not the same as the
   *          Hackystat account name although it may be the same.
   * @return The SensorShell instance for the specific Hackystat user.
   * @throws SensorShellMapException When the user account and/or tool is undefined.
   */
  public SensorShell getUserShell(String toolAccount) throws SensorShellMapException {
   return getUserShell(toolAccount, null);
  }
  
  /**
   * Gets the SensorShell instance for a Hackystat user with an account for the given tool. Assumes
   * that toolAccount is known and will map to a userKey; use hasUserShell() to check whether this
   * is true before calling this method. Instantiates the SensorShell instance if it is not yet
   * available.
   * 
   * @param toolAccount The name of the account for the given tool. This is not the same as the
   *          Hackystat account name although it may be the same.
   * @param properties A properties instance holding SensorShell properties to be used to 
   * initialize the underlying SensorShell if it requires instantiation. If null, then no 
   * additional properties are required.          
   * @return The SensorShell instance for the specific Hackystat user.
   * @throws SensorShellMapException When the user account and/or tool is undefined.
   */
  public SensorShell getUserShell(String toolAccount, Properties properties) 
  throws SensorShellMapException {
    // Check argument that it is valid
    if (toolAccount == null || toolAccount.length() == 0) {
      throw new SensorShellMapException("Error: toolAccount is null or the empty string.");
    }
    String trimmedToolAccount = toolAccount.trim();
    try {
      String user = this.userMap.get(this.tool, trimmedToolAccount, UserMapKey.USER);
      String password = this.userMap.get(this.tool, trimmedToolAccount, UserMapKey.PASSWORD);
      String host = this.userMap.get(this.tool, trimmedToolAccount, UserMapKey.SENSORBASE);
      // If we haven't instantiated this shell yet, then do it now.
      if (this.toolAccountsToShells.get(toolAccount) == null) {
        // First, build the SensorShellProperties instance. 
        // If properties is null, then call the three arg constructor. otherwise pass them in.
        // Note: properties do not override user preferences as set in sensorshell.properties.
        SensorShellProperties sensorProps = (properties == null) ? 
            new SensorShellProperties(host, user, password) :
              new SensorShellProperties(host, user, password, properties, false) ;
        // Now, instantiate the non-interactive SensorShell.
        SensorShell userShell = new SensorShell(sensorProps, false, this.tool + "-" + user);
        this.toolAccountsToShells.put(trimmedToolAccount, userShell);
      }
      // Now return the SensorShell.
      return this.toolAccountsToShells.get(trimmedToolAccount);
    }
    catch (Exception e) {
      throw new SensorShellMapException("Error getting SensorShell for " + toolAccount, e);
    }
  }
}