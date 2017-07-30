package zynq

import chisel3._
import chisel3.util._
import chisel3.iotesters._
import chisel3.testers._
import config._
import Common._
import diplomacy._
import Common.Util._
import ReferenceChipBackend._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import junctions._
import unittest._
import junctions.NastiConstants._
import uncore.tilelink2._
import config.{Parameters, Field}
import RV32_3stage._

object ReferenceChipBackend {
  val initMap = new HashMap[Module, Bool]()
}

case class MasterConfig(base: Long, size: Long, beatBytes: Int, idBits: Int)
case object ExtMem extends Field[MasterConfig]
case object DebugAddrSlave extends Field[MasterConfig]
class WithZynqAdapter extends Config((site, here, up) => {
  case junctions.NastiKey => junctions.NastiParameters(dataBits = 32,
      addrBits = 32,idBits = 12)
  case ExtMem => MasterConfig(base= 0x60000000L, size= 0x10000000L, beatBytes= 4, idBits= 4)
  case DebugAddrSlave => MasterConfig(base= 0x40000000L, size= 0x10000000L, beatBytes= 4, idBits= 4)
  case TLMonitorBuilder => (args: TLMonitorArgs) => None
  case TLCombinationalCheck => false
  //case UnitTests => Seq(new TLMulticlientXbarTest(1,2))(this)
})

class Top extends Module {
  implicit val sodor_conf = SodorConfiguration()
  val inParams = new WithZynqAdapter
  inParams.alterPartial({case UnitTests => new TLMulticlientXbarTest(1,2)(inParams)})
  val tile = LazyModule(new SodorTile()(sodor_conf,inParams)).module
  val io = IO(new Bundle {
    val ps_axi_slave = Flipped(tile.io.ps_slave.cloneType)
    val mem_axi = tile.io.mem_axi4.cloneType
  })
  io.mem_axi <> tile.io.mem_axi4
  tile.io.ps_slave <> io.ps_axi_slave
}

class TopTests extends SteppedHWIOTester {
  def reqW(addr: BigInt,data: BigInt) = {
    poke(device_under_test.io.ps_axi_slave(0).aw.valid, 1)
    poke(device_under_test.io.ps_axi_slave(0).w.valid, 1)
    poke(device_under_test.io.ps_axi_slave(0).aw.bits.addr, addr)
    poke(device_under_test.io.ps_axi_slave(0).aw.bits.len, 1L)
    poke(device_under_test.io.ps_axi_slave(0).aw.bits.size, 2L)
    poke(device_under_test.io.ps_axi_slave(0).w.bits.data, data)
    poke(device_under_test.io.ps_axi_slave(0).b.ready, 1)
  }
  def resetWReq = {
    poke(device_under_test.io.ps_axi_slave(0).aw.valid, 0)
    poke(device_under_test.io.ps_axi_slave(0).w.valid, 0)
  }
  def doneW = {
    expect(device_under_test.io.ps_axi_slave(0).b.valid, true)
    poke(device_under_test.io.ps_axi_slave(0).b.ready, 0)
  }
  def reqR(addr: BigInt) = {
    poke(device_under_test.io.ps_axi_slave(0).ar.valid, 1)
    poke(device_under_test.io.ps_axi_slave(0).ar.bits.addr, addr)
    poke(device_under_test.io.ps_axi_slave(0).ar.bits.len, 1L)
    poke(device_under_test.io.ps_axi_slave(0).ar.bits.size, 2L) 
  }
  def doneR = {
    poke(device_under_test.io.ps_axi_slave(0).r.ready, 1)
    poke(device_under_test.io.ps_axi_slave(0).ar.valid, 0)
  }
  def checkRResp(data: BigInt) = {
    expect(device_under_test.io.ps_axi_slave(0).r.bits.data, data)
    expect(device_under_test.io.ps_axi_slave(0).r.valid, true)
  }  
  
  enable_scala_debug = false
  enable_printf_debug = false
  val device_under_test = Module(new Top())
  
    poke(device_under_test.io.mem_axi(0).aw.ready , 1)
    poke(device_under_test.io.mem_axi(0).w.ready , 1)
    reqW(0x40000010L,0x35353535L)
    expect(device_under_test.io.ps_axi_slave(0).aw.ready, true)
    expect(device_under_test.io.ps_axi_slave(0).w.ready, true)
  step(3)
    doneW
    resetWReq
    reqR(0x40000010L)  
  step(3)
    checkRResp(0x35353535L)
    doneR
    reqW(0x400000E4L,0x60000000L)
  step(3)
    doneW
    resetWReq
    reqR(0x400000F0L)
  step(2)  
    expect(device_under_test.io.mem_axi(0).ar.bits.addr , 0x60000000L)
    expect(device_under_test.io.mem_axi(0).ar.valid , true)
    poke(device_under_test.io.mem_axi(0).r.valid, 1)
    poke(device_under_test.io.mem_axi(0).r.bits.data, 85)
  step(3)
    expect(device_under_test.io.ps_axi_slave(0).r.valid, true)
    expect(device_under_test.io.ps_axi_slave(0).r.bits.data, 85L)
    /*reqW(0x400000F0L,0x03040304L)
  step(3)
    expect(device_under_test.io.mem_axi(0).w.bits.data , 0x03040304L)
    expect(device_under_test.io.mem_axi(0).aw.bits.addr , 0x60000000L)
    expect(device_under_test.io.mem_axi(0).w.valid , true)
    expect(device_under_test.io.mem_axi(0).aw.valid , true)*/
}

class TestHarnessTester(dut: TestHarness) extends PeekPokeTester(dut) {
  when(dut.io.success === true.B){
    printf("done\n")
  }
}

object elaborate extends ChiselFlatSpec{
  def main(args: Array[String]): Unit = {
    implicit val inParams = new WithZynqAdapter
    if(!args.isEmpty && args(0) == "testtop")
      assertTesterPasses(new TopTests,additionalVResources = Seq("/SyncMem.sv"))
/*    else if(!args.isEmpty && args(0) == "testxbar"){
      chisel3.iotesters.Driver(() => new TestHarness()(inParams),"firrtl") {
        c => new TestHarnessTester(c)
      }should be(true)
    }*/
    else 
      chisel3.Driver.execute(args, () => new Top)
  }
}