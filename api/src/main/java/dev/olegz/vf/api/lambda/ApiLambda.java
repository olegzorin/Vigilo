package dev.olegz.vf.api.lambda;

import dev.olegz.vf.core.domain.lambdaversion.Lambda;

public class ApiLambda {
    public final int lambdaId;
    public final String lambdaName;
    public String name;
    public String author;
    public String description;

    public ApiLambda(Lambda lambda) {
        this.lambdaId = lambda.lambdaId;
        this.lambdaName = lambda.lambdaName;
    }
}
