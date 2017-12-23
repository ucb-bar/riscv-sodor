package zynq

import chisel3._
import chisel3.iotesters._
import config._
import diplomacy._
import scala.collection.mutable.HashMap
import uncore.tilelink2._
import RV32_3stage.Constants._

object ReferenceChipBackend {
  val initMap = new HashMap[Module, Bool]()
}

case class MasterConfig(base: Long, size: Long, beatBytes: Int, idBits: Int)
case object ExtMem extends Field[MasterConfig]
case object MMIO extends Field[MasterConfig]
case object DebugAddrSlave extends Field[MasterConfig]
class WithZynqAdapter extends Config((site, here, up) => {
  case junctions.NastiKey => junctions.NastiParameters(dataBits = 32,
      addrBits = 32,idBits = 12)
  case ExtMem => MasterConfig(base= 0x10000000L, size= 0x10000000L, beatBytes= 4, idBits= 4)
  case MMIO => MasterConfig(base= 0x40000000L, size= 0x10000L, beatBytes= 4, idBits= 4)
  case DebugAddrSlave => MasterConfig(base= 0x40000000L, size= 0x10000000L, beatBytes= 4, idBits= 4)
  case TLMonitorBuilder => (args: TLMonitorArgs) => None
  case TLCombinationalCheck => false
  case Common.xprlen => 32
  case Common.usingUser => false
  case NUM_MEMORY_PORTS => 2
  case PREDICT_PCP4 => true
  case Common.PRINT_COMMIT_LOG => false
})

class Top extends Module {
  val inParams = new WithZynqAdapter
  val tile = LazyModule(new SodorTile()(inParams)).module
  val io = IO(new Bundle {
    val ps_axi_slave = Flipped(tile.io.ps_slave.cloneType)
    val mem_axi = tile.io.mem_axi4.cloneType
  })
  io.mem_axi <> tile.io.mem_axi4
  tile.io.ps_slave <> io.ps_axi_slave
}

object elaborate extends ChiselFlatSpec{
  def main(args: Array[String]): Unit = {
    implicit val inParams = new WithZynqAdapter
    if(!args.isEmpty && args(0) == "testtop")
      assertTesterPasses(new TopTests,additionalVResources = Seq("/SyncMem.sv"))
    else 
      chisel3.Driver.execute(args, () => new Top)
  }
}