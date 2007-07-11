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
   */
  public void initialize() {
    initialize(this.properties.getAutoSendInterval());
  }

  /**
   * Process an AutoSend command, given a list whose first argument is the auto
   *
   * @param interval  An integer that specifies the interval in minutes for sending data.
   */
  public void initialize(String interval) {
    // Begin by getting the number of minutes
    int minutes = 0;
    try {
      minutes = Integer.parseInt(interval);
      if (minutes < 0) {
        throw new IllegalArgumentException("Minutes must be a non-negative integer.");
      }
    }
    catch (Exception e) {
      this.shell.println("AutoSend error (invalid argument: " + interval + ")" + cr + e);
    }
    initialize(minutes);
  }
  
  /**
   * Initializes the autosend to the number of specified minutes. If 0, then autosend is disabled. 
   * @param minutes The minutes. 
   */
  public void initialize(int minutes) {
    // Cancel the current timer if any.
    if (this.timer != null) {
      this.timer.cancel();
    }
    // Don't set up a new timer if minutes value is 0.
    if (minutes == 0) {
      this.shell.println("AutoSend disabled");
    }
    else {
      // Otherwise set up a timer with the newly specified value.
      this.timer = new Timer(true);
      int milliseconds = minutes * 60 * 1000;
      this.timer.schedule(new AutoSendCommandTask(shell), milliseconds, milliseconds);
      this.shell.println("AutoSend set to " + minutes + " minutes");
    }
  }
  
  /**
   * Inner class providing a timer-based command to invoke the send() method of the SensorShell.
   * @author Philip M. Johnson
   */
  private static class AutoSendCommandTask extends TimerTask {
    
    /** The sensor shell. */
    private SensorShell shell;

    /**
     * Creates the TimerTask.
     * @param shell The sensorshell.
     */
    public AutoSendCommandTask(SensorShell shell) {
      this.shell = shell;
    }

    /** Invoked periodically by the timer in AutoSendCommand. */
    @Override
    public void run() {
      this.shell.println("Autosending...");
      this.shell.send();
    }
  }
}
