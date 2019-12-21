package org.gamboni.cloudspill.message;

/**
 * @author tendays
 */
public class SettableStatusListener<T extends StatusReport> implements StatusReport {

    protected T listener;

    public void set(T listener) {
        this.listener = listener;
    }

    public void unset(T listener) {
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

    @Override
    public void refresh() {
        StatusReport delegate = listener;
        if (delegate != null) {
            delegate.refresh();
        }
    }
}
