Although Elastic already has some integrations for Nginx log parsing, the logs we are receiving come customized in a completely different format with additional data than what the integration expects. So in order to accommodate this and provide richer processing we decided to process these logs ourselves.

# Description
The NGINX log format is relatively simple and easy to parse. It consists of a syslog header followed by LEEF data.

Example:
```
<190>Jul 30 17:16:43 nginx-proxy1.ua.pt nginx: LEEF:1.0|NGINX|NGINX|1.23.2|301|devTime=30/Jul/2023:17:16:43 +0100\tdevTimeFormat=dd/MMM/yyyy:HH:mm:ssZ\tsrc=192.168.1.1\tdst=192.168.1.2\tdstPort=80\tproto=HTTP/1.1\tusrName=-\trequest=GET /page HTTP/1.1\tbody_bytes_sent=162\thttp_referer=-\thttp_true_client_ip=-\thttp_user_agent=Mozilla/5.0 (compatible; DotBot/1.2; +https://opensiteexplorer.org/dotbot; help@moz.com)\thttp_x_header=-\thttp_x_forwarded_for=216.244.66.248:42642\trequest_time=0.000\tupstream_response_time=-\tpipe=.\turi_query=-\turi_path=/page\tcookie=-
```

Which is interpreted and mapped as:
- `<190>` -> `syslog priority` -> ignored
- `Jul 30 17:16:43` -> `syslog timestamp` -> ignored
- `nginx-proxy1.ua.pt` -> the hostname of the system that generated the log -> `host.hostname`
- `nginx` ->  the name of the process that generated the log -> `process.name` and `event.provider`
- `LEEF:1.0|NGINX|NGINX|1.23.2|` -> LEEF header -> ignored
- `301` -> the [HTTP status code](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status) -> `http.response.status_code`

The remaining data is in a key-value format which contains the following fields:
- `devTime` -> The timestamp of when the event was generated -> `@timestamp`
- `devTimeFormat` -> The format `devTime` is in (`dd/MMM/yyyy:HH:mm:ss Z`)
- `src` -> The source IP -> `source.ip`
- `dst` -> The destination IP -> `destination.ip`
- `dstPort` -> The destination port -> `destination.port`
- `proto` -> The protocol used -> ignored (duplicate information)
- `usrName` -> The associated user name -> `user.name`
- `request` -> `String containing the request method, the complete request url and the HTTP version` -> `http.request.method`, `url.original` and `http.version`
- `body_bytes_sent` -> The number of bytes sent in response to the request -> `http.response.body.bytes`
- `http_referer` -> The HTTP referrer -> `http.referer`
- `http_true_client_ip` -> The true client IP -> `client.ip` and `nginx.access.http_true_client_ip`
- `http_user_agent` -> The user agent -> `user_agent.original`
- `http_x_header` -> The HTTP X header -> `nginx.access.http_x_header`
- `http_x_forwarded_for` -> Split to `nginx.access.http_x_forwarded_for`  
- `request_time` -> Full request time, starting when NGINX reads the first byte from the client and ending when NGINX sends the last byte of the response body -> `event.duration` (in nano-seconds) and `nginx.access.request_time` (in seconds)   
- `upstream_response_time` -> Time between establishing a connection to an upstream server and receiving the last byte of the response body -> split to `nginx.access.upstream_response_time`
- `pipe` -> `p` if request was pipelined, `.` otherwise -> `nginx.access.pipe`
- `uri_query` -> The URL query -> `url.query`
- `uri_path` -> The URL path -> `url.path`
- `cookie` -> The cookies sent with the request  -> `nginx.access.cookies`

##### **Note:**
Not all of these fields contain data in every event. They are only mapped if present with valid data.

If the `request` field does not contain valid data the value `nginx_request_grok_parse_failure` is added to `tags`.

If the `http_true_client_ip` field does not contain valid data the value `nginx_true_client_ip_grok_parse_failure` is added to `tags`. 

If `nginx.access.http_true_client_ip` does not exist and `http_x_forwarded_for` does, the first address in `http_x_forwarded_for` will be mapped to `client.ip` (and `client.port` if present).

The user agent is processed to the `user_agent` field.

Geo location is run on `client`, `source` and `destination` IPs if they don't belong to a private `CIDR`. If the `client`, `source`  or `destination` IPs do belong to a private `CIDR` the value(s) `private_client_ip`, `private_source_ip` and `private_destination_ip` are added to `tags` respectively.

When present `nginx.access.http_x_forwarded_for` and `upstream_response_time` will always be arrays even if they only containing one element.

The related fields (`related.ip`, `related.hosts` and `related.user`) are filled with all unique matches for all valid events. There is an additional IP list in `nginx.access.remote_ip_list` that contains a list off all remote IP addresses i.e. all except the destination IP.

