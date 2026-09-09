package dev.olegz.vf.aws;

import java.io.IOException;
import java.nio.file.Files;

import dev.olegz.vf.common.props.PropertyStore;

/** Initializes isolated local AWS storage before a local-client test class is initialized. */
public abstract class LocalAwsTestSupport {

    static {
        try {
            PropertyStore.set("vf.aws.local.root", Files.createTempDirectory("vf-aws-test").toString());
            PropertyStore.set("vf.aws.local", "true");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
