package com.github.manolo8.darkbot.utils.login;

/**
 * Credentials collected from the Unity login popup (see {@code UnityLoginForm}).
 *
 * <p>Unlike {@link LoginData} (which is tied to the Flash login flow — preloader url,
 * flashvars, spacemap), this holder only carries what the {@code UnityPacketAdapter}
 * session worker needs: the universe server and either {@code username+password}
 * (portal login via {@code BigPointPortalHandler}) or {@code sid} (a live portal
 * {@code dosid} cookie exchanged for a fresh game sid).
 */
public class UnityCredentials {

    /** Universe server, e.g. {@code es2} / {@code de1} / {@code en1}. Empty = auto-detect (user/pass only). */
    public final String server;
    public final String username;
    public final String password;
    /** Live {@code dosid} cookie of a portal session (SID tab). */
    public final String sid;

    public UnityCredentials(String server, String username, String password, String sid) {
        this.server = server;
        this.username = username;
        this.password = password;
        this.sid = sid;
    }

    /** True when the SID tab was used (dosid + server present). */
    public boolean hasSid() {
        return sid != null && !sid.isEmpty() && server != null && !server.isEmpty();
    }

    /** True when the user/pass tab was used. */
    public boolean hasUser() {
        return username != null && !username.isEmpty();
    }

    @Override
    public String toString() {
        return "UnityCredentials{server='" + server + "', user='" + (username == null ? null : username) + "', sid='" +
                (sid == null || sid.isEmpty() ? null : sid.length() + " chars") + "'}";
    }
}
