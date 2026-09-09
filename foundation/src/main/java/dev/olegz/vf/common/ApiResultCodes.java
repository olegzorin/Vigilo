package dev.olegz.vf.common;

public class ApiResultCodes {
    public static final byte SUCCESS = 0; // SUCCESS CODE

    public static final byte FAILURE = 1; // internal error

    public static final byte WRONG_API_KEY = 2;
    public static final byte OBJECT_NOT_FOUND = 6;
    public static final byte ACCESS_DENIED = 7;
    public static final byte WRONG_PARAM_VALUE = 8;
    public static final byte MISSED_PARAMETER = 9;
    public static final byte WRONG_USERNAME_PASSWORD = 12;
    public static final byte PARSING_ERROR = 14;
    public static final byte DUPLICATE_USERNAME = 20;
    public static final byte DUPLICATE_ENTITY = 26;
    public static final byte METHOD_NOT_FOUND = 29;
    public static final byte EXTERNAL_APPLICATION_ERROR = 32;
    public static final byte RESOURCE_NOT_AVAILABLE = 38;
    public static final byte OPERATION_NOT_ALLOWED = 40;
    public static final byte EXTERNAL_CONNECTION_ERROR = 42;
    public static final byte NOT_AUTHENTICATED = 45;
    public static final byte WEAK_PASSWORD = 46;


    private static final int CODES_LENGTH = WEAK_PASSWORD + 1;

    private static final String[] CODE_DESCRIPTIONS = new String[CODES_LENGTH];
    static {
        CODE_DESCRIPTIONS[FAILURE] = "Internal error";
        CODE_DESCRIPTIONS[WRONG_API_KEY] = "Wrong API key";
        CODE_DESCRIPTIONS[OBJECT_NOT_FOUND] = "Object not found";
        CODE_DESCRIPTIONS[ACCESS_DENIED] = "Access denied";
        CODE_DESCRIPTIONS[WRONG_PARAM_VALUE] = "Wrong parameter value";
        CODE_DESCRIPTIONS[MISSED_PARAMETER] = "Missed mandatory parameter value";
        CODE_DESCRIPTIONS[WRONG_USERNAME_PASSWORD] = "Invalid username or wrong password";
        CODE_DESCRIPTIONS[PARSING_ERROR] = "Error in parsing of input data";
        CODE_DESCRIPTIONS[DUPLICATE_USERNAME] = "Duplicate user name";
        CODE_DESCRIPTIONS[DUPLICATE_ENTITY] = "Duplicate entity or property";
        CODE_DESCRIPTIONS[METHOD_NOT_FOUND] = "Requested API method unavailable";
        CODE_DESCRIPTIONS[EXTERNAL_APPLICATION_ERROR] = "Third party application error";
        CODE_DESCRIPTIONS[RESOURCE_NOT_AVAILABLE] = "Requested resource is not available";
        CODE_DESCRIPTIONS[OPERATION_NOT_ALLOWED] = "Request not allowed in the current state of resource";
        CODE_DESCRIPTIONS[EXTERNAL_CONNECTION_ERROR] = "Cannot connect to external resource";
        CODE_DESCRIPTIONS[NOT_AUTHENTICATED] = "Not authenticated";
        CODE_DESCRIPTIONS[WEAK_PASSWORD] = "The password is not strong enough";
    }

    public static String codeDescription(int code) {
        return (code >= 0) && (code < CODE_DESCRIPTIONS.length) ? CODE_DESCRIPTIONS[code] : null;
    }

    private static final boolean[] NO_WARN_CODES = new boolean[CODES_LENGTH];
    static {
        for (int i = WRONG_API_KEY; i <= MISSED_PARAMETER; i++) NO_WARN_CODES[i] = true;
        NO_WARN_CODES[WRONG_USERNAME_PASSWORD] = true;
        NO_WARN_CODES[DUPLICATE_USERNAME] = true;
        NO_WARN_CODES[DUPLICATE_ENTITY] = true;
        NO_WARN_CODES[RESOURCE_NOT_AVAILABLE] = true;
        NO_WARN_CODES[OPERATION_NOT_ALLOWED] = true;
    }

    public static boolean noWarn(byte code) {
        return (code >= 0) && (code < NO_WARN_CODES.length) && NO_WARN_CODES[code];
    }
}
