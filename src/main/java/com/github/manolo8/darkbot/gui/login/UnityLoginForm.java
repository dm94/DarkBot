package com.github.manolo8.darkbot.gui.login;

import com.github.manolo8.darkbot.gui.utils.UIUtils;
import com.github.manolo8.darkbot.utils.I18n;
import com.github.manolo8.darkbot.utils.login.UnityCredentials;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;

/**
 * Unity login popup: the GUI alternative to the {@code -login} properties file for the
 * {@code UnityPacketAdapter}. It lets the user choose between the portal flow
 * ({@link UnityUserLogin}, username/password, server optional = auto-detect) and a saved
 * session ({@link UnitySidLogin}, server + dosid).
 *
 * <p>Unlike the Flash {@link LoginForm}, this popup performs <b>no network I/O</b>: it
 * only collects {@link UnityCredentials} and closes. The real login (portal SSO / SID
 * exchange, map server handshake) happens on the adapter's session worker, so the GUI
 * stays responsive and a failed login is reported through the adapter's log/state instead
 * of blocking the EDT.
 */
public class UnityLoginForm extends JPanel {

    private final JTabbedPane tabbedPane = new JTabbedPane();

    private final JLabel infoLb = new JLabel("");
    private final JButton loginBtn = new JButton(I18n.get("gui.login.log_in.button"));

    private volatile UnityCredentials result;

    public UnityLoginForm() {
        super(new MigLayout("wrap 2, ins 0", "[]10px:push[]", "[]8px[]"));
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        tabbedPane.addTab(I18n.get("gui.login.unity.user_pass"), new UnityUserLogin());
        tabbedPane.addTab(I18n.get("gui.login.unity.sid"), new UnitySidLogin());

        loginBtn.addActionListener(ac -> collect());

        add(tabbedPane, "span 2, growx, height 112px!");
        add(infoLb, "gapleft 8px, grow 0");
        add(loginBtn, "gapright 8px");
    }

    public JButton getLoginBtn() {
        return loginBtn;
    }

    /** The collected credentials, or {@code null} if the popup was dismissed without logging in. */
    public UnityCredentials getResult() {
        return result;
    }

    private void setInfoText(String text) {
        infoLb.setText(text);
        infoLb.setToolTipText(text);
        UIUtils.setRed(infoLb, true);
    }

    private void collect() {
        UnityCredentials creds = ((UnityLoginScreen) tabbedPane.getSelectedComponent()).collect();
        if (creds == null) {
            setInfoText(I18n.get("gui.login.unity.missing_fields"));
            return;
        }
        result = creds;
        SwingUtilities.getWindowAncestor(UnityLoginForm.this).setVisible(false);
    }
}
