package dev.olegz.vf.core.domain.lambdabuild;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

import dev.olegz.vf.aws.ecr.EcrPublicSupport;
import dev.olegz.vf.aws.s3.S3Presigning;
import dev.olegz.vf.common.exception.WrongParameterValueException;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import org.apache.commons.text.StringSubstitutor;

public final class BuildConfig {
    // Processor architecture
    public static final byte ARCH_X86 = 1;
    private static final byte ARCH_ARM = 2;

    private static final String lambdaModule = "lambda";
    private static final String lambdaHandler = "lambda_handler";

    /**
     * Dockerfile tempate for 2-stage build of a Docker image for AWS Lambda.
     * <p>
     * The first stage uses an official Python image based on Linux.
     * This image has installed tar, gcc and other utilities with which the lambda
     * source files and requirements (including binary libs) are fullfilled
     * and compiled.
     * <p>
     * The second stage uses an official Amazon Python image for Lambda (base image).
     * The final image is created by adding a layer to the base image that includes
     * the files built in the previous stage.
     */
    private static final String dockerfileTemplate = """
        FROM ${stageImage} AS builder
        RUN mkdir -p /var/build
        WORKDIR /var/build
        RUN curl --silent --show-error '${sourceUrl}' | tar -x -C .
        RUN if [ -f requirements.txt ]; then pip --disable-pip-version-check install --requirement requirements.txt --target .; fi
        RUN python -m compileall -q -b .
        RUN find . -type f -a -name '*.py' -print0 | xargs -0 rm -f

        FROM ${baseImage}
        COPY --from=builder /var/build /var/task
        CMD ["lambda.lambda_handler"]
        """;

    public static String makeDockerfile(LambdaCodeUpload upload) {
        URL sourceReadUrl = S3Presigning.makeS3ReadPresignedUrl(
            Lambda.S3_BUCKET, upload.codeObjectId, 300_000, null
        );

        StringSubstitutor substitutor = new StringSubstitutor(
            Map.of("stageImage", upload.stageImage,
                "sourceUrl", sourceReadUrl.toString(),
                "baseImage", upload.baseImage)
        );

        return substitutor.replace(BuildConfig.dockerfileTemplate);
    }

    final byte arch;
    final String baseImage;
    final String stageImage;

    private BuildConfig(byte arch, String baseImage, String stageImage) {
        this.arch = arch;
        this.baseImage = baseImage;
        this.stageImage = stageImage;
    }

    @Override
    public String toString() {
        return "arch=" + arch + ", baseImage=" + baseImage + ", stageImage=" + stageImage;
    }

    /**
     * Creates the container-image build configuration for the requested Python build target.
     * <p>
     * {@code buildTarget} combines the Python version and processor architecture in
     * {@code <python-version>-<architecture>} form, for example {@code 3.12-arm64}
     * or {@code 3.11-x86_64}. The target must identify a supported AWS Lambda Python
     * base image. Its Python version selects the Docker build-stage image, while its
     * architecture selects both the build-stage image variant and the architecture of
     * the deployed Lambda function.
     *
     * @param buildTarget supported Python version and processor architecture combination
     * @param codeFiles files extracted from the lambda code archive
     * @return resolved build configuration
     * @throws WrongParameterValueException if the build target is unsupported or the
     * lambda code does not contain the required Lambda module and handler
     */
    public static BuildConfig create(String buildTarget, Map<String, byte[]> codeFiles) {
        // Returns non-null or throws exception if buildTarget is not supported
        String baseImage = EcrPublicSupport.getPythonImageUri(buildTarget);

        int index = buildTarget.lastIndexOf('-');
        String pythonVersion = buildTarget.substring(0, index);
        String architecture = buildTarget.substring(index + 1);

        byte arch = ARCH_X86;
        String stageImage = "amd64/python:" + pythonVersion; // image from docker hub
        if (architecture.equals("arm64")) {
            arch = ARCH_ARM;
            stageImage = "arm64v8/python:" + pythonVersion;
        }

        checkLambdaModule(codeFiles);

        return new BuildConfig(arch, baseImage, stageImage);
    }

    private static void checkLambdaModule(Map<String, byte[]> codeFiles) {
        byte[] lambdaModule = codeFiles.get(BuildConfig.lambdaModule + ".py");
        if (lambdaModule == null) {
            throw new WrongParameterValueException("Lambda source code must contain module '" + BuildConfig.lambdaModule + "'");
        }

        Pattern pattern = Pattern.compile("def\\s+" + lambdaHandler + "\\s*\\(");
        if (!pattern.matcher(new String(lambdaModule, StandardCharsets.UTF_8)).find()) {
            throw new WrongParameterValueException("The lambda.py file must contain definition of '" + lambdaHandler + "' function");
        }
    }

}
