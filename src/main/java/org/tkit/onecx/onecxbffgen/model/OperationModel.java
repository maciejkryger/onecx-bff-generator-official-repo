package org.tkit.onecx.onecxbffgen.model;
public record OperationModel(String operationId, String httpMethod, String path,
                             String requestBodyType, String responseType, int successStatusCode,
                             String resolvedBackendOperationId, String resolvedBackendRequestBodyType,
                             String resolvedBackendResponseType, String resolvedBackendPath) {
    public OperationModel(String operationId, String httpMethod, String path,
                          String requestBodyType, String responseType) {
        this(operationId, httpMethod, path, requestBodyType, responseType, 0, null, null, null, null);
    }
    public OperationModel(String operationId, String httpMethod, String path,
                          String requestBodyType, String responseType, int successStatusCode) {
        this(operationId, httpMethod, path, requestBodyType, responseType, successStatusCode, null, null, null, null);
    }
    public boolean hasRequestBody() {
        return requestBodyType != null && !requestBodyType.isBlank();
    }
    public boolean hasResponseBody() {
        return responseType != null && !responseType.isBlank();
    }
    public String resolvedBackendOperationId() {
        return backendOperationId != null && !backendOperationId.isBlank() ? backendOperationId : operationId;
    }
    public String resolvedBackendRequestBodyType() {
        return backendRequestBodyType != null && !backendRequestBodyType.isBlank() ? backendRequestBodyType : requestBodyType;
    }
    public String resolvedBackendResponseType() {
        return backendResponseType != null && !backendResponseType.isBlank() ? backendResponseType : responseType;
    }
    public String resolvedBackendPath() {
        return backendPath != null && !backendPath.isBlank() ? backendPath : path;
    }
}
