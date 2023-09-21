There are no integrations available for Bind log processing so we decided to process these logs ourselves.

There are 3 main types of bind events being received.

## Type 1 - Queries

These events represent regular queries

Example: 
> <99>Aug  8 00:00:00 ns named\[22222\]: client **@0x7fc85ccfcb68** 192.168.1.1#66666 **(ua.pt)**: **view external:** query: ua.pt IN DNSKEY -ED (193.136.172.18)

**Note:** Fields in bold are optional and do not appear in every query

**Note:** The hexadecimal address after client (`@0x7fc85ccfcb68`) represents [the memory address of the data structure used to hold the working state for the query](https://serverfault.com/a/1017500) and thus is not mapped to any field.

The data in the preceding event is interpreted in the following way:
- `<99>` -> **ignored**
- `Aug  8 00:00:00` -> the timestamp of the event
- `ns` -> the hostname of the server that generated the event
- `named` -> the process name of the process in the server that received the query
- `22222` -> the process id of the process in the server that received the query
- `@0x7fc85ccfcb68` -> **ignored**
- `192.168.1.1` -> the IP of the client making the query
- `66666` -> the port of the client making the query
- `external` -> the DNS view the query came from
- `ua.pt` -> the name being queried
- `IN` -> the [query class](https://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml#dns-parameters-2)
- `DNSKEY` -> the [query type](https://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml#dns-parameters-4)
- `-ED` -> the [query flags](https://kb.isc.org/docs/aa-00434)
- `193.136.172.18` -> the IP of the server that received the query

The data in the preceding event is mapped to [ECS](https://www.elastic.co/guide/en/ecs/current/ecs-field-reference.html)in the following way:
- `Aug  8 00:00:00` -> `@timestamp`
- `ns` -> `host.hostname`
- `named` -> `process.name` and `event.provider`
- `22222` -> `process.pid`
- `192.168.1.1` -> `client.ip`
- `66666` -> `client.port`
- `external` -> `interface.alias`
- `ua.pt` -> `dns.question.name`
- `IN` -> `dns.question.class`
- `DNSKEY` -> `dns.question.type`
- `-ED` -> `dns.header_flags`
- `193.136.172.18` -> `host.ip`

The timestamp is processed and converted from `Europe/Lisbon` timezone to `UTC`.

IP Geo location (City and AS databases) is ran on client's IPs that come from the `external` view (i.e. public IPs).

The header flags are mapped from the [bind9 representation](https://kb.isc.org/docs/aa-00434) to the [spec representation](https://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml#dns-parameters-12). Note that not all bind9 flags can be mapped to the official spec.

The mapping is done as follows:
- `+` -> `RD (Recursion Desired)`
- `D` -> `DO (DNSSEC answer OK)`
- `C` -> `CD (Checking Disabled)`

The DNS name being queried is divided into 3 main parts if possible. The eTLD (effective Top Level Domain), the registered domain and the subdomain (mapped to `dns.question.top_level_domain`, `dns.question.registered_domain` and `dns.question.subdomain` respectively). The [Public Suffix List](https://publicsuffix.org/) is used to determine all available eTLDs. 

For example the name `sub1.sub2.sub3.co.uk` is divided as follows:
- `eTLD` -> `co.uk`
- `registed domain` -> `sub3.co.uk`
- `subdomain` -> `sub1.sub2`

The following fields are constant in this type of events:
- `event.action` = `query_received`
- `dns.opcode` = `QUERY`
- `dns.type` = `query`

More on DNS Opcodes [here](https://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml#dns-parameters-5)

### Special case - Reverse DNS queries

Reverse DNS queries represent a challenge in parsing given the character `.` is used to separate both the domain labels and octets/quartets.

Reverse IPv4:
`1.1.168.192.in-addr.arpa`

Reverse IPv6:
`1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.ip6.arpa`

Both IPv4 and IPv6 reverse queries are detected and parsed. The addresses are reversed to their proper representation and mapped to `dns.question.ip` (yes, they are added to `related.ip`)

For events of this type the value `reverse_dns_lookup` is added to the `tags` field.

## Type 2 - RPZ rewrites

[Response Policy Zones](https://www.isc.org/rpz/) (RPZ) is a DNS Firewall that can block access to dangerous and or malicious network assets by intercepting them and optionally rewriting them to a safe location that informs the user of what happened.

Example:
> <99>Aug  8 00:00:00 ns named\[22222\]:  client **@0x7fc85ccfcb68** 192.168.1.1#66666 **(bad.somewhere)**: **view internal:** rpz QNAME Local-Data rewrite bad.somewhere/A/IN via bad.somewhere.malware

**Note:** Fields in bold are optional and do not appear in every query

Given the similarity of the initial part of these events that have already been described, the corresponding fields will not be described here again. They can be found in [[#Type 1 - Queries]]

Additional fields are interpreted as:
- `bad.somewhere` -> the original name being queried
- `A` -> the [query type](https://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml#dns-parameters-4)
- `IN` -> the [query class](https://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml#dns-parameters-2)
- `bad.somewhere.malware` -> the name being rewritten to replace the original

Additional fields are mapped as:
- `bad.somewhere` -> `dns.question.name` 
- `A` -> `dns.question.type` and `dns.answers[0].type`
- `IN` -> `dns.question.class` and `dns.answers[0].class`
- `bad.somewhere.malware` -> `dns.answers[0].name`

Similarly to [[#Type 1 - Queries]] the following fields are constant in this type of events:
- `event.action` = `query_received`
- `dns.opcode` = `QUERY`
- `dns.type` = `query`

With the addition of:
- `dns.answers[0].rpz_rewritten` = `true`

## Type 3 - Notifications

There are 2 types of notification events. [[#Notifies sent]] and [[#Notification received]]

Given the similarity of the initial part of these events that have already been described, the corresponding fields will not be described here again. They can be found in [[#Type 1 - Queries]]

### Notifies sent

Example:
```
<99>Aug  8 00:00:00 ns named[22222]: zone qa.ua.pt/IN/internal: sending notifies (serial 123456)
```

Additional fields are interpreted as:
- `qa.ua.pt` -> the DNS zone
- `IN` -> the [query class](https://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml#dns-parameters-2)
- `internal` -> the DNS view the query came from

Additional fields are mapped as:
- `qa.ua.pt` -> `dns.zone`
- `IN` -> `dns.class`
- `internal` -> `interface.alias`

The following fields are constant in this type of events:
- `event.action` = `received notify`
- `dns.opcode` = `UPDATE`

### Notification received

Example:
```
<99>Aug  8 00:00:00 ns named[22222]: client 193.136.172.18#36522: view internal: received notify for zone 'qa.ua.pt'
```

Additional fields are interpreted as:
- `qa.ua.pt` -> the DNS zone
- `internal` -> the DNS view the query came from

Additional fields are mapped as:
- `qa.ua.pt` -> `dns.zone`
- `internal` -> `interface.alias`

The following fields are constant in this type of events:
- `event.action` = `sending notifies`
- `dns.opcode` = `UPDATE`

## Global 

The following fields are constant for all types of events:
- `event.kind` = `event`
- `event.module` = `bind`
- `event.category` -> `network`
- `event.type` -> `access`
- `event.created` -> the current timestamp in `ISO8601` format
- `event.dataset` -> `bind`

The related fields (`related.ip` and `related.hosts`) are filled with all existing corresponding values for all events.

**Note:** additional fields to the ones specified here will be found. these are unrelated to the event itself and are mainly related to Elastic Search Stream metadata and IP Geo-location.

Events that do not match any of these types are parsed as a string to the `message` field. We still try to parse the `client` and `interface` fields.
# Index Template
A custom index template was created along with a custom index lifecycle policy.

### Fields:
```json
{ "template": { "settings": { "index": { "lifecycle": { "name": "logs-bind-ilm-policy" }, "codec": "best_compression", "routing": { "allocation": { "include": { "_tier_preference": "data_hot" } } }, "number_of_replicas": "1" } }, "mappings": { "dynamic": "true", "dynamic_date_formats": [ "strict_date_optional_time", "yyyy/MM/dd HH:mm:ss Z||yyyy/MM/dd Z" ], "dynamic_templates": [ { "strings_as_keyword": { "match_mapping_type": "string", "mapping": { "ignore_above": 1024, "type": "keyword" } } }, { "match_ip": { "match": "ip", "match_mapping_type": "string", "mapping": { "type": "ip" } } } ], "date_detection": true, "numeric_detection": false, "properties": { "@timestamp": { "type": "date" }, "@version": { "type": "keyword", "ignore_above": 1024 }, "client": { "properties": { "as": { "properties": { "number": { "type": "long" }, "organizarion": { "properties": { "name": { "type": "keyword", "ignore_above": 1024 } } } } }, "geo": { "properties": { "city_name": { "type": "keyword", "ignore_above": 1024 }, "continent_code": { "type": "keyword", "ignore_above": 1024 }, "country_iso_code": { "type": "keyword", "ignore_above": 1024 }, "country_name": { "type": "keyword", "ignore_above": 1024 }, "location": { "type": "geo_point" }, "postal_code": { "type": "keyword", "ignore_above": 1024 }, "region_iso_code": { "type": "keyword", "ignore_above": 1024 }, "region_name": { "type": "keyword", "ignore_above": 1024 }, "timezone": { "type": "keyword", "ignore_above": 1024 } } }, "ip": { "type": "ip" }, "mmdb": { "properties": { "dma_code": { "type": "long" } } }, "port": { "type": "long" } } }, "data_stream": { "properties": { "dataset": { "type": "constant_keyword" }, "namespace": { "type": "constant_keyword" }, "type": { "type": "constant_keyword" } } }, "dns": { "properties": { "answers": { "properties": { "class": { "type": "keyword", "ignore_above": 1024 }, "name": { "type": "keyword", "ignore_above": 1024 }, "rpz_rewritten": { "type": "boolean" }, "type": { "type": "keyword", "ignore_above": 1024 } } }, "header_flags": { "type": "keyword", "ignore_above": 1024 }, "op_code": { "type": "keyword", "ignore_above": 1024 }, "question": { "properties": { "class": { "type": "keyword", "ignore_above": 1024 }, "ip": { "type": "ip" }, "name": { "type": "keyword", "ignore_above": 1024 }, "registered_domain": { "type": "keyword", "ignore_above": 1024 }, "subdomain": { "type": "keyword", "ignore_above": 1024 }, "top_level_domain": { "type": "keyword", "ignore_above": 1024 }, "type": { "type": "keyword", "ignore_above": 1024 } } }, "type": { "type": "keyword", "ignore_above": 1024 }, "zone": { "type": "keyword", "ignore_above": 1024 } } }, "ecs": { "properties": { "version": { "type": "keyword", "ignore_above": 1024 } } }, "event": { "properties": { "action": { "type": "keyword", "ignore_above": 1024 }, "category": { "type": "keyword", "ignore_above": 1024 }, "created": { "type": "date" }, "kind": { "type": "constant_keyword" }, "module": { "type": "constant_keyword" }, "original": { "type": "keyword", "ignore_above": 4096 }, "provider": { "type": "keyword", "ignore_above": 1024 }, "type": { "type": "keyword", "ignore_above": 1024 } } }, "host": { "properties": { "hostname": { "type": "keyword", "ignore_above": 1024 }, "ip": { "type": "ip" } } }, "input": { "properties": { "type": { "type": "keyword", "ignore_above": 1024 } } }, "interface": { "properties": { "alias": { "type": "keyword", "ignore_above": 1024 } } }, "message": { "type": "match_only_text" }, "process": { "properties": { "name": { "type": "keyword", "ignore_above": 1024 }, "pid": { "type": "long" } } }, "related": { "properties": { "hosts": { "type": "keyword", "ignore_above": 1024 }, "ip": { "type": "ip" } } }, "tags": { "type": "keyword", "ignore_above": 1024 } } }, "aliases": {} } }
```

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
- https://jpmens.net/2011/02/22/bind-querylog-know-your-flags/
- https://serverfault.com/questions/1017463/dns-ddos-attack-would-like-to-understand-log
- https://superuser.com/questions/1264211/understanding-bind9-query-log
- https://docs.nxlog.co/userguide/integrate/dns-monitoring-linux.html
- https://kb.isc.org/docs/aa-00434