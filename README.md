regproxy
========

An http reverse proxy with a dynamic list of upstreams.

Requests are forwarded to all upstreams in parallel and all are expected to be successful. Only one response is 
returned to the client.

## Use cases:

It can be used to implement a control plane for a dynamic set of services, where commands are synchronous and 
errors must be highlighted to the caller rather than ignored.

## Prerequisites
* java > 11
* maven

## Instructions
To build:
```bash
mvn clean package
```

To run:
```bash
java -jar target/app.jar
```

For command line options see: 
```bash
java -jar target/app.jar --help
```

To test: 
* start the proxy
* start up 2 http servers on different ports
* register both those servers with the reverse proxy
* make a request to the proxy
```bash
java -jar target/app.jar&
python -m http.server 3000&
python -m http.server 3001&
curl -X PUT http://localhost:9876/register -d '{"name": "upstream-1", "callback": "http://localhost:3000"}'
curl -X PUT http://localhost:9876/register -d '{"name": "upstream-2", "callback": "http://localhost:3001"}'
curl http://localhost:9877/
```
Then you can stop one upstream, and see requests will now fail
```bash
kill %1                     # kill the first python server
curl http://localhost:9877/ # Should fail
```

## Extensions:

* Allow deregistration of upstreams 
* Upstreams can register for specific URLs only
* Replace the in-memory list with a service discovery system e.g. netflix eureka