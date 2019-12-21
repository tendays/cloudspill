package org.gamboni.cloudspill.message;

/** Implemented by objects able to display the status of a synchronisation operation.
 *
 * @author tendays
 */
public interface StatusReport {

    public enum Severity {
        INFO,
        ERROR
    }

    /** Display a message about progress. Messages with higher severity hide low severity messages. */
    void updateMessage(Severity severity, String message);
    /** Display progress of the current operation. */
    void updatePercent(int percent);

    /** Called to notify that a new item was added to the database */
    void refresh();
}
