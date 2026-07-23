package xyz.malefic.koven.api

import xyz.malefic.koven.core.field.CookieField
import xyz.malefic.koven.core.field.HeaderField
import xyz.malefic.koven.core.field.HeaderProvider
import xyz.malefic.koven.core.field.PathField
import xyz.malefic.koven.core.field.PathProvider
import xyz.malefic.koven.core.field.QueryField
import xyz.malefic.koven.core.field.QueryProvider

/**
 * A builder for [ApiContract] types.
 */
@Suppress("ktlint:standard:max-line-length")
class ApiContractBuilder<Req, Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider>(
    private val path: String,
    private val httpMethod: HttpMethod = HttpMethod.POST,
    private val requiredRequestHeaders: List<HeaderField<*>> = emptyList(),
    private val requiredResponseHeaders: List<HeaderField<*>> = emptyList(),
    private val requestHeaderDecoder: HeaderField<ReqH>,
    private val responseHeaderDecoder: HeaderField<ResH>,
    private val pathDecoder: PathField<PathP>,
    private val queryDecoder: QueryField<QueryP>,
    private val queryParams: List<String> = emptyList(),
    private val requiredCookies: List<CookieField<*>> = emptyList(),
    private val requestFormat: BodyFormat<Req>,
    private val responseFormat: BodyFormat<Res>,
    private val isProtected: Boolean = false,
) {
    /**
     * Sets the request body format.
     */
    fun requestFormat(format: BodyFormat<Req>) = copy<ReqH, ResH, PathP, QueryP>(requestFormat = format)

    /**
     * Sets the response body format.
     */
    fun responseFormat(format: BodyFormat<Res>) = copy<ReqH, ResH, PathP, QueryP>(responseFormat = format)

    /**
     * Sets the HTTP method.
     */
    fun method(httpMethod: HttpMethod) = copy<ReqH, ResH, PathP, QueryP>(httpMethod = httpMethod)

    /**
     * Marks the endpoint as requiring authentication.
     */
    fun protected() = copy<ReqH, ResH, PathP, QueryP>(isProtected = true)

    /**
     * Sets the request header decoder and the set of required headers for validation.
     *
     * @param decoder The [HeaderField] to decode the request headers.
     * @param required The set of required headers. If empty, the decoder is used as the required header(s), though flattened (such that composite headers pass in their constituent fields).
     */
    fun <NewReqH : HeaderProvider> requestHeaders(
        decoder: HeaderField<NewReqH>,
        vararg required: HeaderField<*>,
    ): ApiContractBuilder<Req, Res, NewReqH, ResH, PathP, QueryP> =
        copy(
            requestHeaderDecoder = decoder,
            requiredRequestHeaders = if (required.isEmpty()) decoder.flatten() else required.toList(),
        )

    /**
     * Sets the response header decoder and the set of required headers for validation.
     *
     * @param decoder The [HeaderField] to decode the response headers.
     * @param required The set of required headers. If empty, the decoder is used as the required header(s), though flattened (such that composite headers pass in their constituent fields).
     */
    fun <NewResH : HeaderProvider> responseHeaders(
        decoder: HeaderField<NewResH>,
        vararg required: HeaderField<*>,
    ): ApiContractBuilder<Req, Res, ReqH, NewResH, PathP, QueryP> =
        copy(
            responseHeaderDecoder = decoder,
            requiredResponseHeaders = if (required.isEmpty()) decoder.flatten() else required.toList(),
        )

    /**
     * Sets the path parameter decoder.
     *
     * Rather than through a function like [query], path params are defined through the path string itself, e.g. "/users/{userId}/posts/{postId}". The decoder is used to decode the path parameters into a [PathProvider] type.
     */
    fun <NewPathP : PathProvider> path(decoder: PathField<NewPathP>): ApiContractBuilder<Req, Res, ReqH, ResH, NewPathP, QueryP> =
        copy(pathDecoder = decoder)

    /**
     * Sets the query parameter decoder and the list of allowed query parameter keys.
     *
     * @param decoder The [QueryField] to decode the query parameters.
     * @param params The set of allowed query parameter keys. If empty, the [QueryField.fields] are used.
     */
    fun <NewQueryP : QueryProvider> query(
        decoder: QueryField<NewQueryP>,
        vararg params: String,
    ): ApiContractBuilder<Req, Res, ReqH, ResH, PathP, NewQueryP> =
        copy(
            queryDecoder = decoder,
            queryParams = if (params.isEmpty()) decoder.fields else params.toList(),
        )

    /**
     * Sets the set of required cookies for validation.
     *
     * @param required The set of required cookies.
     */
    fun cookies(vararg required: CookieField<*>): ApiContractBuilder<Req, Res, ReqH, ResH, PathP, QueryP> =
        copy(requiredCookies = required.toList())

    @Suppress("UNCHECKED_CAST")
    private fun <NewReqH : HeaderProvider, NewResH : HeaderProvider, NewPathP : PathProvider, NewQueryP : QueryProvider> copy(
        httpMethod: HttpMethod = this.httpMethod,
        requiredRequestHeaders: List<HeaderField<*>> = this.requiredRequestHeaders,
        requiredResponseHeaders: List<HeaderField<*>> = this.requiredResponseHeaders,
        requestHeaderDecoder: HeaderField<NewReqH> = this.requestHeaderDecoder as HeaderField<NewReqH>,
        responseHeaderDecoder: HeaderField<NewResH> = this.responseHeaderDecoder as HeaderField<NewResH>,
        pathDecoder: PathField<NewPathP> = this.pathDecoder as PathField<NewPathP>,
        queryDecoder: QueryField<NewQueryP> = this.queryDecoder as QueryField<NewQueryP>,
        queryParams: List<String> = this.queryParams,
        requiredCookies: List<CookieField<*>> = this.requiredCookies,
        requestFormat: BodyFormat<Req> = this.requestFormat,
        responseFormat: BodyFormat<Res> = this.responseFormat,
        isProtected: Boolean = this.isProtected,
    ): ApiContractBuilder<Req, Res, NewReqH, NewResH, NewPathP, NewQueryP> =
        ApiContractBuilder(
            path,
            httpMethod,
            requiredRequestHeaders,
            requiredResponseHeaders,
            requestHeaderDecoder,
            responseHeaderDecoder,
            pathDecoder,
            queryDecoder,
            queryParams,
            requiredCookies,
            requestFormat,
            responseFormat,
            isProtected,
        )

    /**
     * Builds the [ApiContract] instance.
     */
    fun build(): ApiContract<Req, Res, ReqH, ResH, PathP, QueryP> =
        object : ApiContract<Req, Res, ReqH, ResH, PathP, QueryP>(
            path,
            httpMethod,
            requiredRequestHeaders,
            requiredResponseHeaders,
            requiredCookies,
            queryParams,
            requestHeaderDecoder,
            responseHeaderDecoder,
            pathDecoder,
            queryDecoder,
            requestFormat,
            responseFormat,
            isProtected,
        ) {}
}
