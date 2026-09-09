package dev.olegz.vf.core.domain.lambdaassignment;

import dev.olegz.vf.core.domain.lambdaversion.VersionBump;
import org.junit.jupiter.api.Test;
import static dev.olegz.vf.core.domain.lambdaversion.VersionBump.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link VersionBump#next(String)}, the server-side version number derivation.
 * <p>
 * The bump is always applied to the highest version a lambda has used; these tests pin down the
 * three-component {@code major.minor.patch} output, the reset rules (a MAJOR bump zeroes minor and
 * patch, a MINOR bump zeroes patch), and the lenient parsing of absent, blank, suffixed and
 * non-numeric prior versions.
 */
class VersionBumpTest {

    @Test
    void noPriorVersion_startsFromZero() {
        // A null "highest" means the lambda has never had a version: 0.0.0 bumped by the chosen level.
        assertEquals("1.0.0", MAJOR.next(null));
        assertEquals("0.1.0", MINOR.next(null));
        assertEquals("0.0.1", PATCH.next(null));
    }

    @Test
    void blankPriorVersion_treatedAsZero() {
        assertEquals("1.0.0", MAJOR.next(""));
        assertEquals("0.1.0", MINOR.next("   "));
    }

    @Test
    void twoComponentBase_bumpsAndNormalizesToThreeComponents() {
        assertEquals("3.0.0", MAJOR.next("2.3"));
        assertEquals("2.4.0", MINOR.next("2.3"));
        assertEquals("2.3.1", PATCH.next("2.3"));
    }

    @Test
    void threeComponentBase_bumpsEachLevel() {
        assertEquals("3.0.0", MAJOR.next("2.3.1"));
        assertEquals("2.4.0", MINOR.next("2.3.1"));
        assertEquals("2.3.2", PATCH.next("2.3.1"));
    }

    @Test
    void majorBump_resetsMinorAndPatch_minorBump_resetsPatch() {
        assertEquals("11.0.0", MAJOR.next("10.7.4"));
        assertEquals("10.8.0", MINOR.next("10.7.4"));
        assertEquals("10.7.5", PATCH.next("10.7.4"));
    }

    @Test
    void multiDigitComponents_incrementNumerically() {
        assertEquals("11.0.0", MAJOR.next("10.20.30"));
        assertEquals("10.21.0", MINOR.next("10.20.30"));
        assertEquals("10.20.31", PATCH.next("10.20.30"));
    }

    @Test
    void suffixAfterNumericComponents_isIgnored() {
        // compareVersions-style leniency: "-beta" / "-rc1" suffixes don't affect the numeric bump.
        assertEquals("2.4.0", MINOR.next("2.3-beta"));
        assertEquals("1.2.4", PATCH.next("1.2.3-rc1"));
        assertEquals("3.0.0", MAJOR.next("2.3.1-SNAPSHOT"));
    }

    @Test
    void nonNumericLeadingComponent_treatedAsZero() {
        // Legacy hand-typed versions like "v1" have no leading digits, so they parse as 0.0.0.
        assertEquals("1.0.0", MAJOR.next("v1"));
        assertEquals("0.1.0", MINOR.next("v1"));
    }

    @Test
    void componentsBeyondThird_areIgnored() {
        assertEquals("1.2.4", PATCH.next("1.2.3.9"));
        assertEquals("1.3.0", MINOR.next("1.2.3.9"));
    }
}
