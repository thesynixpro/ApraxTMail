package dev.aprax.tmaii.backend.utils

import io.ktor.http.HttpStatusCode

open class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val retryAfterSeconds: Int? = null,
    val details: Map<String, String>? = null,
) : Exception(message)

class BadRequestException(message: String, code: String = "bad_request") :
    ApiException(HttpStatusCode.BadRequest, code, message)

class UnauthorizedException(message: String = "Unauthorized", code: String = "unauthorized") :
    ApiException(HttpStatusCode.Unauthorized, code, message)

class ForbiddenException(message: String = "Forbidden", code: String = "forbidden") :
    ApiException(HttpStatusCode.Forbidden, code, message)

class NotFoundException(message: String = "Not found", code: String = "not_found") :
    ApiException(HttpStatusCode.NotFound, code, message)

class ConflictException(message: String, code: String = "conflict") :
    ApiException(HttpStatusCode.Conflict, code, message)

class TooManyRequestsException(
    message: String = "Rate limit exceeded",
    code: String = "rate_limit_exceeded",
    retryAfterSeconds: Int? = null,
) : ApiException(HttpStatusCode.TooManyRequests, code, message, retryAfterSeconds)

class ProviderUnavailableException(
    message: String,
    code: String = "provider_unavailable",
    val providerId: String,
) : ApiException(HttpStatusCode.BadGateway, code, message)

class ProviderErrorException(
    message: String,
    code: String,
    status: HttpStatusCode,
    val providerId: String,
    retryAfterSeconds: Int? = null,
) : ApiException(status, code, message, retryAfterSeconds)

class NotSupportedOperationException(message: String) :
    ApiException(HttpStatusCode.NotImplemented, "not_supported", message)
