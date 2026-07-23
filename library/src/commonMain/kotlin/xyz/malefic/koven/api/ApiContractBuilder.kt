package xyz.malefic.koven.api

import xyz.malefic.koven.core.field.CookieField
import xyz.malefic.koven.core.field.CookieProvider
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
class ApiContractBuilder<Req, Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider, CookieP : CookieProvider>(
    private val path: String,
    private val httpMethod: HttpMethod = HttpMethod.POST,
    private val requestHeaderDecoder: HeaderField<ReqH>,
    private val responseHeaderDecoder: HeaderField<ResH>,
    private val pathDecoder: PathField<PathP>,
    private val queryDecoder: QueryField<QueryP>,
    private val cookieDecoder: CookieField<CookieP>,
    private val requestFormat: BodyFormat<Req>,
    private val responseFormat: BodyFormat<Res>,
    private val isProtected: Boolean = false,
) {
    /**
     * Sets the request body format.
     */
    fun requestFormat(format: BodyFormat<Req>) = copy<ReqH, ResH, PathP, QueryP, CookieP>(requestFormat = format)

    /**
     * Sets the response body format.
     */
    fun responseFormat(format: BodyFormat<Res>) = copy<ReqH, ResH, PathP, QueryP, CookieP>(responseFormat = format)

    /**
     * Sets the HTTP method.
     */
    fun method(httpMethod: HttpMethod) = copy<ReqH, ResH, PathP, QueryP, CookieP>(httpMethod = httpMethod)

    /**
     * Marks the endpoint as requiring authentication.
     */
    fun protected() = copy<ReqH, ResH, PathP, QueryP, CookieP>(isProtected = true)

    /**
     * Sets the request header decoder.
     *
     * @param decoder The [HeaderField] to decode the request headers.
     */
    fun <NewReqH : HeaderProvider> requestHeaders(
        decoder: HeaderField<NewReqH>,
    ): ApiContractBuilder<Req, Res, NewReqH, ResH, PathP, QueryP, CookieP> =
        copy(
            requestHeaderDecoder = decoder,
        )

    /**
     * Sets the response header decoder.
     *
     * @param decoder The [HeaderField] to decode the response headers.
     */
    fun <NewResH : HeaderProvider> responseHeaders(
        decoder: HeaderField<NewResH>,
    ): ApiContractBuilder<Req, Res, ReqH, NewResH, PathP, QueryP, CookieP> =
        copy(
            responseHeaderDecoder = decoder,
        )

    /**
     * Sets the path parameter decoder.
     *
     * Rather than through a function like [query], path params are defined through the path string itself, e.g. "/users/{userId}/posts/{postId}". The decoder is used to decode the path parameters into a [PathProvider] type.
     */
    fun <NewPathP : PathProvider> path(decoder: PathField<NewPathP>): ApiContractBuilder<Req, Res, ReqH, ResH, NewPathP, QueryP, CookieP> =
        copy(pathDecoder = decoder)

    /**
     * Sets the query parameter decoder.
     *
     * @param decoder The [QueryField] to decode the query parameters.
     */
    fun <NewQueryP : QueryProvider> query(
        decoder: QueryField<NewQueryP>,
    ): ApiContractBuilder<Req, Res, ReqH, ResH, PathP, NewQueryP, CookieP> =
        copy(
            queryDecoder = decoder,
        )

    /**
     * Sets the cookie parameter decoder.
     *
     * @param decoder The [CookieField] to decode the request cookies.
     */
    fun <NewCookieP : CookieProvider> cookies(
        decoder: CookieField<NewCookieP>,
    ): ApiContractBuilder<Req, Res, ReqH, ResH, PathP, QueryP, NewCookieP> =
        copy(
            cookieDecoder = decoder,
        )

    @Suppress("UNCHECKED_CAST")
    private fun <NewReqH : HeaderProvider, NewResH : HeaderProvider, NewPathP : PathProvider, NewQueryP : QueryProvider, NewCookieP : CookieProvider> copy(
        httpMethod: HttpMethod = this.httpMethod,
        requestHeaderDecoder: HeaderField<NewReqH> = this.requestHeaderDecoder as HeaderField<NewReqH>,
        responseHeaderDecoder: HeaderField<NewResH> = this.responseHeaderDecoder as HeaderField<NewResH>,
        pathDecoder: PathField<NewPathP> = this.pathDecoder as PathField<NewPathP>,
        queryDecoder: QueryField<NewQueryP> = this.queryDecoder as QueryField<NewQueryP>,
        cookieDecoder: CookieField<NewCookieP> = this.cookieDecoder as CookieField<NewCookieP>,
        requestFormat: BodyFormat<Req> = this.requestFormat,
        responseFormat: BodyFormat<Res> = this.responseFormat,
        isProtected: Boolean = this.isProtected,
    ): ApiContractBuilder<Req, Res, NewReqH, NewResH, NewPathP, NewQueryP, NewCookieP> =
        ApiContractBuilder(
            path,
            httpMethod,
            requestHeaderDecoder,
            responseHeaderDecoder,
            pathDecoder,
            queryDecoder,
            cookieDecoder,
            requestFormat,
            responseFormat,
            isProtected,
        )

    /**
     * Builds the [ApiContract] instance.
     */
    fun build(): ApiContract<Req, Res, ReqH, ResH, PathP, QueryP, CookieP> =
        object : ApiContract<Req, Res, ReqH, ResH, PathP, QueryP, CookieP>(
            path,
            httpMethod,
            requestHeaderDecoder,
            responseHeaderDecoder,
            pathDecoder,
            queryDecoder,
            cookieDecoder,
            requestFormat,
            responseFormat,
            isProtected,
        ) {}
}
