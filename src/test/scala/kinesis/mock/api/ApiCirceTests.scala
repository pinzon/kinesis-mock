package kinesis.mock
package api

import enumeratum.scalacheck._

import kinesis.mock.instances.arbitrary._

class ApiCirceTests extends CirceTests {
  identityLawTest[AddTagsToStreamRequest]
  identityLawTest[CreateStreamRequest]
  identityLawTest[DecreaseStreamRetentionRequest]
  identityLawTest[DeleteStreamRequest]
  identityLawTest[DeregisterStreamConsumerRequest]
  identityLawTest[DescribeLimitsResponse]
  identityLawTest[DescribeStreamConsumerRequest]
  identityLawTest[DescribeStreamRequest]
  identityLawTest[DescribeStreamResponse]
  identityLawTest[DescribeStreamSummaryRequest]
  identityLawTest[DescribeStreamSummaryResponse]
  identityLawTest[DisableEnhancedMonitoringRequest]
  identityLawTest[DisableEnhancedMonitoringResponse]
  identityLawTest[EnableEnhancedMonitoringRequest]
  identityLawTest[EnableEnhancedMonitoringResponse]
  identityLawTest[GetRecordsRequest]
  identityLawTest[GetRecordsResponse]
  identityLawTest[GetShardIteratorRequest]
  identityLawTest[GetShardIteratorResponse]
  identityLawTest[ListShardsRequest]
  identityLawTest[ListShardsResponse]
  identityLawTest[ListStreamConsumersRequest]
  identityLawTest[ListStreamConsumersResponse]
  identityLawTest[ListStreamsRequest]
  identityLawTest[ListStreamsResponse]
  identityLawTest[ListTagsForStreamRequest]
  identityLawTest[ListTagsForStreamResponse]
  identityLawTest[MergeShardsRequest]
  identityLawTest[PutRecordRequest]
  identityLawTest[PutRecordResponse]
  identityLawTest[PutRecordsRequest]
  identityLawTest[PutRecordsRequestEntry]
  identityLawTest[PutRecordsResponse]
  identityLawTest[PutRecordsResultEntry]
  identityLawTest[RegisterStreamConsumerRequest]
  identityLawTest[RegisterStreamConsumerResponse]
  identityLawTest[SplitShardRequest]
  identityLawTest[StartStreamEncryptionRequest]
  identityLawTest[StopStreamEncryptionRequest]
  identityLawTest[UpdateShardCountRequest]
}
