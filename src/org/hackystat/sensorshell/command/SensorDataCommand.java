package org.hackystat.sensorshell.command;

import java.util.Map;
import javax.xml.datatype.XMLGregorianCalendar;

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
  
  /**
   * Creates the SensorDataCommand. 
   * @param shell The sensorshell. 
   * @param properties The sensorproperties. 
   */
  public SensorDataCommand(SensorShell shell, SensorProperties properties) {
    super(shell, properties);
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
    if (this.shell.getPingCommand().isPingable()) {
      //OfflineManager.getInstance().clear();
      try {
        this.shell.getClient().putSensorDataBatch(sensorDatas);
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
   * Given a Map containing key-value pairs corresponding to SensorData fields and properties,
   * constructs a SensorData instance and stores it for subsequent sending to the SensorBase.
   * @param keyValMap The map of key-value pairs. 
   * @throws Exception If problems occur creating the SensorData instance. 
   */
  public void add(Map<String, String> keyValMap) throws Exception {
    // Begin by creating the sensor data instance. 
    SensorData data = new SensorData();
    XMLGregorianCalendar tstamp = Tstamp.makeTimestamp();
    data.setOwner(getMap(keyValMap, "Owner", this.shell.getEmail()));
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
