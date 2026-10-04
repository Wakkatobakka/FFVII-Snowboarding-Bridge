package com.wakka.bridge;

/** Pause reasons remain independent: foreground never overrides an explicit pause. */
public final class SessionGate {
    private boolean foreground, userPaused;
    public synchronized void foreground(boolean value) { foreground=value; notifyAll(); }
    public synchronized void userPaused(boolean value) { userPaused=value; notifyAll(); }
    public synchronized boolean isUserPaused() { return userPaused; }
    public synchronized boolean isForeground() { return foreground; }
    public synchronized boolean blocked() { return !foreground || userPaused; }
    public synchronized void awaitRunning() {
        boolean interrupted=false;
        while(blocked()) {
            try { wait(); } catch(InterruptedException e) { interrupted=true; }
        }
        if(interrupted) Thread.currentThread().interrupt();
    }
}
