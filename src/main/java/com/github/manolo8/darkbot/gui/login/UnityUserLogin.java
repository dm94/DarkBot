package com.github.manolo8.darkbot.gui.login;

import com.github.manolo8.darkbot.utils.I18n;
import com.github.manolo8.darkbot.utils.login.UnityCredentials;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;

/**
 * Unity portal-login tab: username + password (+ optional server/universe, empty = the
 * session worker auto-detects it through the portal SSO flow). No Flash preloader logic.
 */
public class UnityUserLogin extends JPanel implements UnityLoginScreen {

    private final JTextField server, username;
    private final JPasswordField password;

    public UnityUserLogin() {
        super(new MigLayout("wrap 2, ins 0 8 0 8, fillx", "[fill,130px]10px[fill,130px]", "[]8px[]"));

        server   = new JTextField(15);
        username = new JTextField(15);
        password = new JPasswordField(15);

        add(new JLabel(I18n.get("gui.login.unity.server")));
        add(server, "growx, span 2, wrap");
        add(new JLabel(I18n.get("gui.login.user_pass.username")));
        add(username, "growx, span 2, wrap");
        add(new JLabel(I18n.get("gui.login.user_pass.password")));
        add(password, "growx, span 2, wrap");
    }

    @Override
    public UnityCredentials collect() {
        String user = username.getText().trim();
        String pass = new String(password.getPassword());

        if (user.isEmpty() || pass.isEmpty()) return null;
        return new UnityCredentials(server.getText().trim(), user, pass, null);
    }
}
