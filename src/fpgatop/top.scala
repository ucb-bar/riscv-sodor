package zynq

import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}
import config._
import Common._
import Common.Util._
import ReferenceChipBackend._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import junctions._
import junctions.NastiConstants._
import config.{Parameters, Field}
import RV32_3stage._

object ReferenceChipBackend {
  val initMap = new HashMap[Module, Bool]()
}

class WithZynqAdapter extends Config((site, here, up) => {
  case junctions.NastiKey => junctions.NastiParameters(
      dataBits = 32,
      addrBits = 32,
      idBits = 12)
})

class Top extends Module {
  val inParams = new WithZynqAdapter
  val io = IO(new Bundle {
    val ps_axi_slave = Flipped(new NastiIO()(inParams))
    val mem_axi = new NastiIO()(inParams)
  })

  implicit val sodor_conf = SodorConfiguration()
  val tile = Module(new SodorTile)

}


object elaborate extends ChiselFlatSpec{
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(args, () => new Top)
  }
}