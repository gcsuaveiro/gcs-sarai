The initial approach for checkpoint log processing consisted in using already existing Elastic integrations which relied on an agent receiving the events through TCP syslog. The original events were using a similar but slightly different format. Upon event reception by Logstash we converted the events to the expected format and dispatched it through syslog to the agent. 

Due to the very large amount of events of this type being received the agent quickly became a bottleneck (this was determined using extensive testing off all pipeline components around the agent). Increasing the number of agents was considered but deemed too difficult and impractical since it would require us to either come up with a way to evenly distribute events to the various agents from a single Logstash instance or to have several Logstash instances each sending to only an agent. Due to this and some naming inconsistencies between what we were receiving and what the integration expected we decided to port the [integration pipeline](https://github.com/elastic/beats/blob/main/x-pack/filebeat/module/checkpoint/firewall/ingest/pipeline.yml) to a Logstash pipeline and modify it to accommodate for this inconsistencies. This allowed us to significantly increase throughput and reduce complexity with a single instance being able to easily handle normal load. This approach also allows us to easily increase throughput by increasing the number of Logstash instances (provided we have at least the same number of Kafka partitions as Logstash instances).

>[!Note]
>There is a [tool](https://www.elastic.co/guide/en/logstash/current/ingest-converter.html) available that automatically convert ingest pipelines to Logstash pipelines (some restrictions apply). Although it not used here, it may be useful in the future.

# Changes made
The following changes were made from the direct port:

Leading and trailing white-spaces were removed from the following fields:
- `checkpoint.administrator`
- `checkpoint.source_user_name`
- `checkpoint.src_user_name`
- `checkpoint.usrName`
- `checkpoint.src_user_dn`
- `destination.user.name`

>[!Note]
>Some fields are derived from these

The`checkpoint.srcPostNAT` field is now interpreted as an alternative to `checkpoint.xlatesrc`.

The `checkpoint.dstPostNAT` field is now interpreted as an alternative to `checkpoint.xlatedst`.

The `checkpoint.dstPostNATPort` field is now interpreted as an alternative to `checkpoint.xlatedport`.

The `checkpoint.srcPostNATPort` field is now interpreted as an alternative to `checkpoint.xlatesport`.

If `source.ip` is in a private CIDR block the tag `private_source_ip` is added.
If `destination.ip` is in a private CIDR block the tag `private_destination_ip` is added.

If `source.user.name` exists it is copied to `user.name` and we attempt to parse the domain name from the user name ex. `ua.pt`.

If `url.original` exists we attempt to split it on space( ` `), since sometimes multiple URLs appear separated by a space (` `). The first URL found is copied to `host.name`

If `checkpoint.appi_name` exists and `host.name` has not been set. In case it contains only one name it is copied to `host.name` removing anything that comes after `/`. If it contains multiple names only the first one is.

If `host.name` has still not been set, the value from `source.ip` is copied to it.

if `message` has not been set, the value in `event.action` is translated to a message in the following way:
- Accept -> Flow accepted
- Allow -> Flow allowed
- Ask User -> Ask User
- Block -> Flow blocked
- Bypass -> Rule bypassed
- Decrypt -> Flow decrypted
- Detect -> Anomally Detected
- Drop -> Flow Dropped
- Encrypt -> Flow encrypted
- Failed Log In -> Failed Log In
- Forgot Passcode -> Forgot Passcode
- IP Changed -> IP Changed
- Key Install -> Key installed
- Log In -> Log In
- Log Out -> Log Out
- Prevent -> Flow prevented
- Redirect -> Flow redirected
- Reject -> Flow rejected
- logged-in -> Logged in
- logon-failed -> Logon failed

>[!caution]
>Some of these were guesses and, as such, they may not be entirely accurate

## ILM Policy
The integration provided Index template was duplicated (with increased priority) and a custom index lifecycle policy was added with the following settings:
### Hot
- `rollover`
	- `1 day` or `50 GB`
### Warm
- `0 hours after rollover`
### Delete
- `28 days after rollover`

# References
- https://github.com/elastic/beats/blob/main/x-pack/filebeat/module/checkpoint/firewall/ingest/pipeline.yml
- https://www.elastic.co/guide/en/logstash/current/ingest-converter.html