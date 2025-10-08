package com.priti;

public class LeakyBucketRateLimiterTest {

    public static void main(String[] args) {
        testBasicFunctionality();
        testBurstHandling();
        testTimeBasedLeaking();
        testMultipleUsers();
        testEdgeCases();

        System.out.println("All tests passed!");
    }

    public static void testBasicFunctionality() {
        System.out.println("=== Testing Basic Functionality ===");

        LeakyBucketRateLimiter.RateLimiterState limiter =
                LeakyBucketRateLimiter.create_rate_limiter(5, 1.0);

        // First request
        LeakyBucketRateLimiter.AllowResult result1 =
                LeakyBucketRateLimiter.allow_request(limiter, "user1", 0.0);
        System.out.println("First request allowed: " + result1.allowed);
        assert result1.allowed : "First request should be allowed";

        // Second request shortly after
        LeakyBucketRateLimiter.AllowResult result2 =
                LeakyBucketRateLimiter.allow_request(result1.newLimiterState, "user1", 1.0);
        System.out.println("Second request allowed: " + result2.allowed);
        assert result2.allowed : "Second request should be allowed";

        // Check bucket state
        LeakyBucketRateLimiter.BucketInfo bucketInfo =
                LeakyBucketRateLimiter.get_bucket_state(result2.newLimiterState, "user1");
        System.out.println("Bucket state: " + bucketInfo);
        assert bucketInfo != null : "Bucket info should exist for user1";

        System.out.println("Basic functionality test passed!\n");
    }

    public static void testBurstHandling() {
        System.out.println("=== Testing Burst Handling ===");

        LeakyBucketRateLimiter.RateLimiterState limiter =
                LeakyBucketRateLimiter.create_rate_limiter(3, 1.0);

        LeakyBucketRateLimiter.RateLimiterState currentState = limiter;
        int allowedCount = 0;

        // Send 5 rapid requests
        for (int i = 0; i < 5; i++) {
            LeakyBucketRateLimiter.AllowResult result =
                    LeakyBucketRateLimiter.allow_request(currentState, "user1", i * 0.1);
            currentState = result.newLimiterState;
            if (result.allowed) {
                allowedCount++;
            }
            System.out.println("Request " + i + " allowed: " + result.allowed);
        }

        System.out.println("Total allowed: " + allowedCount + " out of 5");
        assert allowedCount == 3 : "Only 3 requests should be allowed (capacity=3)";

        System.out.println("Burst handling test passed!\n");
    }

    public static void testTimeBasedLeaking() {
        System.out.println("=== Testing Time-Based Leaking ===");

        LeakyBucketRateLimiter.RateLimiterState limiter =
                LeakyBucketRateLimiter.create_rate_limiter(2, 1.0); // Leaks 1 unit per second

        // Fill the bucket
        LeakyBucketRateLimiter.AllowResult result1 =
                LeakyBucketRateLimiter.allow_request(limiter, "user1", 0.0);
        LeakyBucketRateLimiter.AllowResult result2 =
                LeakyBucketRateLimiter.allow_request(result1.newLimiterState, "user1", 0.1);

        // Bucket should be full now (2 units)
        LeakyBucketRateLimiter.BucketInfo fullBucket =
                LeakyBucketRateLimiter.get_bucket_state(result2.newLimiterState, "user1");
        System.out.println("Full bucket water level: " + fullBucket.waterLevel);
        assert fullBucket.waterLevel == 2.0 : "Bucket should be full";

        // Wait for 1.5 seconds - should leak 1.5 units
        LeakyBucketRateLimiter.AllowResult result3 =
                LeakyBucketRateLimiter.allow_request(result2.newLimiterState, "user1", 1.6);
        System.out.println("Request after 1.6s allowed: " + result3.allowed);

        LeakyBucketRateLimiter.BucketInfo leakedBucket =
                LeakyBucketRateLimiter.get_bucket_state(result3.newLimiterState, "user1");
        System.out.println("Leaked bucket water level: " + leakedBucket.waterLevel);
        assert leakedBucket.waterLevel < 1.0 : "Bucket should have leaked";

        System.out.println("Time-based leaking test passed!\n");
    }

    public static void testMultipleUsers() {
        System.out.println("=== Testing Multiple Users ===");

        LeakyBucketRateLimiter.RateLimiterState limiter =
                LeakyBucketRateLimiter.create_rate_limiter(2, 1.0);

        // User1 fills their bucket
        LeakyBucketRateLimiter.AllowResult result1 =
                LeakyBucketRateLimiter.allow_request(limiter, "user1", 0.0);
        LeakyBucketRateLimiter.AllowResult result2 =
                LeakyBucketRateLimiter.allow_request(result1.newLimiterState, "user1", 0.1);

        // User2 should have independent bucket
        LeakyBucketRateLimiter.AllowResult result3 =
                LeakyBucketRateLimiter.allow_request(result2.newLimiterState, "user2", 0.2);
        System.out.println("User2 first request allowed: " + result3.allowed);
        assert result3.allowed : "User2 should have independent bucket";

        // User1's bucket should still be full
        LeakyBucketRateLimiter.BucketInfo user1Bucket =
                LeakyBucketRateLimiter.get_bucket_state(result3.newLimiterState, "user1");
        LeakyBucketRateLimiter.BucketInfo user2Bucket =
                LeakyBucketRateLimiter.get_bucket_state(result3.newLimiterState, "user2");

        System.out.println("User1 bucket: " + user1Bucket.waterLevel);
        System.out.println("User2 bucket: " + user2Bucket.waterLevel);

        assert user1Bucket.waterLevel == 2.0 : "User1 bucket should be full";
        assert user2Bucket.waterLevel == 1.0 : "User2 bucket should have 1 unit";

        System.out.println("Multiple users test passed!\n");
    }

    public static void testEdgeCases() {
        System.out.println("=== Testing Edge Cases ===");

        // Test first request from new user
        LeakyBucketRateLimiter.RateLimiterState limiter =
                LeakyBucketRateLimiter.create_rate_limiter(5, 1.0);

        LeakyBucketRateLimiter.AllowResult result =
                LeakyBucketRateLimiter.allow_request(limiter, "new_user", 10.0);
        assert result.allowed : "First request from new user should be allowed";

        // Test timestamps going backwards
        LeakyBucketRateLimiter.AllowResult result2 =
                LeakyBucketRateLimiter.allow_request(result.newLimiterState, "new_user", 5.0);
        System.out.println("Request with earlier timestamp handled correctly");

        // Test very large time gaps
        LeakyBucketRateLimiter.AllowResult result3 =
                LeakyBucketRateLimiter.allow_request(result2.newLimiterState, "new_user", 10000.0);
        LeakyBucketRateLimiter.BucketInfo bucket =
                LeakyBucketRateLimiter.get_bucket_state(result3.newLimiterState, "new_user");
        System.out.println("Bucket after large time gap: " + bucket.waterLevel);
        assert bucket.waterLevel == 1.0 : "Bucket should be empty except for current request";

        // Test non-existent user bucket state
        LeakyBucketRateLimiter.BucketInfo nonExistent =
                LeakyBucketRateLimiter.get_bucket_state(limiter, "non_existent_user");
        assert nonExistent == null : "Should return null for non-existent user";

        System.out.println("Edge cases test passed!\n");
    }
}