Additionally the following fields are always added:
- `event.kind` -> `event`
- `event.module` -> `nginx`
- `event.category` -> `web`
- `event.type` -> `access`
- `event.created` -> the current timestamp in `ISO8601` format
- `event.dataset` -> `nginx.access`

`nginx-access` is always also added to `tags`.

If the HTTP response (`http.response.status_code`) is less than 399 the value `success` is added to the field `event.outcome`, otherwise the value `failure` is added.

The `url.original` along with `destination.port` and `destination.ip` are processed if possible, into 4 parts:
- `fragment` -> `url.fragment`
- `extension` -> `url.extension`
- `scheme` -> `url.scheme`
- `full` -> `url.full`

`url.path` and `url.query` are processed directly from their respective fields listed above.

`url.full` only exists if a valid URL was able to be formed and parsed. In some special cases this field will not exist. 

`url.domain` and `url.port` will contain the `destination.ip` and `destination.port` if `url.original` does not contain a domain or a port. 

`url.scheme` is assumed from the `destination.port` (`443` -> `https`, `80` -> `http` , `otherwise` -> `unknwon`) if `url.original` does not contain a scheme.

##### **Note-2:** 
Additional fields to the ones specified above will be found. These are not directly related to the event itself and are mainly related to [Elastic Search Stream metadata](https://www.elastic.co/guide/en/ecs/current/ecs-data_stream.html), [IP Geo-location](https://www.elastic.co/guide/en/ecs/current/ecs-geo.html) and [user-agent](https://www.elastic.co/guide/en/ecs/current/ecs-user_agent.html) processing.


# Index Template
A custom index template was created along with a custom index lifecycle policy.
### Fields:
- `@timestamp` -> `date`
- `@version` -> `keyword`
- `client.as.number` -> `long`
- `client.as.organization.name` -> `keyword`
- `client.as.organization.name.text` -> `match_only_text`
- `client.geo.city_name` -> `keyword`
- `client.geo.continent_code` -> `keyword`
- `client.geo.country_iso_code` -> `keyword`
- `client.geo.country_name` -> `keyword`
- `client.geo.location` -> `geo_point`
- `client.geo.postal_code` -> `keyword`
- `client.geo.region_iso_code` -> `keyword`
- `client.geo.region_name` -> `keyword`
- `client.geo.timezone` -> `keyword`
- `client.ip` -> `ip`
- `client.port` -> `long`
- `client.mmdb.dma_code` -> `long`
- `data_stream.dataset` -> `constant_keyword`
- `data_stream.namespace` -> `constant_keyword`
- `data_stream.type` -> `constant_keyword`
- `destination.as.number` -> `long`
- `destination.as.organization.name` -> `keyword`
- `destination.as.organization.name.text` -> `match_only_text`
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
- `destination.mmdb.dma_code` -> `long`
- `ecs.version` -> `keyword`
- `event.category` -> `keyword`
- `event.created` -> `date`
- `event.dataset` -> `keyword`
- `event.duration` -> `long`
- `event.kind` -> `keyword`
- `event.module` -> `keyword`
- `event.original` -> `keyword`
- `event.outcome` -> `keyword`
- `event.provider` -> `keyword`
- `event.type` -> `keyword`
- `host.hostname` -> `keyword`
- `http.request.method` -> `keyword`
- `http.request.referrer` -> `keyword`
- `http.response.body.bytes` -> `long`
- `http.response.status_code` -> `long`
- `http.version` -> `keyword`
- `nginx.access.cookies` -> `match_only_text`
- `nginx.access.http_x_forwarded_for` -> `keyword`
- `nginx.access.http_true_client_ip` -> `ip`
- `nginx.access.pipe` -> `keyword`
- `nginx.access.remote_ip_list` -> `ip`
- `nginx.access.request_time` -> `float`
- `nginx.access.upstream_response_time` -> `float`
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
- `source.mmdb.dma_code` -> `long`
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
- `user_agent.device.name` -> `keyword`
- `user_agent.name` -> `keyword`
- `user_agent.original` -> `keyword`
- `user_agent.original.text` -> `match_only_text`
- `user_agent.os.full` -> `keyword`
- `user_agent.os.full.text` -> `match_only_text`
- `user_agent.os.name` -> `keyword`
- `user_agent.os.name.text` -> `match_only_text`
- `user_agent.os.version` -> `keyword`
- `user_agent.version` -> `keyword`

### Settings

#### Index
```json
{
  "index": {
    "lifecycle": {
      "name": "logs-nginx-ilm-policy"
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
- https://nginx.org/en/docs/http/ngx_http_log_module.html
- https://serverfault.com/questions/753682/iis-server-farm-with-arr-why-does-http-x-forwarded-for-have-a-port-number
- https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For