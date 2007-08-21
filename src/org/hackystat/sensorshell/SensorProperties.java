package org.hackystat.sensorshell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * Provides access to the hackystat properties file for each sensor, and provides reasonable 
 * default values when properties file lacks property values. <p>
 *
 * The creation of a SensorProperties instance requires a valid sensor properties file. The sensor 
 * properties file is stored in userhome/.hackystat/v8.sensor.properties. It is a user-maintained
 * file of key=value pairs. <p>
 * 
 * All of the properties in v8.sensor.properties are added to the System property instance after 
 * being read from the file. This enables other tools that interoperate with Hackystat to obtain 
 * settings from the System instance and/or environments to configure System property settings
 * (such as for SSL) simply by adding entries to the properties file. 
 *
 * @author Philip M. Johnson, Aaron A. Kagawa
 */
public class SensorProperties {

  /** Hackystat host. */
  private static final String HOST_KEY = "HACKYSTAT_SENSORBASE_HOST";
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

  /** Whether the properties file exists and was successfully read. */
  private boolean fileAvailable = false;
  /** The internal properties object. */
  private Properties sensorProps = new Properties();

  /**
   * Initializes based on the user's v8.sensor.properties file.
   * @throws SensorPropertiesException If the SensorProperties instance cannot be 
   * instantiated. 
   */
  public SensorProperties() throws SensorPropertiesException {
    this(new File(System.getProperty("user.home") + "/.hackystat/v8.sensor.properties"));
  }

  /**
   * Provides access to Hackystat Sensor settings by reading specified sensor properties file. 
   * @param sensorFile The sensor file to read.
   * @throws SensorPropertiesException If the SensorProperties instance cannot be 
   * instantiated. 
   */
  public SensorProperties(File sensorFile) throws SensorPropertiesException {
    // validation of the sensor file
    if (sensorFile == null) {
      throw new SensorPropertiesException("Invalid sensor properties file");
    }
    if (!sensorFile.exists()) {
      throw new SensorPropertiesException("Sensor properties file does not exist at "
          + sensorFile.getAbsolutePath());
    }
    if (!sensorFile.isFile()) {
      throw new SensorPropertiesException("Sensor properties must be a file.");
    }
    if (!sensorFile.canRead()) {
      throw new SensorPropertiesException("Unable to read the sensor properties file at "
          + sensorFile.getAbsolutePath());
    }

    // the sensor file should be valid at this point, whether there is anything is not know yet.
    this.sensorFile = sensorFile;
    FileInputStream fileStream = null;
    try {
      fileStream = new FileInputStream(this.sensorFile);
      this.sensorProps.load(fileStream);
      if (this.sensorProps.size() > 0) {
        this.fileAvailable = true;
        // Add the current set of hackystat properties to the System property object.
        this.addToSystemProperties(this.sensorProps);
      }
    }
    catch (FileNotFoundException e) {
      throw new SensorPropertiesException("Sensor properties file does not exist at "
          + sensorFile.getAbsolutePath());
    }
    catch (IOException e) {
      throw new SensorPropertiesException("Unable to read the sensor properties file at "
          + sensorFile.getAbsolutePath());
    }
    finally {
      try {
        fileStream.close();
      }
      catch (Exception e) {
        System.err.println("Error closing stream: " + e);
      }
    }
  }

  /**
   * Creates a "minimal" sensor properties file usable for test case purposes. Needed by SensorShell
   * which must be passed a SensorProperties object containing a host, email, and password.
   * @param host The hackystat host.
   * @param email The user's email.
   * @param password The user's password.
   */
  public SensorProperties(String host, String email, String password) {
    this.sensorProps.setProperty(SensorProperties.HOST_KEY, host);
    this.sensorProps.setProperty(SensorProperties.EMAIL_KEY, email);
    this.sensorProps.setProperty(SensorProperties.PASSWORD_KEY, password);
    // Add the current set of hackystat properties to the System property object.
    this.addToSystemProperties(this.sensorProps);
    this.fileAvailable = false;
  }
  
  /**
   * Returns the directory in which the sensor.properties file is located (if it exists). This is
   * normally the .hackystat directory. If this SensorProperties instance was created without a
   * sensor.properties file, or if for some other reason the sensor.properties file cannot be found,
   * then this method returns null.
   *
   * @return A File instance indicating a directory, or null.
   */
  public File getSensorPropertiesDir() {
    if ((this.sensorFile != null) && (this.sensorFile.exists())) {
      return this.sensorFile.getParentFile();
    }
    else {
      return null;
    }
  }
  
  /**
   * Returns the trimmed property value associated with the property key in this 
   * v8.sensor.properties file. In most cases, it is recommended that clients use the access 
   * methods like <code>getHackystatHost()</code>, because these methods provide default values.
   * @param key The parameter key.
   * @return The trimmed property value associated with this property key, or null if not found.
   */
  public String getProperty(String key) {
    String value = this.sensorProps.getProperty(key);
    if (value != null) {
      value = value.trim();
    }
    return value;
  }

  /**
   * Returns the hackystat host. Defaults to http://localhost/.
   * @return The hackystat host.
   */
  public String getHackystatHost() {
    String host = this.sensorProps.getProperty(HOST_KEY, "http://localhost/").trim(); 
    if (!host.endsWith("/")) {
      host = host + "/";
    }
    return host;
  }


  /**
   * Returns the absolute path to the properties file, or the empty string if the file is not
   * available.
   * @return The absolutePath value or the empty string.
   */
  public String getAbsolutePath() {
    return (this.sensorFile == null) ? "" : this.sensorFile.getAbsolutePath();
  }


  /**
   * Returns the password for this user. Defaults to "ChangeThis"
   * @return The user password.
   */
  public String getPassword() {
    return this.sensorProps.getProperty(PASSWORD_KEY, "ChangeThis").trim();
  }

  /**
   * Returns the email for this user. Defaults to "ChangeThis@changethis.com"
   * @return The user email.
   */
  public String getEmail() {
    return this.sensorProps.getProperty(EMAIL_KEY, "ChangeThis@changethis.com").trim();
  }
  
  /**
   * Returns the AutoSend interval for use by the SensorShell, or 10 if it was not specified. 
   * Returned as a string since it is typically sent off to the SensorShell as a String argument.
   * @return The autosend interval.
   */
  public String getAutoSendInterval() {
    String intervalString = this.sensorProps.getProperty(AUTOSEND_KEY, "10").trim();
    try {
      // make sure it's an integer.
      Integer.parseInt(intervalString);
      return intervalString;
    }
    catch (Exception e) {
      return "10";
    }
  }

  /**
   * Returns the StateChange interval for use by sensors, or 30 if it was not specified. The
   * stateChange interval must be greater than 0, otherwise 30 is returned. 
   * @return The state change interval in seconds.
   */
  public int getStateChangeInterval() {
    String intervalString = this.sensorProps.getProperty(STATECHANGE_KEY, "30").trim();
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
   * @return True if file was found and readable.
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

