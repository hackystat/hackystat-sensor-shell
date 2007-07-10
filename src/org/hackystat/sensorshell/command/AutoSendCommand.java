package org.hackystat.sensorshell.command;

import java.util.Timer;
import java.util.TimerTask;

import org.hackystat.sensorshell.SensorProperties;
import org.hackystat.sensorshell.SensorShell;

/**
 * Implements the AutoSend facility, which automatically sends all SensorData to the Sensorbase
 * at regular intervals as specified in the sensor.properties file.  
 * @author Philip Johnson
 */
public class AutoSendCommand extends Command {

  /**
   * Creates the AutoSendCommand. 
   * @param shell The sensorshell. 
   * @param properties The sensorproperties. 
   */
  public AutoSendCommand(SensorShell shell, SensorProperties properties) {
    super(shell, properties);
  }
  
  /** The timer that enables periodic sending. */
  private Timer timer = null;
  
  /**
   * Sets the AutoSend interval from the sensor.properties file. 
   * @return A string describing the results of the initialization.
   */
  public String initialize() {
    return initialize(this.properties.getProperty("HACKYSTAT_AUTOSEND_INTERVAL"));
  }

  /**
   * Process an AutoSend command, given a list whose first argument is the auto
   *
   * @param interval  An integer that specifies the interval in minutes for sending data.
   * @return A string describing the results of the initialization.
   */
  public String initialize(String interval) {
    // Begin by getting the number of minutes
    int minutes;
    try {
      minutes = Integer.parseInt(interval);
      if (minutes < 0) {
        throw new IllegalArgumentException("Minutes must be a non-negative integer.");
      }
    }
    catch (Exception e) {
      return "AutoSend error (invalid argument: " + interval + ")" + cr + e;
    }
    // Cancel the current timer if any.
    if (this.timer != null) {
      this.timer.cancel();
    }
    // Don't set up a new timer if minutes value is 0.
    if (minutes == 0) {
      return "AutoSend OK (disabled)";
    }
    // Otherwise set up a timer with the newly specified value.
    this.timer = new Timer(true);
    int milliseconds = minutes * 60 * 1000;
    this.timer.schedule(new AutoSendCommandTask(shell), milliseconds, milliseconds);
    return "AutoSend OK (set to " + minutes + " minutes)";
  }
  
  /**
   * Inner class providing a timer-based command to invoke the send() method of the SensorShell.
   * @author Philip M. Johnson
   */
  private static class AutoSendCommandTask extends TimerTask {
    
    /** The SensorShell used to find the send command. */
    private SensorShell shell;

    /**
     * Creates the TimerTask.
     * @param shell  The notification shell.
     */
    public AutoSendCommandTask(SensorShell shell) {
      this.shell = shell;
    }

    /** Invoked periodically by the timer in AutoSendCommand. */
    @Override
    public void run() {
      this.shell.getSensorDataCommand().send();
    }
  }
}
