package org.hackystat.sensorshell.usermap;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.hackystat.sensorshell.usermap.resource.jaxb.ObjectFactory;
import org.hackystat.sensorshell.usermap.resource.jaxb.User;
import org.hackystat.sensorshell.usermap.resource.jaxb.Usermap;
import org.hackystat.sensorshell.usermap.resource.jaxb.Usermaps;
import org.hackystat.utilities.home.HackystatUserHome;

/**
 * Gets hackystat information for a specific tool by parsing a predefined xml file located in the
 * user's .hackystat/usermap directory. The file should be named 'UserMaps.xml'. It is important to
 * keep in mind that this code is working client-side, not on the Hackystat server.
 * 
 * <p>
 * Instantiation of <code>UserMap</code> will parse the assumed xml file and derive a mapping from
 * specific tool accounts for users to their Hackystat information.
 * 
 * <p>
 * Developers should not have to directly use this class at all. They should only have to interface
 * with the <code>SensorShellMap</code> class which manages an instance of this class.
 * <p>
 * If the UserMap.xml file does not exist, an empty UserMap is returned.
 * <p>
 * The "tool" string is always compared in a case-insensitive fashion. The User, ToolAccount,
 * Password, and Sensorbase are case-sensitive.
 * 
 * @author Julie Ann Sakuda
 */
class UserMap {
  
  /** Keys in the user map used to access information. */
  enum UserMapKey {
    /** The key for accessing the user value. */
    USER,
    /** The key for accessing the password value. */
    PASSWORD,
    /** The key for accessing the sensorbase value. */
    SENSORBASE
  }

  /** Three-key map for storing mappings for tool, toolaccount, user, password, and sensorbase. */
  private Map<String, Map<String, Map<UserMapKey, String>>> userMappings;
  
  /** The (potentially non-existent) UserMap.xml file. */
  private File userMapFile = null;

  /**
   * Creates the user map and initializes the user mappings.
   * Returns an empty UserMap if the UserMap.xml file cannot be found.
   * 
   * @throws SensorShellMapException Thrown if the UserMap.xml cannot be parsed.
   */
  UserMap() throws SensorShellMapException  {
    this.userMappings = new HashMap<String, Map<String, Map<UserMapKey, String>>>();

    File sensorPropsDir = new File(HackystatUserHome.getHome(), "/.hackystat/sensorshell/");
    String userMapFilePath = sensorPropsDir.getAbsolutePath() + "/usermap/UserMap.xml";
    this.userMapFile = new File(userMapFilePath);
    if (userMapFile.exists()) {
      this.loadUserMapFile(userMapFile);
    }
  }



  /**
   * This constructor initializes the user mappings from a given file. This version of the
   * constructor is mainly useful for junit test cases that want to verify the content of a dummy
   * UserMap.xml file.
   * 
   * @param userMapFile A UserMap.xml file.
   * @exception SensorShellMapException Occurs if UserMap.xml is invalid.
   */
  UserMap(File userMapFile) throws SensorShellMapException {
    this.userMappings = new HashMap<String, Map<String, Map<UserMapKey, String>>>();
    this.loadUserMapFile(userMapFile);
  }

  /**
   * Uses JAXB to read through the UserMap.xml file and add all information to the user map.
   * 
   * @param userMapFile The UserMap.xml file.
   * @throws SensorShellMapException Thrown if JAXB encounters an error reading the xml file.
   */
  private void loadUserMapFile(File userMapFile) throws SensorShellMapException {
    try {
      JAXBContext context = JAXBContext.newInstance(ObjectFactory.class);
      Unmarshaller unmarshaller = context.createUnmarshaller();

      Usermaps usermaps = (Usermaps) unmarshaller.unmarshal(userMapFile);
      List<Usermap> usermapList = usermaps.getUsermap();
      for (Usermap usermap : usermapList) {
        // user lowercase tool for case-insensitive comparison
        String tool = usermap.getTool().toLowerCase();
        List<User> userList = usermap.getUser();
        for (User user : userList) {
          String toolAccount = user.getToolaccount();

          String userName = user.getUser();
          this.put(tool, toolAccount, UserMapKey.USER, userName);

          String password = user.getPassword();
          this.put(tool, toolAccount, UserMapKey.PASSWORD, password);

          String sensorbase = user.getSensorbase();
          this.put(tool, toolAccount, UserMapKey.SENSORBASE, sensorbase);
        }
      }
    }
    catch (JAXBException e) {
      throw new SensorShellMapException("Error reading UserMap.xml file.", e);
    }
  }

