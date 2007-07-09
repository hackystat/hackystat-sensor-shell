package org.hackystat.sensorshell;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * Provides access to the hackystat properties file for each sensor, and provides reasonable default
 * values when properties file is missing, cannot be read, or lacks property values. <p>
 *
 * The sensor properties file is stored in userhome/.hackystat/sensor.properties. It is a
 * user-maintained file of key=value pairs. All values are trim()'d to prevent whitespace from
 * introducing bugs. <p>
 * 
 * All of the properties in sensor.properties are added to the System property instance after being
 * read from the file.  This enables other tools that interoperate with Hackystat to obtain 
 * settings from the System instance and/or environments to configure System property settings
 * (such as for SSL) simply by adding entries to the properties file. 
 *
 * @author    Philip M. Johnson
 */
public class SensorProperties {

  /** Hackystat host. */
  private static final String HOST_KEY = "HACKYSTAT_HOST";
  /** User's email. */
  private static final String EMAIL_KEY = "HACKYSTAT_EMAIL";
  /** User's password. */
  private static final String PASSWORD_KEY = "HACKYSTAT_PASSWORD";
  /** Auto send interval.  */
  private static final String AUTOSEND_KEY = "HACKYSTAT_AUTOSEND_INTERVAL";
  /** State change interval. */
  private static final String STATECHANGE_KEY = "HACKYSTAT_STATE_CHANGE_INTERVAL";

  /** The standard location of the sensor properties file. */
  private File sensorFile;
  /** The sensor type creating this instance. */
  private String sensorType = "";
  /** Whether the properties file exists and was successfully read. */
  private boolean fileAvailable = false;
  /** The internal properties object. */
  private Properties sensorProps = new Properties();
  /** Indicates whether this sensor is active or not. */
  private boolean sensorEnabled = false;


  /**
   * Initializes access to the Hackystat Sensor settings stored in the user's sensor.properties
   * file.
   *
   * @param sensorType  The type of sensor requesting information. For example, "JUnit", "Emacs",
   *      etc. The string is uppercased internally so that case doesn't matter.
   */
  public SensorProperties(String sensorType) {
    this(sensorType.toUpperCase(), new File(System.getProperty("user.home") +
        "/.hackystat/v8.sensor.properties"));
  }

  /**
   * Creates a "minimal" sensor properties file usable for test case purposes. Needed by SensorShell
   * which must be passed a SensorProperties object containing a host, email, and password.
   *
   * @param host  The hackystat host.
   * @param email   The user's email.
   * @param password   The user's password.
   */
  public SensorProperties(String host, String email, String password) {
    sensorProps.setProperty(SensorProperties.HOST_KEY, host);
    sensorProps.setProperty(SensorProperties.EMAIL_KEY, email);
    sensorProps.setProperty(SensorProperties.PASSWORD_KEY, password);
    // Add the current set of hackystat properties to the System property object.
    addToSystemProperties(sensorProps);
    this.fileAvailable = false;
    this.sensorEnabled = false;
  }

  /**
   * Provides access to Hackystat Sensor settings by reading the passed file.
   *
   * @param sensorType  The type of sensor requesting information, such as "JUnit", etc.
   * @param sensorFile  The sensor file to read.
   */
  public SensorProperties(String sensorType, File sensorFile) {
    this.sensorFile = sensorFile;
    this.sensorType = sensorType.toUpperCase();
    try {
      if (sensorFile.exists()) {
        sensorProps.load(new FileInputStream(sensorFile));
        if (sensorProps.size() > 0) {
          this.fileAvailable = true;
          String enableKey = "ENABLE_" + this.sensorType + "_SENSOR";
          this.sensorEnabled = sensorProps.getProperty(enableKey, "false").trim().equals("true");
          // Add the current set of hackystat properties to the System property object.
          addToSystemProperties(sensorProps);
        }
      }
    }
    catch (Exception e) {
      this.fileAvailable = false;
      this.sensorEnabled = false;
    }
  }


  /**
   * Returns the directory in which the sensor.properties file is located (if it exists). This is
   * normally the .hackystat directory. If this SensorProperties instance was created without a
   * sensor.properties file, or if for some other reason the sensor.properties file cannot be found,
   * then this method returns null.
   *
   * @return   A File instance indicating a directory, or null.
   */
  public File getSensorPropertiesDir() {
    if ((sensorFile != null) && (sensorFile.exists())) {
      return sensorFile.getParentFile();
    }
    else {
      return null;
    }
  }


