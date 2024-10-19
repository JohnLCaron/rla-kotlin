package org.cryptobiotic.rla.util

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.LinkedList

/**
 * A pseudo-random number generator based on Philip Stark's pseudo-random number
 * generator found at
 * [
 * https://www.stat.berkeley.edu/~stark/Java/Html/sha256Rand.htm](https://www.stat.berkeley.edu/~stark/Java/Html/sha256Rand.htm).
 *
 * @author Joey Dodds <jdodds></jdodds>@freeandfair.us>
 * @author Joseph R. Kiniry <kiniry></kiniry>@freeandfair.us>
 * @version 1.0.0
 * @review kiniry Why is this not just a static class?
 */
class PseudoRandomNumberGenerator(
    the_seed: String,
    the_with_replacement: Boolean,
    the_minimum: Int,
    the_maximum: Int
) {
    /**
     * The message digest we will use for generating hashes.
     */
    private val my_sha256_digest: MessageDigest

    /**
     * The random numbers generated so far.
     */
    private val my_random_numbers = mutableListOf<Int>()

    /**
     * The current number to use for generation.
     */
    //@ private invariant 0 <= my_count;
    private var my_count = 0

    /**
     * True if we should "replace" drawn numbers once they are drawn. True allows
     * repeats.
     */
    private val my_with_replacement: Boolean

    /**
     * The seed given, which must be at least of length MININUM_SEED_LENGTH and
     * whose contents must only be digits.
     */
    //@ private invariant MINIMUM_SEED_LENGTH <= my_seed.length();
    //@ private invariant seedOnlyContainsDigits(my_seed);
    private val my_seed: String

    /**
     * The minimum value to generate.
     */
    private val my_minimum: Int

    /**
     * The maximum value to generate.
     */
    private val my_maximum: Int

    //@ private invariant my_minimum <= my_maximum;
    /**
     * The maximum index that can be generated without replacement.
     */
    private val my_maximum_index: Int

    /**
     * Create a pseudo-random number generator with functionality identical to
     * Rivest's `sampler.py` example implementation in Python of an
     * RLA sampler.
     *
     * @param the_seed The seed to generate random numbers from
     * @param the_with_replacement True if duplicates can be generated
     * @param the_minimum The minimum value to generate
     * @param the_maximum The maximum value to generate
     */
    //@ requires 20 <= the_seed.length();
    //@ requires seedOnlyContainsDigits(the_seed);
    //@ requires the_minimum <= the_maximum;
    init {
        // @trace randomness.seed side condition
        assert(MINIMUM_SEED_LENGTH <= the_seed.length)

        my_sha256_digest = MessageDigest.getInstance("SHA-256")
        my_with_replacement = the_with_replacement
        my_seed = the_seed
        assert(the_minimum < the_maximum)
        my_minimum = the_minimum
        my_maximum = the_maximum
        my_maximum_index = my_maximum - my_minimum + 1
    }

    /**
     * Generate the specified list of random numbers.
     *
     * @param the_from the "index" of the first random number to give
     * @param the_to the "index" of the final random number to give
     *
     * @return A list containing the_to - the_from + 1 random numbers
     */
    //@ requires the_from <= the_to;
    // @todo kiniry Refine this specification to include public model fields.
    // requires my_with_replacement || the_to <= my_maximum_index;
    fun getRandomNumbers(the_from: Int, the_to: Int): List<Int> {
        assert(the_from <= the_to)
        assert(my_with_replacement || the_to <= my_maximum_index)
        if (the_to + 1 > my_random_numbers.size) {
            extendList(the_to + 1)
        }
        // subList has an exclusive upper bound, but we have an inclusive one
        return my_random_numbers.subList(the_from, the_to + 1)
    }

    /**
     * A helper function to extend the list of generated random numbers.
     * @param the_length the number of random numbers to generate.
     */
    //@ private behavior
    //@   requires 0 <= the_length;
    //@   ensures my_random_numbers.size() == the_length;
    private fun extendList(the_length: Int) {
        while (my_random_numbers.size < the_length) {
            generateNext()
        }
    }

    /**
     * Attempt to generate the next random number. This will either extend the
     * list of random numbers in length or leave it the same. It will always
     * advance the count.
     */
    fun generateNext() {
        my_count++
        assert(my_with_replacement || my_count <= my_maximum_index)

        val hash_input = my_seed + "," + my_count

        val hash_output =
            my_sha256_digest.digest(hash_input.toByteArray(StandardCharsets.UTF_8))
        val int_output = BigInteger(1, hash_output)

        val in_range =
            int_output.mod(BigInteger.valueOf((my_maximum - my_minimum + 1).toLong()))
        val pick = my_minimum + in_range.intValueExact()

        if (my_with_replacement || !my_random_numbers.contains(pick)) {
            my_random_numbers.add(pick)
        }
    }

    companion object {
        /**
         * The minimum seed length specified in CRLS, and hence our formal specification,
         * is 20 characters.
         * @trace corla.randomness.seed
         */
        const val MINIMUM_SEED_LENGTH: Int = 20

        /**
         * Checks to see if the passed potential seed only contains digits.
         * @param the_seed is the seed to check.
         */
        /*@ behavior
    @   ensures (\forall int i; 0 <= i && i < the_seed.length(); 
    @            Character.isDigit(the_seed.charAt(i)));
    @*/
        /*@ pure @*/ fun seedOnlyContainsDigits(the_seed: String): Boolean {
            for (i in 0 until the_seed.length) {
                if (!Character.isDigit(the_seed.get(i))) {
                    return false
                }
            }
            return true
        }
    }
}

