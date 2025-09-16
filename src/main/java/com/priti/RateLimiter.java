package com.priti;
import java.util.HashMap;
import java.util.Map;

public class RateLimiter {

    // Immutable class to represent bucket state
    public static class BucketState {
        public final double lastUpdateTime;
        public final double currentLevel;

        public BucketState(double lastUpdateTime, double currentLevel) {
            this.lastUpdateTime = lastUpdateTime;
            this.currentLevel = currentLevel;
        }

        @Override
        public String toString() {
            return String.format("BucketState{lastUpdateTime=%.3f, currentLevel=%.3f}",
                    lastUpdateTime, currentLevel);
        }
    }

    // Immutable class to represent rate limiter state
    public static class RateLimiterState {
        public final int capacity;
        public final double leakRate;
        public final Map<String, BucketState> userBuckets;

        public RateLimiterState(int capacity, double leakRate, Map<String, BucketState> userBuckets) {
            this.capacity = capacity;
            this.leakRate = leakRate;
            this.userBuckets = Map.copyOf(userBuckets); // Defensive copy for immutability
        }

        public RateLimiterState withUserBuckets(Map<String, BucketState> newUserBuckets) {
            return new RateLimiterState(capacity, leakRate, newUserBuckets);
        }
    }

    // Creates a new rate limiter
    public static RateLimiterState create_rate_limiter(int capacity, double leak_rate) {
        return new RateLimiterState(capacity, leak_rate, new HashMap<>());
    }

    // Determines if a request should be allowed
    public static Object[] allow_request(RateLimiterState limiter, String user_id, double timestamp) {
        // Handle timestamp going backwards - use the latest timestamp
        BucketState currentBucket = limiter.userBuckets.get(user_id);
        double effectiveTimestamp = timestamp;

        if (currentBucket != null && timestamp < currentBucket.lastUpdateTime) {
            effectiveTimestamp = currentBucket.lastUpdateTime;
        }

        // Calculate leaked amount since last update
        double leakedAmount = 0.0;
        if (currentBucket != null) {
            double timeElapsed = effectiveTimestamp - currentBucket.lastUpdateTime;
            leakedAmount = timeElapsed * limiter.leakRate;
        }

        // Calculate new bucket level after leaking
        double newLevel;
        if (currentBucket == null) {
            newLevel = 1.0; // First request for this user
        } else {
            newLevel = Math.max(0, currentBucket.currentLevel - leakedAmount);
            newLevel += 1.0; // Add the current request
        }

        // Check if request should be allowed
        boolean allowed = newLevel <= limiter.capacity;

        // If not allowed, don't update the bucket (leak but don't add the request)
        if (!allowed) {
            newLevel -= 1.0; // Remove the current request
        }

        // Create new bucket state
        BucketState newBucketState = new BucketState(effectiveTimestamp, newLevel);

        // Create new user buckets map with the updated state
        Map<String, BucketState> newUserBuckets = new HashMap<>(limiter.userBuckets);
        newUserBuckets.put(user_id, newBucketState);

        // Return result and new limiter state
        RateLimiterState newLimiter = limiter.withUserBuckets(newUserBuckets);
        return new Object[]{allowed, newLimiter};
    }

    // Returns current bucket information for debugging
    public static BucketState get_bucket_state(RateLimiterState limiter, String user_id) {
        return limiter.userBuckets.get(user_id);
    }

