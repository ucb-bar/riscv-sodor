package zynq

import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import config._
import Common._
import diplomacy._
import Common.Util._
import ReferenceChipBackend._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import junctions._
import junctions.NastiConstants._
import uncore.tilelink2._
import config.{Parameters, Field}
import RV32_3stage._

object ReferenceChipBackend {
  val initMap = new HashMap[Module, Bool]()
}

case class MasterConfig(base: Long, size: Long, beatBytes: Int, idBits: Int)
case object ExtMem extends Field[MasterConfig]
case object DebugAddr extends Field[MasterConfig]
case object DebugAddrSlave extends Field[MasterConfig]
class WithZynqAdapter extends Config((site, here, up) => {
  case junctions.NastiKey => junctions.NastiParameters(dataBits = 32,
      addrBits = 32,idBits = 12)
  case ExtMem => MasterConfig(base= 0x80000000L, size= 0x10000000L, beatBytes= 4, idBits= 4)
  case DebugAddr => MasterConfig(base= 0x40000000L, size= 0x10000000L, beatBytes= 4, idBits= 4)
  case DebugAddrSlave => MasterConfig(base= 0x50000000L, size= 0x10000000L, beatBytes= 4, idBits= 4)
  case TLMonitorBuilder => (args: TLMonitorArgs) => None
  case TLCombinationalCheck => false
})

class Top extends Module {
  implicit val sodor_conf = SodorConfiguration()
  val inParams = new WithZynqAdapter
  val tile = LazyModule(new SodorTile()(sodor_conf,inParams)).module
  val io = IO(new Bundle {
    val ps_axi_slave = Flipped(tile.io.ps_slave.cloneType) //Flipped(new NastiIO()(inParams))
    val mem_axi = tile.io.mem_axi4.cloneType
  })
  io.mem_axi <> tile.io.mem_axi4
  tile.io.ps_slave <> io.ps_axi_slave
}


object elaborate extends ChiselFlatSpec{
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(args, () => new Top)
  }
}