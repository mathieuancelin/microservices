'use strict'

let compileAggregator = run({
  name: 'compileAggregator',
  sh: './gradlew aggregator:compileJava',
  watch: ['aggregator/src/main/java/**', 'aggregator/src/main/resources/**']
})

let compileBikeService = run({
  name: 'compileBikeService',
  sh: './gradlew bikes-service:compileJava',
  watch: ['bikes-service/src/main/java/**', 'bikes-service/src/main/resources/**']
})

let compileContainerService = run({
  name: 'compileContainerService',
  sh: './gradlew aggregator:compileJava',
  watch: ['containers-service/src/main/java/**', 'containers-service/src/main/resources/**']
})

let compileServiceProxy = run({
  name: 'compileServiceProxy',
  sh: './gradlew service-proxy:compileJava',
  watch: ['service-proxy/src/main/java/**', 'service-proxy/src/main/resources/**']
})

/*let compileDevRunner = run({
  name: 'compileDevRunner',
  sh: './gradlew dev-runner:compileJava',
  watch: ['dev-runner/src/main/java/**', 'dev-runner/src/main/resources/**']
}).dependsOn(compileAggregator, compileBikeService, compileContainerService, compileServiceProxy)*/

let server = runServer({
  httpPort,
  sh: `./gradlew dev-runner:run -q -Dport="${httpPort}"`
}).dependsOn(compileAggregator, compileBikeService, compileContainerService, compileServiceProxy)

proxy(server, 8080)
