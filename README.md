# moovup-programming-test
Implementing a rate limiter using a leaky bucket algorithm:

Brief explanation of design decisions and trade-offs:
1. Immutable Design Decisions:
   -Chose immutability for thread-safety and predictability over maximum performance-Used immutable RateLimiterState and BucketState classes
   -Each operation returns a new state object instead of mutating existing state
2. Memory Management:
   -Uses defensive copying for immutability
   -Each user gets their own independent bucket
3. Timestamp Handling
   -Handles backwards timestamps by using the latest known timestamp
4. Leak Calculation
   -Calculates exact leakage based on elapsed time since last update
5. Overflow Handling
      -If request would overflow, the bucket leaks but doesn't add the new request
      -Maintains accurate water level for subsequent requests
