package org.hackystat.sensorshell.command;

import java.util.Map;
import javax.xml.datatype.XMLGregorianCalendar;

import org.hackystat.sensorbase.client.SensorBaseClient;
import org.hackystat.sensorbase.client.SensorBaseClientException;
import org.hackystat.utilities.tstamp.Tstamp;
import org.hackystat.sensorbase.resource.sensordata.jaxb.Properties;
import org.hackystat.sensorbase.resource.sensordata.jaxb.Property;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorDatas;
import org.hackystat.sensorshell.SensorProperties;
import org.hackystat.sensorshell.SensorShell;

/**
 * Implements the SensorData commands, of which there is "add", "send", and "statechange".
 * @author Philip Johnson
 */
public class SensorDataCommand extends Command { 
    
  private static final String RESOURCE = "Resource";
  /** The list of unsent SensorData instances. */
  private SensorDatas sensorDatas = new SensorDatas();
  /** The Ping Command. */
  private PingCommand pingCommand;
  /** The sensorbase client. */
  private SensorBaseClient client;
  /** Holds the Resource value from the last StateChange event. */
  private String lastStateChangeResource = "";
  /** Holds the bufferSize value from the last StateChange event. */
  private long lastStateChangeResourceCheckSum = 0; 
  
  /**
   * Creates the SensorDataCommand. 
   * @param shell The sensorshell. 
   * @param properties The sensorproperties.
   * @param pingCommand The Ping Command. 
   * @param client The SensorBase client.
   */
  public SensorDataCommand(SensorShell shell, SensorProperties properties, 
      PingCommand pingCommand, SensorBaseClient client) {
    super(shell, properties);
    this.pingCommand = pingCommand;
    this.client = client;
  }
  
  /**
   * Sends accumulated data, including offline and current data from the AddCommand.
   * If server not pingable, then the offline data is saved for a later attempt.
   * @return The number of sensor data instances that were sent. 
   */
  public int send() {
    int numDataSent = 0;
    // Log this command if not running interactively.
    if (!this.shell.isInteractive()) {
      this.shell.getLogger().info("#> send" + cr);
    }
    if (this.pingCommand.isPingable()) {
      //OfflineManager.getInstance().clear();
      try {
        if (sensorDatas.getSensorData().isEmpty()) {
          this.shell.println("No sensor data to send.");
          return 0;
        }
        this.shell.println("About to send the following sensor data:");
        this.shell.println("<Timestamp SDT Owner Tool Resource Runtime {Properties}>");
        for (SensorData data : sensorDatas.getSensorData()) {
          this.shell.println("  " + formatSensorData(data));
        }
        this.client.putSensorDataBatch(sensorDatas);
        this.shell.println(sensorDatas.getSensorData().size() + " SensorData instances sent to " +
            this.properties.getHackystatHost());
        numDataSent = sensorDatas.getSensorData().size();
        this.sensorDatas.getSensorData().clear();
      }
      catch (SensorBaseClientException e) {
        this.shell.println("Error sending data: " + e);
        numDataSent = 0;
      }
    }
    else {
      if (this.shell.enableOfflineData()) {
        this.shell.println("Server not available. Storing commands offline.");
        numDataSent = 0;
        //OfflineManager.getInstance().store(this);
      }
      else {
        this.shell.println("Server not available and offline storage disabled. Data lost.");
        numDataSent = 0;
      }
    }
    return numDataSent;
  }
  
  /**
   * Returns the data instance in a formatted string.
   * @param data The sensor data instance. 
   * @return A string displaying the instance.
   */
  private String formatSensorData(SensorData data) {
    StringBuffer buffer = new StringBuffer(75);
    buffer.append('<');
    buffer.append(data.getTimestamp());
    buffer.append(' ');
    buffer.append(data.getSensorDataType());
    buffer.append(' ');
    buffer.append(data.getOwner());
    buffer.append(' ');
    buffer.append(data.getTool());
    buffer.append(' ');
    buffer.append(data.getResource());
    buffer.append(' ');
    buffer.append(data.getRuntime());
    buffer.append(' ');
    for (Property property : data.getProperties().getProperty()) {
      buffer.append(property.getKey());
      buffer.append('=');
      buffer.append(property.getValue());
      buffer.append(' ');
    }
    buffer.append('>');
    return buffer.toString();
  }
  