    // Test method
    public static void main(String[] args) {
        System.out.println("=== Testing Rate Limiter ===");

        // Test 1: Basic functionality
        System.out.println("\n1. Basic functionality:");
        RateLimiterState limiter = create_rate_limiter(5, 1.0);

        Object[] result1 = allow_request(limiter, "user1", 0.0);
        boolean allowed1 = (Boolean) result1[0];
        limiter = (RateLimiterState) result1[1];
        System.out.println("Request at t=0: " + allowed1 + ", state: " + get_bucket_state(limiter, "user1"));

        Object[] result2 = allow_request(limiter, "user1", 1.0);
        boolean allowed2 = (Boolean) result2[0];
        limiter = (RateLimiterState) result2[1];
        System.out.println("Request at t=1: " + allowed2 + ", state: " + get_bucket_state(limiter, "user1"));

        // Test 2: Burst handling
        System.out.println("\n2. Burst handling:");
        limiter = create_rate_limiter(3, 1.0);

        for (int i = 0; i < 5; i++) {
            Object[] result = allow_request(limiter, "user2", i * 0.1);
            boolean allowed = (Boolean) result[0];
            limiter = (RateLimiterState) result[1];
            System.out.println("Request " + (i+1) + " at t=" + (i*0.1) + ": " + allowed +
                    ", level: " + get_bucket_state(limiter, "user2").currentLevel);
        }

        //
        limiter = create_rate_limiter(4, 2.0);
        String userId = "user3";

        // Fill bucket completely
        for (int i = 0; i < 4; i++) {
            Object[] result = allow_request(limiter, userId, i * 0.1);
            limiter = (RateLimiterState) result[1];
        }

        // Test 3: Time-based leaking
        System.out.println("\n3. Time-based leaking:");
        limiter = create_rate_limiter(2, 1.0);

        Object[] result3 = allow_request(limiter, "user3", 0.0);
        Object[] result4 = allow_request(limiter, "user3", 0.1);
        Object[] result5 = allow_request(limiter, "user3", 0.2); // Should be rejected
        Object[] result6 = allow_request(limiter, "user3", 2.0); // Should be allowed after leaking

        System.out.println("Request at t=0.0: " + (Boolean) result3[0]);
        System.out.println("Request at t=0.1: " + (Boolean) result4[0]);
        System.out.println("Request at t=0.2: " + (Boolean) result5[0]);
        System.out.println("Request at t=2.0: " + (Boolean) result6[0]);

        // Test 4: Multiple users
        System.out.println("\n4. Multiple users:");
        limiter = create_rate_limiter(2, 1.0);

        Object[] user1Req1 = allow_request(limiter, "userA", 0.0);
        Object[] user2Req1 = allow_request(limiter, "userB", 0.0);
        Object[] user1Req2 = allow_request(limiter, "userA", 0.1);
        Object[] user2Req2 = allow_request(limiter, "userB", 0.1);
        Object[] user1Req3 = allow_request(limiter, "userA", 0.2); // Should be rejected
        Object[] user2Req3 = allow_request(limiter, "userB", 0.2); // Should be rejected

        System.out.println("UserA requests: " + (Boolean) user1Req1[0] + ", " +
                (Boolean) user1Req2[0] + ", " + (Boolean) user1Req3[0]);
        System.out.println("UserB requests: " + (Boolean) user2Req1[0] + ", " +
                (Boolean) user2Req2[0] + ", " + (Boolean) user2Req3[0]);

        // Test 5: Edge case - timestamp going backwards
        System.out.println("\n5. Timestamp going backwards:");
        limiter = create_rate_limiter(3, 1.0);

        Object[] forward = allow_request(limiter, "user4", 5.0);
        Object[] backward = allow_request(limiter, "user4", 3.0); // Earlier timestamp

        System.out.println("Request at t=5.0: " + (Boolean) forward[0]);
        System.out.println("Request at t=3.0: " + (Boolean) backward[0] +
                " (used effective timestamp: 5.0)");

        // Test 6: Large time gap
        System.out.println("\n6. Large time gap:");
        limiter = create_rate_limiter(3, 1.0);

        Object[] first = allow_request(limiter, "user5", 0.0);
        Object[] second = allow_request(limiter, "user5", 100.0); // Large gap

        System.out.println("Request at t=0.0: " + (Boolean) first[0]);
        System.out.println("Request at t=100.0: " + (Boolean) second[0] +
                " (bucket should be empty due to leaking)");
    }


}

