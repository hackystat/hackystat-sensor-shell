package org.hackystat.sensorshell.command;

import java.util.Map;
import javax.xml.datatype.XMLGregorianCalendar;

import org.hackystat.sensorbase.client.SensorBaseClient;
import org.hackystat.sensorbase.client.SensorBaseClientException;
import org.hackystat.sensorbase.resource.sensordata.Tstamp;
import org.hackystat.sensorbase.resource.sensordata.jaxb.Properties;
import org.hackystat.sensorbase.resource.sensordata.jaxb.Property;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorDatas;
import org.hackystat.sensorshell.SensorProperties;
import org.hackystat.sensorshell.SensorShell;

/**
 * Implements the SensorData commands, of which there is an "add" and a "send" command. 
 * @author Philip Johnson
 */
public class SensorDataCommand extends Command { 
    
  /** The list of unsent SensorData instances. */
  private SensorDatas sensorDatas = new SensorDatas();
  /** The Ping Command. */
  private PingCommand pingCommand;
  /** The sensorbase client. */
  private SensorBaseClient client;
  
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
   */
  public void send() {
    // Log this command if not running interactively.
    if (!this.shell.isInteractive()) {
      this.shell.getLogger().info("#> send" + cr);
    }
    if (this.pingCommand.isPingable()) {
      //OfflineManager.getInstance().clear();
      try {
        if (sensorDatas.getSensorData().isEmpty()) {
          this.shell.println("No sensor data to send.");
          return;
        }
        this.shell.println("About to send the following sensor data:");
        this.shell.println("<Timestamp SDT Owner Tool Resource Runtime {Properties}>");
        for (SensorData data : sensorDatas.getSensorData()) {
          this.shell.println("  " + formatSensorData(data));
        }
        this.client.putSensorDataBatch(sensorDatas);
        this.shell.println(sensorDatas.getSensorData().size() + " SensorData instances sent to " +
            this.properties.getHackystatHost());
        this.sensorDatas.getSensorData().clear();
      }
      catch (SensorBaseClientException e) {
        this.shell.println("Error sending data: " + e);
      }
    }
    else {
      if (this.shell.enableOfflineData()) {
        this.shell.println("Server not available. Storing commands offline.");
        //OfflineManager.getInstance().store(this);
      }
      else {
        this.shell.println("Server not available and offline storage disabled. Data lost.");
      }
    }
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
    data.setResource(getMap(keyValMap, "Resource", ""));
    data.setRuntime(Tstamp.makeTimestamp(getMap(keyValMap, "Runtime", tstamp.toString())));
    data.setSensorDataType(getMap(keyValMap, "SensorDataType", ""));
    data.setTimestamp(Tstamp.makeTimestamp(getMap(keyValMap, "Timestamp", tstamp.toString())));
    data.setTool(getMap(keyValMap, "Tool", "unknown"));
    data.setProperties(new Properties());
    // Add all remaining key-val pairs to the property list. 
    for (Map.Entry<String, String> entry : keyValMap.entrySet()) {
      Property property = new Property();
      property.setKey(entry.getKey());
      property.setValue(entry.getValue());
      data.getProperties().getProperty().add(property);
    }
    // Now that our SensorData instance is initialized, put it on the list.
    sensorDatas.getSensorData().add(data);
    this.shell.println("Adding: " + formatSensorData(data));
    
  }

  /**
   * Returns the value associated with key in keyValMap, or the default, and also removes the 
   * mapping associated with key from the keyValMap.
   * @param keyValMap The map
   * @param key The key
   * @param defaultValue The value to return if the key has no mapping.
   * @return The value to be used.
   */
  private String getMap(Map<String, String> keyValMap, String key, String defaultValue) {
    String value = (keyValMap.get(key) == null) ? defaultValue : keyValMap.get(key);
    keyValMap.remove(key);
    return value;
  }
}
