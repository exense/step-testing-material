// The two system properties show the JVM arguments passed to the local Java agent (--localAgentVmArgs)
output.add("ran", "groovy")
output.add("vmArgFoo", System.getProperty("e2e.foo", "unset"))
output.add("vmArgBar", System.getProperty("e2e.bar", "unset"))
