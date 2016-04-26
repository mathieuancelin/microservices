'use strict'

let compileLibrary = run({
  name: 'compileLibrary',
  sh: './gradlew library:compileJava',
  watch: ['library/src/main/java/**', 'library/src/main/resources/**']
})

let compileAggregator = run({
  name: 'compileAggregator',
  sh: './gradlew aggregator:compileJava',
  watch: ['aggregator/src/main/java/**', 'aggregator/src/main/resources/**']
}).dependsOn(compileLibrary)

let compileBikeService = run({
  name: 'compileBikeService',
  sh: './gradlew bikes-service:compileJava',
  watch: ['bikes-service/src/main/java/**', 'bikes-service/src/main/resources/**']
}).dependsOn(compileLibrary)

let compileContainerService = run({
  name: 'compileContainerService',
  sh: './gradlew aggregator:compileJava',
  watch: ['containers-service/src/main/java/**', 'containers-service/src/main/resources/**']
}).dependsOn(compileLibrary)

let compileServiceProxy = run({
  name: 'compileServiceProxy',
  sh: './gradlew service-proxy:compileJava',
  watch: ['service-proxy/src/main/java/**', 'service-proxy/src/main/resources/**']
}).dependsOn(compileLibrary)

let server = runServer({
  httpPort,
  sh: `./gradlew dev-runner:run -q -Dport="${httpPort}"`
}).dependsOn(compileLibrary, compileAggregator, compileBikeService, compileContainerService, compileServiceProxy)

proxy(server, 8080)
