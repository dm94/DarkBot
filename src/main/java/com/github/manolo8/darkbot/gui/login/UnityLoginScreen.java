package com.github.manolo8.darkbot.gui.login;

import com.github.manolo8.darkbot.utils.login.UnityCredentials;

/**
 * A Unity login tab: collects {@link UnityCredentials} from its fields when the user
 * presses the Log in button. Unlike the Flash {@link LoginScreen}, no network happens
 * here — the popup only gathers what the {@code UnityPacketAdapter} session worker needs
 * (universe server + username/password, or server + dosid).
 */
public interface UnityLoginScreen {

    /**
     * Collects the credentials typed in this tab.
     *
     * @return the collected credentials, or {@code null} if a required field is empty
     *         (the form shows an error and keeps the popup open).
     */
    UnityCredentials collect();
}
