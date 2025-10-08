import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LeakyBucketRateLimiter {

    // Immutable bucket state
    public static class BucketState {
        public final double waterLevel;
        public final double lastUpdateTime;

        public BucketState(double waterLevel, double lastUpdateTime) {
            this.waterLevel = waterLevel;
            this.lastUpdateTime = lastUpdateTime;
        }

        @Override
        public String toString() {
            return String.format("BucketState{waterLevel=%.2f, lastUpdateTime=%.2f}",
                    waterLevel, lastUpdateTime);
        }
    }

    // Immutable rate limiter state
    public static class RateLimiter {
        public final int capacity;
        public final double leakRate;
        public final Map<String, BucketState> userBuckets;

        public RateLimiter(int capacity, double leakRate, Map<String, BucketState> userBuckets) {
            this.capacity = capacity;
            this.leakRate = leakRate;
            this.userBuckets = Collections.unmodifiableMap(new HashMap<>(userBuckets));
        }
    }

    // Result class for allow_request
    public static class AllowResult {
        public final boolean allowed;
        public final RateLimiter newLimiter;

        public AllowResult(boolean allowed, RateLimiter newLimiter) {
            this.allowed = allowed;
            this.newLimiter = newLimiter;
        }
    }

    /**
     * Creates a new rate limiter
     * @param capacity maximum bucket size
     * @param leakRate units leaked per second
     * @return new RateLimiter instance
     */
    public static RateLimiter create_rate_limiter(int capacity, double leakRate) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (leakRate <= 0) {
            throw new IllegalArgumentException("Leak rate must be positive");
        }
        return new RateLimiter(capacity, leakRate, new HashMap<>());
    }

    /**
     * Updates bucket state by applying leakage based on elapsed time
     */
    private static BucketState updateBucketLeakage(BucketState currentState,
                                                   double currentTime,
                                                   double leakRate,
                                                   int capacity) {
        if (currentState == null) {
            return new BucketState(0.0, currentTime);
        }

        double elapsedTime = currentTime - currentState.lastUpdateTime;

        // Handle cases where time goes backwards or doesn't change
        if (elapsedTime <= 0) {
            return currentState;
        }

        double leakedAmount = elapsedTime * leakRate;
        double newWaterLevel = Math.max(0.0, currentState.waterLevel - leakedAmount);

        return new BucketState(newWaterLevel, currentTime);
    }

    /**
     * Determines if a request should be allowed
     * @param limiter current rate limiter state
     * @param userId user identifier
     * @param timestamp current timestamp in seconds
     * @return AllowResult with decision and new limiter state
     */
    public static AllowResult allow_request(RateLimiter limiter, String userId, double timestamp) {
        // Get current bucket state and update for leakage
        BucketState currentState = limiter.userBuckets.get(userId);
        BucketState updatedState = updateBucketLeakage(currentState, timestamp, limiter.leakRate, limiter.capacity);

        // Check if request can be accommodated
        boolean allowed = updatedState.waterLevel + 1 <= limiter.capacity;

        // Calculate new water level
        double newWaterLevel = allowed ? updatedState.waterLevel + 1 : updatedState.waterLevel;

        // Create new bucket state
        BucketState newState = new BucketState(newWaterLevel, timestamp);

        // Create new user buckets map with updated state
        Map<String, BucketState> newUserBuckets = new HashMap<>(limiter.userBuckets);
        newUserBuckets.put(userId, newState);

        // Create new limiter
        RateLimiter newLimiter = new RateLimiter(limiter.capacity, limiter.leakRate, newUserBuckets);

        return new AllowResult(allowed, newLimiter);
    }

    /**
     * Returns current bucket information for debugging
     * @param limiter current rate limiter
     * @param userId user identifier
     * @return bucket info or null if user doesn't exist
     */
    public static BucketState get_bucket_state(RateLimiter limiter, String userId) {
        return limiter.userBuckets.get(userId);
    }

    // Test methods
    public static void runTests() {
        System.out.println("=== Running Rate Limiter Tests ===");

        // Test 1: Basic functionality
        System.out.println("\n1. Basic functionality test:");
        RateLimiter limiter = create_rate_limiter(5, 1.0);
        AllowResult result1 = allow_request(limiter, "user1", 0.0);
        System.out.println("First request at t=0: " + result1.allowed + " (expected: true)");

        AllowResult result2 = allow_request(result1.newLimiter, "user1", 1.0);
        System.out.println("Second request at t=1: " + result2.allowed + " (expected: true)");

        // Test 2: Burst handling
        System.out.println("\n2. Burst handling test:");
        RateLimiter burstLimiter = create_rate_limiter(3, 1.0);
        AllowResult burstResult = null;
        RateLimiter currentLimiter = burstLimiter;

        // Send 5 rapid requests
        for (int i = 0; i < 5; i++) {
            burstResult = allow_request(currentLimiter, "user2", 0.0 + i * 0.1);
            System.out.println("Request " + (i + 1) + " at t=" + (0.0 + i * 0.1) +
                    ": " + burstResult.allowed + " (waterLevel: " +
                    get_bucket_state(burstResult.newLimiter, "user2").waterLevel + ")");
            currentLimiter = burstResult.newLimiter;
        }

        // Test 3: Time-based leaking
        System.out.println("\n3. Time-based leaking test:");
        RateLimiter leakLimiter = create_rate_limiter(3, 1.0);

        // Fill the bucket
        AllowResult leakResult = allow_request(leakLimiter, "user3", 0.0);
        leakResult = allow_request(leakResult.newLimiter, "user3", 0.1);
        leakResult = allow_request(leakResult.newLimiter, "user3", 0.2);
        System.out.println("Bucket filled. Water level: " +
                get_bucket_state(leakResult.newLimiter, "user3").waterLevel);

        // Wait for leakage
        leakResult = allow_request(leakResult.newLimiter, "user3", 2.0);
        System.out.println("After 2 seconds, request allowed: " + leakResult.allowed +
                " (waterLevel: " + get_bucket_state(leakResult.newLimiter, "user3").waterLevel + ")");

        // Test 4: Multiple users
        System.out.println("\n4. Multiple users test:");
        RateLimiter multiLimiter = create_rate_limiter(2, 1.0);

        AllowResult userAResult = allow_request(multiLimiter, "userA", 0.0);
        userAResult = allow_request(userAResult.newLimiter, "userA", 0.1);

        AllowResult userBResult = allow_request(userAResult.newLimiter, "userB", 0.2);
        userBResult = allow_request(userBResult.newLimiter, "userB", 0.3);

        System.out.println("UserA bucket: " + get_bucket_state(userBResult.newLimiter, "userA"));
        System.out.println("UserB bucket: " + get_bucket_state(userBResult.newLimiter, "userB"));

        // Test 5: Edge cases
        System.out.println("\n5. Edge cases test:");

        // Time going backwards
        RateLimiter timeLimiter = create_rate_limiter(3, 1.0);
        AllowResult timeResult = allow_request(timeLimiter, "user4", 5.0);
        timeResult = allow_request(timeResult.newLimiter, "user4", 3.0); // Earlier timestamp
        System.out.println("Request with earlier timestamp handled. Water level: " +
                get_bucket_state(timeResult.newLimiter, "user4").waterLevel);

        // Large time gap
        timeResult = allow_request(timeResult.newLimiter, "user4", 100.0);
        System.out.println("After large time gap, water level: " +
                get_bucket_state(timeResult.newLimiter, "user4").waterLevel);

        // First request for new user
        RateLimiter newUserLimiter = create_rate_limiter(2, 1.0);
        AllowResult newUserResult = allow_request(newUserLimiter, "newUser", 10.0);
        System.out.println("First request for new user: " + newUserResult.allowed +
                " (expected: true), water level: " +
                get_bucket_state(newUserResult.newLimiter, "newUser").waterLevel);
    }

    public static void main(String[] args) {
        runTests();
    }
}
