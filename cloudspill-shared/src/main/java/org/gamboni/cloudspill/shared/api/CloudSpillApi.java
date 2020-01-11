package org.gamboni.cloudspill.shared.api;

/**
 * @author tendays
 */
public abstract class CloudSpillApi {
    /** Function used by clients to ensure connectivity is available and check API compatibility. */
    public static final String PING = "/ping";
    public static final String PING_PREAMBLE = "CloudSpill server.";
    /** Data version field in response */
    public static final String PING_DATA_VERSION = "Data-Version";
    /** Public url field in response */
    public static final String PING_PUBLIC_URL = "Url";

    /** "Create User" function: name of the user to create (POST parameter) */
    public static final String CREATE_USER_NAME = "name";
    /** "Create User" function: password of the user to create (POST parameter) */
    public static final String CREATE_USER_PASS = "pass";

    /** PUT URL to upload a file */
    public static String upload(String user, String folder, String path) {
        return "/item/"+ user +"/"+ folder +"/"+ path;
    }
    /** "Upload file" function: file timestamp HTTP header */
    public static final String UPLOAD_TIMESTAMP_HEADER = "X-CloudSpill-Timestamp";
    /** "Upload file" function: file type (ItemType) HTTP header */
    public static final String UPLOAD_TYPE_HEADER = "X-CloudSpill-Type";
}
