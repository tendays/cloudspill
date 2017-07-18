package org.gamboni.cloudspill.message;

/**
 * @author tendays
 */
public class SettableStatusListener implements StatusReport {

    private StatusReport listener;

    public void set(StatusReport listener) {
        this.listener = listener;
    }

    public void unset(StatusReport listener) {
        if (this.listener == listener) {
            this.listener = null;
        }
    }

    @Override
    public void updatePercent(final int percent) {
        StatusReport delegate = listener;
        if (delegate != null) {
            delegate.updatePercent(percent);
        }
    }

    @Override
    public void updateMessage(Severity severity, String message) {
        StatusReport delegate = listener;
        if (delegate != null) {
            delegate.updateMessage(severity, message);
        }
    }
}
