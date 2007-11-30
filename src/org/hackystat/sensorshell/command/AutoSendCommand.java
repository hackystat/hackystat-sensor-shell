package org.hackystat.sensorshell.command;

import java.util.Timer;
import java.util.TimerTask;

import org.hackystat.sensorshell.SensorShellException;
import org.hackystat.sensorshell.SensorShellProperties;
import org.hackystat.sensorshell.SingleSensorShell;

/**
 * Implements the AutoSend facility, which automatically sends all SensorData to the Sensorbase
 * at regular intervals as specified in the sensorshell.autosend.timeinterval property.
 * @author Philip Johnson
 */
public class AutoSendCommand extends Command {
  
  /** The timer that enables periodic sending. */
  private Timer timer = null;
  
  /**
   * Creates the AutoSendCommand and starts a timer-based process running that wakes up and 
   * invokes send based upon the value of the SensorShellProperties autosend timeinterval.
   * @param shell The sensorshell. 
   * @param properties The sensorproperties. 
   */
  public AutoSendCommand(SingleSensorShell shell, SensorShellProperties properties) {
    super(shell, properties);
    double minutes = properties.getAutoSendTimeInterval();
    // Don't set up a timer if minutes value is close to 0.
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
   * Cancels the timer if there is one. 
   */
  public void quit() {
    if (this.timer != null) {
      this.timer.cancel();
    }
  }
  
  /**
   * Inner class providing a timer-based command to invoke the send() method of the SensorShell.
   * @author Philip M. Johnson
   */
  private static class AutoSendCommandTask extends TimerTask {
    
    /** The sensor shell. */
    private SingleSensorShell shell;

    /**
     * Creates the TimerTask.
     * @param shell The sensorshell.
     */
    public AutoSendCommandTask(SingleSensorShell shell) {
      this.shell = shell;
    }

    /** Invoked periodically by the timer in AutoSendCommand. */
    @Override
    public void run() {
      this.shell.println("Autosending...");
      try {
        this.shell.send();
      }
      catch (SensorShellException e) {
        this.shell.println("Error sending data.");
      }
    }
  }
}
