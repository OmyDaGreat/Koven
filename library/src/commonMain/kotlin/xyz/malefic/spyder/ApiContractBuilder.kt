package xyz.malefic.spyder

/**
 * A builder for [ApiContract] types.
 */
@Suppress("ktlint:standard:max-line-length")
class ApiContractBuilder<Req, Res, ReqH : HeaderProvider, ResH : HeaderProvider, PathP : PathProvider, QueryP : QueryProvider> internal constructor(
    private val path: String,
    private val method: Method = Method.POST,
    private val requiredRequestHeaders: List<HeaderField<*>> = emptyList(),
    private val requiredResponseHeaders: List<HeaderField<*>> = emptyList(),
    private val requestHeaderDecoder: HeaderField<ReqH>,
    private val responseHeaderDecoder: HeaderField<ResH>,
    private val pathDecoder: PathField<PathP>,
    private val queryDecoder: QueryField<QueryP>,
    private val queryParams: List<String> = emptyList(),
) {
    /**
     * Sets the HTTP method.
     */
    fun method(method: Method) = copy<ReqH, ResH, PathP, QueryP>(method = method)

    /**
     * Sets the request header decoder and the set of required headers for validation.
     *
     * @param decoder The [HeaderField] to decode the request headers.
     * @param required The set of required headers. If empty, the decoder is used as the required headers, though flattened (such that composite headers pass in their constituent fields).
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
     * @param required The set of required headers. If empty, the decoder is used as the required headers, though flattened (such that composite headers pass in their constituent fields).
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
     */
    fun <NewQueryP : QueryProvider> query(
        decoder: QueryField<NewQueryP>,
        vararg params: String,
    ): ApiContractBuilder<Req, Res, ReqH, ResH, PathP, NewQueryP> = copy(queryDecoder = decoder, queryParams = params.toList())

    @Suppress("UNCHECKED_CAST")
    private fun <NewReqH : HeaderProvider, NewResH : HeaderProvider, NewPathP : PathProvider, NewQueryP : QueryProvider> copy(
        method: Method = this.method,
        requiredRequestHeaders: List<HeaderField<*>> = this.requiredRequestHeaders,
        requiredResponseHeaders: List<HeaderField<*>> = this.requiredResponseHeaders,
        requestHeaderDecoder: HeaderField<NewReqH> = this.requestHeaderDecoder as HeaderField<NewReqH>,
        responseHeaderDecoder: HeaderField<NewResH> = this.responseHeaderDecoder as HeaderField<NewResH>,
        pathDecoder: PathField<NewPathP> = this.pathDecoder as PathField<NewPathP>,
        queryDecoder: QueryField<NewQueryP> = this.queryDecoder as QueryField<NewQueryP>,
        queryParams: List<String> = this.queryParams,
    ): ApiContractBuilder<Req, Res, NewReqH, NewResH, NewPathP, NewQueryP> =
        ApiContractBuilder(
            path,
            method,
            requiredRequestHeaders,
            requiredResponseHeaders,
            requestHeaderDecoder,
            responseHeaderDecoder,
            pathDecoder,
            queryDecoder,
            queryParams,
        )

    /**
     * Builds the [ApiContract] instance.
     */
    fun build(): ApiContract<Req, Res, ReqH, ResH, PathP, QueryP> =
        object : ApiContract<Req, Res, ReqH, ResH, PathP, QueryP>(
            path,
            method,
            requiredRequestHeaders,
            requiredResponseHeaders,
            requestHeaderDecoder,
            responseHeaderDecoder,
            pathDecoder,
            queryDecoder,
        ) {
            override val queryParams: List<String> = this@ApiContractBuilder.queryParams
        }
}

/**
 * Entry point for creating an [ApiContract] via builder.
 */
@Suppress("UNCHECKED_CAST")
fun <Req, Res> apiContract(path: String) =
    ApiContractBuilder<Req, Res, NoHeaders, NoHeaders, NoParams, NoParams>(
        path = path,
        requestHeaderDecoder = NoHeaders as HeaderField<NoHeaders>,
        responseHeaderDecoder = NoHeaders as HeaderField<NoHeaders>,
        pathDecoder = NoParams as PathField<NoParams>,
        queryDecoder = NoParams as QueryField<NoParams>,
    )
