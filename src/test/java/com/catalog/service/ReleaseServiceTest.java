package com.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReleaseServiceTest {

    @ParameterizedTest
    @CsvSource({
            "1.2.3,   1, 2, 3, ",
            "0.0.1,   0, 0, 1, ",
            "15.3.0,  15, 3, 0, ",
            "100.200.300, 100, 200, 300, "
    })
    void parseSemVer_stableVersions(String version, int major, int minor, int patch, String prerelease) {
        var result = ReleaseService.parseSemVer(version);
        assertThat(result.major()).isEqualTo(major);
        assertThat(result.minor()).isEqualTo(minor);
        assertThat(result.patch()).isEqualTo(patch);
        assertThat(result.prerelease()).isNull();
        assertThat(result.prereleaseSortKey()).isNull();
    }

    @Test
    void parseSemVer_stripsLeadingV() {
        var result = ReleaseService.parseSemVer("v2.1.0");
        assertThat(result.major()).isEqualTo(2);
        assertThat(result.minor()).isEqualTo(1);
        assertThat(result.patch()).isEqualTo(0);
        assertThat(result.prerelease()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "1.0.0-alpha,       alpha",
            "1.0.0-alpha.1,     alpha.1",
            "1.0.0-alpha.beta,  alpha.beta",
            "1.0.0-beta,        beta",
            "1.0.0-beta.2,      beta.2",
            "1.0.0-beta.11,     beta.11",
            "1.0.0-rc.1,        rc.1",
            "1.0.0-0.3.7,       0.3.7",
            "1.0.0-x.7.z.92,    x.7.z.92"
    })
    void parseSemVer_prereleaseVersions(String version, String expectedPrerelease) {
        var result = ReleaseService.parseSemVer(version);
        assertThat(result.major()).isEqualTo(1);
        assertThat(result.minor()).isEqualTo(0);
        assertThat(result.patch()).isEqualTo(0);
        assertThat(result.prerelease()).isEqualTo(expectedPrerelease);
        assertThat(result.prereleaseSortKey()).isNotNull();
    }

    @Test
    void parseSemVer_ignoresBuildMetadata() {
        var result = ReleaseService.parseSemVer("1.0.0-beta.1+build.123");
        assertThat(result.major()).isEqualTo(1);
        assertThat(result.prerelease()).isEqualTo("beta.1");

        var stable = ReleaseService.parseSemVer("1.0.0+20130313144700");
        assertThat(stable.major()).isEqualTo(1);
        assertThat(stable.prerelease()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "1.2",
            "abc",
            "1.2.3.4",
            "01.2.3",
            "1.02.3",
            "1.2.03",
            "1.0.0-al..pha",
            "''"
    })
    void parseSemVer_invalidVersions(String version) {
        assertThatThrownBy(() -> ReleaseService.parseSemVer(version))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseSemVer_nullThrows() {
        assertThatThrownBy(() -> ReleaseService.parseSemVer(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildPrereleaseSortKey_numericIdentifiersPadded() {
        assertThat(ReleaseService.buildPrereleaseSortKey("1"))
                .isEqualTo("0~0000000001");
        assertThat(ReleaseService.buildPrereleaseSortKey("11"))
                .isEqualTo("0~0000000011");
    }

    @Test
    void buildPrereleaseSortKey_alphanumericIdentifiersPrefixed() {
        assertThat(ReleaseService.buildPrereleaseSortKey("alpha"))
                .isEqualTo("1~alpha");
        assertThat(ReleaseService.buildPrereleaseSortKey("beta"))
                .isEqualTo("1~beta");
    }

    @Test
    void buildPrereleaseSortKey_mixedIdentifiers() {
        assertThat(ReleaseService.buildPrereleaseSortKey("beta.2"))
                .isEqualTo("1~beta.0~0000000002");
        assertThat(ReleaseService.buildPrereleaseSortKey("alpha.1"))
                .isEqualTo("1~alpha.0~0000000001");
    }

    @Test
    void buildPrereleaseSortKey_correctLexicographicOrder() {
        // SemVer 2.0.0 spec precedence chain:
        // alpha < alpha.1 < alpha.beta < beta < beta.2 < beta.11 < rc.1
        String alpha = ReleaseService.buildPrereleaseSortKey("alpha");
        String alpha1 = ReleaseService.buildPrereleaseSortKey("alpha.1");
        String alphaBeta = ReleaseService.buildPrereleaseSortKey("alpha.beta");
        String beta = ReleaseService.buildPrereleaseSortKey("beta");
        String beta2 = ReleaseService.buildPrereleaseSortKey("beta.2");
        String beta11 = ReleaseService.buildPrereleaseSortKey("beta.11");
        String rc1 = ReleaseService.buildPrereleaseSortKey("rc.1");

        assertThat(alpha).isLessThan(alpha1);
        assertThat(alpha1).isLessThan(alphaBeta);
        assertThat(alphaBeta).isLessThan(beta);
        assertThat(beta).isLessThan(beta2);
        assertThat(beta2).isLessThan(beta11);
        assertThat(beta11).isLessThan(rc1);
    }

    @Test
    void buildPrereleaseSortKey_numericLessThanAlphanumeric() {
        // Per SemVer spec: numeric identifiers always have lower precedence than alphanumeric
        String numeric = ReleaseService.buildPrereleaseSortKey("1");
        String alpha = ReleaseService.buildPrereleaseSortKey("alpha");
        assertThat(numeric).isLessThan(alpha);
    }
}
