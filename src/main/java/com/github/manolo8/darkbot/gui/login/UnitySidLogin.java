package com.github.manolo8.darkbot.gui.login;

import com.github.manolo8.darkbot.utils.I18n;
import com.github.manolo8.darkbot.utils.login.UnityCredentials;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;

/**
 * Unity saved-session tab: universe server + a live portal {@code dosid} cookie, exchanged
 * for a fresh game sid by the session worker ({@code BigPointPortalHandler.sidFromDosid}).
 * The server is required here: a dosid belongs to one universe.
 */
public class UnitySidLogin extends JPanel implements UnityLoginScreen {

    private final JTextField server, sid;

    public UnitySidLogin() {
        super(new MigLayout("wrap 2, ins 0 8 0 8, fillx", "[fill,130px]10px[fill,130px]", "[]8px[]"));

        server = new JTextField(15);
        sid    = new JTextField(15);

        add(new JLabel(I18n.get("gui.login.sid.server")));
        add(server, "growx, span 2, wrap");
        add(new JLabel(I18n.get("gui.login.sid.sid")));
        add(sid, "growx, span 2, wrap");
    }

    @Override
    public UnityCredentials collect() {
        String sv = server.getText().trim();
        String ds = sid.getText().trim();

        if (sv.isEmpty() || ds.isEmpty()) return null;
        return new UnityCredentials(sv, null, null, ds);
    }
}
