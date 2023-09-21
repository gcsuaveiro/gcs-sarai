
Although Elastic already has some integrations for Apache log parsing, the logs we are receiving come customized with slightly different data than what the integration expects. So in order to accommodate this and provide richer processing we decided to process these logs ourselves.

# Description
The Apache log format is relatively simple.

Example:
```
<157>Aug 10 14:37:49 moodle3-2 httpd: 192.168.1.1 193.136.173.95 - - [10/Aug/2023:14:37:49 +0100] "GET /auth/shibboleth/index.php HTTP/1.1" 302 443 780 elearning.ua.pt
```

Which is interpreted and mapped as:
- `<157>` -> syslog priority -> **( ignored )**
- `Aug 10 14:37:49` -> syslog timestamp -> **( ignored )**
- `moodle3-2` -> the hostname of the system that generated the log -> `host.hostname`
- `httpd` -> the name of the process that generated the log -> `process.name` and `event.provider`
- `192.168.1.1` -> the source IP -> `source.ip`
- `193.136.173.95` -> the destination IP `destination.ip`
- `-` -> nothing **( ignored )**
- - -> the user name **( optional, default -> `-` )** -> `user.name`
- `[10/Aug/2023:14:37:49 +0100]` -> the Apache access time -> `@timestamp`
- `GET` -> the [HTTP Request method](https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods) **( optional, [see note](#note) )** -> `http.request.method`
- `/auth/shibboleth/index.php` -> the request line ( contains path + query + fragment ) **( optional, [see note](#note) )** -> `url.original`
- `1.1` -> the [HTTP version](https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/Evolution_of_HTTP) **( optional, [see note](#note) )** -> `http.version`
- `302` -> the [HTTP status code](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status) -> `http.response.status_code`
- `443` -> the destination port ( `443` for HTTPS and `80` for HTTP otherwise `unknown` ) -> `destination.port` and `url.port`
- `780` -> the length of the response body in `bytes` **( optional, default -> `-` )** ->`http.response.body.bytes`
- `elearning.ua.pt` -> the destination domain -> `destination.domain`

The `url.original` along with `destination.port` and `destination.domain` are processed if possible, into 6 parts:
- `path` -> `url.path`
- `query` -> `url.query`
- `fragment` -> `url.fragment`
- `extension` -> `url.extension`
- `scheme` -> `url.scheme`
- `full` -> `url.full`

`url.full` only exists if a valid full URL was able to be formed and parsed. In some special cases, such as `OPTIONS *` requests or in the [example below](#note), this field will not exist.

Geo location is run on both source and destination IPs if they don't belong to a private `CIDR`. If the `source` and or `destination` IPs do belong to a private `CIDR` the value(s) `private_source_ip` and or `private_destination_ip` are added to `tags` .

The related fields (`related.ip`, `related.hosts` and `related.user`) are filled with all unique matches for all valid events.

Additionally the following fields are always added:
- `event.kind` -> `event`
- `event.module` -> `apache`
- `event.category` -> `web`
- `event.type` -> `access`
- `event.created` -> the current timestamp in `ISO8601` format
- `event.dataset` -> `apache.access`

If the HTTP response is less than 399 the value `success` is added to the field `event.outcome`, otherwise the value `failure` is added.

##### **Note:** 
Although the HTTP request method, URL, and HTTP versions are optional they either all or none exist and are replaced by a single `-`  

Example:
```
<157>Aug 10 14:57:55 moodle3-2 httpd: 192.168.1.1 193.136.173.95 - - [10/Aug/2023:14:57:55 +0100] "-" 408 443 - elearning-projetos.ua.pt
```

##### **Note-2:** 
Additional fields to the ones specified above will be found. These are not directly related to the event itself and are mainly related to Elastic Search Stream metadata and IP Geo-location.

# Index Template
A custom index template was created along with a custom index lifecycle policy.
### Fields:
- `@timestamp` -> `date`
- `@version` -> `keyword`
- `data_stream.dataset` -> `constant_keyword`
- `data_stream.namespace` -> `constant_keyword`
- `data_stream.type` -> `constant_keyword`
- `destination.as.number` -> `long`
- `destination.as.organization.name` -> `keyword`
- `destination.as.organization.name.text` -> `match_only_text`
- `destination.domain` -> `keyword`
- `destination.geo.city_name` -> `keyword`
- `destination.geo.continent_code` -> `keyword`
- `destination.geo.country_iso_code` -> `keyword`
- `destination.geo.country_name` -> `keyword`
- `destination.geo.location` -> `geo_point`
- `destination.geo.postal_code` -> `keyword`
- `destination.geo.region_iso_code` -> `keyword`
- `destination.geo.region_name` -> `keyword`
- `destination.geo.timezone` -> `keyword`
- `destination.ip` -> `ip`
- `destination.port` -> `long`
- `ecs.version` -> `keyword`
- `event.category` -> `keyword`
- `event.created` -> `date`
- `event.dataset` -> `keyword`
- `event.kind` -> `keyword`
- `event.module` -> `keyword`
- `event.original` -> `keyword`
- `event.outcome` -> `keyword`
- `event.provider` -> `keyword`
- `event.type` -> `keyword`
- `host.hostname` -> `keyword`
- `http.request.method` -> `keyword`
- `http.response.body.bytes` -> `long`
- `http.response.status_code` -> `long`
- `http.version` -> `keyword`
- `process.name` -> `keyword`
- `related.hosts` -> `keyword`
- `related.ip` -> `ip`
- `related.user` -> `keyword`
- `source.as.number` -> `long`
- `source.as.organization.name` -> `keyword`
- `source.as.organization.name.text` -> `match_only_text`
- `source.geo.city_name` -> `keyword`
- `source.geo.continent_code` -> `keyword`
- `source.geo.country_iso_code` -> `keyword`
- `source.geo.country_name` -> `keyword`
- `source.geo.location` -> `geo_point`
- `source.geo.postal_code` -> `keyword`
- `source.geo.region_iso_code` -> `keyword`
- `source.geo.region_name` -> `keyword`
- `source.geo.timezone` -> `keyword`
- `source.ip` -> `ip`
- `tags` -> `keyword`
- `url.domain` -> `keyword`
- `url.extension` -> `keyword`
- `url.fragment` -> `keyword`
- `url.full` -> `wildcard`
- `url.original` -> `wildcard`
- `url.path` -> `wildcard`
- `url.port` -> `long`
- `url.scheme` -> `keyword`
- `url.query` -> `keyword`
- `user.name` -> `keyword`

### Settings

#### Index
```json
{
  "index": {
    "lifecycle": {
      "name": "logs-apache-ilm-policy"
    },
    "codec": "best_compression",
    "number_of_replicas": "1"
  }
}
```

#### Dynamic templates
```json
[
	{
	  "match_ip": {
	    "mapping": {
		  "type": "ip"
		},
	    "match_mapping_type": "string",
	    "match": "ip"
	  }
	},
	{
	  "strings_as_keyword": {
	    "mapping": {
		"ignore_above": 1024,
		  "type": "keyword"
		},
		"match_mapping_type": "string"
	  }
	}
]
```

# ILM Policy

### Hot
- `rollover`
	- `7 days` or `50 GB`
### Warm
- `0 hours after rollover`
### Delete
- `90 days after rollover`

# References
- https://serverfault.com/questions/147093/apache-logs-not-showing-requested-url-or-user-ip
- https://cwiki.apache.org/confluence/display/httpd/InternalDummyConnection
- https://httpd.apache.org/docs/2.4/logs.html