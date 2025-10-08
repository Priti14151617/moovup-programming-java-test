package com.priti;
import java.util.*;

public class LeakyBucketRateLimiter {

    // Immutable bucket state
    public static class BucketState {
        public final double waterLevel;
        public final double lastUpdateTime;

        public BucketState(double waterLevel, double lastUpdateTime) {
            this.waterLevel = waterLevel;
            this.lastUpdateTime = lastUpdateTime;
        }

       /* @Override
        public String toString() {
            return String.format("BucketState{waterLevel=%.2f, lastUpdateTime=%.2f}",
                    waterLevel, lastUpdateTime);
        }*/
    }

    // Rate limiter state - immutable
    public static class RateLimiterState {
        public final int capacity;
        public final double leakRate;
        public final Map<String, BucketState> userBuckets;

        public RateLimiterState(int capacity, double leakRate, Map<String, BucketState> userBuckets) {
            this.capacity = capacity;
            this.leakRate = leakRate;
            this.userBuckets = Collections.unmodifiableMap(new HashMap<>(userBuckets));
        }

        public RateLimiterState withUpdatedBucket(String userId, BucketState newBucket) {
            Map<String, BucketState> updatedBuckets = new HashMap<>(userBuckets);
            updatedBuckets.put(userId, newBucket);
            return new RateLimiterState(capacity, leakRate, updatedBuckets);
        }
    }

    // Result of allow_request operation
    public static class AllowResult {
        public final boolean allowed;
        public final RateLimiterState newLimiterState;

        public AllowResult(boolean allowed, RateLimiterState newLimiterState) {
            this.allowed = allowed;
            this.newLimiterState = newLimiterState;
        }
    }

    // Bucket info for debugging
    public static class BucketInfo {
        public final double waterLevel;
        public final double lastUpdateTime;
        public final int capacity;
        public final double leakRate;

        public BucketInfo(double waterLevel, double lastUpdateTime, int capacity, double leakRate) {
            this.waterLevel = waterLevel;
            this.lastUpdateTime = lastUpdateTime;
            this.capacity = capacity;
            this.leakRate = leakRate;
        }

     /*   @Override
        public String toString() {
            return String.format(
                    "BucketInfo{waterLevel=%.2f, lastUpdateTime=%.2f, capacity=%d, leakRate=%.2f}",
                    waterLevel, lastUpdateTime, capacity, leakRate);
        }*/
    }

    // Core functions

    public static RateLimiterState create_rate_limiter(int capacity, double leakRate) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (leakRate <= 0) {
            throw new IllegalArgumentException("Leak rate must be positive");
        }
        return new RateLimiterState(capacity, leakRate, new HashMap<>());
    }

    public static AllowResult allow_request(RateLimiterState limiter, String userId, double timestamp) {
        BucketState currentBucket = limiter.userBuckets.get(userId);

        // Calculate new water level after leaking
        BucketState updatedBucket = calculateUpdatedBucket(currentBucket, limiter, timestamp);

        // Check if request can be allowed
        boolean allowed = updatedBucket.waterLevel < limiter.capacity;

        // If allowed, add water to bucket
        BucketState finalBucket = allowed ?
                new BucketState(updatedBucket.waterLevel + 1, timestamp) :
                updatedBucket;

        RateLimiterState newLimiterState = limiter.withUpdatedBucket(userId, finalBucket);
        return new AllowResult(allowed, newLimiterState);
    }

    public static BucketInfo get_bucket_state(RateLimiterState limiter, String userId) {
        BucketState bucket = limiter.userBuckets.get(userId);
        if (bucket == null) {
            return null;
        }
        return new BucketInfo(bucket.waterLevel, bucket.lastUpdateTime,
                limiter.capacity, limiter.leakRate);
    }

    // Helper method to calculate water level after leaking
    private static BucketState calculateUpdatedBucket(BucketState currentBucket,
                                                      RateLimiterState limiter,
                                                      double timestamp) {
        if (currentBucket == null) {
            // First request from user - start with empty bucket
            return new BucketState(0.0, timestamp);
        }

        double elapsedTime = timestamp - currentBucket.lastUpdateTime;

        // Handle time going backwards or staying the same
        if (elapsedTime <= 0) {
            return currentBucket;
        }

        // Calculate leaked water
        double leakedWater = elapsedTime * limiter.leakRate;
        double newWaterLevel = Math.max(0.0, currentBucket.waterLevel - leakedWater);

        return new BucketState(newWaterLevel, timestamp);
    }
}