package org.tkit.onecx.onecxbffgen.model;

public record OperationModel(String operationId, String httpMethod, String path,
                             String requestBodyType, String responseType, int successStatusCode) {

    public OperationModel(String operationId, String httpMethod, String path,
                          String requestBodyType, String responseType) {
        this(operationId, httpMethod, path, requestBodyType, responseType, 0);
    }

    public boolean hasRequestBody() {
        return requestBodyType != null && !requestBodyType.isBlank();
    }

    public boolean hasResponseBody() {
        return responseType != null && !responseType.isBlank();
    }
}

