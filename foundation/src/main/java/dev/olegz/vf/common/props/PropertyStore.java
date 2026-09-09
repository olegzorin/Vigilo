package dev.olegz.vf.common.props;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.common.util.AesEncryptor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class which implements several methods to access values of properties stored in property files.
 * All the property files must be located in %HOME_DIR%/config/properties/ or in resources.
 * Property files are watched for changes.
 */
public final class PropertyStore {
    private static final Logger logger = LoggerFactory.getLogger(PropertyStore.class);

    // Effective property values from all sources
    private static final ConcurrentHashMap<String, String> effectiveProps = new ConcurrentHashMap<>(1000);
    // Programmatically set properties override file properties
    private static final ConcurrentHashMap<String, String> programmaticOverrides = new ConcurrentHashMap<>(300);

    // Parsed string-list properties
    private static final ConcurrentHashMap<String, ParsedStringProperty> parsedStringProps = new ConcurrentHashMap<>();

    private static volatile boolean configured = false;
    private static volatile Thread watchThread;
    private static volatile boolean watching;

    private static AesEncryptor encryptor;
    private static final String ENCRYPTION_PREFIX = "***";
    private static final int ENCRYPTION_PREFIX_LEN = ENCRYPTION_PREFIX.length();

    // property files extension
    private static final String PROPERTY_FILE_SUFFIX = ".properties";

    // property file time stamps to check if they were changed before reloading
    private static final ConcurrentHashMap<String, Long> propertyFileTimestamps = new ConcurrentHashMap<>();

    private static volatile EnumMap<IntProp, IntPropValue> intPropMap;
    private static volatile EnumMap<DurationProp, DurationPropValue> durationPropMap;

    private record IntPropValue(int intValue, String rawValue) {
        @Override
        @SuppressWarnings("NullableProblems")
        public String toString() {
            return Integer.toString(intValue);
        }
    }

    private record DurationPropValue(Duration duration, String rawValue) {
        @Override
        @SuppressWarnings("NullableProblems")
        public String toString() {
            return duration.toString();
        }
    }

    private record ParsedStringProperty(String rawValue, List<String> values) {
    }

    /**
     * Do not allow instantiation of this class.
     */
    private PropertyStore() {
    }

    /**
     * Initialize the property system.
     */
    private static synchronized void init() {
        if (configured) return;

        logger.info("Initialize the property system.");

        Path propertyDir = VigiloEnvironment.requirePropertyFilesDir();

        try {
            loadFromFiles(propertyDir);
        } catch (Exception e) {
            logger.error("Exception during PropertyStore initialization", e);
        }

        byte[] kek = VigiloEnvironment.getKek();
        if (kek != null) {
            encryptor = new AesEncryptor(kek);
        }

        configured = true;

        initTypedProps();

        startPropertyFilesWatcher(propertyDir);

        logger.info("The property system initialized.");
    }

    /**
     * Ensure the property system is initialized and property-file watching is started.
     * Idempotent: once the store is configured, subsequent calls are no-ops.
     */
    public static void start() {
        init();
    }

    public static void shutdown() {
        watching = false;
        Thread currentWatchThread = watchThread;
        if (currentWatchThread != null) {
            currentWatchThread.interrupt();
        }
    }

    public static String getString(String name, boolean trimToNull) {
        String value = effectiveProps.get(name);
        return trimToNull ? StringUtils.trimToNull(value) : value;
    }

    public static String getString(String name) {
        return getString(name, false);
    }

    /**
     * Set a property
     * @param name  Property name
     * @param value Property value
     * @return true if the property value updated
     */
    public static boolean set(String name, String value) {
        // this property value overwrites a property from files
        String oldValue = programmaticOverrides.put(name, value);
        if ((oldValue != null) && oldValue.equals(value)) return false;

        setPropertyValue(name, value);
        return true;
    }

    /**
     * Get a property.
     * @param name         Property name.
     * @param defaultValue Default property value.
     * @return Property value or default.
     */
    public static String getString(String name, String defaultValue) {
        String value = getString(name);
        return value != null ? value : defaultValue;
    }

    /**
     * Get comma-separated non-empty values of a property.
     * @param name property name
     * @return an immutable list of non-empty property values, or {@code null} when the property is absent
     */
    public static List<String> getList(String name) {
        String rawValue = getString(name);
        if (rawValue == null) {
            parsedStringProps.remove(name);
            return null;
        }

        return parsedStringProps.compute(name, (_, cached) -> {
            if ((cached != null) && cached.rawValue().equals(rawValue)) return cached;

            String[] splitValues = rawValue.split("\\s*,\\s*");
            ArrayList<String> values = new ArrayList<>(splitValues.length);
            for (String value : splitValues) {
                if (!value.isEmpty()) values.add(value);
            }

            return new ParsedStringProperty(rawValue, List.copyOf(values));
        }).values();
    }

