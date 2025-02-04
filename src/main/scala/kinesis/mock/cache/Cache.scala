package kinesis.mock
package cache

import java.io.FileWriter

import cats.effect._
import cats.effect.std.Supervisor
import cats.syntax.all._
import com.fasterxml.jackson.databind.ObjectMapper
import io.circe.jackson._
import io.circe.syntax._
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import kinesis.mock.api._
import kinesis.mock.models._
import kinesis.mock.syntax.semaphore._

class Cache private (
    streamsRef: Ref[IO, Streams],
    semaphores: CacheSemaphores,
    config: CacheConfig,
    supervisor: Supervisor[IO]
) {

  val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def addTagsToStream(
      req: AddTagsToStreamRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)("Processing AddTagsToStream request") *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.addTagsToStream.tryAcquireRelease(
        req
          .addTagsToStream(streamsRef)
          .flatTap(
            _.fold(
              e =>
                logger.warn(ctx.context, e)(
                  "Adding tags to stream was unuccessful"
                ),
              _ =>
                logger.debug(ctx.context)(
                  "Successfully added tags to the stream"
                )
            )
          ),
        logger
          .warn(ctx.context)("Rate limit exceeded for AddTagsToStream")
          .as(
            Left(
              LimitExceededException(
                "Rate limit exceeded for AddTagsToStream"
              )
            )
          )
      )
  }
  def removeTagsFromStream(
      req: RemoveTagsFromStreamRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)("Processing RemoveTagsFromStream request") *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.removeTagsFromStream.tryAcquireRelease(
        req
          .removeTagsFromStream(streamsRef)
          .flatTap(
            _.fold(
              e =>
                logger.warn(ctx.context, e)(
                  "Removing tags from stream was unuccessful"
                ),
              _ =>
                logger.debug(ctx.context)(
                  "Successfully removed tags from the stream"
                )
            )
          ),
        logger
          .warn(ctx.context)("Rate limit exceeded for RemoveTagsFromStream")
          .as(
            Left(
              LimitExceededException(
                "Rate limit exceeded for RemoveTagsFromStream"
              )
            )
          )
      )
  }

  def createStream(
      req: CreateStreamRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)("Processing CreateStream request") *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.createStream.tryAcquireRelease(
        for {
          createStreamsRes <- req
            .createStream(
              streamsRef,
              config.shardLimit,
              config.awsRegion,
              config.awsAccountId
            )
          _ <- createStreamsRes.fold(
            e =>
              logger.warn(ctx.context, e)(
                "Creating stream was unuccessful"
              ),
            _ =>
              logger.debug(ctx.context)(
                "Successfully created stream"
              )
          )
          _ <- supervisor
            .supervise(
              logger.debug(ctx.context)(
                s"Delaying setting stream to active for ${config.createStreamDuration.toString}"
              ) *>
                IO.sleep(config.createStreamDuration) *>
                logger.debug(ctx.context)(
                  s"Setting stream to active"
                ) *>
                streamsRef
                  .update(streams =>
                    streams.findAndUpdateStream(req.streamName)(x =>
                      x.copy(streamStatus = StreamStatus.ACTIVE)
                    )
                  )
            )
            .void
        } yield createStreamsRes,
        logger
          .warn(ctx.context)("Rate limit exceeded for CreateStream")
          .as(
            Left(
              LimitExceededException(
                "Rate limit exceeded for CreateStream"
              )
            )
          )
      )
  }

  def deleteStream(
      req: DeleteStreamRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)("Processing DeleteStream request") *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.deleteStream.tryAcquireRelease(
        for {
          deleteStreamRes <- req.deleteStream(streamsRef)
          _ <- deleteStreamRes.fold(
            e =>
              logger.warn(ctx.context, e)(
                "Deleting stream was unuccessful"
              ),
            _ =>
              logger.debug(ctx.context)(
                "Successfully deleted stream"
              )
          )
          _ <- supervisor
            .supervise(
              logger.debug(ctx.context)(
                s"Delaying removing the stream for ${config.deleteStreamDuration.toString}"
              ) *>
                IO.sleep(config.deleteStreamDuration) *>
                logger.debug(ctx.context)(
                  s"Removing stream"
                ) *>
                streamsRef.update(x => x.removeStream(req.streamName))
            )
            .void
        } yield deleteStreamRes,
        logger
          .warn(ctx.context)("Rate limit exceeded for DeleteStream")
          .as(
            Left(
              LimitExceededException(
                "Rate limit exceeded for DeleteStream"
              )
            )
          )
      )
  }

  def decreaseStreamRetention(
      req: DecreaseStreamRetentionPeriodRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing DecreaseStreamRetentionPeriod request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .decreaseStreamRetention(streamsRef)
        .flatTap(
          _.fold(
            e =>
              logger.warn(ctx.context, e)(
                "Decreasing the stream retention period was unuccessful"
              ),
            _ =>
              logger.debug(ctx.context)(
                "Successfully decreased the stream retention period "
              )
          )
        )
  }

  def increaseStreamRetention(
      req: IncreaseStreamRetentionPeriodRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing IncreaseStreamRetentionPeriod request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .increaseStreamRetention(streamsRef)
        .flatTap(
          _.fold(
            e =>
              logger.warn(ctx.context, e)(
                "Increasing the stream retention period was unuccessful"
              ),
            _ =>
              logger.debug(ctx.context)(
                "Successfully increased the stream retention period "
              )
          )
        )
  }

  def describeLimits(
      context: LoggingContext
  ): IO[Response[DescribeLimitsResponse]] =
    logger.debug(context.context)("Processing DescribeLimits request") *>
      semaphores.describeLimits.tryAcquireRelease(
        {
          DescribeLimitsResponse
            .get(config.shardLimit, streamsRef)
            .flatMap(response =>
              logger.debug(context.context)("Successfully described limits") *>
                logger
                  .trace(context.addJson("response", response.asJson).context)(
                    "Logging response"
                  )
                  .as(Right(response))
            )
        },
        logger
          .warn(context.context)("Rate limit exceeded for DescribeLimits")
          .as(
            Left(
              LimitExceededException("Rate limit exceeded for DescribeLimits")
            )
          )
      )

  def describeStream(
      req: DescribeStreamRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[DescribeStreamResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing DescribeStream request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.describeStream.tryAcquireRelease(
        req
          .describeStream(streamsRef)
          .flatMap { response =>
            response.fold(
              e =>
                logger
                  .warn(ctx.context, e)(
                    "Describing the stream was unuccessful"
                  )
                  .as(response),
              r =>
                logger.debug(ctx.context)(
                  "Successfully described the stream"
                ) *> logger
                  .trace(ctx.addEncoded("response", r, isCbor).context)(
                    "Logging response"
                  )
                  .as(response)
            )
          },
        logger
          .warn(context.context)("Rate limit exceeded for DescribeStream")
          .as(
            Left(
              LimitExceededException("Rate limit exceeded for DescribeStream")
            )
          )
      )
  }

  def describeStreamSummary(
      req: DescribeStreamSummaryRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[DescribeStreamSummaryResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing DescribeStreamSummary request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.describeStreamSummary.tryAcquireRelease(
        req
          .describeStreamSummary(streamsRef)
          .flatMap(response =>
            response.fold(
              e =>
                logger
                  .warn(ctx.context, e)(
                    "Describing the stream summary was unuccessful"
                  )
                  .as(response),
              r =>
                logger.debug(ctx.context)(
                  "Successfully described the stream summary"
                ) *> logger
                  .trace(ctx.addEncoded("response", r, isCbor).context)(
                    "Logging response"
                  )
                  .as(response)
            )
          ),
        logger
          .warn(context.context)(
            "Rate limit exceeded for DescribeStreamSummary"
          )
          .as(
            Left(
              LimitExceededException(
                "Rate limit exceeded for DescribeStreamSummary"
              )
            )
          )
      )
  }

  def registerStreamConsumer(
      req: RegisterStreamConsumerRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[RegisterStreamConsumerResponse]] = {
    val ctx = context + ("streamArn" -> req.streamArn)
    logger.debug(ctx.context)(
      "Processing RegisterStreamConsumer request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.registerStreamConsumer.tryAcquireRelease(
        req
          .registerStreamConsumer(streamsRef)
          .flatMap(response =>
            response
              .fold(
                e =>
                  logger
                    .warn(ctx.context, e)(
                      "Describing the stream summary was unuccessful"
                    )
                    .as(response),
                r =>
                  logger.debug(ctx.context)(
                    "Successfully described the stream summary"
                  ) *> logger
                    .trace(ctx.addEncoded("response", r, isCbor).context)(
                      "Logging response"
                    ) *> supervisor
                    .supervise(
                      logger.debug(ctx.context)(
                        s"Delaying setting the consumer as ACTIVE for ${config.registerStreamConsumerDuration.toString}"
                      ) *>
                        IO.sleep(config.registerStreamConsumerDuration) *>
                        logger.debug(ctx.context)(
                          s"Setting consumer as ACTIVE"
                        ) *> streamsRef.update(x =>
                          x.streams.values
                            .find(_.streamArn == req.streamArn)
                            .fold(x)(stream =>
                              x.updateStream(
                                stream.copy(consumers =
                                  stream.consumers ++ Vector(
                                    r.consumer.consumerName -> Consumer(
                                      r.consumer.consumerArn,
                                      r.consumer.consumerCreationTimestamp,
                                      r.consumer.consumerName,
                                      ConsumerStatus.ACTIVE,
                                      req.streamArn
                                    )
                                  )
                                )
                              )
                            )
                        )
                    )
                    .void
                    .as(response)
              )
          ),
        logger
          .warn(context.context)(
            "Rate limit exceeded for RegisterStreamConsumer"
          )
          .as(
            Left(
              LimitExceededException(
                "Rate limit exceeded for RegisterStreamConsumer"
              )
            )
          )
      )
  }

  def deregisterStreamConsumer(
      req: DeregisterStreamConsumerRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[Unit]] = {
    logger.debug(context.context)(
      "Processing DeregisterStreamConsumer request"
    ) *>
      logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.deregisterStreamConsumer.tryAcquireRelease(
        req
          .deregisterStreamConsumer(streamsRef)
          .flatMap(response =>
            response
              .fold(
                e =>
                  logger
                    .warn(context.context, e)(
                      "Deregistering the stream consumer was unuccessful"
                    )
                    .as(response.as(())),
                consumer =>
                  logger.debug(context.context)(
                    "Successfully registered the stream consumer"
                  ) *> supervisor
                    .supervise(
                      logger.debug(context.context)(
                        s"Delaying removing the consumer for ${config.deregisterStreamConsumerDuration.toString}"
                      ) *>
                        IO.sleep(config.deregisterStreamConsumerDuration) *>
                        logger.debug(context.context)(
                          s"Removing the consumer"
                        ) *>
                        streamsRef.update(x =>
                          x.streams.values
                            .find(s =>
                              s.consumers.keys.toVector
                                .contains(consumer.consumerName)
                            )
                            .fold(x)(stream =>
                              x.updateStream(
                                stream
                                  .copy(consumers = stream.consumers.filterNot {
                                    case (consumerName, _) =>
                                      consumerName == consumer.consumerName
                                  })
                              )
                            )
                        )
                    )
                    .void
                    .as(response.as(()))
              )
          ),
        logger
          .warn(context.context)(
            "Rate limit exceeded for DeregisterStreamConsumer"
          )
          .as(
            Left(
              LimitExceededException(
                "Rate limit exceeded for DeregisterStreamConsumer"
              )
            )
          )
      )
  }

  def describeStreamConsumer(
      req: DescribeStreamConsumerRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[DescribeStreamConsumerResponse]] =
    logger.debug(context.context)(
      "Processing DescribeStreamConsumer request"
    ) *>
      logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.describeStreamConsumer.tryAcquireRelease(
        req
          .describeStreamConsumer(streamsRef)
          .flatMap(response =>
            response.fold(
              e =>
                logger
                  .warn(context.context, e)(
                    "Describing the stream consumer was unuccessful"
                  )
                  .as(response),
              r =>
                logger.debug(context.context)(
                  "Successfully described the stream consumer"
                ) *> logger
                  .trace(context.addEncoded("response", r, isCbor).context)(
                    "Logging response"
                  )
                  .as(response)
            )
          ),
        logger
          .warn(context.context)(
            "Rate limit exceeded for DescribeStreamConsumer"
          )
          .as(
            Left(
              LimitExceededException(
                "Limit exceeded for DescribeStreamConsumer"
              )
            )
          )
      )

  def disableEnhancedMonitoring(
      req: DisableEnhancedMonitoringRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[DisableEnhancedMonitoringResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing DisableEnhancedMonitoring request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .disableEnhancedMonitoring(streamsRef)
        .flatMap(response =>
          response.fold(
            e =>
              logger
                .warn(context.context, e)(
                  "Disabling the enhanced monitoring was unuccessful"
                )
                .as(response),
            r =>
              logger.debug(context.context)(
                "Successfully disabled enhanced monitoring"
              ) *> logger
                .trace(context.addEncoded("response", r, isCbor).context)(
                  "Logging response"
                )
                .as(response)
          )
        )
  }

  def enableEnhancedMonitoring(
      req: EnableEnhancedMonitoringRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[EnableEnhancedMonitoringResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing EnableEnhancedMonitoring request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .enableEnhancedMonitoring(streamsRef)
        .flatMap(response =>
          response.fold(
            e =>
              logger
                .warn(context.context, e)(
                  "Enabling the enhanced monitoring was unuccessful"
                )
                .as(response),
            r =>
              logger.debug(context.context)(
                "Successfully enabled enhanced monitoring"
              ) *> logger
                .trace(context.addEncoded("response", r, isCbor).context)(
                  "Logging response"
                )
                .as(response)
          )
        )
  }

  def listShards(
      req: ListShardsRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[ListShardsResponse]] =
    logger.debug(context.context)(
      "Processing ListShards request"
    ) *>
      logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.listShards.tryAcquireRelease(
        req
          .listShards(streamsRef)
          .flatMap(response =>
            response.fold(
              e =>
                logger
                  .warn(context.context, e)(
                    "Listing shards was unuccessful"
                  )
                  .as(response),
              r =>
                logger.debug(context.context)(
                  "Successfully listed shards"
                ) *> logger
                  .trace(context.addEncoded("response", r, isCbor).context)(
                    "Logging response"
                  )
                  .as(response)
            )
          ),
        logger
          .warn(context.context)(
            "Rate limit exceeded for ListShards"
          )
          .as(Left(LimitExceededException("Limit exceeded for ListShards")))
      )

  def listStreamConsumers(
      req: ListStreamConsumersRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[ListStreamConsumersResponse]] = {
    val ctx = context + ("streamArn" -> req.streamArn)
    logger.debug(ctx.context)(
      "Processing ListStreamConsumers request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.listStreamConsumers.tryAcquireRelease(
        req
          .listStreamConsumers(streamsRef)
          .flatMap(response =>
            response.fold(
              e =>
                logger
                  .warn(context.context, e)(
                    "Listing stream consumers was unuccessful"
                  )
                  .as(response),
              r =>
                logger.debug(context.context)(
                  "Successfully listed stream consumers"
                ) *> logger
                  .trace(context.addEncoded("response", r, isCbor).context)(
                    "Logging response"
                  )
                  .as(response)
            )
          ),
        logger
          .warn(ctx.context)(
            "Rate limit exceeded for ListShards"
          )
          .as(
            Left(
              LimitExceededException("Limit exceeded for ListStreamConsumers")
            )
          )
      )
  }

  def listStreams(
      req: ListStreamsRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[ListStreamsResponse]] =
    logger.debug(context.context)(
      "Processing ListStreams request"
    ) *>
      logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.listStreams.tryAcquireRelease(
        req
          .listStreams(streamsRef)
          .flatMap(response =>
            response.fold(
              e =>
                logger
                  .warn(context.context, e)(
                    "Listing streams was unuccessful"
                  )
                  .as(response),
              r =>
                logger.debug(context.context)(
                  "Successfully listed streams"
                ) *> logger
                  .trace(context.addEncoded("response", r, isCbor).context)(
                    "Logging response"
                  )
                  .as(response)
            )
          ),
        logger
          .warn(context.context)(
            "Rate limit exceeded for ListStreams"
          )
          .as(
            Left(
              LimitExceededException("Limit exceeded for ListStreams")
            )
          )
      )

  def listTagsForStream(
      req: ListTagsForStreamRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[ListTagsForStreamResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing ListTagsForStream request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.listTagsForStream.tryAcquireRelease(
        req
          .listTagsForStream(streamsRef)
          .flatMap(response =>
            response.fold(
              e =>
                logger
                  .warn(context.context, e)(
                    "Listing tags for stream was unuccessful"
                  )
                  .as(response),
              r =>
                logger.debug(context.context)(
                  "Successfully listed tags for stream"
                ) *> logger
                  .trace(context.addEncoded("response", r, isCbor).context)(
                    "Logging response"
                  )
                  .as(response)
            )
          ),
        logger
          .warn(ctx.context)(
            "Rate limit exceeded for ListTagsForStream"
          )
          .as(
            Left(
              LimitExceededException("Limit exceeded for ListTagsForStream")
            )
          )
      )
  }

  def startStreamEncryption(
      req: StartStreamEncryptionRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing StartStreamEncryption request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .startStreamEncryption(streamsRef)
        .flatMap(response =>
          response
            .fold(
              e =>
                logger
                  .warn(ctx.context, e)(
                    "Starting stream encryption was unuccessful"
                  )
                  .as(response),
              _ =>
                logger.debug(ctx.context)(
                  "Successfully started stream encryption"
                ) *> supervisor
                  .supervise(
                    logger.debug(context.context)(
                      s"Delaying setting the stream to active for ${config.startStreamEncryptionDuration.toString}"
                    ) *>
                      IO.sleep(config.startStreamEncryptionDuration) *>
                      logger.debug(context.context)(
                        s"Setting the stream to active"
                      ) *>
                      streamsRef
                        .update(updated =>
                          updated.findAndUpdateStream(req.streamName)(x =>
                            x.copy(streamStatus = StreamStatus.ACTIVE)
                          )
                        )
                  )
                  .as(response)
            )
        )
  }

  def stopStreamEncryption(
      req: StopStreamEncryptionRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing StopStreamEncryption request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .stopStreamEncryption(streamsRef)
        .flatMap(response =>
          response
            .fold(
              e =>
                logger
                  .warn(ctx.context, e)(
                    "Stopping stream encryption was unuccessful"
                  )
                  .as(response),
              _ =>
                logger.debug(ctx.context)(
                  "Successfully stopped stream encryption"
                ) *>
                  supervisor
                    .supervise(
                      logger.debug(context.context)(
                        s"Delaying setting the stream to active for ${config.stopStreamEncryptionDuration.toString}"
                      ) *>
                        IO.sleep(config.stopStreamEncryptionDuration) *>
                        logger.debug(context.context)(
                          s"Setting the stream to active"
                        ) *>
                        streamsRef
                          .update(updated =>
                            updated.findAndUpdateStream(req.streamName)(x =>
                              x.copy(streamStatus = StreamStatus.ACTIVE)
                            )
                          )
                    )
                    .void
                    .as(response)
            )
        )

  }

  def getShardIterator(
      req: GetShardIteratorRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[GetShardIteratorResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing GetShardIterator request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .getShardIterator(streamsRef)
        .flatMap(response =>
          response
            .fold(
              e =>
                logger
                  .warn(ctx.context, e)(
                    "Getting the shard iterator was unuccessful"
                  )
                  .as(response),
              r =>
                logger.debug(ctx.context)(
                  "Successfully got the shard iterator"
                ) *> logger
                  .trace(ctx.addEncoded("response", r, isCbor).context)(
                    "Logging response"
                  )
                  .as(response)
            )
        )

  }
  def getRecords(
      req: GetRecordsRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[GetRecordsResponse]] =
    logger.debug(context.context)(
      "Processing GetRecords request"
    ) *>
      logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      req
        .getRecords(streamsRef)
        .flatMap(response =>
          response
            .fold(
              e =>
                logger
                  .warn(context.context, e)(
                    "Getting records was unuccessful"
                  )
                  .as(response),
              r =>
                logger.debug(context.context)(
                  "Successfully got records"
                ) *> logger
                  .trace(context.addEncoded("response", r, isCbor).context)(
                    "Logging response"
                  )
                  .as(response)
            )
        )

  def putRecord(
      req: PutRecordRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[PutRecordResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    for {
      _ <- logger.debug(ctx.context)("Processing PutRecord request")
      _ <- logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      )
      res <- req.putRecord(streamsRef)
      _ <- res.fold(
        e =>
          logger
            .warn(ctx.context, e)(
              "Putting record was unuccessful"
            ),
        r =>
          logger.debug(ctx.context)(
            "Successfully put record"
          ) *> logger
            .trace(ctx.addEncoded("response", r, isCbor).context)(
              "Logging response"
            )
      )
    } yield res
  }

  def putRecords(
      req: PutRecordsRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[PutRecordsResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    for {
      _ <- logger.debug(ctx.context)("Processing PutRecords request")
      _ <- logger.trace(context.addEncoded("request", req, isCbor).context)(
        "Logging request"
      )
      res <- req.putRecords(streamsRef)
      _ <- res.fold(
        e =>
          logger
            .warn(ctx.context, e)(
              "Putting records was unuccessful"
            ),
        r =>
          logger.debug(ctx.context)(
            "Successfully put records"
          ) *> logger
            .trace(ctx.addEncoded("response", r, isCbor).context)(
              "Logging response"
            )
      )
    } yield res
  }

  def mergeShards(
      req: MergeShardsRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing MergeShards request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.mergeShards.tryAcquireRelease(
        for {
          result <- req.mergeShards(streamsRef)
          _ <- result.fold(
            e =>
              logger
                .warn(ctx.context, e)(
                  "Merging shards was unuccessful"
                ),
            _ =>
              logger.debug(ctx.context)(
                "Successfully merged shards"
              )
          )
          _ <- supervisor
            .supervise(
              logger.debug(context.context)(
                s"Delaying setting the stream to active for ${config.mergeShardsDuration.toString}"
              ) *>
                IO.sleep(config.mergeShardsDuration) *>
                logger.debug(context.context)(
                  s"Setting the stream to active"
                ) *>
                streamsRef
                  .update(updated =>
                    updated.findAndUpdateStream(req.streamName)(x =>
                      x.copy(streamStatus = StreamStatus.ACTIVE)
                    )
                  )
            )
            .void
        } yield result,
        logger
          .warn(ctx.context)(
            "Rate limit exceeded for MergeShards"
          )
          .as(Left(LimitExceededException("Limit Exceeded for MergeShards")))
      )
  }

  def splitShard(
      req: SplitShardRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[Unit]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing SplitShard request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *>
      semaphores.splitShard.tryAcquireRelease(
        for {
          result <- req.splitShard(streamsRef, config.shardLimit)
          _ <- result.fold(
            e =>
              logger
                .warn(ctx.context, e)(
                  "Splitting shard was unuccessful"
                ),
            _ =>
              logger.debug(ctx.context)(
                "Successfully split shard"
              )
          )
          _ <- supervisor
            .supervise(
              logger.debug(context.context)(
                s"Delaying setting the stream to active for ${config.splitShardDuration.toString}"
              ) *>
                IO.sleep(config.splitShardDuration) *>
                logger.debug(context.context)(
                  s"Setting the stream to active"
                ) *>
                streamsRef
                  .update(updated =>
                    updated.findAndUpdateStream(req.streamName)(x =>
                      x.copy(streamStatus = StreamStatus.ACTIVE)
                    )
                  )
            )
            .void
        } yield result,
        logger
          .warn(ctx.context)(
            "Rate limit exceeded for MergeShards"
          )
          .as(Left(LimitExceededException("Limit Exceeded for SplitShard")))
      )
  }

  def updateShardCount(
      req: UpdateShardCountRequest,
      context: LoggingContext,
      isCbor: Boolean
  ): IO[Response[UpdateShardCountResponse]] = {
    val ctx = context + ("streamName" -> req.streamName.streamName)
    logger.debug(ctx.context)(
      "Processing UpdateShardCount request"
    ) *>
      logger.trace(ctx.addEncoded("request", req, isCbor).context)(
        "Logging request"
      ) *> (for {
        result <- req.updateShardCount(streamsRef, config.shardLimit)
        _ <- result.fold(
          e =>
            logger
              .warn(ctx.context, e)(
                "Updating shard count was unuccessful"
              ),
          _ =>
            logger.debug(ctx.context)(
              "Successfully updated shard count"
            )
        )
        _ <- supervisor
          .supervise(
            logger.debug(context.context)(
              s"Delaying setting the stream to active for ${config.updateShardCountDuration.toString}"
            ) *>
              IO.sleep(config.updateShardCountDuration) *>
              logger.debug(context.context)(
                s"Setting the stream to active"
              ) *>
              streamsRef
                .update(updated =>
                  updated.findAndUpdateStream(req.streamName)(x =>
                    x.copy(streamStatus = StreamStatus.ACTIVE)
                  )
                )
          )
          .void
      } yield result)
  }

  def persistToDisk(context: LoggingContext): IO[Unit] =
    IO.pure(config.persistConfig.shouldPersist)
      .ifM(
        semaphores.persistData.permit.use(_ =>
          for {
            streams <- streamsRef.get
            ctx = context ++ Vector(
              "fileName" -> config.persistConfig.fileName,
              "path" -> config.persistConfig.osPath.toString
            )
            _ <- IO
              .interruptible(false)(os.exists(config.persistConfig.osPath))
              .ifM(
                IO.unit,
                logger.info(ctx.context)("Creating directories") >>
                  IO.interruptible(false)(
                    os.makeDir.all(config.persistConfig.osPath)
                  )
              )
            js = streams.asJson
            jacksonJs = circeToJackson(js)
            res <- IO(new FileWriter(config.persistConfig.osFile.toIO, false))
              .bracket { fw =>
                val om = new ObjectMapper()
                for {
                  _ <- logger
                    .debug(ctx.context)("Persisting stream data to disk")
                  r <- IO
                    .interruptible(false)(om.writer().writeValue(fw, jacksonJs))
                  _ <- logger
                    .debug(ctx.context)("Successfully persisted stream data")
                } yield r
              }(fw => IO(fw.close()))
          } yield res
        ),
        logger
          .warn(context.context)("Persist config was not provided, ignoring")
      )
}

object Cache {
  def apply(
      config: CacheConfig,
      streams: Streams = Streams.empty // scalafix:ok
  )(implicit C: Concurrent[IO]): IO[Cache] = for {
    ref <- Ref.of[IO, Streams](streams)
    semaphores <- CacheSemaphores.create
    supervisorResource = Supervisor[IO]
    cache <- supervisorResource.use(supervisor =>
      IO(new Cache(ref, semaphores, config, supervisor))
    )
  } yield cache

  def loadFromFile(
      config: CacheConfig
  )(implicit C: Concurrent[IO]): IO[Cache] = {
    val om = new ObjectMapper()

    IO.interruptible(false)(os.exists(config.persistConfig.osFile))
      .ifM(
        for {
          jn <- IO
            .interruptible(false)(om.readTree(config.persistConfig.osFile.toIO))
          streams <- IO.fromEither(jacksonToCirce(jn).as[Streams])
          res <- apply(config, streams)
        } yield res,
        apply(config)
      )
  }
}
