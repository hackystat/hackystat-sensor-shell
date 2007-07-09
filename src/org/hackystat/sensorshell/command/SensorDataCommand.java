package org.hackystat.sensorshell.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.datatype.XMLGregorianCalendar;

import org.hackystat.sensorbase.resource.sensordata.Tstamp;
import org.hackystat.sensorbase.resource.sensordata.jaxb.Properties;
import org.hackystat.sensorbase.resource.sensordata.jaxb.Property;
import org.hackystat.sensorbase.resource.sensordata.jaxb.SensorData;
import org.hackystat.sensorshell.SensorProperties;
import org.hackystat.sensorshell.SensorShell;

public class SensorDataCommand extends Command { 
  
  private List<SensorData> sensorDataList = new ArrayList<SensorData>();
  
  public SensorDataCommand(SensorShell shell, SensorProperties properties) {
    super(shell, properties);
  }

  @Override
  public String getHelpString() {
    // TODO Auto-generated method stub
    return null;
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
      this.shell.getSensorDataCommand().send();
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
    for (String key : keyValMap.keySet()) {
      String value = keyValMap.get(key);
      Property property = new Property();
      property.setKey(key);
      property.setValue(value);
      data.getProperties().getProperty().add(property);
    }
    // Now that our SensorData instance is initialized, put it on the list.
    sensorDataList.add(data);
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
