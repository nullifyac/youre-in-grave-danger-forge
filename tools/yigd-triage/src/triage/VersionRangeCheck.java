package triage;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

/** Uses Forge's Maven version semantics rather than a string or SemVer comparison. */
public final class VersionRangeCheck {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected RANGE followed by VERSION=true|false checks");
        }
        VersionRange range = VersionRange.createFromVersionSpec(args[0]);
        boolean failed = false;
        for (int i = 1; i < args.length; i++) {
            int separator = args[i].lastIndexOf('=');
            if (separator < 1) {
                throw new IllegalArgumentException("Invalid expectation: " + args[i]);
            }
            String version = args[i].substring(0, separator);
            String expectedText = args[i].substring(separator + 1);
            if (!expectedText.equals("true") && !expectedText.equals("false")) {
                throw new IllegalArgumentException("Expected true or false: " + args[i]);
            }
            boolean expected = Boolean.parseBoolean(expectedText);
            boolean actual = range.containsVersion(new DefaultArtifactVersion(version));
            System.out.printf("%s: %s contains %s = %s (expected %s)%n",
                    actual == expected ? "PASS" : "FAIL", args[0], version, actual, expected);
            failed |= actual != expected;
        }
        if (failed) {
            System.exit(1);
        }
    }
}
