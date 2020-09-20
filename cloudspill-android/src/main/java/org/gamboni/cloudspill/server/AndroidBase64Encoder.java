package org.gamboni.cloudspill.server;

import android.util.Base64;

import org.gamboni.cloudspill.shared.api.Base64Encoder;

/**
 * @author tendays
 */
public class AndroidBase64Encoder implements Base64Encoder {
    public static final AndroidBase64Encoder INSTANCE = new AndroidBase64Encoder();

    @Override
    public String encode(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
