package org.hackystat.sensorshell.command;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.hackystat.sensorshell.SensorShellException;
import org.hackystat.sensorshell.SensorShellProperties;
import org.hackystat.sensorshell.SingleSensorShell;
import org.hackystat.utilities.stacktrace.StackTrace;

/**
 * Implements the AutoSend facility, which automatically sends all SensorData to the Sensorbase
 * at regular intervals as specified in the sensorshell.autosend.timeinterval property.
 * @author Philip Johnson
 */
public class AutoSendCommand extends Command {
  
  /** The timer that enables periodic sending. */
  private Timer timer = null;
  /** The autosend command task for this shell. */
  private AutoSendCommandTask task = null;
  
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
      this.task = new AutoSendCommandTask(shell);
      this.timer.schedule(task, milliseconds, milliseconds);
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
   * Returns the exception that was thrown during autosend, or null if none was thrown.
   * @return The exception if one was thrown previously, or null if none was thrown. 
   */
  public SensorShellException getException() {
    return (this.task == null) ? null : this.task.getException();
  }
  
  /**
   * Inner class providing a timer-based command to invoke the send() method of the SensorShell.
   * @author Philip M. Johnson
   */
  private static class AutoSendCommandTask extends TimerTask {
    
    /** The sensor shell. */
    private SingleSensorShell shell;
    /** The exception that was thrown by this task. */
    private SensorShellException exception = null;
    /** The time at which the exception was thrown. */
    private Date exceptionTime = null;
    
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
      this.shell.println("Timer-based invocation of send().");
      try {
        this.shell.send();
      }
      catch (SensorShellException e) {
        this.shell.getLogger().info("Error in timer-based invocation: " + StackTrace.toString(e));
        this.exceptionTime = new Date();
        this.exception = e;
      }
    }
    
    /**
     * The exception that was thrown during the run() method of this task.
     * @return The thrown exception, or null if no exception was thrown.
     * 
     */
    public SensorShellException getException() {
      return this.exception;
    }
    
    /**
     * The time that an exception was thrown during the run() method of this task.
     * @return The thrown exception time, or null if no exception was thrown. 
     * 
     */
    public Date getExceptionTime() {
      return this.exceptionTime;
    }
    
  }
}
