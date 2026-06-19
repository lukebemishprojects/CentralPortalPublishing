package dev.lukebemish.centralportalpublishing;

import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class CentralPortalBundleSpec {
    public abstract Property<String> getUsername();

    public abstract Property<String> getPassword();

    public abstract Property<String> getPortalUrl();

    public abstract Property<String> getPublishingType();

    public abstract Property<Long> getVerificationTimeoutSeconds();

    public abstract Property<Boolean> getStripSignatureHashes();

    public abstract Property<Boolean> getStripOptionalHashes();

    @Inject
    public CentralPortalBundleSpec() {
        getPortalUrl().convention("https://central.sonatype.com/");
        getVerificationTimeoutSeconds().convention(2L*60);
        getPublishingType().convention("USER_MANAGED");
        getStripOptionalHashes().convention(false);
        getStripSignatureHashes().convention(true);
    }
}
