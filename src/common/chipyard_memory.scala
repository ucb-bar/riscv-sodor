package Common

import chisel3._
import chisel3.util._
import chisel3.experimental._

import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.model.OMSRAM
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class ScratchpadDataArray(implicit p: Parameters) extends L1HellaCacheModule()(p) {
  val io = new Bundle {
    val req = Valid(new DCacheDataReq).flip
    val resp = Vec(nWays, UInt(width = req.bits.wdata.getWidth)).asOutput
  }

  require(rowBytes % wordBytes == 0)
  val eccMask = if (eccBits == wordBits) Seq(true.B) else io.req.bits.eccMask.asBools
  val wMask = if (nWays == 1) eccMask else (0 until nWays).flatMap(i => eccMask.map(_ && io.req.bits.way_en(i)))
  val wWords = io.req.bits.wdata.grouped(encBits * (wordBits / eccBits))
  val addr = io.req.bits.addr >> rowOffBits
  val data_arrays = Seq.tabulate(rowBytes / wordBytes) {
    i =>
      DescribedSRAM(
        name = s"data_arrays_${i}",
        desc = "DCache Data Array",
        size = nSets * cacheBlockBytes / rowBytes,
        data = Vec(nWays * (wordBits / eccBits), UInt(width = encBits))
      )
  }

  val rdata = for (((array, omSRAM), i) <- data_arrays zipWithIndex) yield {
    val valid = io.req.valid && (Bool(data_arrays.size == 1) || io.req.bits.wordMask(i))
    when (valid && io.req.bits.write) {
      val wData = wWords(i).grouped(encBits)
      array.write(addr, Vec((0 until nWays).flatMap(i => wData)), wMask)
    }
    val data = array.read(addr, valid && !io.req.bits.write)
    data.grouped(wordBits / eccBits).map(_.asUInt).toSeq
  }
  (io.resp zip rdata.transpose).foreach { case (resp, data) => resp := data.asUInt }
}

class SodorScratchpad(implicit p: Parameters) extends LazyModule {
  val address = new AddressSet(0x80000000, 0xffffffff)  // TODO: What is the new address for the scratchpad?
  val slavePort = new ScratchpadSlavePort(address = address, coreDataBytes = 8, usingAtomics = False)
  

}