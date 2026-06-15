package gorda.driver.ui.service.current

import gorda.driver.utils.NumberHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TripFeeComputationTest {

    @Test
    fun unboundBranchFloorsToMinFeeWhenSnapshotIsNonEmpty() {
        val totalRide = 0.0
        val priceMinFee = 6000.0
        val feeMultiplier = 1.0

        val floor = NumberHelper.roundToMultipleOf500(priceMinFee * feeMultiplier)
        val effective = maxOf(totalRide, floor)

        assertEquals(6000.0, effective, 0.0)
    }

    @Test
    fun unboundBranchAppliesMultiplierAndRounding() {
        val totalRide = 0.0
        val priceMinFee = 6000.0
        val feeMultiplier = 1.3  // 6000 × 1.3 = 7800 → round500 → 8000

        val floor = NumberHelper.roundToMultipleOf500(priceMinFee * feeMultiplier)
        val effective = maxOf(totalRide, floor)

        assertEquals(8000.0, effective, 0.0)
    }

    @Test
    fun unboundBranchPreservesFeeAboveFloor() {
        val totalRide = 18500.0
        val priceMinFee = 6000.0
        val feeMultiplier = 1.0

        val floor = NumberHelper.roundToMultipleOf500(priceMinFee * feeMultiplier)
        val effective = maxOf(totalRide, floor)

        assertEquals(18500.0, effective, 0.0)
    }

    @Test
    fun emptySnapshotGuardYieldsNullTripFeeWhenFeeIsZero() {
        val totalRide = 0.0
        val priceMinFee = 0.0  // empty snapshot
        val feeMultiplier = 1.0

        val floor = if (priceMinFee > 0.0) {
            NumberHelper.roundToMultipleOf500(priceMinFee * feeMultiplier)
        } else {
            0.0
        }
        val effective = maxOf(totalRide, floor)
        val rawFee = Math.round(effective / 100) * 100  // NumberHelper.roundDouble logic
        val tripFee: Int? = if (rawFee.toInt() == 0) null else rawFee.toInt()

        assertNull(tripFee)
    }

    @Test
    fun nonZeroFeeIsNeverNullledOut() {
        val totalRide = 12000.0
        val rawFee = (Math.round(totalRide / 100) * 100).toInt()
        val tripFee: Int? = if (rawFee == 0) null else rawFee

        assertNotNull(tripFee)
        assertEquals(12000, tripFee)
    }
}
