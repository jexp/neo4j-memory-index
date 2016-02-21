# Neo4j In-Memory Schema Index Provider

**This is in alpha-state, still in development. Not ready for serious use**

Implements a Schema Index Provider for Neo4j, label based indexes using an in memory sorted-map, which.

## Approach

This index only exists in memory, it is populated when the database starts up.
The index is not persisted to disk, it should provide read and write performance for memory-access speeds

## Installation

`mvn clean install`

That will create a zip-file: `target/memory-index-1.0-provider.zip` whose content you have to put in Neo4j's classpath.


## Ideas

### optimize key-storage

- optimize map on first addition for a concrete value type, e.g. int-long or float-long map or int-long[] map
- optionally use different instances for different value types
- if we have multiple instances, we can ask them in parallel for their value
- write an implementation based on sorted arrays using Arrays.binarySearch()
- use non-sorted map by default, trigger changing to sorted map/sorting only after first range access was requested

### optimize value (node-ids) storage

- initiallly optimize for single node-id entry
- use a separate instance for single node values int->long map
- use an optimized instance as long as node-ids are < Integer.MAX_VALUE
- optionally use a compressed bitset for larger amounts of node-ids
