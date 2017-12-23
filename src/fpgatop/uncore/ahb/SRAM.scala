// See LICENSE.SiFive for license details.

package uncore.ahb

import Chisel._
import config._
import diplomacy._
import util._

class AHBRAM(address: AddressSet, executable: Boolean = true, beatBytes: Int = 4, fuzzHreadyout: Boolean = false)(implicit p: Parameters) extends LazyModule
{
  val node = AHBSlaveNode(Seq(AHBSlavePortParameters(
    Seq(AHBSlaveParameters(
      address       = List(address),
      regionType    = RegionType.UNCACHED,
      executable    = executable,
      supportsRead  = TransferSizes(1, beatBytes * AHBParameters.maxTransfer),
      supportsWrite = TransferSizes(1, beatBytes * AHBParameters.maxTransfer))),
    beatBytes  = beatBytes)))

  // We require the address range to include an entire beat (for the write mask)
  require ((address.mask & (beatBytes-1)) == beatBytes-1)

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val in = node.bundleIn
    }

    def bigBits(x: BigInt, tail: List[Boolean] = List.empty[Boolean]): List[Boolean] =
      if (x == 0) tail.reverse else bigBits(x >> 1, ((x & 1) == 1) :: tail)
    val mask = bigBits(address.mask >> log2Ceil(beatBytes))

    val in = io.in(0)

    // The mask and address during the address phase
    val a_access    = in.htrans === AHBParameters.TRANS_NONSEQ || in.htrans === AHBParameters.TRANS_SEQ
    val a_request   = in.hready && in.hsel && a_access
    val a_mask      = uncore.tilelink2.maskGen(in.haddr, in.hsize, beatBytes)
    val a_address   = Cat((mask zip (in.haddr >> log2Ceil(beatBytes)).toBools).filter(_._1).map(_._2).reverse)
    val a_write     = in.hwrite

    // The data phase signals
    val d_wdata = Vec.tabulate(beatBytes) { i => in.hwdata(8*(i+1)-1, 8*i) }

    // AHB writes must occur during the data phase; this poses a structural
    // hazard with reads which must occur during the address phase. To solve
    // this problem, we delay the writes until there is a free cycle.
    //
    // The idea is to record the address information from address phase and
    // then as soon as possible flush the pending write. This cannot be done
    // on a cycle when there is an address phase read, but on any other cycle
    // the write will execute. In the case of reads following a write, the
    // result must bypass data from the pending write into the read if they
    // happen to have matching address.

    // Pending write?
    val p_valid     = RegInit(Bool(false))
    val p_address   = Reg(a_address)
    val p_mask      = Reg(a_mask)
    val p_latch_d   = Reg(Bool())
    val p_wdata     = d_wdata holdUnless p_latch_d

    // Use single-ported memory with byte-write enable
    val mem = SeqMem(1 << mask.filter(b=>b).size, Vec(beatBytes, Bits(width = 8)))

    // Decide is the SRAM port is used for reading or (potentially) writing
    val read = a_request && !a_write
    // In case we choose to stall, we need to hold the read data
    val d_rdata = mem.readAndHold(a_address, read)
    // Whenever the port is not needed for reading, execute pending writes
    when (!read && p_valid) {
      p_valid := Bool(false)
      mem.write(p_address, p_wdata, p_mask.toBools)
    }

    // Record the request for later?
    p_latch_d := a_request && a_write
    when (a_request && a_write) {
      p_valid   := Bool(true)
      p_address := a_address
      p_mask    := a_mask
    }

    // Does the read need to be muxed with the previous write?
    val a_bypass = a_address === p_address && p_valid
    val d_bypass = RegEnable(a_bypass, a_request)

    // Mux in data from the pending write
    val muxdata = Vec((p_mask.toBools zip (p_wdata zip d_rdata))
                      map { case (m, (p, r)) => Mux(d_bypass && m, p, r) })

    // Don't fuzz hready when not in data phase
    val d_request = Reg(Bool(false))
    when (in.hready) { d_request := Bool(false) }
    when (a_request)  { d_request := Bool(true) }

    // Finally, the outputs
    in.hreadyout := (if(fuzzHreadyout) { !d_request || LFSR16(Bool(true))(0) } else { Bool(true) })
    in.hresp     := AHBParameters.RESP_OKAY
    in.hrdata    := Mux(in.hreadyout, muxdata.asUInt, UInt(0))
  }
}
