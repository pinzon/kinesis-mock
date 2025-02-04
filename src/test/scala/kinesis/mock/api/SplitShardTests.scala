package kinesis.mock
package api

import cats.effect._
import enumeratum.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class SplitShardTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should split shards")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(5, streamName, awsRegion, awsAccountId)
      val active =
        streams.findAndUpdateStream(streamName)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )
      val shardToSplit =
        active.streams(streamName).shards.keys.head

      val newStartingHashKey = Gen
        .choose(
          shardToSplit.hashKeyRange.startingHashKey + BigInt(1),
          shardToSplit.hashKeyRange.endingHashKey - BigInt(1)
        )
        .one
        .toString

      val req =
        SplitShardRequest(
          newStartingHashKey,
          shardToSplit.shardId.shardId,
          streamName
        )

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.splitShard(streamsRef, 50)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamName).exists { stream =>
          stream.shards.keys.toVector.count(shard =>
            shard.parentShardId.contains(shardToSplit.shardId.shardId)
          ) == 2 && stream.streamStatus == StreamStatus.UPDATING
        },
        s"req: $req\n" +
          s"resShards: ${s.streams(streamName).shards.keys.map(_.shardId)}"
      )
  })

  test("It should reject if the stream is inactive")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(5, streamName, awsRegion, awsAccountId)

      val shardToSplit =
        streams.streams(streamName).shards.keys.head

      val newStartingHashKey = Gen
        .choose(
          shardToSplit.hashKeyRange.startingHashKey + BigInt(1),
          shardToSplit.hashKeyRange.endingHashKey - BigInt(1)
        )
        .one
        .toString

      val req =
        SplitShardRequest(
          newStartingHashKey,
          shardToSplit.shardId.shardId,
          streamName
        )

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        res <- req.splitShard(streamsRef, 50)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test("It should reject if the shard is not found")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(5, streamName, awsRegion, awsAccountId)
      val active = streams.findAndUpdateStream(streamName)(s =>
        s.copy(streamStatus = StreamStatus.ACTIVE)
      )
      val shardToSplit =
        ShardId.create(
          active
            .streams(streamName)
            .shards
            .keys
            .toVector
            .map(_.shardId.index)
            .max + 1
        )

      val req =
        SplitShardRequest(
          "0",
          shardToSplit.shardId,
          streamName
        )

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.splitShard(streamsRef, 50)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test("It should reject if the operation would exceed the shard limit")(
    PropF.forAllF {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId
      ) =>
        val streams =
          Streams.empty.addStream(5, streamName, awsRegion, awsAccountId)
        val active =
          streams.findAndUpdateStream(streamName)(s =>
            s.copy(streamStatus = StreamStatus.ACTIVE)
          )
        val shardToSplit =
          active.streams(streamName).shards.keys.head

        val newStartingHashKey = Gen
          .choose(
            shardToSplit.hashKeyRange.startingHashKey + BigInt(1),
            shardToSplit.hashKeyRange.endingHashKey - BigInt(1)
          )
          .one
          .toString

        val req =
          SplitShardRequest(
            newStartingHashKey,
            shardToSplit.shardId.shardId,
            streamName
          )

        for {
          streamsRef <- Ref.of[IO, Streams](active)
          res <- req.splitShard(streamsRef, 5)
        } yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )
}