  /**
   * Puts a single user map entry for user, password, or sensorbase into the three-key map.
   * 
   * @param tool The tool the mapping is for.
   * @param toolAccount The tool account for the mapping.
   * @param key Either user, password, or sensorbase key.
   * @param value The value associated with the key given.
   */
  private void put(String tool, String toolAccount, UserMapKey key, String value) {
    if (!this.userMappings.containsKey(tool)) {
      this.userMappings.put(tool, new HashMap<String, Map<UserMapKey, String>>());
    }

    Map<String, Map<UserMapKey, String>> toolMapping = this.userMappings.get(tool);
    if (!toolMapping.containsKey(toolAccount)) {
      toolMapping.put(toolAccount, new HashMap<UserMapKey, String>());
    }

    Map<UserMapKey, String> toolAccountMapping = toolMapping.get(toolAccount);
    toolAccountMapping.put(key, value);
  }

  /**
   * Gets the value of the given <code>UserMapKey</code> associated with the given tool and
   * toolAccount.
   * 
   * @param tool The tool name.
   * @param toolAccount The tool account name.
   * @param key The USER, PASSWORD, or SENSORBASE key representing the desired value.
   * @return Returns the value matching the criteria given or null if none can be found.
   */
  String get(String tool, String toolAccount, UserMapKey key) {
    if (tool == null) {
      return null;
    }
    String lowercasetool = tool.toLowerCase();
    if (this.userMappings.containsKey(lowercasetool)) {
      Map<String, Map<UserMapKey, String>> toolMapping = this.userMappings.get(lowercasetool);
      if (toolMapping.containsKey(toolAccount)) {
        Map<UserMapKey, String> toolAccountMapping = toolMapping.get(toolAccount);
        return toolAccountMapping.get(key);
      }
    }
    return null;
  }

  /**
   * Returns true if there is a defined userKey for the given Tool and ToolAccount.
   * 
   * @param tool A Tool, such as "Jira".
   * @param toolAccount A Tool account, such as "johnson".
   * @return True if the toolAccount is defined for the given tool in this userMap.
   */
  boolean hasUser(String tool, String toolAccount) {
    // Lowercase the tool if possible.
    if (tool == null) {
      return false;
    }
    String lowercaseTool = tool.toLowerCase();
    if (this.userMappings.containsKey(lowercaseTool)) {
      Map<String, Map<UserMapKey, String>> toolMapping = this.userMappings.get(lowercaseTool);
      return toolMapping.containsKey(toolAccount);
    }
    return false;
  }
  
  /**
   * Returns the usermap.xml file path, which may or may not exist.
   * @return The usermap.xml file path.
   */
  String getUserMapFile() {
    return this.userMapFile.getAbsolutePath();
  }


  /**
   * Returns the set of tool account names for the passed tool.
   * @param tool The tool of interest.
   * @return The tool account names. 
   */
  Set<String> getToolAccounts(String tool) {
    String lowerCaseTool = tool.toLowerCase();
    Set<String> toolAccounts = new HashSet<String>();
    Map<String, Map<UserMapKey, String>> toolMapping = this.userMappings.get(lowerCaseTool);
    if (toolMapping == null) {
      return toolAccounts;
    }
    else {
      toolAccounts.addAll(toolMapping.keySet());
      return toolAccounts;
    }
  }
}

