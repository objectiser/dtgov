package org.overlord.dtgov.ui.server.services.dtgov;

import java.util.Locale;

/**
 * A client used to access a dtgov server
 * 
 * @author David Virgil Naranjo
 */
public interface IDtgovClient {
    public void stopProcess(String targetUUID, long processId) throws Exception;

    public void setLocale(Locale locale);
}