  /**
   * Given a Map containing key-value pairs corresponding to SensorData fields and properties,
   * constructs a SensorData instance and stores it for subsequent sending to the SensorBase.
   * @param keyValMap The map of key-value pairs. 
   * @throws Exception If problems occur creating the SensorData instance. 
   */
  public void add(Map<String, String> keyValMap) throws Exception {
    // Begin by creating the sensor data instance. 
    SensorData data = new SensorData();
    XMLGregorianCalendar tstamp = Tstamp.makeTimestamp();
    data.setOwner(getMap(keyValMap, "Owner", this.properties.getEmail()));
    data.setResource(getMap(keyValMap, RESOURCE, ""));
    data.setRuntime(Tstamp.makeTimestamp(getMap(keyValMap, "Runtime", tstamp.toString())));
    data.setSensorDataType(getMap(keyValMap, "SensorDataType", ""));
    data.setTimestamp(Tstamp.makeTimestamp(getMap(keyValMap, "Timestamp", tstamp.toString())));
    data.setTool(getMap(keyValMap, "Tool", "unknown"));
    data.setProperties(new Properties());
    // Add all non-standard key-val pairs to the property list. 
    for (Map.Entry<String, String> entry : keyValMap.entrySet()) {
      Property property = new Property();
      String key = entry.getKey();
      if (isProperty(key)) {
        property.setKey(key);
        property.setValue(entry.getValue());
        data.getProperties().getProperty().add(property);
      }
    }
    // Now that our SensorData instance is initialized, put it on the list.
    sensorDatas.getSensorData().add(data);
    this.shell.println("Adding: " + formatSensorData(data));
    
  }
  
  /**
   * Provides an easy way for sensors to implement "StateChange" behavior.  From the sensor side,
   * StateChange can be implemented by creating a timer process that wakes up at regular 
   * intervals and gets the file (resource) that the current active buffer is attached to, plus
   * a "checksum" (typically the size of the resource in characters or bytes or whatever).  
   * Then, the timer process simply creates the
   * appropriate keyValMap as if for an 'add' event, and provides it to this method along with 
   * the buffer size.  This method will check the passed buffer size and the Resource field 
   * against the values for these two fields passed in the last call of this method, and if 
   * either one has changed, then the "add" method is called with the keyValMap.  
   * @param resourceCheckSum Indicates the state of the resource, typically via its size. 
   * @param keyValMap The map of key-value pairs. 
   * @throws Exception If problems occur while invoking the 'add' command. 
   */
  public void statechange(long resourceCheckSum, Map<String, String> keyValMap) throws Exception {
    // Get the Resource attribute, default it to "" if not found.
    String resource = (keyValMap.get(RESOURCE) == null) ? "" : keyValMap.get(RESOURCE);
    // Do an add if the resource or buffer size has changed.
    if (!this.lastStateChangeResource.equals(resource)) { //NOPMD
      this.shell.println("Invoking add: Resource has changed to: " + resource);
      this.add(keyValMap);
    }
    else if (this.lastStateChangeResourceCheckSum != resourceCheckSum) { //NOPMD
      this.shell.println("Invoking add: CheckSum has changed to: " + resourceCheckSum);
      this.add(keyValMap);
    }
    else {
      this.shell.println("No change in resource: " + resource + ", checksum: " + resourceCheckSum);
    }
    // Always update the 'last' values.
    this.lastStateChangeResourceCheckSum = resourceCheckSum;
    this.lastStateChangeResource = resource;
  }

  /**
   * Returns the value associated with key in keyValMap, or the default if the key is not present.
   * @param keyValMap The map
   * @param key The key
   * @param defaultValue The value to return if the key has no mapping.
   * @return The value to be used.
   */
  private String getMap(Map<String, String> keyValMap, String key, String defaultValue) {
    return (keyValMap.get(key) == null) ? defaultValue : keyValMap.get(key);
  }
  
  /**
   * Returns true if the passed key is not one of the standard Sensor Data fields including
   * "Timestamp", "Runtime", "Tool", "Resource", "Owner", or "SensorDataType".
   * @param key The key.
   * @return True if the passed key indicates a property, not a standard sensor data field.
   */
  private boolean isProperty(String key) {
    return 
        (!"Timestamp".equals(key)) &&
        (!"Runtime".equals(key)) &&
        (!"Tool".equals(key)) &&
        (!RESOURCE.equals(key)) &&
        (!"Owner".equals(key)) &&
        (!"SensorDataType".equals(key));
  }
}