  /**
   * Returns true if the supplied property is set to true in this sensor properties.
   *
   * @param sensorType  A string such as "Eclipse" or "Eclipse_UnitTest".
   * @return            True if this property is enabled.
   */
  public boolean isSensorTypeEnabled(String sensorType) {
    String enableKey = "ENABLE_" + sensorType.toUpperCase() + "_SENSOR";
    return sensorProps.getProperty(enableKey, "false").trim().equals("true");
  }
  
  /**
   * Returns the trimmed property value associated with the property key in 
   * this sensor.properties file.
   * @param key The parameter key.
   * @return The trimmed property value associated with this property key, or null if not found.
   */
  public String getProperty(String key) {
    String value = sensorProps.getProperty(key);
    return (value == null) ? value : value.trim();
  }


  /**
   * Returns true if the sensor corresponding to these properties should be used.
   *
   * @return   True if the sensor is enabled.
   */
  public boolean isSensorEnabled() {
    return this.sensorEnabled;
  }


  /**
   * Returns the hackystat host. Defaults to http://localhost/.
   *
   * @return   The hackystat host.
   */
  public String getHackystatHost() {
    String host = sensorProps.getProperty(HOST_KEY, "http://localhost/").trim(); 
    if (!host.endsWith("/")) {
      host = host + "/";
    }
    return host;
  }


  /**
   * Returns the absolute path to the properties file, or the empty string if the file is not
   * available.
   *
   * @return   The absolutePath value or the empty string.
   */
  public String getAbsolutePath() {
    return (this.sensorFile == null) ? "" : this.sensorFile.getAbsolutePath();
  }


  /**
   * Returns the password for this user. Defaults to "ChangeThis"
   *
   * @return The user password.
   */
  public String getPassword() {
    return sensorProps.getProperty(PASSWORD_KEY, "ChangeThis").trim();
  }

  /**
   * Returns the email for this user. Defaults to "ChangeThis@changethis.com"
   *
   * @return The user email.
   */
  public String getEmail() {
    return sensorProps.getProperty(EMAIL_KEY, "ChangeThis@changethis.com").trim();
  }
  
  /**
   * Returns the AutoSend interval for use by the SensorShell, or 0 if it was not specified. This is
   * returned as a string since it is typically sent off to the SensorShell as a String argument.
   *
   * @return   The autosend interval.
   */
  public String getAutoSendInterval() {
    String intervalString = sensorProps.getProperty(AUTOSEND_KEY, "0").trim();
    try {
      // make sure it's an integer.
      Integer.parseInt(intervalString);
      return intervalString;
    }
    catch (Exception e) {
      return "0";
    }
  }

  /**
   * Returns the StateChange interval for use by sensors, or 30 if it was not specified. The
   * stateChange interval must be greater than 0.
   *
   * @return   The state change interval in seconds.
   */
  public int getStateChangeInterval() {
    String intervalString = sensorProps.getProperty(STATECHANGE_KEY, "60").trim();
    try {
      int interval = Integer.parseInt(intervalString);
      return (interval > 0) ? interval : 30;
    }
    catch (Exception e) {
      return 30;
    }
  }


  /**
   * Returns true if the sensor properties file was found and read successfully.
   *
   * @return   True if file was found and readable.
   */
  public boolean isFileAvailable() {
    return this.fileAvailable;
  }

  
  /**
   * Updates the System properties object with the contents of the passed Hackystat
   * properties instance.  This method is declared static so that it can be invoked
   * from both SensorProperties and ServerProperties.  The hackystat properties added from
   * SensorProperties were found in sensor.properties. The hackystat properties added from
   * ServerProperties were found in hackystat.server.properties.  
   * This method is package-private for access by SensorProperties.
   * @param hackyProperties A Properties instance containing hackystat properties.
   */
  private void addToSystemProperties(Properties hackyProperties) {
    Properties systemProperties = System.getProperties();
    systemProperties.putAll(hackyProperties);
    System.setProperties(systemProperties);
  }
}

