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
   * Sets the AutoSend time interval and buffer size from the sensor.properties file. 
   */
  public void initialize() {
    setTimeInterval(this.properties.getAutoSendTimeInterval());
    setBufferSize(this.properties.getAutoSendBufferSize());
  }

  /**
   * Process an AutoSendTimeInterval command, given a list whose first argument is the auto send
   *
   * @param interval  An integer that specifies the interval in minutes for sending data.
   */
  public void setTimeInterval(String interval) {
    // Begin by getting the number of minutes
    double minutes = 0;
    try {
      minutes = Double.parseDouble(interval);
      if (minutes < 0) {
        throw new IllegalArgumentException("Minutes must be a non-negative double.");
      }
    }
    catch (Exception e) {
      this.shell.println("AutoSend error (invalid argument: " + interval + ")" + cr + e);
    }
    setTimeInterval(minutes);
  }
  
  /**
   * Initializes the autosend time interval to the number of specified minutes. 
   * If 0, then autosend is disabled. 
   * @param minutes The minutes. 
   */
  public void setTimeInterval(double minutes) {
    // Cancel the current timer if any.
    if (this.timer != null) {
      this.timer.cancel();
    }
    // Don't set up a new timer if minutes value is close to 0.
    if (minutes < 0.009) {
      this.shell.println("AutoSend disabled.");
    }
    else {
      // Otherwise set up a timer with the newly specified value.
      this.timer = new Timer(true);
      int milliseconds = (int) (minutes * 60 * 1000);
      this.timer.schedule(new AutoSendCommandTask(shell), milliseconds, milliseconds);
      this.shell.println("AutoSend time interval set to " + (int) (minutes * 60) + " seconds");
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
  
  /**
   * Process an AutoSendBufferSize command, given a string containing the max buffer size. 
   * @param buffSizeString  An integer that specifies the maximum buffer size before sending data. 
   */
  public void setBufferSize(String buffSizeString) {
    // Begin by getting the buffer size. 
    int bufferSize = 0;
    try {
      bufferSize = Integer.parseInt(buffSizeString);
      if (bufferSize < 0) {
        throw new IllegalArgumentException("BufferSize must be a non-negative integer.");
      }
    }
    catch (Exception e) {
      this.shell.println("AutoSend error (invalid argument: " + buffSizeString + ")" + cr + e);
    }
    setBufferSize(bufferSize);
  }
  
  /**
   * Initializes the autosend buffer size to the number indicating the maximum buffer size
   * before invoking a send command.
   * @param bufferSize The buffer size. 
   */
  public void setBufferSize(int bufferSize) {
    this.shell.setAutoSendBufferSize(bufferSize);
    this.shell.println("AutoSend buffer size set to " + bufferSize + " entries");
  }
}