    /**
     * Get an integer property.
     * @param name         Property name.
     * @param defaultValue Default property value.
     * @return Property value or default.
     */
    public static int getInt(String name, int defaultValue) {
        String value = getString(name);

        if ((value != null) && (value.length() > 0)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.error("Exception in parsing of the int property value: " + name + "='" + value + "'");
            }
        }

        return defaultValue;
    }

    public static int getInt(IntProp intProp) {
        IntPropValue value = intPropMap.get(intProp);
        return value != null ? value.intValue : intProp.defaultValue;
    }

    public static Duration getDuration(DurationProp durationProp) {
        DurationPropValue value = durationPropMap.get(durationProp);
        return value != null ? value.duration : durationProp.defaultValue;
    }


    /**
     * Get a long property.
     * @param name         Property name.
     * @param defaultValue Default property value.
     * @return Property value or default.
     */
    public static long getLong(String name, long defaultValue) {
        String value = getString(name);

        if ((value != null) && (value.length() > 0)) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                logger.error("Exception in parsing of the long property value: " + name + "='" + value + "'");
            }
        }

        return defaultValue;
    }

    /**
     * Get a boolean property.
     * @param name         Property name.
     * @param defaultValue Default property value.
     * @return Property value or default.
     */
    public static boolean getBoolean(String name, boolean defaultValue) {
        String value = getString(name);
        return value == null ? defaultValue : "true".equals(value);
    }

    /**
     * Get and decrypt property value
     * @param name property name
     * @return Decrypted value or value itself
     */
    public static String decrypt(String name) {
        return decryptValue(getString(name));
    }

    /**
     * Decrypt value
     * @return Decrypted value or value itself
     */
    public static String decryptValue(String value) {
        if ((value != null) && value.startsWith(ENCRYPTION_PREFIX)) {
            try {
                return encryptor.decrypt(value.substring(ENCRYPTION_PREFIX_LEN));
            } catch (Exception e) {
                logger.error("Exception in decrypting property " + StringUtils.truncate(value, 10) + '\n' + e);
            }
        }
        return value;
    }

    public static String encrypt(String value) throws GeneralSecurityException {
        return ENCRYPTION_PREFIX + encryptor.encrypt(value);
    }

    // Property Loading

    /**
     * Load system properties from files located in the properties dir
     */
    private static void loadFromFiles(Path propertyDir) {
        logger.debug(">loadFromFile() property directory: " + propertyDir);
        if (propertyDir == null) {
            logger.error("Empty properties directory setting value");
            return;
        }

        try (DirectoryStream<Path> files = Files.newDirectoryStream(propertyDir, "*" + PROPERTY_FILE_SUFFIX)) {
            boolean empty = true;

            for (Path file : files) {
                empty = false;
                try {
                    loadFromFile(file);
                } catch (Exception e) {
                    logger.error("Exception in reading property file " + file, e);
                }
            }
            if (empty) {
                logger.warn("Empty properties directory " + propertyDir);
            }
        } catch (Exception ie) {
            logger.error("Exception in reading property files: ", ie);
        }

        logger.debug("<loadFromFile()");
    }

    /**
     * Load properties from a file
     * @param file property file
     */
    private static void loadFromFile(Path file)
        throws IOException
    {
        BasicFileAttributes fileAttributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        String filePath = file.toRealPath().toString();

        Long fileLoadTime = propertyFileTimestamps.get(filePath);
        long lastModifiedTime = fileAttributes.lastModifiedTime().toMillis();

        if ((fileLoadTime == null) || (lastModifiedTime != fileLoadTime)) {
            Properties fileProperties = new Properties();
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                fileProperties.load(reader);
            }

            propertyFileTimestamps.put(filePath, lastModifiedTime);

            for (String name : fileProperties.stringPropertyNames()) {
                if (!programmaticOverrides.containsKey(name)) {
                    setPropertyValue(name, fileProperties.getProperty(name));
                }
            }
        }
    }

    private static void setPropertyValue(String name, String value) {
        effectiveProps.put(name, value);
    }

    private static void startPropertyFilesWatcher(Path propertyDir) {
        if (propertyDir == null) {
            logger.error("Exception in property files watching: properties directory not set");
            return;
        }

        watching = true;
        watchThread = Thread.ofVirtual().name("PropertyFilesWatcher").start(() -> {
                try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                    propertyDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                    while (watching) {
                        checkForWatchEvents(watchService, propertyDir);
                    }
                } catch (IOException | InterruptedException ignore) {
                } catch (Exception e) {
                    logger.error("Exception in property files watching", e);
                }
            }
        );
    }

    private static void checkForWatchEvents(WatchService watchService, Path propertyDir) throws InterruptedException {
        WatchKey key = watchService.take();

        Thread.sleep(1000L); // prevent receiving duplicate event

        List<WatchEvent<?>> events = key.pollEvents();
        if ((events == null) || events.isEmpty()) {
            key.reset();
            return;
        }

        boolean hasUpdates = false;
        for (WatchEvent<?> e : events) {
            WatchEvent.Kind<?> kind = e.kind();
            Object context = e.context();

            if (context instanceof Path contextPath) {
                Path file = contextPath;
                String fileName = file.getFileName().toString();

                if (!fileName.endsWith(PROPERTY_FILE_SUFFIX)) continue;

                hasUpdates = true;

                if (logger.isInfoEnabled()) {
                    logger.info("Property file event: " + kind.name() +
                        ", path=" + file + ", absolute=" + file.isAbsolute());
                }

                if (!file.isAbsolute()) {
                    file = propertyDir.resolve(contextPath);
                }

                try {
                    loadFromFile(file);
                } catch (Exception ee) {
                    logger.error("Exception in refreshing properties from " + file, ee);
                }
            } else {
                logger.warn("Other event: kind=" + kind.name() + ", context=" + context);
            }
        }

        if (hasUpdates) updateTypedProps();

        key.reset();
    }

    // Typed Properties

    private static void initTypedProps() {
        intPropMap = new EnumMap<>(IntProp.class);
        for (IntProp prop : IntProp.values()) {
            String value = effectiveProps.get(prop.label);
            if ((value != null) && (value.length() > 0)) {
                putIntPropValue(intPropMap, prop, value);
            }
        }

        durationPropMap = new EnumMap<>(DurationProp.class);
        for (DurationProp prop : DurationProp.values()) {
            String value = effectiveProps.get(prop.label);
            if ((value != null) && (value.length() > 0)) {
                putDurationPropValue(durationPropMap, prop, value);
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("Initialized int properties: {}", intPropMap);
            logger.info("Initialized duration properties: {}", durationPropMap);
        }
    }

    public static void updateTypedProps() {
        updateTypedProps(effectiveProps);
    }

    static void updateTypedProps(Map<String, String> propsMap) {
        EnumMap<IntProp, IntPropValue> intMap = new EnumMap<>(intPropMap);
        EnumMap<DurationProp, DurationPropValue> durationMap = new EnumMap<>(durationPropMap);

        boolean updated = false;
        for (IntProp prop : IntProp.values()) {
            String value = propsMap.get(prop.label);
            if ((value == null) || (value.length() == 0)) continue;

            IntPropValue intPropValue = intMap.get(prop);
            if ((intPropValue != null) && value.equals(intPropValue.rawValue)) continue;

            putIntPropValue(intMap, prop, value);
            updated = true;
        }

        for (DurationProp prop : DurationProp.values()) {
            String value = propsMap.get(prop.label);
            if ((value == null) || (value.length() == 0)) continue;

            DurationPropValue durationPropValue = durationMap.get(prop);
            if ((durationPropValue != null) && value.equals(durationPropValue.rawValue)) continue;

            putDurationPropValue(durationMap, prop, value);
            updated = true;
        }

        if (updated) {
            intPropMap = intMap;
            durationPropMap = durationMap;
            logger.info("Updated int properties: {}", intPropMap);
            logger.info("Updated duration properties: {}", durationPropMap);
        }
    }

    private static void putIntPropValue(EnumMap<IntProp, IntPropValue> intMap, IntProp prop, String value) {
        try {
            intMap.put(prop, new IntPropValue(Integer.parseInt(value), value));
        } catch (NumberFormatException e) {
            logger.error("Exception in parsing int property value: " + prop + '(' + prop.label + ")='" + value + "'");
        }
    }

    private static void putDurationPropValue(
        EnumMap<DurationProp, DurationPropValue> durationMap, DurationProp prop, String value
    ) {
        try {
            durationMap.put(prop, new DurationPropValue(parseDuration(value), value));
        } catch (RuntimeException e) {
            logger.error("Exception in parsing duration property value: " + prop + '(' + prop.label + ")='" + value + "'");
        }
    }

    static Duration parseDuration(String value) {
        if ((value == null) || value.isEmpty()) {
            throw new IllegalArgumentException("Duration value is empty");
        }

        if (value.endsWith("ms")) {
            return Duration.ofMillis(parseDurationAmount(value, 2));
        }

        long amount = parseDurationAmount(value, 1);
        return switch (value.charAt(value.length() - 1)) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Unsupported duration unit: " + value);
        };
    }

    private static long parseDurationAmount(String value, int unitLength) {
        int amountLength = value.length() - unitLength;
        if (amountLength <= 0) throw new IllegalArgumentException("Duration amount is missing: " + value);

        for (int i = 0; i < amountLength; i++) {
            char c = value.charAt(i);
            if ((c < '0') || (c > '9')) {
                throw new IllegalArgumentException("Invalid duration amount: " + value);
            }
        }
        return Long.parseLong(value.substring(0, amountLength));
    }

    // Only in unit tests
    public static void set(IntProp prop, int value) {
        intPropMap.put(prop, new IntPropValue(value, Integer.toString(value)));
    }

    // Only in unit tests
    public static void set(DurationProp prop, Duration value) {
        if (value.isNegative()) {
            throw new IllegalArgumentException("Duration must not be negative: " + value);
        }
        durationPropMap.put(prop, new DurationPropValue(value, value.toString()));
    }
}
