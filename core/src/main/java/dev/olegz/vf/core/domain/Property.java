package dev.olegz.vf.core.domain;


import org.apache.commons.lang3.StringUtils;

public class Property {
    /** User properties **/
    public static final String API_PROPERTY_PREFIX = "vf.api.";
    public static final String USER_TIME_FORMAT = API_PROPERTY_PREFIX + "user-time";
    public static final String USER_TIME_FORMAT_12H = "12";
    public static final String USER_SIGNATURE_PUBLIC_KEY = "SPK.";

    /** Last device command ID */
    public static final String COMMAND_ID = "commandId";

    // Location narrative data max value per location
    public static final String NARRATIVE_MAX_DATE = "NMD";

    // OnScreen link
    public static final String LINK = "link";
    // AG, FreeUs device phone number
    public static final String PHONE_NUMBER = "phone";

    public static final int MAX_PROPERTY_NAME_LEN = 250;
    public static final int MAX_USER_PROPERTY_NAME_LEN_NO_PREFIX = MAX_PROPERTY_NAME_LEN - API_PROPERTY_PREFIX.length();
    public static final int MAX_USER_PROPERTY_VALUE_LEN = 10000;
    public static final int MAX_DEVICE_VALUE_LEN = 10000;
    public static final int MAX_INDEX_LEN = 50;

    private static final String NULL_INDEX = "N";

    public String name;
    public String index;
    public String value;
    public Byte displayType;
    public boolean hidden;

    public Property() {
    }

    public Property(String name, String value, Byte displayType) {
        this.name = name;
        this.value = value;
        this.displayType = displayType;
    }

    public Property(String name, String index, String value, boolean hidden) {
        this.name = name;
        this.index = index;
        this.value = value;
        this.hidden = hidden;
    }

    @Override
    public String toString() {
        return "{" + name + (index != null ? ", #" + index : "") + "=" + value + (hidden ? ", hidden" : "") + '}';
    }

    public void prepareDeviceProperty() {
        index = index == null ? NULL_INDEX : StringUtils.truncate(index, MAX_INDEX_LEN);
        value = StringUtils.truncate(StringUtils.trimToNull(value), MAX_DEVICE_VALUE_LEN);
    }

    public void prepareSelected() {
        if (NULL_INDEX.equals(index)) index = null;
    }

    public static String makePersistentIndex(String index) {
        return index == null ? NULL_INDEX : index;
    }
}
