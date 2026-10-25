package com.sloth.browser.engine;

/**
 * Stands in for the browser's own settings model in the harness.
 *
 * <p>Only the fields the shell touches: the shell finds this object by its type on the
 * activity and writes it by field name, exactly as it does on the device, so the lookup
 * and the write are exercised rather than mocked away.</p>
 */
public class SlothSettings extends EngineBase {

    public boolean screenGuard = true;
    public boolean webrtcBlock;
    public boolean httpsOnly;
    public boolean javascript = true;
    public boolean acceptCookies = true;
    public boolean thirdPartyCookies;
    public boolean popups;
    public boolean blockAutoplay = true;
    public boolean prefetch;
    public boolean dnt;
    public boolean gpc;
    public boolean fingerprintResist;
    public boolean privateDefault;
    public long textZoom = 100;
    public int autoLockMin = 5;
}
